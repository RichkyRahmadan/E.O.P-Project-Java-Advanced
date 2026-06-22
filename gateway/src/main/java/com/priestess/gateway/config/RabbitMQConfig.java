package com.priestess.gateway.config;

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

    public static final String EXCHANGE_IDENTITY  = "eop.identity.exchange";

    public static final String QUEUE_USER_SUSPENDED = "user.suspended";

    @Bean
    public DirectExchange identityExchange() {
        return new DirectExchange(EXCHANGE_IDENTITY, true, false);
    }

    @Bean
    public Queue userSuspendedQueue() {
        return QueueBuilder.durable(QUEUE_USER_SUSPENDED).build();
    }

    @Bean
    public Binding bindingUserSuspended(Queue userSuspendedQueue, DirectExchange identityExchange) {
        return BindingBuilder.bind(userSuspendedQueue).to(identityExchange)
                .with(QUEUE_USER_SUSPENDED);
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
