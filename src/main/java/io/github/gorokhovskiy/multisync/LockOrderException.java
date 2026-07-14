package io.github.gorokhovskiy.multisync;

/**
 * Exception thrown when an invalid lock configuration is provided,
 * such as passing duplicate lock objects in a single acquisition call.
 */
public class LockOrderException extends RuntimeException {

    public LockOrderException(String message) {
        super(message);
    }
}
