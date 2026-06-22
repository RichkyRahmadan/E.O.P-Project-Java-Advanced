package com.priestess.oracle.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_SUPPORT  = "eop.support.exchange";
    public static final String EXCHANGE_DLQ      = "eop.support.dlq.exchange";

    public static final String QUEUE_COMPLAINT_PROCESSING = "complaint.processing";

    public static final String QUEUE_COMPLAINT_DLQ = "complaint.dlq";

    @Bean
    public DirectExchange supportExchange() {
        return new DirectExchange(EXCHANGE_SUPPORT, true, false);
    }

    @Bean
    public DirectExchange dlqExchange() {
        return new DirectExchange(EXCHANGE_DLQ, true, false);
    }

    @Bean
    public Queue complaintProcessingQueue() {
        return QueueBuilder.durable(QUEUE_COMPLAINT_PROCESSING)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLQ)
                .withArgument("x-dead-letter-routing-key", QUEUE_COMPLAINT_DLQ)
                .build();
    }

    @Bean
    public Queue complaintDlqQueue() {
        return QueueBuilder.durable(QUEUE_COMPLAINT_DLQ).build();
    }

    @Bean
    public Binding bindingComplaintProcessing(Queue complaintProcessingQueue, DirectExchange supportExchange) {
        return BindingBuilder.bind(complaintProcessingQueue).to(supportExchange)
                .with(QUEUE_COMPLAINT_PROCESSING);
    }

    @Bean
    public Binding bindingComplaintDlq(Queue complaintDlqQueue, DirectExchange dlqExchange) {
        return BindingBuilder.bind(complaintDlqQueue).to(dlqExchange)
                .with(QUEUE_COMPLAINT_DLQ);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
