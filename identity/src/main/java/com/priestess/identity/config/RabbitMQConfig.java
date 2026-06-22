package com.priestess.identity.config;

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
 * RabbitMQConfig — Konfigurasi Message Broker RabbitMQ untuk Identity Service.
 *
 * <p>Sesuai rules-eop-priestess.md SECTION 6 (Security Mechanism & Stateful Session Handling):
 * <blockquote>
 * "Ketika Admin membekukan akun di Identity Service, service tersebut akan langsung
 * melempar pesan {@code user.suspended} ke Message Broker. Gateway yang bertindak
 * sebagai Consumer akan menangkap pesan tersebut secara real-time dan langsung
 * menandai sesi user tersebut tidak valid, memberikan efek penendangan user secara
 * instan tanpa menunggu token kedaluwarsa 15 menit."
 * </blockquote>
 *
 * <h2>Topologi Queue Identity</h2>
 * <pre>
 *   user.suspended   ← Identity Service PUBLISH saat Admin membekukan akun
 *   user.suspended   → Gateway SuspendedUserConsumer CONSUME (invalidate in-memory cache)
 * </pre>
 */
@Configuration
public class RabbitMQConfig {

    // =========================================================================
    // KONSTANTA — Nama Exchange dan Queue
    // =========================================================================

    public static final String EXCHANGE_IDENTITY  = "eop.identity.exchange";

    /**
     * Topic yang dipublikasikan Identity saat Admin membekukan akun user.
     * Gateway berlangganan topic ini untuk invalidasi sesi secara real-time.
     */
    public static final String QUEUE_USER_SUSPENDED = "user.suspended";

    /**
     * Topic yang dipublikasikan saat merchant berhasil terdaftar.
     * Core Finance Service berlangganan topic ini untuk sinkronisasi owner.
     */
    public static final String QUEUE_MERCHANT_REGISTERED = "merchant.registered";

    // =========================================================================
    // EXCHANGE
    // =========================================================================

    @Bean
    public DirectExchange identityExchange() {
        return new DirectExchange(EXCHANGE_IDENTITY, true, false);
    }

    // =========================================================================
    // QUEUE
    // =========================================================================

    @Bean
    public Queue userSuspendedQueue() {
        return QueueBuilder.durable(QUEUE_USER_SUSPENDED).build();
    }

    @Bean
    public Queue merchantRegisteredQueue() {
        return QueueBuilder.durable(QUEUE_MERCHANT_REGISTERED).build();
    }

    // =========================================================================
    // BINDING
    // =========================================================================

    @Bean
    public Binding bindingUserSuspended(Queue userSuspendedQueue, DirectExchange identityExchange) {
        return BindingBuilder.bind(userSuspendedQueue).to(identityExchange)
                .with(QUEUE_USER_SUSPENDED);
    }

    @Bean
    public Binding bindingMerchantRegistered(Queue merchantRegisteredQueue, DirectExchange identityExchange) {
        return BindingBuilder.bind(merchantRegisteredQueue).to(identityExchange)
                .with(QUEUE_MERCHANT_REGISTERED);
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
