package allmart.orderservice.config;

import allmart.orderservice.application.required.OutboxRepository;
import allmart.orderservice.domain.event.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Outbox 이벤트 재발행 Spring Batch 안전망 (Spring Batch 6.x).
 * Debezium CDC 장애 또는 binlog 만료로 인해 Kafka 발행이 누락된 이벤트를 5분마다 재발행.
 *
 * 대상: publishedAt IS NULL AND createdAt &lt; NOW() - 5분
 * 동작: KafkaTemplate으로 직접 발행 후 publishedAt DB 저장
 * 중복 처리: 모든 Kafka 소비자는 멱등성 보장 (orderId/tossOrderId 기준 상태 체크)
 *
 * 정상 운영 시 CDC가 5분 이내 발행 → 이 배치가 처리할 대상 없음.
 * CDC 장애 시에만 실제로 재발행 작동.
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class OutboxRepublishBatchConfig {

    private static final int CHUNK_SIZE = 100;
    private static final long STALE_MINUTES = 5L;

    @Value("${spring.kafka.bootstrap-servers:localhost:19092}")
    private String bootstrapServers;

    private final JobRepository jobRepository;
    private final OutboxRepository outboxRepository;
    private final JobOperator jobOperator;

    /** 5분마다 실행 — CDC 장애 유실 이벤트 재발행 */
    @Scheduled(fixedDelay = 5 * 60 * 1_000L, initialDelay = 60_000L)
    void triggerRepublish() {
        try {
            var params = new JobParametersBuilder()
                    .addLocalDateTime("runAt", LocalDateTime.now())
                    .toJobParameters();
            var execution = jobOperator.start(outboxRepublishJob(), params);
            log.debug("Outbox 재발행 배치 완료: status={}, 처리건수={}",
                    execution.getStatus(),
                    execution.getStepExecutions().stream()
                            .mapToLong(s -> s.getWriteCount()).sum());
        } catch (Exception e) {
            log.error("Outbox 재발행 배치 실행 실패", e);
        }
    }

    @Bean
    Job outboxRepublishJob() {
        return new JobBuilder("outboxRepublishJob", jobRepository)
                .start(outboxRepublishStep())
                .build();
    }

    @Bean
    Step outboxRepublishStep() {
        return new StepBuilder("outboxRepublishStep", jobRepository)
                .<OutboxEvent, OutboxEvent>chunk(CHUNK_SIZE)
                .reader(outboxItemReader())
                .writer(outboxItemWriter())
                .build();
    }

    /**
     * Outbox 미발행 이벤트 리더.
     * @StepScope — 배치 실행마다 새로 생성되어 cutoff를 실행 시각 기준으로 계산.
     * (버그 수정: @Bean 싱글톤이면 cutoff가 앱 시작 시각에 고정됨)
     */
    @Bean
    @StepScope
    ItemReader<OutboxEvent> outboxItemReader() {
        // 배치 실행 시마다 새로 계산 — @StepScope가 보장
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(STALE_MINUTES);
        Iterator<OutboxEvent> iter = outboxRepository
                .findUnpublished(cutoff, PageRequest.of(0, 1000))
                .getContent()
                .iterator();
        return () -> iter.hasNext() ? iter.next() : null;
    }

    /**
     * Outbox 재발행 라이터.
     * Kafka 발행 후 publishedAt을 DB에 명시적으로 저장.
     * (버그 수정: markPublished() 후 save() 없으면 detached 엔티티라 더티 체킹 미작동 → 무한 중복 발행)
     */
    @Bean
    ItemWriter<OutboxEvent> outboxItemWriter() {
        KafkaTemplate<String, String> kafka = outboxKafkaTemplate();
        return items -> {
            for (OutboxEvent event : items.getItems()) {
                kafka.send(event.getEventType(), event.getAggregateId(), event.getPayload());
                event.markPublished();
                outboxRepository.save(event);  // DB 저장 — detached 엔티티 merge
                log.info("Outbox 재발행: eventType={}, aggregateId={}", event.getEventType(), event.getAggregateId());
            }
        };
    }

    /**
     * Outbox 배치 전용 KafkaTemplate.
     * acks=all + max.in.flight=1 — 안전망이므로 유실 없음 + 순서 보장.
     * (버그 수정: acks=1은 리더 장애 시 유실 가능, 안전망 목적에 부적합)
     */
    KafkaTemplate<String, String> outboxKafkaTemplate() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
    }
}
