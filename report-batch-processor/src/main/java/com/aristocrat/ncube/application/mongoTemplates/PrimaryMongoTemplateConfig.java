package com.aristocrat.ncube.application.mongoTemplates;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableConfigurationProperties(MultipleMongoProperties.class)
public class PrimaryMongoTemplateConfig {
	
private final MultipleMongoProperties mongoProperties;
	
    public PrimaryMongoTemplateConfig(MultipleMongoProperties mongoProperties) {
		super();
		this.mongoProperties = mongoProperties;
	}
    
    @Primary
    @Bean(name = "primaryMongoTemplate")
    public MongoTemplate getPrimaryMongoTemplate() throws Exception {
    	return new MongoTemplate(new SimpleMongoClientDatabaseFactory(this.mongoProperties.getPrimary().getUri()));
    }

}
