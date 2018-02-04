package org.workflow.core.model;

import java.io.Serializable;

abstract class SearchDTO implements Serializable{
 
	private byte batchSize = 10;
	private int start = 0;
	private String orderBy = "desc";
	private String filterBy;
	private static final byte MINBATCHSIZE = 0;
	private static final byte MAXBATCHSIZE = 0;
	
	private String sortBy ;
	public byte getBatchSize() {
		return batchSize;
	}
	public void setBatchSize(byte batchSize) {
		this.batchSize = batchSize;
	}
	public int getStart() {
		return start;
	}
	public void setStart(int start) {
		this.start = start;
	}
	public String getOrderBy() {
		return orderBy;
	}
	public void setOrderBy(String orderBy) {
		this.orderBy = orderBy;
	}
	public String getFilterBy() {
		return filterBy;
	}
	public void setFilterBy(String filterBy) {
		this.filterBy = filterBy;
	}
	public String getSortBy() {
		return sortBy;
	}
	public void setSortBy(String sortBy) {
		this.sortBy = sortBy;
	}
	
	
}
