package com.musicinsights.spotifycatalog.bootstrap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
/**
 * FlywayRunner가 실제로 Flyway 마이그레이션을 수행하는지 검증하는 통합 테스트.
 *
 * <p>테스트에서는 스프링 컨텍스트에서 {@link org.springframework.boot.ApplicationRunner} 빈을 주입받아 실행하고,
 * 실행 후 JDBC로 {@code flyway_schema_history} 테이블을 조회하여 성공한 마이그레이션 기록이 존재하는지 확인한다.</p>
 */
@SpringBootTest
class FlywayRunnerTest {

    /** 테스트 프로필(application-test.yml)의 datasource 설정 값을 읽기 위한 Environment */
    @Autowired Environment env;

    /** FlywayRunner 설정 클래스에서 등록한 ApplicationRunner 빈 */
    @Autowired org.springframework.boot.ApplicationRunner flywayRunner;

    /**
     * FlywayRunner 실행 후 Flyway 히스토리 테이블에 성공 기록이 1개 이상 쌓였는지 검증한다.
     *
     * @throws Exception JDBC 연결/쿼리 수행 과정에서 발생할 수 있는 예외
     */
    @Test
    @DisplayName("FlywayRunner가 마이그레이션을 수행")
    void runFlyway() throws Exception {
        flywayRunner.run(new DefaultApplicationArguments(new String[0]));

        String url  = env.getProperty("spring.datasource.url");
        String user = env.getProperty("spring.datasource.username");
        String pass = env.getProperty("spring.datasource.password");

        assertThat(url).isNotNull();

        try (Connection conn = DriverManager.getConnection(url, user, pass);
             var st = conn.createStatement();
             var rs = st.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1")) {
            rs.next();
            assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(1);
        }
    }
}