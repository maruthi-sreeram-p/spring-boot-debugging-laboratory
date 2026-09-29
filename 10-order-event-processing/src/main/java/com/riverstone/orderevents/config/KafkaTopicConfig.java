package com.riverstone.orderevents.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Both topics are partitioned so the pipeline can scale horizontally. Kafka guarantees
 * ordering within a partition, so everything that must be processed in sequence has to land
 * on the same one.
 */
@Configuration
public class KafkaTopicConfig {

    private final RiverstoneProperties properties;

    public KafkaTopicConfig(RiverstoneProperties properties) {
        this.properties = properties;
    }

    @Bean
    public NewTopic ordersCreatedTopic() {
        return TopicBuilder.name(properties.getTopics().getOrdersCreated())
                .partitions(properties.getTopics().getPartitions())
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic inventoryEventsTopic() {
        return TopicBuilder.name(properties.getTopics().getInventoryEvents())
                .partitions(properties.getTopics().getPartitions())
                .replicas(1)
                .build();
    }
}
