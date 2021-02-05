package net.devtech.grossfabrichacks.loader;

import java.util.Map;
import net.devtech.grossfabrichacks.util.Util;

public interface GrossClassLoader {
    Class<?> getLoadedClass(String name);

    boolean isClassLoaded(String name);

    Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException;

    Map<String, Class<?>> getOverridingClasses();

    default void override(Class<?> klass) {
        this.getOverridingClasses().put(klass.getName(), klass);
    }

    default void override(ClassLoader classLoader, String name) {
        Util.handle(() -> this.override(classLoader.loadClass(name)));
    }
}
