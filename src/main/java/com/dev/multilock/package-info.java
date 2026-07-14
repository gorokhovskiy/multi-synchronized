/**
 * A utility library for safely acquiring multiple intrinsic JVM locks
 * ({@code synchronized}) without risking deadlocks.
 *
 * <p>This library uses a recursive lock-ordering algorithm based on
 * {@link System#identityHashCode(Object)} to ensure that all threads
 * across the JVM acquire locks in a strict, globally consistent order.</p>
 *
 * @since 1.0.0
 */
package com.dev.multilock;
