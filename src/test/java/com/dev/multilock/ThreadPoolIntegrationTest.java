package com.dev.multilock;

import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for compatibility with default Java thread pools.
 */
class ThreadPoolIntegrationTest {

    @Test
    void testWithForkJoinPoolCommonPool() throws ExecutionException, InterruptedException {
        Object lockA = new Object();
        Object lockB = new Object();
        
        // Use the common pool (used by CompletableFuture by default)
        ForkJoinPool commonPool = ForkJoinPool.commonPool();
        
        Callable<Integer> task = () -> {
            return OrderedLocks.multiSynchronized(lockA, lockB)
                .call(() -> {
                    // Simulate work
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return 42;
                });
        };
        
        // Submit multiple tasks
        Future<Integer> future1 = commonPool.submit(task);
        Future<Integer> future2 = commonPool.submit(task);
        
        assertEquals(42, future1.get());
        assertEquals(42, future2.get());
    }

    @Test
    void testWithCachedThreadPool() throws InterruptedException {
        final int NUM_THREADS = 100;
        Object lock1 = new Object();
        Object lock2 = new Object();
        
        ExecutorService executor = Executors.newCachedThreadPool();
        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(NUM_THREADS);
        
        try {
            for (int i = 0; i < NUM_THREADS; i++) {
                executor.submit(() -> {
                    try {
                        OrderedLocks.multiSynchronized(lock1, lock2)
                            .run(() -> {
                                successCount.incrementAndGet();
                                // Simulate some work
                                try {
                                    Thread.sleep(10);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            });
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            boolean completed = latch.await(10, TimeUnit.SECONDS);
            assertTrue(completed, "All tasks should complete");
            assertEquals(NUM_THREADS, successCount.get(), "All tasks should succeed");
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Executor should shut down");
        }
    }

    @Test
    void testWithFixedThreadPool() throws InterruptedException {
        final int POOL_SIZE = 10;
        final int NUM_TASKS = 100;
        
        ExecutorService executor = Executors.newFixedThreadPool(POOL_SIZE);
        Object sharedLock = new Object();
        AtomicInteger activeThreads = new AtomicInteger(0);
        AtomicInteger maxConcurrency = new AtomicInteger(0);
        
        try {
            CountDownLatch latch = new CountDownLatch(NUM_TASKS);
            
            for (int i = 0; i < NUM_TASKS; ++i) {
                executor.submit(() -> {
                    try {
                        int active = activeThreads.incrementAndGet();
                        maxConcurrency.updateAndGet(current -> Math.max(current, active));
                        
                        OrderedLocks.multiSynchronized(sharedLock)
                            .run(() -> {
                                // Critical section
                                try {
                                    Thread.sleep(20);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            });
                    } finally {
                        activeThreads.decrementAndGet();
                        latch.countDown();
                    }
                });
            }
            
            latch.await(30, TimeUnit.SECONDS);
            assertEquals(0L, latch.getCount(), "All tasks should complete");
            
            // With a fixed pool of 10, max concurrency should be close to 10
            assertTrue(maxConcurrency.get() <= POOL_SIZE + 1, 
                "Max concurrency should respect pool size");
            
        } finally {
            executor.shutdown();
        }
    }
}