package com.musicinsights.spotifycatalog.bootstrap;

import org.flywaydb.core.Flyway;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/**
 * 애플리케이션 시작 시점에 Flyway 마이그레이션을 실행하는 설정 클래스입니다.
 *
 * <p>{@link ApplicationRunner}를 Bean으로 등록해, 컨텍스트 초기화 직후
 * JDBC datasource 설정을 기반으로 {@link Flyway#migrate()}를 수행합니다.</p>
 *
 * <p>주요 설정값:
 * {@code spring.datasource.*}, {@code spring.flyway.locations},
 * {@code spring.flyway.baseline-on-migrate}, {@code spring.flyway.baseline-version}</p>
 */
@Configuration
@Profile("local")
public class FlywayRunner {

    /**
     * 애플리케이션 시작 직후 Flyway 마이그레이션을 실행하는 Runner Bean을 생성합니다.
     *
     * @param env application.yml 및 profile 설정을 조회하기 위한 {@link Environment}
     * @return Flyway 마이그레이션을 수행하는 {@link ApplicationRunner}
     */
    @Bean
    ApplicationRunner runFlyway(Environment env) {
        return args -> {
            String url = env.getProperty("spring.datasource.url");
            String user = env.getProperty("spring.datasource.username");
            String pass = env.getProperty("spring.datasource.password");

            Flyway flyway = Flyway.configure()
                    .dataSource(url, user, pass)
                    .locations(env.getProperty("spring.flyway.locations", "classpath:db/migration"))
                    .baselineOnMigrate(Boolean.parseBoolean(
                            env.getProperty("spring.flyway.baseline-on-migrate", "false")
                    ))
                    .baselineVersion(env.getProperty("spring.flyway.baseline-version", "0"))
                    .load();

            flyway.migrate();
        };
    }
}
