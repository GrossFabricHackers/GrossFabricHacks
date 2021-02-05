package net.devtech.grossfabrichacks.util;

import net.fabricmc.loader.gui.FabricGuiEntry;
import net.gudenau.lib.unsafe.Unsafe;

public interface Util {
    static void handle(ThrowingRunnable action) {
        try {
            action.execute();
        } catch (Throwable throwable) {
            throw crash(throwable);
        }
    }

    static <T> T handle(ThrowingSupplier<T> action) {
        try {
            return action.execute();
        } catch (Throwable throwable) {
            throw crash(throwable);
        }
    }

    static <T> void handle(T input, ThrowingConsumer<T> action) {
        try {
            action.execute(input);
        } catch (Throwable throwable) {
            throw crash(throwable);
        }
    }

    static RuntimeException crash(Throwable throwable) {
        FabricGuiEntry.displayCriticalError(new RuntimeException("GrossFabricHacks encountered an error. Report it along with a log to https://github.com/GrossFabricHackers/GrossFabricHacks/issues", throwable), true);

        return Unsafe.throwException(throwable);
    }
}
