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

/**
 * RabbitMQConfig — Konfigurasi Message Broker RabbitMQ untuk E.O.P Gateway.
 *
 * <p>Gateway berperan sebagai <b>Consumer</b> dari event-event yang diterbitkan
 * oleh Identity Service. Sesuai rules-eop-priestess.md SECTION 6:
 * <blockquote>
 * "E.O.P Gateway... yang bertindak sebagai Consumer akan menangkap pesan
 * {@code user.suspended} tersebut secara real-time dan langsung menandai sesi
 * user tersebut tidak valid saat itu juga di memori."
 * </blockquote>
 *
 * <h2>Queue yang Dikonsumsi Gateway</h2>
 * <pre>
 *   user.suspended   ← Diterbitkan oleh Identity Service
 *                    → Gateway SuspendedUserConsumer menambahkan ke in-memory cache
 * </pre>
 *
 * <p>Exchange dan Queue harus sesuai persis dengan yang dideklarasikan
 * di {@code identity/config/RabbitMQConfig.java} agar binding berjalan benar.
 */
@Configuration
public class RabbitMQConfig {

    // =========================================================================
    // KONSTANTA — HARUS IDENTIK dengan identity/config/RabbitMQConfig.java
    // =========================================================================

    /** Exchange yang sama persis dengan yang dideklarasikan di Identity Service. */
    public static final String EXCHANGE_IDENTITY  = "eop.identity.exchange";

    /** Queue yang sama persis dengan yang dideklarasikan di Identity Service. */
    public static final String QUEUE_USER_SUSPENDED = "user.suspended";

    // =========================================================================
    // EXCHANGE & QUEUE — Consumer side declaration (idempotent di RabbitMQ)
    // =========================================================================

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

    // =========================================================================
    // MESSAGE CONVERTER & RABBIT TEMPLATE
    // =========================================================================

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
