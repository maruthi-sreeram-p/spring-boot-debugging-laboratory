package com.spicebox.ordering.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Order events go out on one topic exchange. Analytics listens to everything that is
 * placed; the dispatch service listens for orders the kitchen has finished so it can send
 * a rider.
 */
@Configuration
public class RabbitConfig {

    private final SpiceboxProperties properties;

    public RabbitConfig(SpiceboxProperties properties) {
        this.properties = properties;
    }

    @Bean
    public TopicExchange foodOrdersExchange() {
        return new TopicExchange(properties.getMessaging().getExchange(), true, false);
    }

    @Bean
    public Queue analyticsQueue() {
        return QueueBuilder.durable(properties.getMessaging().getAnalyticsQueue()).build();
    }

    @Bean
    public Queue dispatchQueue() {
        return QueueBuilder.durable(properties.getMessaging().getDispatchQueue()).build();
    }

    @Bean
    public Binding analyticsBinding(Queue analyticsQueue, TopicExchange foodOrdersExchange) {
        return BindingBuilder.bind(analyticsQueue).to(foodOrdersExchange).with("order.placed");
    }

    @Bean
    public Binding dispatchBinding(Queue dispatchQueue, TopicExchange foodOrdersExchange) {
        return BindingBuilder.bind(dispatchQueue).to(foodOrdersExchange).with("order.ready");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
