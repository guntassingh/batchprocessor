package com.aristocrat.ncube.application.beans;


import org.bson.BsonTimestamp;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.aristocrat.ncube.quartz.beans.JobInfo;
import com.aristocrat.ncube.quartz.beans.TimerInfo;

import lombok.Data;

@Data
@Document(collection = "reportConfig")
public class ReportConfig {
	
	@Id
	private String id;
	private String query;
	private String sourceCollection;
	private String reportName;
	private String userName;
	private String frequency;
	private String description;
	private boolean enabled;
	private BsonTimestamp createdTime;
	private BsonTimestamp updatedTime;
	private String[] emailAddresses;
	
	public JobInfo getJobInfo() {
		JobInfo info = new JobInfo();
		info.setQuery(query);
		info.setReportName(reportName);
		info.setSourceCollection(sourceCollection);
		return info;
	}
	
	public TimerInfo getTimerInfo() {
		TimerInfo info = new TimerInfo();
		info.setCron(true);
		info.setCronSchedule(frequency);
		return info;
	}

}
