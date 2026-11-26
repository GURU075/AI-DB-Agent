package com.guru.AI_Assisted_Database_Agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI-Assisted Database Agent
 *
 * DataSource and JPA auto-configuration are intentionally excluded — this app
 * manages per-session DataSources dynamically via ConnectionService / SessionState.
 */
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class
})
@EnableScheduling
public class AiAssistedDatabaseAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAssistedDatabaseAgentApplication.class, args);
    }
}
