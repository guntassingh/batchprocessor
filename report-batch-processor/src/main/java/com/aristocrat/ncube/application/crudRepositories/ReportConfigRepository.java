package com.aristocrat.ncube.application.crudRepositories;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.aristocrat.ncube.application.beans.ReportConfig;

public interface ReportConfigRepository  extends MongoRepository<ReportConfig, String> {
	
}
