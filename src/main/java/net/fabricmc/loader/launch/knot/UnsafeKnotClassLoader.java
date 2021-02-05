package net.fabricmc.loader.launch.knot;

import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import java.net.URLClassLoader;
import java.util.Map;
import net.devtech.grossfabrichacks.loader.GrossClassLoader;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.game.GameProvider;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import user11681.reflect.Classes;

@SuppressWarnings({"JavadocReference", "deprecation"})
public class UnsafeKnotClassLoader extends KnotClassLoader implements GrossClassLoader {
    /**
     * {@linkplain net.fabricmc.loader.launch.server.InjectingURLClassLoader InjectingURLClassLoader} in production servers; {@linkplain ClassLoader#getSystemClassLoader system class loader} everywhere else
     */
    private static final ClassLoader preKnotClassLoader = KnotClassLoader.class.getClassLoader();
    private static final URLClassLoader parent;
    private static final GrossKnotClassDelegate delegate;
    private static final Object2ReferenceOpenHashMap<String, Class<?>> overridingClasses = new Object2ReferenceOpenHashMap<>();

    public UnsafeKnotClassLoader(boolean isDevelopment, EnvType envType, GameProvider provider) {
        super(isDevelopment, envType, provider);
    }

    @Override
    public Class<?> getLoadedClass(String name) {
        return super.findLoadedClass(name);
    }

    @Override
    public boolean isClassLoaded(String name) {
        synchronized (super.getClassLoadingLock(name)) {
            return super.findLoadedClass(name) != null || Classes.findLoadedClass(preKnotClassLoader, name) != null;
        }
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (super.getClassLoadingLock(name)) {
            Class<?> klass;

            if ((klass = overridingClasses.get(name)) == null && (klass = super.findLoadedClass(name)) == null) {
                if (name.startsWith("java.") || name.startsWith("com.google.gson.")) {
                    klass = preKnotClassLoader.loadClass(name);
                } else {
                    byte[] input = delegate.getPostMixinClassByteArray(name);

                    if (input == null) {
                        klass = preKnotClassLoader.loadClass(name);
                    } else {
                        int packageEnd = name.lastIndexOf('.');

                        if (packageEnd > 0) {
                            String packageName = name.substring(0, packageEnd);

                            if (super.getPackage(packageName) == null) {
                                super.definePackage(packageName, null, null, null, null, null, null, null);
                            }
                        }

                        klass = super.defineClass(name, input, 0, input.length, delegate.getMetadata(name, parent.getResource(delegate.getClassFileName(name))).codeSource);
                    }
                }
            }

            if (resolve) {
                super.resolveClass(klass);
            }

            return klass;
        }
    }

    @Override
    public Map<String, Class<?>> getOverridingClasses() {
        return overridingClasses;
    }

    static {
        KnotClassLoader knotClassLoader = (KnotClassLoader) FabricLauncherBase.getLauncher().getTargetClassLoader();

        parent = (URLClassLoader) knotClassLoader.getParent();
        delegate = Classes.staticCast(knotClassLoader.getDelegate(), GrossKnotClassDelegate.class);
    }
}
