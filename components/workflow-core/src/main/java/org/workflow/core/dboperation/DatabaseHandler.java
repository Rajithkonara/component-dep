package org.workflow.core.dboperation;

import com.wso2telco.core.dbutils.DbUtils;
import com.wso2telco.core.dbutils.exception.BusinessException;
import com.wso2telco.core.dbutils.exception.GenaralError;
import com.wso2telco.core.dbutils.util.DataSourceNames;
import edu.emory.mathcs.backport.java.util.Collections;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.workflow.core.model.HistoryDetails;
import org.workflow.core.model.HistoryResponse;
import org.workflow.core.model.HistorySearchDTO;
import org.workflow.core.util.Tables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Copyright (c) 2016, WSO2.Telco Inc. (http://www.wso2telco.com) All Rights Reserved.
 * <p>
 * WSO2.Telco Inc. licences this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class DatabaseHandler {

    protected Log log;
    private static final String ALL = "__ALL__";

    public DatabaseHandler() {
        log = LogFactory.getLog(DatabaseHandler.class);
    }


    public int getSubscriberkey(String userid) throws BusinessException {
        StringBuilder sql = new StringBuilder();
        sql.append("select subscriber_id from ")
                .append(Tables.AM_SUBSCRIBER.getTObject())
                .append(" WHERE USER_ID = ?");
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        int subscriber = 0;
        try {
            conn = DbUtils.getDbConnection(DataSourceNames.WSO2AM_DB);
            ps = conn.prepareStatement(sql.toString());
            ps.setString(1, userid);
            rs = ps.executeQuery();
            if (rs.next()) {
                subscriber = rs.getInt("subscriber_id");
            } else {
                subscriber = 0;
            }

        } catch (Exception e) {
            handleException("getSubscriberkey", e);
        } finally {
            DbUtils.closeAllConnections(ps, conn, rs);
        }
        return subscriber;
    }

    /**
     * Handle exception.
     *
     * @param msg the msg
     * @param t   the t
     * @throws Exception the exception
     */
    public void handleException(String msg, Throwable t) throws BusinessException {
        log.error(msg, t);
        throw new BusinessException(GenaralError.INTERNAL_SERVER_ERROR);
    }

    public HistoryResponse getApprovalHistory(String subscriber, String applicationName, int applicationId, String operator, String status, int offset, int count) throws BusinessException {

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        StringBuilder sql = new StringBuilder();
        List<HistoryDetails> applist = new ArrayList<HistoryDetails>();
        HistoryResponse historyResponse = new HistoryResponse();
        String depDB = DbUtils.getDbNames().get(DataSourceNames.WSO2TELCO_DEP_DB);
        String apimgtDB = DbUtils.getDbNames().get(DataSourceNames.WSO2AM_DB);

        sql.append("SELECT * FROM ")
                .append("(SELECT application_id, name,created_by,IF(description IS NULL, " +
                        "'Not Specified', description) AS description,")
                .append("ELT(FIELD(application_status, 'CREATED', 'APPROVED', 'REJECTED'), " +
                        "'PENDING APPROVE', 'APPROVED', 'REJECTED') AS app_status,")
                .append("(SELECT GROUP_CONCAT(opco.operatorname SEPARATOR ',') FROM " +
                        depDB + "." + Tables.DEP_OPERATOR_APPS.getTObject() + " opcoApp ")
                .append("INNER JOIN " + depDB + "." + Tables.DEP_OPERATORS.getTObject()
                        + " opco ON opcoApp.operatorid = opco.id ")
                .append("WHERE opcoApp.isactive = 1 AND opcoApp.applicationid = " +
                        "amapp.application_id GROUP BY opcoApp.applicationid) AS oparators ")
                .append("FROM " + apimgtDB + "." + Tables.AM_APPLICATION.getTObject() + " amapp ")
                .append("WHERE " +
                        "EXISTS( SELECT 1 FROM "
                        + depDB + "." +
                        Tables.DEP_OPERATOR_APPS.getTObject() + " opcoApp ")
                .append("INNER JOIN " + depDB + "." +
                        Tables.DEP_OPERATORS.getTObject() + " opco" +
                        " ON opcoApp.operatorid = opco.id ")
                .append("WHERE opcoApp.isactive LIKE ? AND opcoApp.applicationid " +
                        "= amapp.application_id AND ")
                .append("opco.operatorname LIKE ? AND amapp.application_id LIKE ?" +
                        " AND amapp.name LIKE ? AND amapp.subscriber_id LIKE ? ) ");

            if(status!=null && !status.isEmpty()&& !status.equals(ALL)) {
            	sql	.append("AND amapp.application_status LIKE ? ");
            }

             sql.append("ORDER BY application_id) t")
                .append(" LIMIT ?,?");

        if (!subscriber.equals(ALL)) {
            subscriber = String.valueOf(getSubscriberkey(subscriber));
        }

        try {
            conn = DbUtils.getDbConnection(DataSourceNames.WSO2AM_DB);
            ps = conn.prepareStatement(sql.toString());
            if (operator.equals(ALL)) {
                ps.setString(2, "%");
                ps.setString(1, "%");
            } else {
                ps.setString(2, operator);
                ps.setString(1,"1");
            }
            if (applicationId == 0) {
                ps.setString(3, "%");
            } else {
                ps.setInt(3, applicationId);
            }

            if (applicationName.equals(ALL)) {
                ps.setString(4, "%");
            } else {
                ps.setString(4, applicationName);
            }

            if (subscriber.equals(ALL)) {
                ps.setString(5, "%");
            } else {
                ps.setInt(5, Integer.parseInt(subscriber));
            }

             if (status!=null && !status.isEmpty() && !status.equals(ALL))  {
                ps.setString(6, status);
                 
                 ps.setInt(7, offset);
                 ps.setInt(8, count);
            }else{
                ps.setInt(6, offset);
                ps.setInt(7, count);
             }

           

            log.debug("get Operator Wise API Traffic");

            int size = 0;
            rs = ps.executeQuery();
            while (rs.next()) {
                /** Does not consider default application */
                if (!rs.getString("name").equalsIgnoreCase("DefaultApplication")) {
                    applist.add(new HistoryDetails(rs));
                    size++;
                }
            }

            historyResponse.setApplications(applist);
            historyResponse.setStart(offset);
            historyResponse.setSize(size);
            historyResponse.setTotal(getApplicationCount(applicationId, applicationName, subscriber, operator, status));


        } catch (Exception e) {
            handleException("getApprovalHistory", e);
        } finally {
            DbUtils.closeAllConnections(ps, conn, rs);
        }
        return historyResponse;
    }
    public HistoryResponse getApprovalHistory(HistorySearchDTO searchDTO) throws BusinessException {

        Connection conn = null;
        PreparedStatement mainPs = null;
        PreparedStatement countPs = null;
        ResultSet rs = null;
        ResultSet count_result = null;
        StringBuilder select_sql = new StringBuilder();
        StringBuilder where_clause = new StringBuilder();
        StringBuilder count_sql = new StringBuilder();
        StringBuilder result_sql = new StringBuilder();
        List<HistoryDetails> applist = new ArrayList<HistoryDetails>();
        HistoryResponse historyResponse = new HistoryResponse();
        String depDB = DbUtils.getDbNames().get(DataSourceNames.WSO2TELCO_DEP_DB);
        String apimgtDB = DbUtils.getDbNames().get(DataSourceNames.WSO2AM_DB);
        List<Object> paramList = new ArrayList<Object>();

        select_sql.append("SELECT * FROM ")
                .append("(SELECT application_id, name,created_by,IF(description IS NULL, " +
                        "'Not Specified', description) AS description,")
                .append("ELT(FIELD(application_status, 'CREATED', 'APPROVED', 'REJECTED'), " +
                        "'PENDING APPROVE', 'APPROVED', 'REJECTED') AS app_status,")
                .append("(SELECT GROUP_CONCAT(opco.operatorname SEPARATOR ',') FROM " +
                        depDB + "." + Tables.DEP_OPERATOR_APPS.getTObject() + " opcoApp ")
                .append("INNER JOIN " + depDB + "." + Tables.DEP_OPERATORS.getTObject()
                        + " opco ON opcoApp.operatorid = opco.id ")
                .append("WHERE opcoApp.isactive = 1 AND opcoApp.applicationid = " +
                        "amapp.application_id GROUP BY opcoApp.applicationid) AS oparators ");






        where_clause.append("FROM ").append( apimgtDB).append( "." )
                .append( Tables.AM_APPLICATION.getTObject() ).append( " amapp ")
                .append(" WHERE   ")
                .append(" EXISTS( SELECT 1 FROM " )
                						.append(depDB ).append( "." )
                .append( Tables.DEP_OPERATOR_APPS.getTObject() ).append( " opcoApp ")
                .append("INNER JOIN ").append( depDB ).append( ".")
                .append( Tables.DEP_OPERATORS.getTObject() ).append( " opco ")
                .append(" ON opcoApp.operatorid = opco.id ")
                .append("WHERE  opcoApp.applicationid = amapp.application_id  ");
        								
        								/*
        								 * if oparator is not null ck for active oparatos
        								 */
        								if(searchDTO.getOperator()!=null && !searchDTO.getOperator().isEmpty()) {

        								    if (searchDTO.getOperator().equals(ALL)) {
                                                paramList.add("%");
                                                paramList.add("%");
                                            } else {
                                                paramList.add(Integer.valueOf(1));
                                                paramList.add(searchDTO.getOperator().trim());
                                            }

                                            where_clause.append(" AND opcoApp.isactive LIKE ? ")
        										.append(" AND opco.operatorname LIKE ?");
        								}
        								/**
        								 * if application id is provied
        								 */
        								if(searchDTO.getApplicationId()!=0 ) {
        									
        									paramList.add(Integer.valueOf(searchDTO.getApplicationId()));

                                            where_clause.append(" AND amapp.application_id = ? " );
        								}
        								/**
        								 * if application name is provied
        								 */
        								if(searchDTO.getApplicationName()!=null && !searchDTO.getApplicationName().isEmpty()) {
        									
        									paramList.add(searchDTO.getApplicationName().trim());

                                            where_clause.append(" AND  amapp.name LIKE ? " );
        								}
        								
        								/**
        								 * if subscription id provied
        								 */
        								if(searchDTO.getSubscriber()!=null && !searchDTO.getSubscriber().isEmpty()) {
        									
        									paramList.add(searchDTO.getSubscriber());

                                            where_clause.append(" AND EXISTS (SELECT 1  FROM " )
        										.append(apimgtDB + "." + Tables.AM_SUBSCRIBER.getTObject()).append(" sub ")
        						                .append(" WHERE  amapp.subscriber_id = sub.subscriber_id AND ")
        						                .append("sub.USER_ID like ?");
        									  
        									  
        								}
        								//Close the sub Query
                    where_clause.append(") )");
        								
        
            if(searchDTO.getStatus()!=null && !searchDTO.getStatus().isEmpty()) {
            	paramList.add(searchDTO.getStatus().trim());
                where_clause.append("AND amapp.application_status LIKE ? ");
            }

            where_clause.append(")");




            count_sql.append("( select count(*) as total ").
                   append(where_clause);

            result_sql.append(select_sql).append(where_clause).append(" t").
                    append(" LIMIT ?,? ");


        try {
            conn = DbUtils.getDbConnection(DataSourceNames.WSO2AM_DB);
            mainPs = conn.prepareStatement(result_sql.toString());
            countPs = conn.prepareStatement(count_sql.toString());

            log.debug("get Operator Wise API Traffic");

            int size = 0;

            setParams(countPs,paramList);

            /**
             * adding limit parameters
             */
            List<Object>  paramList_main = new ArrayList (paramList);
            paramList_main.add(searchDTO.getStart());
            paramList_main.add(searchDTO.getBatchSize()+searchDTO.getStart());
            /**
             * set the parameters for main query
             */

            setParams(mainPs,paramList_main);

            count_result  = countPs.executeQuery();

            int total =0;
            if (count_result.next()) {
                total = count_result.getInt("total");
            }

            if(total>0){
                rs = mainPs.executeQuery();

                while (rs.next()) {
                    /** Does not consider default application */
                    if (!rs.getString("name").equalsIgnoreCase("DefaultApplication")) {
                        applist.add(new HistoryDetails(rs));
                        size++;
                    }
                }

                historyResponse.setApplications(applist);
            } else {
                historyResponse.setApplications(Collections.emptyList());
            }
            historyResponse.setStart(searchDTO.getStart());
            historyResponse.setSize((int)searchDTO.getBatchSize());
            historyResponse.setTotal(total);


        } catch (Exception e) {
            handleException("getApprovalHistory", e);
        } finally {
            DbUtils.closeAllConnections(mainPs, conn, rs);
            DbUtils.closeAllConnections(countPs, null , count_result);
        }
        return historyResponse;
    }
    
    
    private void setParams(PreparedStatement ps,List<Object> param) throws SQLException {
     for(int x =0; x<param.size();x++) {
    	 ps.setObject(x+1, param.get(x));
     }
    	
    }

    public int getApplicationCount(int applicationId, String applicationName, String subscriber, String operator, String status) throws BusinessException {

        StringBuilder sql = new StringBuilder();
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        String depDB = DbUtils.getDbNames().get(DataSourceNames.WSO2TELCO_DEP_DB);
        String apimgtDB = DbUtils.getDbNames().get(DataSourceNames.WSO2AM_DB);
        int count = 0;

        sql.append("SELECT count(*) as count FROM ")
                .append("(SELECT application_id, name,created_by,IF(description IS NULL, 'Not Specified', description) AS description,")
                .append("ELT(FIELD(application_status, 'CREATED', 'APPROVED', 'REJECTED'), 'PENDING APPROVE', 'APPROVED', 'REJECTED') AS app_status,")
                .append("(SELECT GROUP_CONCAT(opco.operatorname SEPARATOR ',') FROM " + depDB + "." + Tables.DEP_OPERATOR_APPS.getTObject())
                .append(" opcoApp INNER JOIN " + depDB + "." + Tables.DEP_OPERATORS.getTObject() + " opco ON opcoApp.operatorid = opco.id WHERE ")
                .append("opcoApp.isactive = 1 AND opcoApp.applicationid = amapp.application_id GROUP BY opcoApp.applicationid) AS oparators ")
                .append("FROM " + apimgtDB + "." + Tables.AM_APPLICATION.getTObject() + " amapp ")
                .append("WHERE EXISTS( SELECT 1 FROM " + depDB + "." + Tables.DEP_OPERATOR_APPS.getTObject())
                .append(" opcoApp INNER JOIN " + depDB + "." + Tables.DEP_OPERATORS.getTObject() + " opco ON opcoApp.operatorid = opco.id WHERE ")
                .append("opcoApp.isactive LIKE ? AND opcoApp.applicationid = amapp.application_id AND ")
                .append("opco.operatorname LIKE ? AND amapp.application_id LIKE ? AND amapp.name LIKE ? AND amapp.subscriber_id LIKE ? ) ")
                .append("AND amapp.application_status LIKE ? ")
                .append("ORDER BY application_id) t");

        try {
            conn = DbUtils.getDbConnection(DataSourceNames.WSO2AM_DB);
            ps = conn.prepareStatement(sql.toString());
            if (operator.equals(ALL)) {
                ps.setString(1, "%");
                ps.setString(2, "%");
            } else {
                ps.setString(2, operator);
                ps.setString(1,"1");
            }
            if (applicationId == 0) {
                ps.setString(3, "%");
            } else {
                ps.setInt(3, applicationId);
            }

            if (applicationName.equals(ALL)) {
                ps.setString(4, "%");
            } else {
                ps.setString(4, applicationName);
            }

            if (subscriber.equals(ALL)) {
                ps.setString(5, "%");
            } else {
                ps.setInt(5, Integer.parseInt(subscriber));
            }

            if (status.equals(ALL)) {
                ps.setString(6, "%");
            } else {
                ps.setString(6, status);
            }

            rs = ps.executeQuery();
            if (rs.next()) {
                count = rs.getInt(1);
            }

        } catch (Exception e) {
            handleException("getSubscriberkey", e);
        } finally {
            DbUtils.closeAllConnections(ps, conn, rs);
        }

        return count;
    }
}
