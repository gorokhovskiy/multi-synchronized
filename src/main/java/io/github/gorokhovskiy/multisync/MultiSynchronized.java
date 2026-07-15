package io.github.gorokhovskiy.multisync;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Deadlock-free acquisition of multiple intrinsic locks via recursive ordering.
 */
public final class MultiSynchronized {

    private static final Comparator<Object> LOCK_COMPARATOR =
        Comparator.comparingInt(System::identityHashCode);

    private final Object[] sortedLocks;

    /**
     * Creates an ordered lock group for the given lock objects.
     */
    public MultiSynchronized(Object... locks) {
        if (locks == null || locks.length == 0) {
            throw new LockOrderException(
                "At least one lock object is required."
            );
        }

        Object[] sorted = Arrays.copyOf(locks, locks.length);
        Arrays.sort(sorted, LOCK_COMPARATOR);

        for (int i = 1; i < sorted.length; i++) {
            if (sorted[i] == sorted[i - 1]) {
                throw new LockOrderException(
                    "Duplicate lock object detected at index " + i
                );
            }
        }

        this.sortedLocks = sorted;
    }

    /**
     * Factory for an ordered lock group.
     */
    public static MultiSynchronized lock(Object... locks) {
        return new MultiSynchronized(locks);
    }

    /**
     * Executes a task while holding all locks.
     */
    public void run(Runnable task) {
        Objects.requireNonNull(task, "Task cannot be null");
        execute(sortedLocks, task);
    }

    /**
     * Executes a task that returns a value while holding all locks.
     */
    public <T> T call(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "Supplier cannot be null");
        ValueHolder<T> holder = new ValueHolder<>();
        execute(sortedLocks, () -> holder.value = supplier.get());
        return holder.value;
    }

    /**
     * Acquires pre-sorted locks recursively, then runs the task.
     */
    private static void execute(Object[] sortedLocks, Runnable task) {
        acquireRecursively(sortedLocks, 0, task);
    }

    private static void acquireRecursively(
        Object[] locks,
        int depth,
        Runnable task
    ) {
        if (depth == locks.length) {
            task.run();
        } else {
            synchronized (locks[depth]) {
                acquireRecursively(locks, depth + 1, task);
            }
        }
    }

    private static class ValueHolder<T> {
        T value;
    }
}
