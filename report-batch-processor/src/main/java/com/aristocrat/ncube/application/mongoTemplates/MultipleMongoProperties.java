package com.aristocrat.ncube.application.mongoTemplates;

import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = "mongodb")
public class MultipleMongoProperties {

    private MongoProperties primary = new MongoProperties();
    private MongoProperties secondary = new MongoProperties();
    
	public MongoProperties getPrimary() {
		return primary;
	}
	public void setPrimary(MongoProperties primary) {
		this.primary = primary;
	}
	public MongoProperties getSecondary() {
		return secondary;
	}
	public void setSecondary(MongoProperties secondary) {
		this.secondary = secondary;
	}
	@Override
	public String toString() {
		return "MultipleMongoProperties [primary=" + primary + ", secondary=" + secondary + "]";
	}
      
    
}