package net.devtech.grossfabrichacks.instrumentation;

import java.lang.instrument.Instrumentation;
import java.util.HashSet;
import java.util.Set;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.devtech.grossfabrichacks.transformer.TransformerApi;
import net.devtech.grossfabrichacks.transformer.asm.RawClassTransformer;
import net.devtech.grossfabrichacks.util.Util;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import user11681.reflect.Classes;

public class InstrumentationApi {
    private static final Set<String> transformable = new HashSet<>();

    public static final Instrumentation instrumentation = ByteBuddyAgent.install();

    /**
     * adds a transformer that pipes a class through TransformerBootstrap,
     * this allows you to mix into any all classes (including JDK!) classes, with a few caveats.
     * <p>
     * If the class was loaded *before* you get to it, do not call this method!
     * don't pipe classes that may already be transformable by mixin, or they may be called twice.
     *
     * @param cls the internal name of the class
     */
    public static void pipeClassThroughTransformerBootstrap(String cls) {
        transformable.add(cls);

        Transformable.init();
    }

    /**
     * a convenience method for {@link InstrumentationApi#retransform(Class, AsmInstrumentationTransformer)}
     * intended to be used when the target class is not visible
     *
     * @param cls         the binary name (defined in the last section of the {@linkplain ClassLoader ClassLoader javadoc}
     *                    of the class to retransform
     * @param transformer the class transformer
     */
    public static void retransform(String cls, AsmInstrumentationTransformer transformer) {
        retransform(Classes.load(cls), transformer);
    }

    public static void retransform(Class<?> cls, AsmInstrumentationTransformer transformer) {
        retransform(cls, (RawClassTransformer) transformer);
    }

    /**
     * a convenience method for {@link InstrumentationApi#retransform(Class, RawClassTransformer)}
     * intended to be used when the target class is not visible
     *
     * @param cls         the binary name
     *                    (defined in the last section of the {@linkplain ClassLoader ClassLoader Javadoc})
     *                    of the class to retransform
     * @param transformer the class transformer
     */
    public static void retransform(String cls, RawClassTransformer transformer) {
        retransform(Classes.load(cls), transformer);
    }

    /**
     * retransform the class represented by {@code cls} by {@code transformer}.
     * The {@code className} passed to {@code transformer} may be null if {@code cls} is a JDK class.
     *
     * @param cls         the class to retransform.
     * @param transformer the class transformer.
     */
    public static void retransform(Class<?> cls, RawClassTransformer transformer) {
        Util.handle(() -> {
            CompatibilityClassFileTransformer fileTransformer = (loader, className, classBeingRedefined, protectionDomain, classfileBuffer) -> {
                if (cls == classBeingRedefined) {
                    return transformer.transform(className, classfileBuffer);
                }

                return classfileBuffer;
            };

            instrumentation.addTransformer(fileTransformer, true);
            instrumentation.retransformClasses(cls);
            instrumentation.removeTransformer(fileTransformer);
        });
    }

    public static ClassNode getNode(Class<?> klass) {
        ClassNode node = new ClassNode();

        new ClassReader(getBytecode(klass)).accept(node, 0);

        return node;
    }

    public static byte[] getBytecode(Class<?> klass) {
        RetainingDummyTransformer transformer = new RetainingDummyTransformer(klass);

        instrumentation.addTransformer(transformer);

        Util.handle(() -> instrumentation.retransformClasses(klass));

        instrumentation.removeTransformer(transformer);

        return transformer.bytecode[0];
    }

    // to seperate out the static block
    private static class Transformable {
        private static boolean init;

        private static final CompatibilityClassFileTransformer TRANSFORMER = (loader, className, classBeingRedefined, protectionDomain, classfileBuffer) -> {
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassNode node = new ClassNode();
            reader.accept(node, 0);

            if (transformable.remove(node.name)) {
                if (transformable.isEmpty()) {
                    deinit();
                }

                return TransformerApi.transformClass(node);
            }

            return classfileBuffer;
        };

        private static void deinit() {
            instrumentation.removeTransformer(TRANSFORMER);
            init = false;
        }

        private static void init() {
            if (!init) {
                instrumentation.addTransformer(TRANSFORMER);
                init = true;
            }
        }

        static {
            // pipe transformer to
            TransformerApi.manualLoad();
        }
    }
}
