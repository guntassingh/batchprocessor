package com.aristocrat.ncube.batch.mongoConfiguration.daos;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;

import org.bson.Document;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.repository.dao.ExecutionContextDao;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;
import org.springframework.util.NumberUtils;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;

@Repository
public class MongoExecutionContextDao extends AbstractMongoDao implements ExecutionContextDao {

    @PostConstruct
    public void init() {
        super.init();
        getCollection().createIndex(new Document().append(STEP_EXECUTION_ID_KEY, 1).append(JOB_EXECUTION_ID_KEY, 1));
    }

    @Override
    public ExecutionContext getExecutionContext(JobExecution jobExecution) {
        return getExecutionContext(JOB_EXECUTION_ID_KEY, jobExecution.getId());
    }

    @Override
    public ExecutionContext getExecutionContext(StepExecution stepExecution) {
        return getExecutionContext(STEP_EXECUTION_ID_KEY, stepExecution.getId());
    }

    @Override
    public void saveExecutionContext(JobExecution jobExecution) {
        saveOrUpdateExecutionContext(JOB_EXECUTION_ID_KEY, jobExecution.getId(), jobExecution.getExecutionContext());
    }

    @Override
    public void saveExecutionContext(StepExecution stepExecution) {
        saveOrUpdateExecutionContext(STEP_EXECUTION_ID_KEY, stepExecution.getId(), stepExecution.getExecutionContext());
    }

    @Override
    public void updateExecutionContext(JobExecution jobExecution) {
        saveOrUpdateExecutionContext(JOB_EXECUTION_ID_KEY, jobExecution.getId(), jobExecution.getExecutionContext());
    }

    @Override
    public void updateExecutionContext(StepExecution stepExecution) {
        saveOrUpdateExecutionContext(STEP_EXECUTION_ID_KEY, stepExecution.getId(), stepExecution.getExecutionContext());
    }

    private void saveOrUpdateExecutionContext(String executionIdKey, Long executionId, ExecutionContext executionContext) {
        Assert.notNull(executionId, "ExecutionId must not be null.");
        Assert.notNull(executionContext, "The ExecutionContext must not be null.");

        Document dbObject = new Document(executionIdKey, executionId);
        for (Map.Entry<String, Object> entry : executionContext.entrySet()) {
            Object value = entry.getValue();
            String key = entry.getKey();
            dbObject.put(key.replaceAll(DOT_STRING, DOT_ESCAPE_STRING), value);
            if (value instanceof BigDecimal || value instanceof BigInteger) {
                dbObject.put(key + TYPE_SUFFIX, value.getClass().getName());
            }
        }
        getCollection().replaceOne(new Document(executionIdKey, executionId), dbObject, new ReplaceOptions().upsert(true));
    }

    @SuppressWarnings({"unchecked"})
    private ExecutionContext getExecutionContext(String executionIdKey, Long executionId) {
        Assert.notNull(executionId, "ExecutionId must not be null.");
        Document result = getCollection().find(new Document(executionIdKey, executionId)).first();
        ExecutionContext executionContext = new ExecutionContext();
        if (result != null) {
            result.remove(executionIdKey);
            removeSystemFields(result);
            for (String key : result.keySet()) {
                Object value = result.get(key);
                String type = (String) result.get(key + TYPE_SUFFIX);
                if (type != null && Number.class.isAssignableFrom(value.getClass())) {
                    try {
                        value = NumberUtils.convertNumberToTargetClass((Number) value, (Class<? extends Number>) Class.forName(type));
                    } catch (Exception e) {
                        logger.warn("Failed to convert {} to {}", key, type);
                    }
                }
                //Mongo db does not allow key name with "." character.
                executionContext.put(
                        key.replaceAll(DOT_ESCAPE_STRING, DOT_STRING), value);
            }
        }
        return executionContext;
    }

    @Override
    protected MongoCollection<Document> getCollection() {
        return primaryMongoTemplate.getCollection(ExecutionContext.class.getSimpleName());
    }

    @Override
    public void saveExecutionContexts(Collection<StepExecution> stepExecutions) {
        Assert.notNull(stepExecutions, "Attempt to save a null collection of step executions");
        for (StepExecution stepExecution : stepExecutions) {
            saveExecutionContext(stepExecution);
            saveExecutionContext(stepExecution.getJobExecution());
        }
    }
}
