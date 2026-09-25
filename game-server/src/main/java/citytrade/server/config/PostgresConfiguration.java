package citytrade.server.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The {@code postgres} Spring profile's database wiring (T24, Architecture 7.3): a plain JDBC
 * {@link DataSource} built from the standard {@code spring.datasource.*} properties, migrated with Flyway
 * before it is handed to anything else. {@link GameServerApplication} excludes Spring Boot's own
 * {@code DataSourceAutoConfiguration} (it would otherwise require a datasource even outside this profile,
 * since the {@code postgres} JDBC driver is always on the classpath), so this class is the only source of a
 * {@code DataSource}/{@code JdbcTemplate} bean, and only under this profile.
 */
@Configuration
@Profile("postgres")
@EnableConfigurationProperties(DataSourceProperties.class)
public class PostgresConfiguration {

    @Bean
    public DataSource dataSource(DataSourceProperties properties) {
        DataSource dataSource = properties.initializeDataSourceBuilder().build();
        Flyway.configure().dataSource(dataSource).load().migrate();
        return dataSource;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
