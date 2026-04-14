package allmart.orderservice.domain;

import allmart.orderservice.config.SnowflakeGenerated;
import jakarta.annotation.Nullable;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.ToString;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;

/**
 * 모든 JPA 엔티티의 공통 베이스.
 * Snowflake ID를 PK로 사용하며, 프록시 안전 equals/hashCode를 제공한다.
 */
@MappedSuperclass
@ToString
public abstract class AbstractEntity {
    @Id
    @SnowflakeGenerated
    @Getter(onMethod_ = {@Nullable})
    private Long id;

    /** Hibernate 프록시를 고려한 equals — 실제 클래스 타입과 ID 모두 일치해야 동등 */
    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        AbstractEntity that = (AbstractEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    /** 프록시 안전 hashCode — 클래스 기준으로 고정 (ID 미할당 상태에서도 안전) */
    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}