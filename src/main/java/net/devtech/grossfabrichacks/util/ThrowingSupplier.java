package net.devtech.grossfabrichacks.util;

import java.util.function.Supplier;

public interface ThrowingSupplier<T> extends Supplier<T> {
    T execute() throws Throwable;

    @Override
    default T get() {
        return Util.handle(this);
    }
}
