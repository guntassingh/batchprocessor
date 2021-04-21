package com.aristocrat.ncube.application.mongoTemplates;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableConfigurationProperties(MultipleMongoProperties.class)
@EnableMongoRepositories(basePackages = "com.aristocrat.ncube.application.crudRepositories", mongoTemplateRef = "secondaryMongoTemplate")
public class SecondaryMongoTemplateConfig {
	
private final MultipleMongoProperties mongoProperties;
	
    public SecondaryMongoTemplateConfig(MultipleMongoProperties mongoProperties) {
		super();
		this.mongoProperties = mongoProperties;
	}
    
    @Bean(name = "secondaryMongoTemplate")
    public MongoTemplate secondaryMongoTemplate() throws Exception {
        return new MongoTemplate(new SimpleMongoClientDatabaseFactory(this.mongoProperties.getSecondary().getUri()));
    }

}
