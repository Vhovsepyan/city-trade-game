package citytrade.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import citytrade.server.persistence.MatchLog;
import citytrade.server.persistence.PostgresMatchLog;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The full application context (not just {@link ServerConfiguration} in isolation, unlike
 * {@code ServerConfigurationTest}) under the {@code postgres} profile: {@link PostgresMatchLog} must be the
 * {@link MatchLog} bean, and {@link PostgresConfiguration}'s {@code DataSource} bean must have already run
 * the Flyway migration by the time the context is up.
 */
@SpringBootTest
@ActiveProfiles("postgres")
class PostgresProfileContextTest {

    private static EmbeddedPostgres postgres;

    @Autowired
    private MatchLog matchLog;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeAll
    static void startDatabase() throws Exception {
        postgres = EmbeddedPostgres.builder().start();
    }

    @AfterAll
    static void stopDatabase() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:" + postgres.getPort() + "/postgres");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @Test
    void postgresProfileWiresThePostgresMatchLogAndMigratesTheSchema() {
        assertThat(matchLog).isInstanceOf(PostgresMatchLog.class);
        Integer matchCount = jdbc.queryForObject("SELECT count(*) FROM matches", Integer.class);
        assertThat(matchCount).isZero();
    }
}
