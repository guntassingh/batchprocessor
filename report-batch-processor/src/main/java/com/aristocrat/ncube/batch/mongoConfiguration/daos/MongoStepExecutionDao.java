package com.aristocrat.ncube.batch.mongoConfiguration.daos;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;

import org.bson.Document;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.repository.dao.StepExecutionDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.Date;

import static com.mongodb.BasicDBObjectBuilder.start;

@Repository
public class MongoStepExecutionDao extends AbstractMongoDao implements StepExecutionDao {
	
	
	@Autowired
	MongoJobExecutionDao jobExecutionDao;

    @PostConstruct
    public void init() {
        super.init();
        getCollection().createIndex(new Document().append(STEP_EXECUTION_ID_KEY, 1).append(JOB_EXECUTION_ID_KEY, 1));

    }

    public void saveStepExecution(StepExecution stepExecution) {
        Assert.isNull(stepExecution.getId(),
                "to-be-saved (not updated) StepExecution can't already have an id assigned");
        Assert.isNull(stepExecution.getVersion(),
                "to-be-saved (not updated) StepExecution can't already have a version assigned");

        validateStepExecution(stepExecution);

        stepExecution.setId(getNextId(StepExecution.class.getSimpleName(), primaryMongoTemplate));
        stepExecution.incrementVersion(); // should be 0 now
        Document object = toDbObjectWithoutVersion(stepExecution);
        object.put(VERSION_KEY, stepExecution.getVersion());
        getCollection().insertOne(object);

    }

    private Document toDbObjectWithoutVersion(StepExecution stepExecution) {
        return new Document()
                .append(STEP_EXECUTION_ID_KEY, stepExecution.getId())
                .append(STEP_NAME_KEY, stepExecution.getStepName())
                .append(JOB_EXECUTION_ID_KEY, stepExecution.getJobExecutionId())
                .append(START_TIME_KEY, stepExecution.getStartTime())
                .append(END_TIME_KEY, stepExecution.getEndTime())
                .append(STATUS_KEY, stepExecution.getStatus().toString())
                .append(COMMIT_COUNT_KEY, stepExecution.getCommitCount())
                .append(READ_COUNT_KEY, stepExecution.getReadCount())
                .append(FILTER_COUT_KEY, stepExecution.getFilterCount())
                .append(WRITE_COUNT_KEY, stepExecution.getWriteCount())
                .append(EXIT_CODE_KEY, stepExecution.getExitStatus().getExitCode())
                .append(EXIT_MESSAGE_KEY, stepExecution.getExitStatus().getExitDescription())
                .append(READ_SKIP_COUNT_KEY, stepExecution.getReadSkipCount())
                .append(WRITE_SKIP_COUNT_KEY, stepExecution.getWriteSkipCount())
                .append(PROCESS_SKIP_COUT_KEY, stepExecution.getProcessSkipCount())
                .append(ROLLBACK_COUNT_KEY, stepExecution.getRollbackCount())
                .append(LAST_UPDATED_KEY, stepExecution.getLastUpdated());
    }

    public synchronized void updateStepExecution(StepExecution stepExecution) {
        // Attempt to prevent concurrent modification errors by blocking here if
        // someone is already trying to do it.
        Integer currentVersion = stepExecution.getVersion();
        Integer newVersion = currentVersion + 1;
        Document object = toDbObjectWithoutVersion(stepExecution);
        object.put(VERSION_KEY, newVersion);
        getCollection().replaceOne(new Document()
                        .append(STEP_EXECUTION_ID_KEY, stepExecution.getId())
                        .append(VERSION_KEY, currentVersion),
                object);

        /*// Avoid concurrent modifications...
        DBObject lastError = mongoTemplate.getDb().getLastError();
        if (!((Boolean) lastError.get(UPDATED_EXISTING_STATUS))) {
            logger.error("Update returned status {}", lastError);
            DBObject existingStepExecution = getCollection().findOne(stepExecutionIdObj(stepExecution.getId()), new BasicDBObject(VERSION_KEY, 1));
            if (existingStepExecution == null) {
                throw new IllegalArgumentException("Can't update this stepExecution, it was never saved.");
            }
            Integer curentVersion = ((Integer) existingStepExecution.get(VERSION_KEY));
            throw new OptimisticLockingFailureException("Attempt to update job execution id="
                    + stepExecution.getId() + " with wrong version (" + currentVersion
                    + "), where current version is " + curentVersion);
        }*/

        stepExecution.incrementVersion();
    }


