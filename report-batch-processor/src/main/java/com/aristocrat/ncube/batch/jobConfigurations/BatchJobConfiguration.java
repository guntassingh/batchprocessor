package com.aristocrat.ncube.batch.jobConfigurations;



import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.bson.Document;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.data.MongoItemReader;
import org.springframework.batch.item.data.builder.MongoItemReaderBuilder;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.file.transform.DelimitedLineAggregator;
import org.springframework.batch.item.file.transform.PassThroughFieldExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.aggregation.SortOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.util.CollectionUtils;

import com.aristocrat.ncube.batch.itemProcessor.DynamicBeanDataProcessor;

import lombok.Data;


@Configuration
@EnableBatchProcessing
public class BatchJobConfiguration {
	
	@Autowired
	public JobBuilderFactory jobBuilderFactory;

	@Autowired
	public StepBuilderFactory stepBuilderFactory;
	
	@Autowired()
	@Qualifier("secondaryMongoTemplate")
	private MongoTemplate secondaryMongoTemplate;
	

	@Bean
	@StepScope
	public MongoItemReader<Document> reader(@Value("#{jobParameters[collectionName]}") String collectionName, 
											@Value("#{jobParameters[queryDocument]}") String queryDocument) {
		
		//CustomMongoItemReader<Document> customReader = new CustomMongoItemReader<>()
		
		String[] fields = {"orderDate", "node", "code1", "code2"};
		String[] projectionFields = {"orderDate", "orderDate", "code1", "code2"};
		MatchOperation matchOp = Aggregation.match(Criteria.where("createdTime").gt(startDate).and("orderDate").lt(endDate).and("status").in("GREEN", "YELLOW"));
		GroupOperation groupOp = Aggregation.group(fields).sum("orderUnts").as("_id");
		ProjectionOperation projectOp = Aggregation.project(projectionFields);
		SortOperation sortOp = Aggregation.sort(Sort.by(Sort.Direction.ASC, "orderDate"));
		Aggregation aggregation = Aggregation.newAggregation(matchOp, groupOp, projectOp, sortOp);
		
		return new MongoItemReaderBuilder<Document>()
				.name("mongoDbReader")
				.template(secondaryMongoTemplate)
				.collection(collectionName)
				.jsonQuery(queryDocument)
				.targetType(Document.class)
				.sorts(new HashMap<String, Sort.Direction>() {{
						put("gameId", Direction.ASC);
						put("partnerId", Direction.ASC);
				}})
				.build();

	  }
 	
 	@Bean
	public DynamicBeanDataProcessor processor() {
		return new DynamicBeanDataProcessor();
	}
	
	@Bean
	public FlatFileItemWriter<Document> filewriter() {
		PassThroughFieldExtractor<Document> fieldExtractor = new PassThroughFieldExtractor<>();
	    //fieldExtractor.setNames(new String[] {"firstName", "lastName" });
	    DelimitedLineAggregator<Document> lineAggregator = new DelimitedLineAggregator<>();
	    lineAggregator.setDelimiter("");
	    lineAggregator.setFieldExtractor(fieldExtractor);
		return new FlatFileItemWriterBuilder<Document>()
				.name("csvWriter")
				.resource(new FileSystemResource("out.csv"))
				.lineAggregator(lineAggregator)
				.build();
	}
	
	@Bean
	public Step fetchDataIntoFile(FlatFileItemWriter<Document> writer, MongoItemReader<Document> reader) {
		return stepBuilderFactory.get("Fetch data into File")
			.<Document, Document> chunk(10)
			.reader(reader)
			.processor(processor())
			.writer(writer)
			.build();
	}

	@Bean
	public Job reportingJob(Step fetchDataIntoFile) {
		return jobBuilderFactory.get("reportingJob")
			.incrementer(new RunIdIncrementer())
			.flow(fetchDataIntoFile).on("COMPLETED").end()
			.end()
			.build();
	}
	
	@Data
	class CustomMongoItemReader<T> extends MongoItemReader<T> {
		
		private MongoTemplate template;
		private Class<? extends T> type;
		private MatchOperation match;
		private ProjectionOperation projection;
		private GroupOperation group;
		private SortOperation sort;
		private String collection;
		private List<AggregationOperation> aggOperations;

		@Override
		protected Iterator<T> doPageRead() {
		    Pageable page = PageRequest.of(this.page, this.pageSize);
		    aggOperations.add(Aggregation.skip(page.getPageNumber() * page.getPageSize()));
		    aggOperations.add(Aggregation.limit(page.getPageSize()));
		    Aggregation agg = Aggregation.newAggregation(aggOperations.toArray(new AggregationOperation[aggOperations.size()]));
		    if(!CollectionUtils.isEmpty(aggOperations)) {
		    	return (Iterator<T>) template.aggregate(agg, collection, this.type).iterator();
		    }else {
		    	return super.doPageRead();
		    }
		}
	    
	    
	}

	

}
