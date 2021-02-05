package net.devtech.grossfabrichacks.relaunch;

import java.util.Arrays;
import net.devtech.grossfabrichacks.GrossFabricHacks;
import net.devtech.grossfabrichacks.entrypoints.RelaunchEntrypoint;
import org.jetbrains.annotations.ApiStatus.Experimental;

@Experimental
public class Main {
    public static final String NAME = "net.devtech.grossfabrichacks.relaunch.Main";

    @SuppressWarnings("RedundantClassCall")
    public static void main(String... args) throws Throwable {
        String entrypoints = System.clearProperty(Relauncher.ENTRYPOINT_PROPERTY);

        if (entrypoints != null) {
            for (String entrypoint : entrypoints.split(GrossFabricHacks.Common.DELIMITER)) {
                RelaunchEntrypoint.class.cast(TransformingClassLoader.instance.loadClass(entrypoint).getDeclaredConstructor().newInstance()).onRelaunch();
            }
        }

        TransformingClassLoader.instance.loadClass(args[0]).getDeclaredMethod("main", String[].class).invoke(null, (Object) Arrays.copyOfRange(args, 1, args.length));
    }

    public static RuntimeException rethrow(Throwable throwable) throws RuntimeException {
        return rethrowInternal(throwable);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException rethrowInternal(Throwable throwable) throws T {
        throw (T) throwable;
    }
}
