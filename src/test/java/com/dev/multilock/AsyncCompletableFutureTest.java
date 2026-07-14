package com.dev.multilock;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for integration with CompletableFuture and asynchronous programming patterns.
 * 
 * Note: Virtual threads provide an elegant solution for bridging legacy Future-based 
 * APIs with modern CompletableFuture composition patterns 【turn0search8】.
 */
class AsyncCompletableFutureTest {

    @Test
    void testCompletableFutureWithMultiLock() {
        Object lockA = new Object();
        Object lockB = new Object();
        
        // Run task asynchronously using CompletableFuture
        CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
            return OrderedLocks.multiSynchronized(lockA, lockB)
                .call(() -> {
                    // Simulate expensive computation
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return 100;
                });
        });
        
        // Chain additional operations
        CompletableFuture<String> result = future
            .thenApply(val -> "Result: " + val)
            .exceptionally(ex -> "Error: " + ex.getMessage());
        
        assertEquals("Result: 100", result.join());
    }

    @Test
    void testMultipleCompletableFuturesWithSharedLocks() {
        Object sharedLock1 = new Object();
        Object sharedLock2 = new Object();
        
        // Create multiple futures that share locks
        List<CompletableFuture<Integer>> futures = IntStream.range(0, 50)
            .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                return OrderedLocks.multiSynchronized(sharedLock1, sharedLock2)
                    .call(() -> {
                        // Simulate work
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return i * 10;
                    });
            }))
            .collect(Collectors.toList());
        
        // Wait for all futures to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );
        
        // Combine results
        CompletableFuture<Integer> combinedResult = allFutures.thenApply(v -> 
            futures.stream()
                .mapToInt(CompletableFuture::join)
                .sum()
        );
        
        // The sum should be 0+10+20+...+490 = 12250
        int expected = IntStream.range(0, 50).sum() * 10;
        assertEquals(expected, combinedResult.join());
    }

    @Test
    void testFutureToCompletableFutureConversion() {
        // This demonstrates bridging legacy Future APIs with CompletableFuture
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Object lock = new Object();
        
        try {
            // Simulate a legacy Future-returning method
            Future<String> legacyFuture = executor.submit(() -> {
                return OrderedLocks.multiSynchronized(lock)
                    .call(() -> "Legacy Result");
            });
            
            // Convert to CompletableFuture using virtual thread (Java 21+) 【turn0search8】
            CompletableFuture<String> completableFuture = new CompletableFuture<>();
            
            Thread.ofVirtual().start(() -> {
                try {
                    completableFuture.complete(legacyFuture.get());
                } catch (InterruptedException | ExecutionException e) {
                    completableFuture.completeExceptionally(e);
                }
            });
            
            // Now you can use CompletableFuture composition
            CompletableFuture<String> result = completableFuture
                .thenApply(String::toUpperCase)
                .thenApply(s -> s + " - PROCESSED");
            
            assertEquals("LEGACY RESULT - PROCESSED", result.join());
            
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void testCompletableFutureWithVirtualThreadExecutor() {
        // Create a virtual thread executor (Java 21+)
        ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        
        try {
            Object lock = new Object();
            
            // Use virtual thread executor with CompletableFuture
            CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
                return OrderedLocks.multiSynchronized(lock)
                    .call(() -> {
                        // Simulate I/O-bound operation
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return 999;
                    });
            }, virtualExecutor);
            
            assertEquals(999, future.join());
            
        } finally {
            virtualExecutor.shutdown();
        }
    }

    @Test
    void testTimeoutHandlingWithCompletableFuture() {
        Object lock = new Object();
        
        // Create a future that might timeout
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            return OrderedLocks.multiSynchronized(lock)
                .call(() -> {
                    try {
                        Thread.sleep(5000); // Long operation
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "Completed";
                });
        });
        
        // Add timeout handling
        CompletableFuture<String> withTimeout = future
            .orTimeout(1, TimeUnit.SECONDS)
            .exceptionally(ex -> {
                if (ex instanceof TimeoutException) {
                    return "Timeout occurred";
                }
                return "Error: " + ex.getMessage();
            });
        
        assertEquals("Timeout occurred", withTimeout.join());
    }
}