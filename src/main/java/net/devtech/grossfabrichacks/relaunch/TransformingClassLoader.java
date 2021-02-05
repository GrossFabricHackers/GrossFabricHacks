package net.devtech.grossfabrichacks.relaunch;

import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;
import net.devtech.grossfabrichacks.loader.OpenClassLoader;
import net.devtech.grossfabrichacks.transformer.asm.AsmClassTransformer;
import net.devtech.grossfabrichacks.transformer.asm.RawClassTransformer;
import net.fabricmc.loader.launch.knot.GrossKnotClassDelegate;
import net.fabricmc.loader.launch.knot.KnotClassLoaderInterfaceAccess;
import net.gudenau.lib.unsafe.Unsafe;
import org.jetbrains.annotations.ApiStatus.Experimental;
import user11681.reflect.Invoker;

@Experimental
public class TransformingClassLoader extends URLClassLoader implements KnotClassLoaderInterfaceAccess, OpenClassLoader {
    public static final TransformingClassLoader instance = new TransformingClassLoader(TransformingClassLoader.class.getClassLoader());
    public static final ClassLoader parent;

    public static final GrossKnotClassDelegate delegate = new GrossKnotClassDelegate(false, null, instance, null);

    private static final MethodHandle findBootstrapClass;
    private static final MethodHandle findLoadedClass;

    public static RawClassTransformer rawTransformer;
    public static AsmClassTransformer asmTransformer;

    private static MethodHandle transformRaw;
    private static MethodHandle transformAsm;

    static int count;

    public TransformingClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
    }

    public static void registerTransformer(RawClassTransformer transformer) {
        if (rawTransformer == null) {
            rawTransformer = transformer;
        } else {
            rawTransformer = rawTransformer.andThen(transformer);
        }

        transformRaw = Invoker.bind(rawTransformer, "transform", byte[].class, String.class, byte[].class);
    }

    public static void registerTransformer(AsmClassTransformer transformer) {
        if (asmTransformer == null) {
            asmTransformer = transformer;
        } else {
            asmTransformer = asmTransformer.andThen(transformer);
        }

        transformAsm = Invoker.bind(asmTransformer, "transform", byte[].class, String.class, byte[].class);
    }

    public static byte[] transform(String name, byte[] bytecode) {
        try {
            if (rawTransformer != null) {
                bytecode = (byte[]) transformRaw.invokeExact(name, bytecode);
            }

            if (asmTransformer != null) {
                return (byte[]) transformAsm.invokeExact(name, bytecode);
            }

            return bytecode;
        } catch (Throwable throwable) {
            throw Main.rethrow(throwable);
        }
    }

    private static void defineClass(String klass) {
        byte[] bytecode = delegate.getRawClassByteArray(klass);

        instance.defineClass(klass, bytecode, 0, bytecode.length);
    }

    @SuppressWarnings("deprecation")
    @Override
    public Class<?> loadClass(String name, boolean resolve) {
        Class<?> klass = super.findLoadedClass(name);

        try {
            if (klass == null
                && (klass = (Class<?>) findLoadedClass.invokeExact(name)) == null
                && (klass = (Class<?>) findBootstrapClass.invokeExact(name)) == null) {
                if (name.startsWith("java.") || name.startsWith("org.objectweb.asm.")) {
                    klass = parent.loadClass(name);
                } else {
                    byte[] bytecode = delegate.getRawClassByteArray(name);

                    if (bytecode == null) {
                        klass = parent.loadClass(name);
                    } else {
                        bytecode = transform(name, bytecode);

                        System.out.printf("%d: %s%n", count++, name);

                        klass = super.defineClass(name, bytecode, 0, bytecode.length, delegate.getMetadata(name, this.getResource(name.replace('.', '/') + ".class")).codeSource());

                        int lastPeriodIndex = name.lastIndexOf('.');

                        if (lastPeriodIndex >= 0) {
                            String pakage = name.substring(0, lastPeriodIndex);

                            if (this.getPackage(pakage) == null) {
                                this.definePackage(pakage, null, null, null, null, null, null, null);
                            }
                        }
                    }
                }
            }
        } catch (Throwable throwable) {
            throw Main.rethrow(throwable);
        }

        if (resolve) {
            super.resolveClass(klass);
        }

        return klass;
    }

    @Override
    public GrossKnotClassDelegate getDelegate() {
        return delegate;
    }

    @Override
    public boolean isClassLoaded(String name) {
        try {
            return this.findLoadedClass(name) != null || ((Class<?>) findLoadedClass.invokeExact(name)) != null;
        } catch (Throwable throwable) {
            throw Main.rethrow(throwable);
        }
    }

    @Override
    public void addURL(URL url) {
        super.addURL(url);
    }

    @Override
    public InputStream getResourceAsStream(String name, boolean skipOriginalLoader) {
        return super.getResourceAsStream(name);
    }

    static {
        parent = instance.getParent();

        try {
            MethodHandle tempfindBootstrapClass;

            try {
                tempfindBootstrapClass = Unsafe.trustedLookup.bind(instance, "findBootstrapClass", MethodType.methodType(Class.class, String.class));
            } catch (NoSuchMethodException exception) {
                tempfindBootstrapClass = Unsafe.trustedLookup.bind(
                    Unsafe.getObject(ClassLoader.class, Unsafe.staticFieldOffset(ClassLoader.class.getDeclaredField("bootstrapClassLoader"))),
                    "findClass",
                    MethodType.methodType(Class.class, String.class)
                );
            }

            findBootstrapClass = tempfindBootstrapClass;
            findLoadedClass = Unsafe.trustedLookup.bind(parent, "findLoadedClass", MethodType.methodType(Class.class, String.class));
        } catch (Throwable exception) {
            throw Main.rethrow(exception);
        }

        defineClass("net.fabricmc.loader.launch.knot.KnotClassLoaderInterface");
        defineClass("net.fabricmc.loader.launch.knot.KnotClassDelegate$Metadata");
        defineClass("net.fabricmc.loader.launch.knot.KnotClassDelegate");
        defineClass("net.fabricmc.loader.launch.knot.MetadataAccess");
        defineClass("net.fabricmc.loader.launch.knot.GrossKnotClassDelegate");

        Thread.currentThread().setContextClassLoader(instance);
    }
}
