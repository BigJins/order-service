package allmart.orderservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka 토픽 자동 생성 설정
 * KafkaAdmin이 브로커에 토픽이 없으면 자동으로 생성한다.
 * (브로커의 auto.create.topics.enable=false 환경에서도 Admin API로 생성 가능)
 */
@Configuration
public class KafkaTopicConfig {

    @Value("${kafka.topics.payment-result}")
    private String paymentResultTopic;

    @Value("${kafka.topics.order-paid}")
    private String orderPaidTopic;

    @Value("${kafka.topics.delivery-completed}")
    private String deliveryCompletedTopic;

    @Value("${kafka.topics.order-canceled}")
    private String orderCanceledTopic;

    @Value("${kafka.topics.order-reserve-failed}")
    private String orderReserveFailedTopic;

    /** payment.result.v1 토픽 — pay-service 발행, order-service 소비 */
    @Bean
    public NewTopic paymentResultTopic() {
        return TopicBuilder.name(paymentResultTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /** order.paid.v1 토픽 — Outbox CDC 릴레이 후 delivery-service 소비 */
    @Bean
    public NewTopic orderPaidTopic() {
        return TopicBuilder.name(orderPaidTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /** delivery.completed.v1 토픽 — delivery-service 발행, order-service 소비 */
    @Bean
    public NewTopic deliveryCompletedTopic() {
        return TopicBuilder.name(deliveryCompletedTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /** order.canceled.v1 토픽 — Outbox CDC 릴레이 후 inventory-service 소비 */
    @Bean
    public NewTopic orderCanceledTopic() {
        return TopicBuilder.name(orderCanceledTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /** order.reserve.failed.v1 토픽 — inventory-service 발행, order-service 소비 */
    @Bean
    public NewTopic orderReserveFailedTopic() {
        return TopicBuilder.name(orderReserveFailedTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
