# multi-synchronized

A lightweight Java library for acquiring **multiple intrinsic locks** (`synchronized`) without deadlocks.

## Why

The JVM’s `synchronized` keyword is simple and efficient for a single monitor, but taking **more than one lock** is easy to get wrong. Classic deadlock looks like this:

```java
// Thread 1
synchronized (lockA) {
    synchronized (lockB) { /* ... */ }
}

// Thread 2
synchronized (lockB) {
    synchronized (lockA) { /* ... */ }  // opposite order → deadlock
}
```

If threads acquire the same locks in different orders, they can wait on each other forever. Fixing that by hand means agreeing on a global lock order everywhere in the codebase—and never violating it.

**multi-synchronized** does that ordering for you. It sorts the lock objects by `System.identityHashCode` and always acquires them in that order, so every thread in the JVM follows the same sequence.

## Features

- **Deadlock-free multi-lock acquisition** via a consistent global order
- **Intrinsic locks only** — uses nested `synchronized`, no `ReentrantLock` required
- **Fluent API** — `MultiSynchronized.lock(...).run(...)` or `.call(...)`
- **Reusable instances** — build once, run many times
- **Validation** — rejects empty lock lists and duplicate lock objects
- **Java 17+**, virtual-thread friendly (same pinning caveats as any `synchronized` use)

## Requirements

- Java 17 or newer
- No runtime dependencies

## Installation

Maven (coordinates for Maven Central / local install):

```xml
<dependency>
  <groupId>io.github.gorokhovskiy</groupId>
  <artifactId>multi-synchronized</artifactId>
  <version>1.0.0</version>
</dependency>
```

## Examples

### Run a critical section with two locks

```java
Object accountLock = new Object();
Object ledgerLock = new Object();

MultiSynchronized.lock(accountLock, ledgerLock).run(() -> {
    // Both locks are held here, always acquired in a safe order
    transfer(from, to, amount);
});
```

Order of arguments does not matter—`lock(B, A)` and `lock(A, B)` acquire the same way.

### Return a value while holding locks

```java
int balance = MultiSynchronized.lock(accountLock, ledgerLock).call(() ->
    account.getBalance()
);
```

### Constructor form

```java
MultiSynchronized sync = new MultiSynchronized(lockA, lockB, lockC);
sync.run(() -> doWork());
String result = sync.call(() -> "done");
```

### Reuse a pre-built lock group

```java
MultiSynchronized shared = MultiSynchronized.lock(lockA, lockB);

shared.run(() -> stepOne());
shared.run(() -> stepTwo());
```

Useful when many threads share the same set of monitors.

### Invalid usage (throws `LockOrderException`)

```java
MultiSynchronized.lock();                 // no locks
MultiSynchronized.lock(lockA, lockA);     // duplicate lock object
```

## How it works

1. Copy the lock array and sort it by `System.identityHashCode(Object)`.
2. Reject duplicates (same object identity).
3. Acquire locks recursively with nested `synchronized` blocks in sorted order.
4. Run your `Runnable` / `Supplier` once all locks are held; unlock in reverse as the stack unwinds.

Because the order is derived from object identity, it is consistent for all threads without a central registry.

## Building

```bash
mvn test
mvn package
```

## License

Apache License 2.0
