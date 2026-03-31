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
public class SnowflakeIdGenerator implements BeforeExecutionGenerator {

    private static final long EPOCH         = 1700000000000L;
    private static final long MACHINE_BITS  = 10L;
    private static final long SEQUENCE_BITS = 8L;
    private static final long MAX_SEQUENCE  = ~(-1L << SEQUENCE_BITS);

    private final long machineId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(SnowflakeGenerated annotation, Member member, GeneratorCreationContext context) {
        String env = System.getenv("SNOWFLAKE_MACHINE_ID");
        this.machineId = (env != null && !env.isBlank()) ? Long.parseLong(env) : 1L;
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
        return EnumSet.of(EventType.INSERT);
    }

    @Override
    public synchronized Object generate(SharedSessionContractImplementor session, Object owner,
                                        Object currentValue, EventType eventType) {
        long now = System.currentTimeMillis();
        if (now == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) now = waitNextMillis(lastTimestamp);
        } else {
            sequence = 0L;
        }
        lastTimestamp = now;
        return ((now - EPOCH) << (MACHINE_BITS + SEQUENCE_BITS))
             | (machineId << SEQUENCE_BITS)
             | sequence;
    }

    private long waitNextMillis(long last) {
        long ts = System.currentTimeMillis();
        while (ts <= last) ts = System.currentTimeMillis();
        return ts;
    }
}
