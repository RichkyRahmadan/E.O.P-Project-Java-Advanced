package com.priestess.core.config;

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

    public static final String EXCHANGE_FINANCE       = "eop.finance.exchange";

    public static final String QUEUE_QRIS_INITIATED   = "qris.payment.initiated";

    public static final String QUEUE_QRIS_SUCCESS     = "qris.payment.success";

    public static final String QUEUE_QRIS_FAILED      = "qris.payment.failed";

    public static final String EXCHANGE_IDENTITY      = "eop.identity.exchange";
    public static final String QUEUE_MERCHANT_REGISTERED = "merchant.registered";

    @Bean
    public DirectExchange financeExchange() {
        return new DirectExchange(EXCHANGE_FINANCE, true, false);
    }

    @Bean
    public Queue qrisInitiatedQueue() {
        return QueueBuilder.durable(QUEUE_QRIS_INITIATED).build();
    }

    @Bean
    public Queue qrisSuccessQueue() {
        return QueueBuilder.durable(QUEUE_QRIS_SUCCESS).build();
    }

    @Bean
    public Queue qrisFailedQueue() {
        return QueueBuilder.durable(QUEUE_QRIS_FAILED).build();
    }

    @Bean
    public Binding bindingQrisInitiated(Queue qrisInitiatedQueue, DirectExchange financeExchange) {
        return BindingBuilder.bind(qrisInitiatedQueue).to(financeExchange)
                .with(QUEUE_QRIS_INITIATED);
    }

    @Bean
    public Binding bindingQrisSuccess(Queue qrisSuccessQueue, DirectExchange financeExchange) {
        return BindingBuilder.bind(qrisSuccessQueue).to(financeExchange)
                .with(QUEUE_QRIS_SUCCESS);
    }

    @Bean
    public Binding bindingQrisFailed(Queue qrisFailedQueue, DirectExchange financeExchange) {
        return BindingBuilder.bind(qrisFailedQueue).to(financeExchange)
                .with(QUEUE_QRIS_FAILED);
    }

    @Bean
    public DirectExchange identityExchange() {
        return new DirectExchange(EXCHANGE_IDENTITY, true, false);
    }

    @Bean
    public Queue merchantRegisteredQueue() {
        return QueueBuilder.durable(QUEUE_MERCHANT_REGISTERED).build();
    }

    @Bean
    public Binding bindingMerchantRegistered(Queue merchantRegisteredQueue, DirectExchange identityExchange) {
        return BindingBuilder.bind(merchantRegisteredQueue).to(identityExchange)
                .with(QUEUE_MERCHANT_REGISTERED);
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