    static Document stepExecutionIdObj(Long id) {
        return new Document(STEP_EXECUTION_ID_KEY, id);
    }


    public StepExecution getStepExecution(JobExecution jobExecution, Long stepExecutionId) {
        return mapStepExecution(getCollection().find(new Document()
                .append(STEP_EXECUTION_ID_KEY, stepExecutionId)
                .append(JOB_EXECUTION_ID_KEY, jobExecution.getId())).first(), jobExecution);
    }

    private StepExecution mapStepExecution(Document object, JobExecution jobExecution) {
        if (object == null) {
            return null;
        }
        StepExecution stepExecution = new StepExecution((String) object.get(STEP_NAME_KEY), jobExecution, ((Long) object.get(STEP_EXECUTION_ID_KEY)));
        stepExecution.setStartTime((Date) object.get(START_TIME_KEY));
        stepExecution.setEndTime((Date) object.get(END_TIME_KEY));
        stepExecution.setStatus(BatchStatus.valueOf((String) object.get(STATUS_KEY)));
        stepExecution.setCommitCount((Integer) object.get(COMMIT_COUNT_KEY));
        stepExecution.setReadCount((Integer) object.get(READ_COUNT_KEY));
        stepExecution.setFilterCount((Integer) object.get(FILTER_COUT_KEY));
        stepExecution.setWriteCount((Integer) object.get(WRITE_COUNT_KEY));
        stepExecution.setExitStatus(new ExitStatus((String) object.get(EXIT_CODE_KEY), ((String) object.get(EXIT_MESSAGE_KEY))));
        stepExecution.setReadSkipCount((Integer) object.get(READ_SKIP_COUNT_KEY));
        stepExecution.setWriteSkipCount((Integer) object.get(WRITE_SKIP_COUNT_KEY));
        stepExecution.setProcessSkipCount((Integer) object.get(PROCESS_SKIP_COUT_KEY));
        stepExecution.setRollbackCount((Integer) object.get(ROLLBACK_COUNT_KEY));
        stepExecution.setLastUpdated((Date) object.get(LAST_UPDATED_KEY));
        stepExecution.setVersion((Integer) object.get(VERSION_KEY));
        return stepExecution;

    }

    public void addStepExecutions(JobExecution jobExecution) {
    	MongoCursor<Document> stepsCoursor = getCollection().find(jobExecutionIdObj(jobExecution.getId())).sort(stepExecutionIdObj(1L)).iterator();
        while (stepsCoursor.hasNext()) {
        	Document stepObject = stepsCoursor.next();
            //Calls constructor of StepExecution, which adds the step; Wow, that's unclear code!
            mapStepExecution(stepObject, jobExecution);
        }
    }

    @Override
    protected MongoCollection<Document> getCollection() {
        return primaryMongoTemplate.getCollection(StepExecution.class.getSimpleName());
    }

    private void validateStepExecution(StepExecution stepExecution) {
        Assert.notNull(stepExecution, "StepExecution cannot be null.");
        Assert.notNull(stepExecution.getStepName(), "StepExecution step name cannot be null.");
        Assert.notNull(stepExecution.getStartTime(), "StepExecution start time cannot be null.");
        Assert.notNull(stepExecution.getStatus(), "StepExecution status cannot be null.");
    }

    @Override
    public void saveStepExecutions(Collection<StepExecution> stepExecutions) {
        Assert.notNull(stepExecutions,"Attempt to save an null collect of step executions");
        for (StepExecution stepExecution: stepExecutions) {
            saveStepExecution(stepExecution);
        }

    }
    
    @Override
    public int countStepExecutions(JobInstance jobInstance, String stepName) {
    	Long id = jobInstance.getId();
    	return (int) getCollection().countDocuments(new Document().append(STEP_NAME_KEY, stepName));
    }
    
    @Override
    public StepExecution getLastStepExecution(JobInstance jobInstance, String stepName) {
    	Long id = jobInstance.getId();
    	MongoCursor<Document> dbCursor = getCollection().find(new Document().append(STEP_NAME_KEY, stepName)).sort(new Document(START_TIME_KEY, -1)).limit(1).iterator();
    	if (!dbCursor.hasNext()) {
            return null;
        } else {
        	Document singleResult = dbCursor.next();
            if (dbCursor.hasNext()) {
                throw new IllegalStateException("There must be at most one latest job execution");
            }
            return mapStepExecution(singleResult, jobExecutionDao.getLastJobExecution(jobInstance));
        }
    }
}
