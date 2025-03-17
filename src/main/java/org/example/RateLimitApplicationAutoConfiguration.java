package org.example;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@ConditionalOnMissingBean(Scheduled.class) // Enable only if the user hasn't done it
@EnableScheduling
public class RateLimitApplicationAutoConfiguration {
}
