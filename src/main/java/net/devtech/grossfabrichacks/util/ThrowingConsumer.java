package net.devtech.grossfabrichacks.util;

import java.util.function.Consumer;

public interface ThrowingConsumer<T> extends Consumer<T> {
    void execute(T object) throws Throwable;

    @Override
    default void accept(T object) {
        Util.handle(object, this);
    }
}
