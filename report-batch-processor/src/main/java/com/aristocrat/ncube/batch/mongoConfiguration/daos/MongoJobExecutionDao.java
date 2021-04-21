package com.aristocrat.ncube.batch.mongoConfiguration.daos;


import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.springframework.batch.core.*;
import org.springframework.batch.core.repository.dao.JobExecutionDao;
import org.springframework.batch.core.repository.dao.NoSuchObjectException;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import java.util.*;

@Repository
public class MongoJobExecutionDao extends AbstractMongoDao implements JobExecutionDao {

    @PostConstruct
    public void init() {
        super.init();
        getCollection().createIndex(new Document().append(JOB_EXECUTION_ID_KEY, 1).append(JOB_INSTANCE_ID_KEY, 1));
    }

    public void saveJobExecution(JobExecution jobExecution) {
        validateJobExecution(jobExecution);
        jobExecution.incrementVersion();
        Long id = getNextId(JobExecution.class.getSimpleName(), primaryMongoTemplate);
        save(jobExecution, id);
    }

    private void save(JobExecution jobExecution, Long id) {
        jobExecution.setId(id);
        Document object = toDbObjectWithoutVersion(jobExecution);
        object.put(VERSION_KEY, jobExecution.getVersion());
        getCollection().insertOne(object);
    }

    private Document toDbObjectWithoutVersion(JobExecution jobExecution) {
    	return new Document()
                .append(JOB_EXECUTION_ID_KEY, jobExecution.getId())
                .append(JOB_INSTANCE_ID_KEY, jobExecution.getJobId())
                .append(START_TIME_KEY, jobExecution.getStartTime())
                .append(END_TIME_KEY, jobExecution.getEndTime())
                .append(STATUS_KEY, jobExecution.getStatus().toString())
                .append(EXIT_CODE_KEY, jobExecution.getExitStatus().getExitCode())
                .append(EXIT_MESSAGE_KEY, jobExecution.getExitStatus().getExitDescription())
                .append(CREATE_TIME_KEY, jobExecution.getCreateTime())
                .append(LAST_UPDATED_KEY, jobExecution.getLastUpdated());
    }

    private void validateJobExecution(JobExecution jobExecution) {
        Assert.notNull(jobExecution, "JobExecution cannot be null.");
        Assert.notNull(jobExecution.getJobId(), "JobExecution Job-Id cannot be null.");
        Assert.notNull(jobExecution.getStatus(), "JobExecution status cannot be null.");
        Assert.notNull(jobExecution.getCreateTime(), "JobExecution create time cannot be null");
    }

    public synchronized void updateJobExecution(JobExecution jobExecution) {
        validateJobExecution(jobExecution);

        Long jobExecutionId = jobExecution.getId();
        Assert.notNull(jobExecutionId, "JobExecution ID cannot be null. JobExecution must be saved before it can be updated");
        Assert.notNull(jobExecution.getVersion(), "JobExecution version cannot be null. JobExecution must be saved before it can be updated");

        Integer version = jobExecution.getVersion() + 1;

        if (getCollection().find(jobExecutionIdObj(jobExecutionId)).first() == null) {
            throw new NoSuchObjectException(String.format("Invalid JobExecution, ID %s not found.", jobExecutionId));
        }

        Document object = toDbObjectWithoutVersion(jobExecution);
        object.put(VERSION_KEY, version);
        UpdateResult update = getCollection().replaceOne(
        		new Document()
                .append(JOB_EXECUTION_ID_KEY, jobExecutionId)
                .append(VERSION_KEY, jobExecution.getVersion()),
                object
        );

        jobExecution.incrementVersion();
    }

    public List<JobExecution> findJobExecutions(JobInstance jobInstance) {
        Assert.notNull(jobInstance, "Job cannot be null.");
        Long id = jobInstance.getId();
        Assert.notNull(id, "Job Id cannot be null.");
        MongoCursor<Document> dbCursor = getCollection().find(jobInstanceIdObj(id)).sort(new Document(JOB_EXECUTION_ID_KEY, -1)).iterator();
        List<JobExecution> result = new ArrayList<>();
        while (dbCursor.hasNext()) {
        	Document dbObject = dbCursor.next();
            result.add(mapJobExecution(jobInstance, dbObject));
        }
        return result;
    }

