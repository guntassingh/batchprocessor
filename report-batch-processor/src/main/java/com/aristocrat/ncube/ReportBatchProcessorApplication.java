package com.aristocrat.ncube;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class ReportBatchProcessorApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReportBatchProcessorApplication.class, args);
	}

}
