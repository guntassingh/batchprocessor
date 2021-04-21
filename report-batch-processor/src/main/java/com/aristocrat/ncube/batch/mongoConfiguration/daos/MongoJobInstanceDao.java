package com.aristocrat.ncube.batch.mongoConfiguration.daos;


import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;

import org.bson.Document;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.repository.dao.JobInstanceDao;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;
import javax.annotation.PostConstruct;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Repository
public class MongoJobInstanceDao extends AbstractMongoDao implements JobInstanceDao {

    @PostConstruct
    @Override
    public void init() {
        super.init();
        getCollection().createIndex(jobInstanceIdObj(1L));
    }

    @Override
    public JobInstance createJobInstance(String jobName, final JobParameters jobParameters) {
        Assert.notNull(jobName, "Job name must not be null.");
        Assert.notNull(jobParameters, "JobParameters must not be null.");
        Assert.state(getJobInstance(jobName, jobParameters) == null, "JobInstance must not already exist");

        Long jobId = getNextId(JobInstance.class.getSimpleName(), primaryMongoTemplate);

        JobInstance jobInstance = new JobInstance(jobId, jobName);

        jobInstance.incrementVersion();

        Map<String, JobParameter> jobParams = jobParameters.getParameters();
        Map<String, Object> paramMap = new HashMap<>(jobParams.size());
        for (Map.Entry<String, JobParameter> entry : jobParams.entrySet()) {
            paramMap.put(entry.getKey().replaceAll(DOT_STRING, DOT_ESCAPE_STRING), entry.getValue().getValue());
        }
        getCollection().insertOne(
                new Document()
                        .append(JOB_INSTANCE_ID_KEY, jobId)
                        .append(JOB_NAME_KEY, jobName)
                        .append(JOB_KEY_KEY, createJobKey(jobParameters))
                        .append(VERSION_KEY, jobInstance.getVersion())
                        .append(JOB_PARAMETERS_KEY, new Document(paramMap))
        );
        return jobInstance;
    }

    @Override
    public JobInstance getJobInstance(String jobName, JobParameters jobParameters) {
        Assert.notNull(jobName, "Job name must not be null.");
        Assert.notNull(jobParameters, "JobParameters must not be null.");

        String jobKey = createJobKey(jobParameters);

        return mapJobInstance(getCollection().find(
                new Document()
                        .append(JOB_NAME_KEY, jobName)
                        .append(JOB_KEY_KEY, jobKey)).first(), jobParameters);
    }
    

    @Override
    public JobInstance getLastJobInstance(String jobName) {
    	Assert.notNull(jobName, "Job name must not be null.");
    	
    	MongoCursor<Document> dbCursor = getCollection().find(
                new Document()
                        .append(JOB_NAME_KEY, jobName)).sort(new Document(JOB_INSTANCE_ID_KEY, -1)).limit(1).iterator();
    	
    	if (!dbCursor.hasNext()) {
            return null;
        } else {
        	Document singleResult = dbCursor.next();
            if (dbCursor.hasNext()) {
                throw new IllegalStateException("There must be at most one latest job instance");
            }
            return mapJobInstance(singleResult);
        }
    }

    @Override
    public JobInstance getJobInstance(Long instanceId) {
        return mapJobInstance(getCollection().find(jobInstanceIdObj(instanceId)).first());
    }

    @Override
    public JobInstance getJobInstance(JobExecution jobExecution) {
    	Document instanceId = primaryMongoTemplate.getCollection(JobExecution.class.getSimpleName()).find(jobExecutionIdObj(jobExecution.getId())).projection(jobInstanceIdObj(1L)).first();
        removeSystemFields(instanceId);
        return mapJobInstance(getCollection().find(instanceId).first());
    }

    @Override
    public List<JobInstance> getJobInstances(String jobName, int start, int count) {
        return mapJobInstances(getCollection().find(new Document(JOB_NAME_KEY, jobName)).sort(jobInstanceIdObj(-1L)).skip(start).limit(count).iterator());
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public List<String> getJobNames() {
        MongoCursor<String> cursor = getCollection().distinct(JOB_NAME_KEY, String.class).iterator();
        List<String> stringList = new ArrayList<String>();
        while(cursor.hasNext()) {
        	stringList.add(cursor.next());
        }
        Collections.sort(stringList);
        return stringList;
    }

    private String createJobKey(JobParameters jobParameters) {

        Map<String, JobParameter> props = jobParameters.getParameters();
        StringBuilder stringBuilder = new StringBuilder();
        List<String> keys = new ArrayList<>(props.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            stringBuilder.append(key).append("=").append(props.get(key).toString()).append(";");
        }

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm not available.  Fatal (should be in the JDK).");
        }

        try {
            byte[] bytes = digest.digest(stringBuilder.toString().getBytes("UTF-8"));
            return String.format("%032x", new BigInteger(1, bytes));
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 encoding not available. Fatal (should be in the JDK).");
        }
    }

    @Override
    protected MongoCollection<Document> getCollection() {
        return primaryMongoTemplate.getCollection(JobInstance.class.getSimpleName());
    }

    private List<JobInstance> mapJobInstances(MongoCursor<Document> dbCursor) {
        List<JobInstance> results = new ArrayList<>();
        while (dbCursor.hasNext()) {
            results.add(mapJobInstance(dbCursor.next()));
        }
        return results;
    }

    private JobInstance mapJobInstance(Document dbObject) {
        return mapJobInstance(dbObject, null);
    }

    private JobInstance mapJobInstance(Document dbObject, JobParameters jobParameters) {
        JobInstance jobInstance = null;
        if (dbObject != null) {
            Long id = (Long) dbObject.get(JOB_INSTANCE_ID_KEY);
            if (jobParameters == null) {
                jobParameters = getJobParameters(id, primaryMongoTemplate);
            }

            jobInstance = new JobInstance(id, (String) dbObject.get(JOB_NAME_KEY)); // should always be at version=0 because they never get updated
            jobInstance.incrementVersion();
        }
        return jobInstance;
    }

    @Override
    public List<JobInstance> findJobInstancesByName(String jobName, int start, int count) {
        List<JobInstance> result = new ArrayList<>();
        List<JobInstance> jobInstances = mapJobInstances(
                getCollection()
                        .find(new Document(JOB_NAME_KEY, jobName))
                        .sort(jobInstanceIdObj(-1L)).iterator()
        );
        for (JobInstance instanceEntry : jobInstances) {
            String key = instanceEntry.getJobName();
            String curJobName = key.substring(0, key.lastIndexOf("|"));

            if (curJobName.equals(jobName)) {
                result.add(instanceEntry);
            }
        }
        return result;
    }

    @Override
    public int getJobInstanceCount(String jobName) throws NoSuchJobException {
        int count = 0;
        List<JobInstance> jobInstances = mapJobInstances(
                getCollection()
                        .find(new Document(JOB_NAME_KEY, jobName))
                        .sort(jobInstanceIdObj(-1L)).iterator()
        );
        for (JobInstance instanceEntry : jobInstances) {
            String key = instanceEntry.getJobName();
            String curJobName = key.substring(0, key.lastIndexOf("|"));

            if (curJobName.equals(jobName)) {
                count++;
            }
        }

        if (count == 0) {
            throw new NoSuchJobException(String.format("No job instances for job name %s were found", jobName));
        } else {
            return count;
        }
    }

}
