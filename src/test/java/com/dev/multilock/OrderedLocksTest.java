package com.dev.multilock;

import java.util.List;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class OrderedLocksTest {

    @Test
    void testSingleLockRun() {
        Object lock = new Object();
        AtomicInteger val = new AtomicInteger(0);
        
        OrderedLocks.multiSynchronized(lock).run(val::incrementAndGet);
        
        assertEquals(1, val.get());
    }

    @Test
    void testMultipleLocksCall() {
        Object lock1 = new Object();
        Object lock2 = new Object();
        
        String result = OrderedLocks.multiSynchronized(lock1, lock2)
                                     .call(() -> "Success");
        
        assertEquals("Success", result);
    }

    @Test
    void testZeroLocksThrowsException() {
        assertThrows(LockOrderException.class, () -> {
            OrderedLocks.multiSynchronized().run(() -> {});
        });
    }

    @Test
    void testDuplicateLockThrowsException() {
        Object lock = new Object();
        
        assertThrows(LockOrderException.class, () -> {
            OrderedLocks.multiSynchronized(lock, lock).run(() -> {});
        });
    }

    @Test
    void testBlockInstanceReusability() {
        // Prove that the SynchronizedBlock object can be safely reused
        Object lock1 = new Object();
        Object lock2 = new Object();
        
        var sharedBlock = OrderedLocks.multiSynchronized(lock1, lock2);
        
        AtomicInteger counter = new AtomicInteger(0);
        sharedBlock.run(counter::incrementAndGet);
        sharedBlock.run(counter::incrementAndGet);
        
        assertEquals(2, counter.get());
    }

    /**
     * Classic Deadlock Scenario Test.
     * Thread 1 wants Lock A then Lock B.
     * Thread 2 wants Lock B then Lock A.
     */
    @Test
    void testPreventsDeadlockUnderHighContention() throws InterruptedException {
        final int THREADS = 100;
        final int ITERATIONS = 50;
        
        Object lockA = new Object();
        Object lockB = new Object();
        Object lockC = new Object();

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch latch = new CountDownLatch(THREADS);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < THREADS; i++) {
            // Purposely randomize the order of locks passed to the method
            boolean reverseOrder = (i % 2 == 0);
            
            // Create the block ONCE per thread to test reusability
            SynchronizedBlock block = reverseOrder 
                ? OrderedLocks.multiSynchronized(lockC, lockB, lockA)
                : OrderedLocks.multiSynchronized(lockA, lockB, lockC);

            executor.submit(() -> {
                try {
                    for (int j = 0; j < ITERATIONS; j++) {
                        block.run(() -> Thread.yield());
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    fail("Thread threw exception: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all threads with a strict timeout
        boolean finished = latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "Deadlock detected! Threads did not finish within timeout.");
        assertEquals(THREADS, successCount.get(), "Not all threads completed successfully.");
        var l = List.of(1, 2);
        for (var i: l) {
            System.out.println("Current " + i);
        }
    }
}