    public JobExecution getLastJobExecution(JobInstance jobInstance) {
        Long id = jobInstance.getId();

        MongoCursor<Document> dbCursor = getCollection().find(jobInstanceIdObj(id)).sort(new Document(CREATE_TIME_KEY, -1)).limit(1).iterator();
        if (!dbCursor.hasNext()) {
            return null;
        } else {
        	Document singleResult = dbCursor.next();
            if (dbCursor.hasNext()) {
                throw new IllegalStateException("There must be at most one latest job execution");
            }
            return mapJobExecution(jobInstance, singleResult);
        }
    }

    public Set<JobExecution> findRunningJobExecutions(String jobName) {
    	MongoCursor<Document> instancesCursor = primaryMongoTemplate.getCollection(JobInstance.class.getSimpleName())
                .find(new Document(JOB_NAME_KEY, jobName)).projection(jobInstanceIdObj(1L)).iterator();
        List<Long> ids = new ArrayList<>();
        while (instancesCursor.hasNext()) {
            ids.add((Long) instancesCursor.next().get(JOB_INSTANCE_ID_KEY));
        }

        MongoCursor<Document> dbCursor = getCollection().find(
        		new Document()
                .append(JOB_INSTANCE_ID_KEY, new Document("$in", ids.toArray()))
                .append(END_TIME_KEY, null)).sort(
                jobExecutionIdObj(-1L)).iterator();
        
        
        Set<JobExecution> result = new HashSet<>();
        while (dbCursor.hasNext()) {
            result.add(mapJobExecution(dbCursor.next()));
        }
        return result;
    }

    public JobExecution getJobExecution(Long executionId) {
        return mapJobExecution(getCollection().find(jobExecutionIdObj(executionId)).first());
    }

    public void synchronizeStatus(JobExecution jobExecution) {
        Long id = jobExecution.getId();
        Document jobExecutionObject = getCollection().find(jobExecutionIdObj(id)).first();
        int currentVersion = jobExecutionObject != null ? ((Integer) jobExecutionObject.get(VERSION_KEY)) : 0;
        if (currentVersion != jobExecution.getVersion()) {
            if (jobExecutionObject == null) {
                save(jobExecution, id);
                jobExecutionObject = getCollection().find(jobExecutionIdObj(id)).first();
            }
            String status = (String) jobExecutionObject.get(STATUS_KEY);
            jobExecution.upgradeStatus(BatchStatus.valueOf(status));
            jobExecution.setVersion(currentVersion);
        }
    }

    @Override
    protected MongoCollection<Document> getCollection() {
        return primaryMongoTemplate.getCollection(JobExecution.class.getSimpleName());
    }

    private JobExecution mapJobExecution(Document dbObject) {
        return mapJobExecution(null, dbObject);
    }

    private JobExecution mapJobExecution(JobInstance jobInstance, Document dbObject) {
        if (dbObject == null) {
            return null;
        }
        Long id = (Long) dbObject.get(JOB_EXECUTION_ID_KEY);
        JobExecution jobExecution;

        if (jobInstance == null) {
            jobExecution = new JobExecution(id);
        } else {
            JobParameters jobParameters = getJobParameters(jobInstance.getId(), primaryMongoTemplate);
            jobExecution = new JobExecution(jobInstance, id, jobParameters, null);
        }
        jobExecution.setStartTime((Date) dbObject.get(START_TIME_KEY));
        jobExecution.setEndTime((Date) dbObject.get(END_TIME_KEY));
        jobExecution.setStatus(BatchStatus.valueOf((String) dbObject.get(STATUS_KEY)));
        jobExecution.setExitStatus(new ExitStatus(((String) dbObject.get(EXIT_CODE_KEY)), (String) dbObject.get(EXIT_MESSAGE_KEY)));
        jobExecution.setCreateTime((Date) dbObject.get(CREATE_TIME_KEY));
        jobExecution.setLastUpdated((Date) dbObject.get(LAST_UPDATED_KEY));
        jobExecution.setVersion((Integer) dbObject.get(VERSION_KEY));

        return jobExecution;
    }
}
