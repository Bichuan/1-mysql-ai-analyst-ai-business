package com.aianalyst.performance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 可选启用的轻量接口压测，不引入 JMeter/Gatling 依赖，也不会默认访问真实环境。
 * 默认只压健康检查；传入 PERFORMANCE_TOKEN 后也可验证受保护的只读接口。
 */
@EnabledIfSystemProperty(named = "runPerformanceTest", matches = "true")
class ApiPerformanceSmokeTest {

    private static final String DEFAULT_URL = "http://127.0.0.1:8080/api/health";

    @Test
    void shouldMeetConfiguredLatencyAndSuccessRateThresholds() {
        String targetUrl = System.getProperty("performance.url", DEFAULT_URL);
        int requestCount = intProperty("performance.requests", 200, 1, 5_000);
        int concurrency = intProperty("performance.concurrency", 20, 1, 200);
        int warmupCount = intProperty("performance.warmup", 10, 0, 200);
        long maxP95Millis = longProperty("performance.maxP95Millis", 1_000L, 1L, 60_000L);
        double minimumSuccessRate = doubleProperty("performance.minSuccessRate", 0.99D, 0D, 1D);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        HttpRequest request = request(targetUrl);
        for (int i = 0; i < warmupCount; i++) {
            execute(client, request);
        }

        ExecutorService workers = Executors.newFixedThreadPool(concurrency);
        List<Sample> samples = new ArrayList<>(requestCount);
        long startedAt = System.nanoTime();
        try {
            List<CompletableFuture<Sample>> futures = new ArrayList<>(requestCount);
            for (int i = 0; i < requestCount; i++) {
                futures.add(CompletableFuture.supplyAsync(() -> execute(client, request), workers));
            }
            // 统一等待可确保吞吐量的计时范围覆盖全部请求，而不是只覆盖任务提交过程。
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            futures.forEach(future -> samples.add(future.join()));
        } finally {
            workers.shutdownNow();
        }
        long elapsedNanos = System.nanoTime() - startedAt;

        long successCount = samples.stream().filter(Sample::successful).count();
        double successRate = (double) successCount / requestCount;
        double throughput = requestCount / (elapsedNanos / 1_000_000_000D);
        List<Long> latencies = samples.stream()
                .map(Sample::latencyMillis)
                .sorted(Comparator.naturalOrder())
                .toList();
        long p50 = percentile(latencies, 0.50D);
        long p95 = percentile(latencies, 0.95D);
        long p99 = percentile(latencies, 0.99D);

        System.out.printf(Locale.ROOT,
                "PERFORMANCE_RESULT url=%s requests=%d concurrency=%d successRate=%.2f%% "
                        + "throughput=%.2f req/s p50=%dms p95=%dms p99=%dms%n",
                targetUrl, requestCount, concurrency, successRate * 100D, throughput, p50, p95, p99);

        assertThat(successRate)
                .as("HTTP 2xx success rate")
                .isGreaterThanOrEqualTo(minimumSuccessRate);
        assertThat(p95)
                .as("P95 response time in milliseconds")
                .isLessThanOrEqualTo(maxP95Millis);
    }

    private HttpRequest request(String targetUrl) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(targetUrl))
                .timeout(Duration.ofSeconds(10))
                .GET();
        String token = System.getenv("PERFORMANCE_TOKEN");
        if (token != null && !token.isBlank()) {
            // Token 只从临时环境变量读取，测试输出中绝不打印凭据。
            builder.header("Authorization", "Bearer " + token.trim());
        }
        return builder.build();
    }

    private Sample execute(HttpClient client, HttpRequest request) {
        long startedAt = System.nanoTime();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return new Sample(response.statusCode(), elapsedMillis(startedAt));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CompletionException(exception);
        } catch (IOException exception) {
            throw new CompletionException(exception);
        }
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private long percentile(List<Long> sortedValues, double percentile) {
        int index = Math.max(0, (int) Math.ceil(percentile * sortedValues.size()) - 1);
        return sortedValues.get(index);
    }

    private int intProperty(String name, int defaultValue, int minimum, int maximum) {
        int value = Integer.parseInt(System.getProperty(name, String.valueOf(defaultValue)));
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private long longProperty(String name, long defaultValue, long minimum, long maximum) {
        long value = Long.parseLong(System.getProperty(name, String.valueOf(defaultValue)));
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private double doubleProperty(String name, double defaultValue, double minimum, double maximum) {
        double value = Double.parseDouble(System.getProperty(name, String.valueOf(defaultValue)));
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private record Sample(int statusCode, long latencyMillis) {
        private boolean successful() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}
