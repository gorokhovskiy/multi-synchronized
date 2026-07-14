package com.dev.multilock;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Represents a pre-ordered group of locks ready for acquisition.
 * This class is intentionally kept minimal and delegates execution to {@link OrderedLocks}.
 */
public final class SynchronizedBlock {

    private final Object[] sortedLocks;

    // Package-private: Only OrderedLocks can instantiate this
    SynchronizedBlock(Object[] sortedLocks) {
        this.sortedLocks = sortedLocks;
    }

    /**
     * Executes a task while holding all locks in the block.
     */
    public void run(Runnable task) {
        Objects.requireNonNull(task, "Task cannot be null");
        // Delegate to the execution engine
        OrderedLocks.execute(this.sortedLocks, task);
    }

    /**
     * Executes a task that returns a value while holding all locks in the block.
     */
    public <T> T call(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "Supplier cannot be null");
        ValueHolder<T> holder = new ValueHolder<>();
        // Delegate to the execution engine
        OrderedLocks.execute(
            this.sortedLocks,
            () -> holder.value = supplier.get()
        );
        return holder.value;
    }

    // Minimal helper class for lambda capture
    private static class ValueHolder<T> {

        T value;
    }
}
