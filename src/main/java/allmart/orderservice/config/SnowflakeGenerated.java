package allmart.orderservice.config;

import org.hibernate.annotations.IdGeneratorType;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;

/**
 * allmart Snowflake ID 생성 어노테이션 (Hibernate 6.x 방식)
 * 엔티티 @Id 필드에 @SnowflakeGenerated 하나만 붙이면 됨
 */
@IdGeneratorType(SnowflakeIdGenerator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({FIELD, METHOD})
public @interface SnowflakeGenerated {
}
