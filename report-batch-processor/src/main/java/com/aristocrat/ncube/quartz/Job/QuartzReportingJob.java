package com.aristocrat.ncube.quartz.Job;


import java.util.Map;
import java.util.UUID;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.configuration.JobLocator;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

import com.aristocrat.ncube.quartz.beans.JobInfo;

public class QuartzReportingJob extends QuartzJobBean {
	 
	@Autowired
    private JobLauncher jobLauncher;
     
    @Autowired
    private JobLocator jobLocator;
    
    private static final String JOB_NAME = "reportingJob";
 
 
    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException 
    {
        try
        {
        	Map < String, Object > jobDataMap = context.getMergedJobDataMap();
        	JobInfo info= (JobInfo) jobDataMap.get("jobInfo");
        	Job job = jobLocator.getJob(JOB_NAME);
            JobParameters params = new JobParametersBuilder()
                    .addString("instance_id", UUID.randomUUID().toString(), true)
                    .addString("collectionName", info.getSourceCollection())
                    .addString("queryDocument", info.getQuery())
                    .toJobParameters();
 
            jobLauncher.run(job, params);
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
        }
    }
}