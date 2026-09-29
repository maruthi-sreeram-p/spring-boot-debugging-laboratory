package com.pulsesend.notifications.config;

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
 * One topic exchange fans notifications out to a queue per channel. Routing keys are
 * {@code notify.<channel>.<templateCode>}, so a channel queue takes everything for its
 * channel and the template code is available for finer-grained subscriptions later.
 *
 * Anything a consumer cannot process is meant to end up on the dead letter queue, where
 * messaging operations can inspect it and replay it once the cause is understood.
 */
@Configuration
public class RabbitTopologyConfig {

    private final PulsesendProperties properties;

    public RabbitTopologyConfig(PulsesendProperties properties) {
        this.properties = properties;
    }

    @Bean
    public TopicExchange notificationsExchange() {
        return new TopicExchange(properties.getMessaging().getExchange(), true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(properties.getMessaging().getDeadLetterExchange(), true, false);
    }

    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(properties.getMessaging().getEmailQueue()).build();
    }

    @Bean
    public Queue smsQueue() {
        return QueueBuilder.durable(properties.getMessaging().getSmsQueue()).build();
    }

    @Bean
    public Queue inAppQueue() {
        return QueueBuilder.durable(properties.getMessaging().getInAppQueue()).build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(properties.getMessaging().getDeadLetterQueue()).build();
    }

    @Bean
    public Binding emailBinding(Queue emailQueue, TopicExchange notificationsExchange) {
        return BindingBuilder.bind(emailQueue).to(notificationsExchange).with("notify.email.*");
    }

    @Bean
    public Binding smsBinding(Queue smsQueue, TopicExchange notificationsExchange) {
        return BindingBuilder.bind(smsQueue).to(notificationsExchange).with("notify.sms.#");
    }

    @Bean
    public Binding inAppBinding(Queue inAppQueue, TopicExchange notificationsExchange) {
        return BindingBuilder.bind(inAppQueue).to(notificationsExchange).with("notify.inapp.#");
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with("#");
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
