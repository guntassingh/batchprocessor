package com.aristocrat.ncube.batch.itemProcessor;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;


public class DynamicBeanDataProcessor implements ItemProcessor<Document, Document>{
	
	private static final Logger log = LoggerFactory.getLogger(DynamicBeanDataProcessor.class);

	@Override
	public Document process(Document item) throws Exception {
		System.out.println(item.toJson());
		return item;
	}
}
