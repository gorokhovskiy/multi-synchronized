package io.github.gorokhovskiy.multisync;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/**
 * Tests for virtual thread compatibility and pinning behavior.
 * 
 * Note: Thread pinning with synchronized blocks is significantly improved in Java 24+ 
 * (JEP 491) 【turn0search0】【turn0search6】. For Java 21-23, consider using ReentrantLock 
 * in hot paths with heavy blocking I/O 【turn0search1】【turn0search3】.
 */
class VirtualThreadTest {

    @Test
    void testMultiLockWithVirtualThread() throws InterruptedException {
        assumeTrue(Runtime.version().feature() >= 21, "Skipping test: Virtual threads require Java 21+");
        Object lockA = new Object();
        Object lockB = new Object();
        AtomicInteger successCount = new AtomicInteger(0);
        
        Thread vThread = Thread.ofVirtual().name("test-vt-1").start(() -> {
            new MultiSynchronized(lockA, lockB)
                .run(() -> {
                    successCount.incrementAndGet();
                    // Simulate some work
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
        });
        
        vThread.join(1000);
        assertFalse(vThread.isAlive(), "Virtual thread should have completed");
        assertEquals(1, successCount.get(), "Task should have executed successfully");
    }

    @Test
    void testHighConcurrencyWithVirtualThreads() throws InterruptedException {
        assumeTrue(Runtime.version().feature() >= 21, "Skipping test: Virtual threads require Java 21+");
        final int NUM_TASKS = 10_000;
        Object lock1 = new Object();
        Object lock2 = new Object();
        Object lock3 = new Object();
        
        AtomicInteger completedTasks = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(NUM_TASKS);
        
        // Start 10,000 virtual threads
        for (int iParameter = 0; iParameter < NUM_TASKS; iParameter++) {
            final int i = iParameter;
            Thread.ofVirtual().start(() -> {
                try {
                    // Alternate lock orders to test prevention
                    if (i % 2 == 0) {
                        new MultiSynchronized(lock1, lock2, lock3)
                            .run(() -> completedTasks.incrementAndGet());
                    } else {
                        new MultiSynchronized(lock3, lock1, lock2)
                            .run(() -> completedTasks.incrementAndGet());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Wait with timeout (virtual threads should complete very quickly)
        boolean completed = latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(completed, "All virtual threads should complete within timeout");
        assertEquals(NUM_TASKS, completedTasks.get(), "All tasks should complete");
    }

    @Test
    void testVirtualThreadPinningWithBlockingOperation() throws InterruptedException {
        assumeTrue(Runtime.version().feature() >= 21, "Skipping test: Virtual threads require Java 21+");
        Object lock = new Object();
        
        // This test demonstrates potential pinning with synchronized + blocking I/O
        // In Java 24+, this should NOT pin (JEP 491) 【turn0search0】【turn0search6】
        Thread vThread = Thread.ofVirtual().name("pinning-test").start(() -> {
            new MultiSynchronized(lock).run(() -> {
                try {
                    // Blocking operation inside synchronized block
                    Thread.sleep(Duration.ofMillis(100));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        });
        
        vThread.join(2000);
        assertFalse(vThread.isAlive(), "Virtual thread should complete");
    }

    @Test
    void testVirtualThreadWithReentrantLockComparison() {
        assumeTrue(Runtime.version().feature() >= 21, "Skipping test: Virtual threads require Java 21+");
        // This test compares behavior with ReentrantLock (recommended for Java 21-23)
        Object lock1 = new Object();
        Object lock2 = new Object();
        
        AtomicInteger syncCounter = new AtomicInteger(0);
        AtomicInteger reentrantCounter = new AtomicInteger(0);
        
        // Test with synchronized (MultiSynchronized)
        new MultiSynchronized(lock1, lock2).run(syncCounter::incrementAndGet);
        
        // Test with ReentrantLock (manual implementation)
        java.util.concurrent.locks.ReentrantLock rLock1 = new java.util.concurrent.locks.ReentrantLock();
        java.util.concurrent.locks.ReentrantLock rLock2 = new java.util.concurrent.locks.ReentrantLock();
        
        rLock1.lock();
        try {
            rLock2.lock();
            try {
                reentrantCounter.incrementAndGet();
            } finally {
                rLock2.unlock();
            }
        } finally {
            rLock1.unlock();
        }
        
        assertEquals(1, syncCounter.get());
        assertEquals(1, reentrantCounter.get());
    }
}