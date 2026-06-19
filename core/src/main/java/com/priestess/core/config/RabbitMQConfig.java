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

/**
 * RabbitMQConfig — Konfigurasi Message Broker RabbitMQ untuk Core Finance Service.
 *
 * <p>Sesuai rules-eop-priestess.md SECTION 2 (WAJIB Message Broker) dan SECTION 5
 * (Workflow Transaksi Stateful via Saga Pattern).
 *
 * <h2>Topologi Queue Core Finance</h2>
 * <pre>
 *   qris.payment.initiated   ← Core Finance PUBLISH saat QRIS dibuat
 *   qris.payment.initiated   → QrisPaymentConsumer CONSUME (proses saldo PostgreSQL)
 *
 *   qris.payment.success     ← QrisPaymentConsumer PUBLISH jika saldo sukses
 *   qris.payment.success     → TransactionLogConsumer CONSUME (update MongoDB SUCCESS)
 *
 *   qris.payment.failed      ← QrisPaymentConsumer PUBLISH jika saldo kurang/error
 *   qris.payment.failed      → TransactionLogConsumer CONSUME (update MongoDB FAILED)
 * </pre>
 *
 * <p>Menggunakan {@link DirectExchange} dengan routing key identik dengan nama queue
 * untuk kemudahan tracing. {@link Jackson2JsonMessageConverter} memastikan objek Java
 * otomatis di-serialize ke JSON saat publish dan di-deserialize saat consume.
 */
@Configuration
public class RabbitMQConfig {

    // =========================================================================
    // NAMA QUEUE & EXCHANGE — Konstanta publik agar digunakan di Producer/Consumer
    // =========================================================================

    public static final String EXCHANGE_FINANCE       = "eop.finance.exchange";

    /** Topic saat QRIS baru dibuat — Consumer memproses mutasi saldo. */
    public static final String QUEUE_QRIS_INITIATED   = "qris.payment.initiated";

    /** Topic saat pembayaran QRIS berhasil — Consumer update MongoDB SUCCESS. */
    public static final String QUEUE_QRIS_SUCCESS     = "qris.payment.success";

    /** Topic saat pembayaran QRIS gagal — Consumer update MongoDB FAILED. */
    public static final String QUEUE_QRIS_FAILED      = "qris.payment.failed";

    // =========================================================================
    // EXCHANGE
    // =========================================================================

    /**
     * DirectExchange utama untuk semua event finansial.
     * Pesan dirouting berdasarkan routing key yang identik dengan nama queue.
     */
    @Bean
    public DirectExchange financeExchange() {
        return new DirectExchange(EXCHANGE_FINANCE, true, false);
    }

    // =========================================================================
    // QUEUES — durable=true agar pesan tidak hilang saat RabbitMQ restart
    // =========================================================================

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

    // =========================================================================
    // BINDINGS — Mengikat Queue ke Exchange dengan routing key
    // =========================================================================

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

    // =========================================================================
    // MESSAGE CONVERTER & RABBIT TEMPLATE
    // =========================================================================

    /**
     * Menggunakan Jackson JSON sebagai converter pesan.
     * Objek Java otomatis di-serialize ke JSON saat publish
     * dan di-deserialize kembali saat consume.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate dikonfigurasi dengan JSON converter agar operasi
     * {@code convertAndSend()} menghasilkan payload JSON.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
