package com.aristocrat.ncube.quartz.utils;


import java.util.Date;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.ScheduleBuilder;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.batch.core.configuration.JobLocator;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;

import com.aristocrat.ncube.quartz.beans.JobInfo;
import com.aristocrat.ncube.quartz.beans.TimerInfo;

public final class TimerUtils {
    private TimerUtils() {}
    

    public static JobDetail buildJobDetail(final Class jobClass, final JobInfo info) {
        final JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put(jobClass.getSimpleName(), info);
        jobDataMap.put("jobInfo", info);
        
        return JobBuilder
                .newJob(jobClass)
                .withIdentity(jobClass.getSimpleName())
                .setJobData(jobDataMap)
                .build();
    }

    public static Trigger buildTrigger(final Class jobClass, final TimerInfo info) {
    	
    	ScheduleBuilder builder = null;
    	if(!info.isCron()) {
    		SimpleScheduleBuilder simpleBuilder = SimpleScheduleBuilder.simpleSchedule().withIntervalInMilliseconds(info.getRepeatIntervalMs());
    		 if (info.isRunForever()) {
    			 simpleBuilder = simpleBuilder.repeatForever();
    	        } else {
    	        	simpleBuilder = simpleBuilder.withRepeatCount(info.getTotalFireCount() - 1);
    	        }
    		 
    		 builder = simpleBuilder;
    	} else {
    		CronScheduleBuilder cronBuilder = CronScheduleBuilder.cronSchedule(info.getCronSchedule());
    		builder = cronBuilder;
    	}

       

        return TriggerBuilder
                .newTrigger()
                .withIdentity(jobClass.getSimpleName())
                .withSchedule(builder)
                .startAt(new Date(System.currentTimeMillis() + info.getInitialOffsetMs()))
                .build();
    }
}
