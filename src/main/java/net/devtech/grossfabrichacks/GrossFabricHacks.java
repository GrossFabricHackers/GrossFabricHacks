package net.devtech.grossfabrichacks;

import net.devtech.grossfabrichacks.entrypoints.PrePrePreLaunch;
import net.devtech.grossfabrichacks.transformer.asm.AsmClassTransformer;
import net.devtech.grossfabrichacks.transformer.asm.RawClassTransformer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.LanguageAdapter;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import user11681.dynamicentry.DynamicEntry;
import user11681.reflect.Reflect;

public class GrossFabricHacks implements LanguageAdapter {
    private static final Logger LOGGER = LogManager.getLogger("GrossFabricHacks");

//    public static final UnsafeKnotClassLoader UNSAFE_LOADER;

    @Override
    public native <T> T create(net.fabricmc.loader.api.ModContainer mod, String value, Class<T> type);

    public static class State {
        public static boolean mixinLoaded;
        public static boolean manualLoad;

        public static boolean shouldWrite;
        // micro-optimization: cache transformer presence
        public static boolean transformPreMixinRawClass;
        public static boolean transformPreMixinAsmClass;
        public static boolean transformPostMixinRawClass;
        public static boolean transformPostMixinAsmClass;
        public static RawClassTransformer preMixinRawClassTransformer;
        public static RawClassTransformer postMixinRawClassTransformer;
        public static AsmClassTransformer preMixinAsmClassTransformer;
        public static AsmClassTransformer postMixinAsmClassTransformer;
    }

    static {
        LOGGER.info("no good? no, this man is definitely up to evil.");

        try {
            final ClassLoader applicationClassLoader = FabricLoader.class.getClassLoader();
            final ClassLoader KnotClassLoader = GrossFabricHacks.class.getClassLoader();

            final String UnsafeKnotClassLoader = "net.fabricmc.loader.launch.knot.UnsafeKnotClassLoader";
            final String[] classes = {
                "net.gudenau.lib.unsafe.Unsafe",
                "net.devtech.grossfabrichacks.unsafe.UnsafeUtil",
                "net.devtech.grossfabrichacks.unsafe.UnsafeUtil$FirstInt",
                UnsafeKnotClassLoader
            };

            final int classCount = classes.length;

            for (int i = FabricLoader.getInstance().isDevelopmentEnvironment() ? 1 : 0; i < classCount; i++) {
                final String name = classes[i];
                Reflect.defineClass(applicationClassLoader, name, IOUtils.toByteArray(KnotClassLoader.getResourceAsStream(name.replace('.', '/') + ".class")), GrossFabricHacks.class.getProtectionDomain());
            }

            Class.forName(UnsafeKnotClassLoader, true, applicationClassLoader);

            final String name = "net.devtech.grossfabrichacks.mixin.GrossFabricHacksPlugin";
            Reflect.defineClass(KnotClassLoader, name, IOUtils.toByteArray(KnotClassLoader.getResourceAsStream(name.replace('.', '/') + ".class")), GrossFabricHacks.class.getProtectionDomain());
        } catch (final Throwable throwable) {
            throw new RuntimeException(throwable);
        }

        DynamicEntry.executeOptionalEntrypoint("gfh:prePrePreLaunch", PrePrePreLaunch.class, PrePrePreLaunch::onPrePrePreLaunch);
    }
}
