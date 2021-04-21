package com.aristocrat.ncube.batch.mongoConfiguration.daos;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;

abstract class AbstractMongoDao {
    //public static final String UPDATED_EXISTING_STATUS = "updatedExisting";
    static final String VERSION_KEY = "version";
    static final String START_TIME_KEY = "startTime";
    static final String END_TIME_KEY = "endTime";
    static final String EXIT_CODE_KEY = "exitCode";
    static final String EXIT_MESSAGE_KEY = "exitMessage";
    static final String LAST_UPDATED_KEY = "lastUpdated";
    static final String STATUS_KEY = "status";
    private static final String SEQUENCES_COLLECTION_NAME = "Sequences";
    private static final String ID_KEY = "_id";
    private static final String NS_KEY = "_ns";
    static final String DOT_ESCAPE_STRING = "\\{dot}";
    static final String DOT_STRING = "\\.";

    // Job Constants
    static final String JOB_NAME_KEY = "jobName";
    static final String JOB_INSTANCE_ID_KEY = "jobInstanceId";
    static final String JOB_KEY_KEY = "jobKey";
    static final String JOB_PARAMETERS_KEY = "jobParameters";

    // Job Execution Constants
    static final String JOB_EXECUTION_ID_KEY = "jobExecutionId";
    static final String CREATE_TIME_KEY = "createTime";

    // Job Execution Contexts Constants
    static final String STEP_EXECUTION_ID_KEY = "stepExecutionId";
    static final String TYPE_SUFFIX = "_TYPE";

    // Step Execution Constants
    static final String STEP_NAME_KEY = "stepName";
    static final String COMMIT_COUNT_KEY = "commitCount";
    static final String READ_COUNT_KEY = "readCount";
    static final String FILTER_COUT_KEY = "filterCout";
    static final String WRITE_COUNT_KEY = "writeCount";
    static final String READ_SKIP_COUNT_KEY = "readSkipCount";
    static final String WRITE_SKIP_COUNT_KEY = "writeSkipCount";
    static final String PROCESS_SKIP_COUT_KEY = "processSkipCout";
    static final String ROLLBACK_COUNT_KEY = "rollbackCount";

    protected Logger logger;

    /**
     * mongoTemplate is used to CRUD Job execution data in Mongo db. This bean
     * needs to be set during bean definition for MongoExecutionContextDao
     */
    @Autowired
    protected MongoTemplate primaryMongoTemplate;

    protected void init() {
        logger = LoggerFactory.getLogger(this.getClass());
    }

    protected abstract MongoCollection<Document> getCollection();


    Long getNextId(String name, MongoTemplate mongoTemplate) {
    	MongoCollection<Document> collection = mongoTemplate.getDb().getCollection(SEQUENCES_COLLECTION_NAME);
    	Document sequence = new Document("name", name);
        collection.updateOne(sequence, new Document("$inc", new Document("value", 1L)), new UpdateOptions().upsert(true));
        return (Long) collection.find(sequence).first().get("value");
    }

    void removeSystemFields(Document dbObject) {
        dbObject.remove(ID_KEY);
        dbObject.remove(NS_KEY);
    }

    Document jobInstanceIdObj(Long id) {
        return new Document(MongoJobInstanceDao.JOB_INSTANCE_ID_KEY, id);
    }

    Document jobExecutionIdObj(Long id) {
        return new Document(JOB_EXECUTION_ID_KEY, id);
    }

    @SuppressWarnings({"unchecked"})
    JobParameters getJobParameters(Long jobInstanceId, MongoTemplate mongoTemplate) {
    	Document jobParamObj = mongoTemplate
                .getCollection(JobInstance.class.getSimpleName())
                .find(new Document(jobInstanceIdObj(jobInstanceId))).first();

        if (jobParamObj != null && jobParamObj.get(MongoJobInstanceDao.JOB_PARAMETERS_KEY) != null){

            Map<String, ?> jobParamsMap = (Map<String, ?>) jobParamObj.get(MongoJobInstanceDao.JOB_PARAMETERS_KEY);

            Map<String, JobParameter> map = new HashMap<>(jobParamsMap.size());
            for (Map.Entry<String, ?> entry : jobParamsMap.entrySet()) {
                Object param = entry.getValue();
                String key = entry.getKey().replaceAll(DOT_ESCAPE_STRING, DOT_STRING);
                if (param instanceof String) {
                    map.put(key, new JobParameter((String) param));
                } else if (param instanceof Long) {
                    map.put(key, new JobParameter((Long) param));
                } else if (param instanceof Double) {
                    map.put(key, new JobParameter((Double) param));
                } else if (param instanceof Date) {
                    map.put(key, new JobParameter((Date) param));
                } else {
                    map.put(key, null);
                }
            }
            return new JobParameters(map);
        }
        return null;
    }
}
