package org.workflow.core.service.app;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.logging.LogFactory;
import org.workflow.core.execption.WorkflowExtensionException;
import org.workflow.core.model.Range;
import org.workflow.core.model.TaskList;
import org.workflow.core.model.TaskSerchDTO;
import org.workflow.core.model.TaskVariableResponse;
import org.workflow.core.service.ReturnableResponse;
import org.workflow.core.util.AppVariable;
import org.workflow.core.util.DeploymentTypes;

import com.wso2telco.core.dbutils.exception.BusinessException;
import com.wso2telco.core.dbutils.model.UserProfileDTO;
import com.wso2telco.core.dbutils.util.Callback;

class DefaultAppRequestBuilder extends AbsractQueryBuilder {

	private static DefaultAppRequestBuilder instance;

	private DefaultAppRequestBuilder() throws BusinessException {
		super.LOG = LogFactory.getLog(DefaultAppRequestBuilder.class);

	}

	public static DefaultAppRequestBuilder getInstace() throws BusinessException {
		if (instance == null) {
			instance = new DefaultAppRequestBuilder();
		}
		return instance;
	}


	private ReturnableResponse generateResponse(final TaskSerchDTO searchDTO,final TaskList taskList ,final UserProfileDTO userProfile) throws ParseException {

		return  new ReturnableResponse() {

			DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX",Locale.ENGLISH);
			
			@Override
			public int getTotal() {
				return taskList.getTotal();
			}

			@Override
			public int getStrat() {
				return taskList.getStart();
			}

			@Override
			public int getBatchSize() {
				return taskList.getSizel();
			}

			@Override
			public String getFilterBy() {
				return searchDTO.getFilterBy();
			}
			

			@Override
			public String getOrderBy() {
				return searchDTO.getOrderBy();
			}

			@Override
			public List<ReturnableTaskResponse> getTasks() {
				List<ReturnableTaskResponse> temptaskList =new ArrayList<ReturnableResponse.ReturnableTaskResponse>();
				
				for ( final TaskList.Task task : taskList.getData()) {
					final Map<AppVariable,TaskVariableResponse> varMap = new HashMap<AppVariable, TaskVariableResponse>();
					 for (final TaskVariableResponse var : task.getVars()) {
							varMap.put( AppVariable.getByKey(var.getName()),var);
						}
					 
					 ReturnableTaskResponse responseTask= new ReturnableTaskResponse() {
						 /**
							 * return task ID
							 */ 
							public int getID() {
								return task.getId();
							}
							
							public String getName() {
								return varMap.get(AppVariable.NAME).getValue() ;
							}
							public String getDescription() {
								return varMap.get(AppVariable.DESCRIPTION).getValue() ;
							}
							
							public String getCreatedDate() {
								return format.format( task.getCreateTime());
							}
							
							public String getTier() {
								return varMap.get(AppVariable.TIER).getValue() ;
							}
							
							public String getAssinee() {
								return task.getAssignee();
							}
					 };
					 
					 temptaskList.add(responseTask);
					
				}
				
				return temptaskList;
			}
			  
		  };
	}
	
	@Override
	protected Callback buildResponse(TaskSerchDTO searchDTO, TaskList taskList, UserProfileDTO userProfile)
			throws BusinessException {
		ReturnableResponse payload;
		Callback returnCall;
		try {
			payload = generateResponse( searchDTO,taskList, userProfile);
			returnCall= new Callback().setPayload(payload)
					.setSuccess(true)
					.setMessage("Application Taks listed success ");
		} catch (ParseException e) {
			returnCall= new Callback().setPayload(null)
					.setSuccess(false)
					.setMessage("Application Taks listed fail ");
		}
		
		 return returnCall;
	}

	@Override
	protected DeploymentTypes getDeployementType() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected Map<String, String> getFilterMap() {
		Map<String, String> filter = new HashMap<String, String>();
		filter.put("name", AppVariable.NAME.key());
		filter.put("applicationname", AppVariable.NAME.key());
		filter.put("appname", AppVariable.NAME.key());
		filter.put("tier", AppVariable.TIER.key());
		filter.put("createdby", AppVariable.USERNAME.key());
		filter.put("owner", AppVariable.USERNAME.key());
		return filter;

	}

	@Override
	protected List<Integer> getHistoricalData(String authHeader, String type, String user, List<Range> months) {
		String process = (type.equals("applications")) ? "application_creation_approval_process" : "subscription_approval_process";
		List<Integer> data = new ArrayList<>();

		TaskList taskList = null;

		for (Range month : months) {

			try {
				taskList = activityClient.getHistoricTasks(month.getStart(), month.getEnd(), process, user);
				data.add(taskList.getTotal());

			} catch (WorkflowExtensionException e) {
				LOG.error("", e);
				throw new BusinessException(e);
			}
		}

		return data;
	}

}
