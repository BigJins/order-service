package allmart.orderservice.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;
import java.util.Map;

/**
 * DB Replica 라우팅 DataSource 설정.
 * spring.datasource.primary.url이 설정된 경우에만 활성화.
 *
 * 설정 예시 (config-server order-service-local.yml):
 *   spring.datasource.primary.jdbc-url: jdbc:mysql://localhost:3307/order_db
 *   spring.datasource.primary.username: myuser
 *   spring.datasource.primary.password: mypassword
 *   spring.datasource.replica.jdbc-url: jdbc:mysql://replica:3307/order_db
 *   spring.datasource.replica.username: myuser
 *   spring.datasource.replica.password: mypassword
 *
 * 미설정 시 Spring Boot 기본 DataSource 자동 구성 사용 (H2, 단일 MySQL 등).
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "spring.datasource.primary.jdbc-url")
public class DataSourceConfig {

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.primary")
    DataSource primaryDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.replica")
    DataSource replicaDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    @Bean
    @Primary
    DataSource routingDataSource(DataSource primaryDataSource, DataSource replicaDataSource) {
        ReplicationRoutingDataSource routing = new ReplicationRoutingDataSource();
        routing.setTargetDataSources(Map.of(
                ReplicationRoutingDataSource.DataSourceType.PRIMARY, primaryDataSource,
                ReplicationRoutingDataSource.DataSourceType.REPLICA, replicaDataSource
        ));
        routing.setDefaultTargetDataSource(primaryDataSource);
        routing.afterPropertiesSet();
        log.info("DB Replica 라우팅 활성화 — readOnly → Replica, 쓰기 → Primary");
        return new LazyConnectionDataSourceProxy(routing);
    }
}
