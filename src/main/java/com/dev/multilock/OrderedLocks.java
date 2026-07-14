package com.dev.multilock;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Factory and execution engine for deadlock-free intrinsic lock acquisition.
 */
public final class OrderedLocks {

    private static final Comparator<Object> LOCK_COMPARATOR =
        Comparator.comparingInt(System::identityHashCode);

    private OrderedLocks() {
        // Utility class
    }

    /**
     * Creates a synchronized block scope for multiple locks.
     */
    public static SynchronizedBlock multiSynchronized(Object... locks) {
        if (locks == null || locks.length == 0) {
            throw new LockOrderException(
                "At least one lock object is required."
            );
        }

        Object[] sortedLocks = Arrays.copyOf(locks, locks.length);
        Arrays.sort(sortedLocks, LOCK_COMPARATOR);

        for (int i = 1; i < sortedLocks.length; i++) {
            if (sortedLocks[i] == sortedLocks[i - 1]) {
                throw new LockOrderException(
                    "Duplicate lock object detected at index " + i
                );
            }
        }

        return new SynchronizedBlock(sortedLocks);
    }

    /**
     * Execution Engine: Acquires pre-sorted locks recursively.
     * Package-private: Hidden from public API, used only by SynchronizedBlock.
     */
    static void execute(Object[] sortedLocks, Runnable task) {
        acquireRecursively(sortedLocks, 0, task);
    }

    /**
     * Recursive synchronization logic.
     */
    private static void acquireRecursively(
        Object[] locks,
        int depth,
        Runnable task
    ) {
        if (depth == locks.length) {
            // Base case: All locks acquired, execute task
            task.run();
        } else {
            // Recursive case: Acquire current lock, then recurse
            synchronized (locks[depth]) {
                acquireRecursively(locks, depth + 1, task);
            }
        }
    }
}
