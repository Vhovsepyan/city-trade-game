package citytrade.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the in-memory friend-room server. {@code DataSourceAutoConfiguration} is excluded because
 * it would otherwise try to build a {@code DataSource} bean on every startup (the {@code postgres} Spring
 * profile's {@code org.postgresql:postgresql} driver is always on the classpath, T24) even when no
 * {@code spring.datasource.*} property is set; {@code PostgresConfiguration} builds the {@code DataSource}
 * itself, only under that profile.
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@EnableScheduling
public class GameServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameServerApplication.class, args);
    }
}
