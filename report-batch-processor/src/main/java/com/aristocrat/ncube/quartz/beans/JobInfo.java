package com.aristocrat.ncube.quartz.beans;

import lombok.Data;

@Data
public class JobInfo {
	
	private String query;
	private String sourceCollection;
	private String reportName;
	
}
