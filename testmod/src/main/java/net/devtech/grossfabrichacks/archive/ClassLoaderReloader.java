package net.devtech.grossfabrichacks.archive;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessControlContext;
import java.security.SecureClassLoader;
import java.util.Locale;
import net.devtech.grossfabrichacks.GrossFabricHacks;
import net.devtech.grossfabrichacks.loader.URLAdder;
import net.devtech.grossfabrichacks.relaunch.Relauncher;
import net.devtech.grossfabrichacks.util.Util;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.discovery.ModResolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import user11681.reflect.Accessor;
import user11681.reflect.Classes;
import user11681.reflect.Fields;
import user11681.reflect.Invoker;
import user11681.reflect.Reflect;

// Do not reload in production.
@SuppressWarnings("ConfusingArgumentToVarargsMethod")
public class ClassLoaderReloader {
    private static final Logger logger = LogManager.getLogger("Reloader");

    public static boolean isReloaded() {
        return Boolean.getBoolean(Relauncher.RELAUNCHED_PROPERTY);
    }

    public static void ensureReloaded() {
        if (!isReloaded()) {
            launchMain();
        }
    }

    public static SecureClassLoader getNewLoader() {
        logger.info("Commence reload.");

        try {
            // close the in-memory file system to avoid later collision
            URLAdder.inMemoryFs.close();

            // look up the classloader hierarchy until we find Launcher$ExtClassLoader
            String launcherClassName = Reflect.java9 ? "jdk.internal.loader.ClassLoaders" : "sun.misc.Launcher";
            String appClassLoaderClassName = launcherClassName + "$AppClassLoader";
            Class<?> extClassLoaderClass = Class.forName(launcherClassName + (Reflect.java9 ? "$PlatformClassLoader" : "$ExtClassLoader"));
            ClassLoader extClassLoader = FabricLoader.class.getClassLoader();

            while (extClassLoader.getClass() != extClassLoaderClass) {
                extClassLoader = extClassLoader.getParent();
            }

            // Make new ExtClassLoader
            ClassLoader newExtClassLoader;

            if (Reflect.java9) {
                ClassLoader bootLoader = (ClassLoader) Invoker.unreflect(Class.forName(launcherClassName), "bootLoader").invoke();
                newExtClassLoader = (ClassLoader) Invoker.findConstructor(extClassLoaderClass, bootLoader.getClass()).invoke(bootLoader);
            } else {
                newExtClassLoader = (ClassLoader) Invoker.unreflect(extClassLoaderClass.getDeclaredMethod("getExtClassLoader")).invoke();
            }

            ReferenceArrayList<URL> URLs = ReferenceArrayList.wrap(((URLClassLoader) ((ClassLoader) GrossFabricHacks.Common.classLoader).getParent()).getURLs());

            for (String path : System.getProperty("java.class.path").split(File.pathSeparator)) {
                URLs.add(new File(path).getCanonicalFile().toURI().toURL());
            }

            // Make new AppClassLoader
            SecureClassLoader newAppClassLoader;

            if (Reflect.java9) {
                newAppClassLoader = (SecureClassLoader) Invoker.findConstructor(Class.forName(appClassLoaderClassName), extClassLoaderClass, Classes.URLClassPath).invoke(
                    extClassLoader,
                    Invoker.findConstructor(Classes.URLClassPath, URL[].class, Class.forName("java.net.URLStreamHandlerFactory"), AccessControlContext.class).invoke(URLs.toArray(), null, null)
                );
            } else {
                newAppClassLoader = (SecureClassLoader) Invoker.findConstructor(Class.forName(appClassLoaderClassName), URL[].class, ClassLoader.class).invoke(URLs.toArray(new URL[0]), extClassLoader);
            }

            Field systemClassLoader = Fields.getField(ClassLoader.class, "scl");

            if (systemClassLoader == null) {
                Accessor.putObject(ClassLoader.class, "applicationClassLoader", newAppClassLoader);
            } else {
                Accessor.putObject(systemClassLoader, newAppClassLoader);
            }

            return newAppClassLoader;
        } catch (Throwable throwable) {
            throw Util.crash(throwable);
        }
    }

    public static void launchMain() {
        launchMain(getNewLoader());
    }

    public static void launchMain(ClassLoader newLoader) {
        LogManager.shutdown();

        try {
            ((Closeable) (Accessor.getObject(ModResolver.class, "inMemoryFs"))).close();
        } catch (IOException exception) {
            throw Util.crash(exception);
        }

        System.setProperty("fabric.side", FabricLoader.getInstance().getEnvironmentType().name().toLowerCase(Locale.ROOT));

        Thread.currentThread().setContextClassLoader(newLoader);

        try {
            Invoker.findStatic(newLoader.loadClass("net.devtech.grossfabrichacks.relaunch.Main"), "main", void.class, String[].class).invokeExact(FabricLoader.getInstance().getLaunchArguments(false));

            System.exit(0);
        } catch (Throwable throwable) {
            throw Util.crash(new RuntimeException("Reloading did not succeed.", throwable));
        }
    }
}
