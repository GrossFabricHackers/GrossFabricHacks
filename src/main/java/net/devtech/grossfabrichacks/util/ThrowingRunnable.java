package net.devtech.grossfabrichacks.util;

public interface ThrowingRunnable extends Runnable {
    void execute() throws Throwable;

    @Override
    default void run() {
        Util.handle(this);
    }
}
