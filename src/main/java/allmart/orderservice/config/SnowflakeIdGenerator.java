package allmart.orderservice.config;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.GeneratorCreationContext;

import java.lang.reflect.Member;
import java.util.EnumSet;

/**
 * allmart 맞춤 Snowflake ID 생성기 (Hibernate 6.x BeforeExecutionGenerator)
 * [타임스탬프 45bit][머신ID 10bit][일련번호 8bit]
 * machine-id: 환경변수 SNOWFLAKE_MACHINE_ID (기본값 1)
 */
public final class SnowflakeIdGenerator implements BeforeExecutionGenerator {

    private static final long EPOCH         = 1700000000000L;
    private static final long MACHINE_BITS  = 10L;
    private static final long SEQUENCE_BITS = 8L;
    private static final long MAX_SEQUENCE  = ~(-1L << SEQUENCE_BITS);

    private final long machineId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    /** SNOWFLAKE_MACHINE_ID 환경변수로 머신 ID 주입 — 미설정 시 기본값 1 */
    public SnowflakeIdGenerator(SnowflakeGenerated annotation, Member member, GeneratorCreationContext context) {
        String env = System.getenv("SNOWFLAKE_MACHINE_ID");
        long id = 1L;
        if (env != null && !env.isBlank()) {
            try { id = Long.parseLong(env.trim()); } catch (NumberFormatException ignored) { }
        }
        this.machineId = id;
    }

    /** INSERT 이벤트에만 ID 생성 */
    @Override
    public EnumSet<EventType> getEventTypes() {
        return EnumSet.of(EventType.INSERT);
    }

    /** Snowflake ID 생성 — 동일 밀리초 내 시퀀스 증가, 오버플로우 시 다음 밀리초 대기 */
    @Override
    public synchronized Object generate(SharedSessionContractImplementor session, Object owner,
                                        Object currentValue, EventType eventType) {
        long now = System.currentTimeMillis();
        if (now == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) now = waitNextMillis(lastTimestamp); // 시퀀스 소진 → 다음 밀리초까지 대기
        } else {
            sequence = 0L;
        }
        lastTimestamp = now;
        // [타임스탬프 45bit][머신ID 10bit][일련번호 8bit] 비트 합산
        return ((now - EPOCH) << (MACHINE_BITS + SEQUENCE_BITS))
             | (machineId << SEQUENCE_BITS)
             | sequence;
    }

    /** 현재 밀리초가 last 이하인 동안 busy-wait */
    private long waitNextMillis(long last) {
        long ts = System.currentTimeMillis();
        while (ts <= last) ts = System.currentTimeMillis();
        return ts;
    }
}
