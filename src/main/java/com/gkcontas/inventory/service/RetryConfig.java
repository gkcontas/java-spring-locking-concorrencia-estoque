package com.gkcontas.inventory.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.backoff.ExponentialRandomBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class RetryConfig {

    /** Attempts, not retries: 8 means one try and seven more. */
    public static final int MAX_ATTEMPTS = 8;

    /**
     * The attempt budget is generous on purpose. With dozens of workers on the
     * same row, three or five attempts are exhausted by contention alone, and
     * the scenario would end up measuring the ceiling instead of the cost.
     *
     * <p>The backoff is randomised, and that matters more than its size. A
     * fixed delay makes every loser wake up at the same instant and collide
     * again, so the conflict rate stays flat no matter how long the wait is.
     */
    @Bean
    RetryTemplate optimisticRetryTemplate() {
        ExponentialRandomBackOffPolicy backOff = new ExponentialRandomBackOffPolicy();
        backOff.setInitialInterval(2);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(40);

        RetryTemplate template = new RetryTemplate();
        template.setRetryPolicy(new SimpleRetryPolicy(MAX_ATTEMPTS,
                java.util.Map.of(OptimisticLockingFailureException.class, true)));
        template.setBackOffPolicy(backOff);
        template.setThrowLastExceptionOnExhausted(true);
        return template;
    }
}
