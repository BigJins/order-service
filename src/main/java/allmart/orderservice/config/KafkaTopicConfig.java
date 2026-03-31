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

    @Bean
    public NewTopic paymentResultTopic() {
        return TopicBuilder.name(paymentResultTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderPaidTopic() {
        return TopicBuilder.name(orderPaidTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic deliveryCompletedTopic() {
        return TopicBuilder.name(deliveryCompletedTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
