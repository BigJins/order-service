package allmart.orderservice.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 트랜잭션 읽기 전용 여부에 따라 Primary/Replica 데이터소스를 라우팅.
 * @Transactional(readOnly=true) → Replica
 * @Transactional (쓰기) → Primary
 *
 * spring.datasource.primary.url이 설정된 경우에만 DataSourceConfig에 의해 등록됨.
 * 테스트/로컬 기본 H2 환경에서는 이 클래스가 사용되지 않음.
 */
public class ReplicationRoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                ? DataSourceType.REPLICA
                : DataSourceType.PRIMARY;
    }

    public enum DataSourceType {
        PRIMARY, REPLICA
    }
}
