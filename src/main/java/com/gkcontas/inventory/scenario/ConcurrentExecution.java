package com.gkcontas.inventory.scenario;

import com.gkcontas.inventory.service.ConcurrentUpdateException;
import com.gkcontas.inventory.service.DeadlockDetectedException;
import com.gkcontas.inventory.service.InsufficientStockException;
import com.gkcontas.inventory.service.RowLockTimeoutException;
import com.gkcontas.inventory.service.StockItemNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

/**
 * Runs N tasks as simultaneously as the machine allows and reports what each
 * one did.
 *
 * <p>Two details decide whether a concurrency scenario measures anything. The
 * threads have to be alive and parked on a latch before the clock starts -- a
 * pool that creates its threads on demand staggers the first requests by
 * milliseconds, which is wider than the window being studied. And each task's
 * failure has to be classified rather than counted, because "32 workers, 1
 * success" means something completely different when the other 31 lost a
 * version check than when they timed out on a lock.
 */
public final class ConcurrentExecution {

    private ConcurrentExecution() {
    }

    public record TaskOutcome(int index, String kind, long micros) {

        public boolean succeeded() {
            return "ok".equals(kind);
        }
    }

    public record Report(List<TaskOutcome> outcomes, long elapsedMicros) {

        public long count(String kind) {
            return outcomes.stream().filter(o -> kind.equals(o.kind())).count();
        }

        public long successes() {
            return count("ok");
        }

        public Map<String, Long> failuresByKind() {
            return outcomes.stream()
                    .filter(o -> !o.succeeded())
                    // A sorted map so the breakdown reads the same way on
                    // every run and a diff of two runs is a diff of numbers.
                    .collect(Collectors.groupingBy(TaskOutcome::kind, TreeMap::new,
                            Collectors.counting()));
        }

        /** Mean of the whole batch, successes and failures alike. */
        public long meanMicros() {
            return outcomes.isEmpty() ? 0
                    : Math.round(outcomes.stream().mapToLong(TaskOutcome::micros).average().orElse(0));
        }

        public long percentileMicros(int percentile) {
            if (outcomes.isEmpty()) {
                return 0;
            }
            List<Long> sorted = outcomes.stream().map(TaskOutcome::micros)
                    .sorted(Comparator.naturalOrder()).toList();
            int index = Math.min(sorted.size() - 1,
                    (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1);
            return sorted.get(Math.max(index, 0));
        }

        public long maxMicros() {
            return outcomes.stream().mapToLong(TaskOutcome::micros).max().orElse(0);
        }

        /** Completed requests per second over the wall clock of the batch. */
        public double throughputPerSecond() {
            return elapsedMicros == 0 ? 0 : outcomes.size() * 1_000_000.0 / elapsedMicros;
        }
    }

    public static Report run(int workers, IntConsumer task) {
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);
        List<TaskOutcome> outcomes = new ArrayList<>(java.util.Collections.nCopies(workers, null));

        // Platform threads, not virtual ones: every task spends its life
        // blocked on a JDBC call, and a carrier thread pinned by a synchronous
        // socket read gives nothing back while turning the thread count into a
        // variable this measurement does not want.
        try (ExecutorService pool = Executors.newFixedThreadPool(workers)) {
            for (int i = 0; i < workers; i++) {
                int index = i;
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        go.await();
                        long startedAt = System.nanoTime();
                        String kind;
                        try {
                            task.accept(index);
                            kind = "ok";
                        } catch (RuntimeException failure) {
                            kind = classify(failure);
                        }
                        long micros = (System.nanoTime() - startedAt) / 1_000;
                        synchronized (outcomes) {
                            outcomes.set(index, new TaskOutcome(index, kind, micros));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            awaitQuietly(ready, 30);
            long startedAt = System.nanoTime();
            go.countDown();
            awaitQuietly(done, 120);
            long elapsedMicros = (System.nanoTime() - startedAt) / 1_000;
            synchronized (outcomes) {
                return new Report(outcomes.stream().filter(java.util.Objects::nonNull).toList(),
                        elapsedMicros);
            }
        }
    }

    private static void awaitQuietly(CountDownLatch latch, long seconds) {
        try {
            if (!latch.await(seconds, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the concurrent batch did not finish in "
                        + seconds + "s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the batch", e);
        }
    }

    static String classify(RuntimeException failure) {
        if (failure instanceof ConcurrentUpdateException) {
            return "conflict";
        }
        if (failure instanceof DeadlockDetectedException) {
            return "deadlock";
        }
        if (failure instanceof RowLockTimeoutException) {
            return "locked";
        }
        if (failure instanceof InsufficientStockException) {
            return "insufficient";
        }
        if (failure instanceof StockItemNotFoundException) {
            return "not_found";
        }
        return "other:" + failure.getClass().getSimpleName();
    }

    /** A think time between the read and the write, in whole milliseconds. */
    public static Runnable thinkFor(long millis) {
        if (millis <= 0) {
            return () -> {
            };
        }
        return () -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }
}
