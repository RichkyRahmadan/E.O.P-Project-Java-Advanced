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

/**
 * RabbitMQConfig — Konfigurasi Message Broker RabbitMQ untuk Oracle Support Service.
 *
 * <p>Sesuai rules-eop-priestess.md SECTION 4:
 * <blockquote>
 * "Support & Oracle Service: Menangani pengaduan pengguna dengan bertindak sebagai
 * Consumer yang mendengarkan antrean keluhan, memprosesnya via Google Gemini AI API,
 * dan memicu Java Mail Sender."
 * </blockquote>
 *
 * <p>Sesuai SECTION 8 (Dead Letter Queue):
 * <blockquote>
 * "Jika terjadi kegagalan pembacaan pesan berulang kali pada Consumer (misal:
 * Google Gemini AI API timeout di Support Service setelah {@code @Retryable} habis),
 * pesan tersebut akan dilemparkan ke antrean khusus bernama {@code complaint-dlq}
 * untuk dianalisis oleh Admin secara manual."
 * </blockquote>
 *
 * <h2>Topologi Queue Oracle</h2>
 * <pre>
 *   complaint.processing  ← SupportController PUBLISH saat user submit keluhan
 *                         → ComplaintConsumer CONSUME (simpan MongoDB + analisis AI + email)
 *
 *   complaint.dlq         ← Dead Letter Queue
 *                         → Jika ComplaintConsumer gagal setelah max retry habis
 *                         → Admin memeriksa manual untuk investigasi
 * </pre>
 */
@Configuration
public class RabbitMQConfig {

    // =========================================================================
    // KONSTANTA
    // =========================================================================

    public static final String EXCHANGE_SUPPORT  = "eop.support.exchange";
    public static final String EXCHANGE_DLQ      = "eop.support.dlq.exchange";

    /** Queue utama untuk proses keluhan secara asinkron. */
    public static final String QUEUE_COMPLAINT_PROCESSING = "complaint.processing";

    /**
     * Dead Letter Queue — sesuai SECTION 8 rules-eop-priestess.md.
     * Pesan masuk ke sini jika {@code ComplaintConsumer} gagal setelah semua retry habis.
     */
    public static final String QUEUE_COMPLAINT_DLQ = "complaint.dlq";

    // =========================================================================
    // EXCHANGE
    // =========================================================================

    @Bean
    public DirectExchange supportExchange() {
        return new DirectExchange(EXCHANGE_SUPPORT, true, false);
    }

    /** Exchange khusus untuk Dead Letter Queue. */
    @Bean
    public DirectExchange dlqExchange() {
        return new DirectExchange(EXCHANGE_DLQ, true, false);
    }

    // =========================================================================
    // QUEUES
    // =========================================================================

    /**
     * Queue utama dengan konfigurasi Dead Letter Routing.
     * Jika pesan di-reject atau TTL habis, otomatis dikirim ke {@code complaint-dlq}.
     */
    @Bean
    public Queue complaintProcessingQueue() {
        return QueueBuilder.durable(QUEUE_COMPLAINT_PROCESSING)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLQ)
                .withArgument("x-dead-letter-routing-key", QUEUE_COMPLAINT_DLQ)
                .build();
    }

    /**
     * Dead Letter Queue — durable agar pesan tidak hilang saat restart.
     * Admin dapat memeriksa pesan di queue ini untuk investigasi manual.
     */
    @Bean
    public Queue complaintDlqQueue() {
        return QueueBuilder.durable(QUEUE_COMPLAINT_DLQ).build();
    }

    // =========================================================================
    // BINDINGS
    // =========================================================================

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
