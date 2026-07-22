package com.aianalyst.service.impl;

import com.aianalyst.config.ModelCircuitBreakerFailurePredicate;
import com.aianalyst.config.ModelCircuitBreakerIgnorePredicate;
import com.aianalyst.service.AsyncModelInvoker;
import com.aianalyst.service.ModelResilienceGateway;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerMetricsAutoConfiguration;
import io.github.resilience4j.springboot3.timelimiter.autoconfigure.TimeLimiterAutoConfiguration;
import io.github.resilience4j.springboot3.timelimiter.autoconfigure.TimeLimiterMetricsAutoConfiguration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskRejectedException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelResilienceGatewayIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AopAutoConfiguration.class,
                    CircuitBreakerAutoConfiguration.class,
                    CircuitBreakerMetricsAutoConfiguration.class,
                    TimeLimiterAutoConfiguration.class,
                    TimeLimiterMetricsAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "resilience4j.circuitbreaker.instances.llm-text-to-sql.sliding-window-type=COUNT_BASED",
                    "resilience4j.circuitbreaker.instances.llm-text-to-sql.sliding-window-size=2",
                    "resilience4j.circuitbreaker.instances.llm-text-to-sql.minimum-number-of-calls=2",
                    "resilience4j.circuitbreaker.instances.llm-text-to-sql.failure-rate-threshold=50",
                    "resilience4j.circuitbreaker.instances.llm-text-to-sql.wait-duration-in-open-state=1h",
                    "resilience4j.circuitbreaker.instances.llm-text-to-sql.record-failure-predicate="
                            + "com.aianalyst.config.ModelCircuitBreakerFailurePredicate",
                    "resilience4j.circuitbreaker.instances.llm-text-to-sql.ignore-exception-predicate="
                            + "com.aianalyst.config.ModelCircuitBreakerIgnorePredicate",
                    "resilience4j.timelimiter.instances.llm-text-to-sql.timeout-duration=50ms",
                    "resilience4j.timelimiter.instances.llm-text-to-sql.cancel-running-future=true");

    @Test
    void shouldOpenTextToSqlCircuitAfterTheConfiguredModelFailures() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            ModelResilienceGateway gateway = context.getBean(ModelResilienceGateway.class);
            MutableAsyncModelInvoker invoker = context.getBean(MutableAsyncModelInvoker.class);
            CircuitBreaker breaker = context.getBean(CircuitBreakerRegistry.class)
                    .circuitBreaker("llm-text-to-sql");
            invoker.coreResult.set(CompletableFuture.failedFuture(
                    new TimeoutException("provider timeout")));

            assertThatThrownBy(() -> gateway.generateSql("first").join())
                    .hasRootCauseInstanceOf(TimeoutException.class);
            assertThatThrownBy(() -> gateway.generateSql("second").join())
                    .hasRootCauseInstanceOf(TimeoutException.class);

            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
            assertThatThrownBy(() -> gateway.generateSql("third").join())
                    .hasRootCauseInstanceOf(CallNotPermittedException.class);
            assertThat(invoker.coreCalls).hasValue(2);
            SimpleMeterRegistry registry = context.getBean(SimpleMeterRegistry.class);
            assertThat(registry.find("resilience4j.circuitbreaker.calls")
                    .tag("name", "llm-text-to-sql").meters()).isNotEmpty();
            assertThat(registry.find("resilience4j.circuitbreaker.not.permitted.calls")
                    .tag("name", "llm-text-to-sql").meters()).isNotEmpty();
        });
    }

    @Test
    void shouldTimeOutANeverCompletingTextToSqlCall() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            ModelResilienceGateway gateway = context.getBean(ModelResilienceGateway.class);
            MutableAsyncModelInvoker invoker = context.getBean(MutableAsyncModelInvoker.class);
            invoker.coreResult.set(new CompletableFuture<>());

            assertThatThrownBy(() -> gateway.generateSql("slow").join())
                    .hasRootCauseInstanceOf(TimeoutException.class);
            assertThat(context.getBean(SimpleMeterRegistry.class)
                    .find("resilience4j.timelimiter.calls")
                    .tag("name", "llm-text-to-sql").meters()).isNotEmpty();
        });
    }

    @Test
    void shouldExcludeExecutorSaturationFromTheCircuitWindow() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            ModelResilienceGateway gateway = context.getBean(ModelResilienceGateway.class);
            MutableAsyncModelInvoker invoker = context.getBean(MutableAsyncModelInvoker.class);
            CircuitBreaker breaker = context.getBean(CircuitBreakerRegistry.class)
                    .circuitBreaker("llm-text-to-sql");
            invoker.coreResult.set(CompletableFuture.failedFuture(
                    new TaskRejectedException("executor saturated")));

            assertThatThrownBy(() -> gateway.generateSql("first").join())
                    .hasRootCauseInstanceOf(TaskRejectedException.class);
            assertThatThrownBy(() -> gateway.generateSql("second").join())
                    .hasRootCauseInstanceOf(TaskRejectedException.class);

            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(breaker.getMetrics().getNumberOfBufferedCalls()).isZero();
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {

        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        ModelCircuitBreakerFailurePredicate modelCircuitBreakerFailurePredicate() {
            return new ModelCircuitBreakerFailurePredicate();
        }

        @Bean
        ModelCircuitBreakerIgnorePredicate modelCircuitBreakerIgnorePredicate() {
            return new ModelCircuitBreakerIgnorePredicate();
        }

        @Bean
        MutableAsyncModelInvoker asyncModelInvoker() {
            return new MutableAsyncModelInvoker();
        }

        @Bean
        ModelResilienceGateway modelResilienceGateway(AsyncModelInvoker asyncModelInvoker) {
            return new ModelResilienceGatewayImpl(asyncModelInvoker);
        }
    }

    static class MutableAsyncModelInvoker implements AsyncModelInvoker {

        private final AtomicReference<CompletableFuture<String>> coreResult =
                new AtomicReference<>(CompletableFuture.completedFuture("ok"));
        private final AtomicInteger coreCalls = new AtomicInteger();

        @Override
        public CompletableFuture<String> invokeCore(String prompt) {
            coreCalls.incrementAndGet();
            return coreResult.get();
        }

        @Override
        public CompletableFuture<String> invokeAnalysis(String prompt) {
            return CompletableFuture.completedFuture("ok");
        }
    }
}
