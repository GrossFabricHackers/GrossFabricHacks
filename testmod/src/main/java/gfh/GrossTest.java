package gfh;

import java.util.Random;
import net.devtech.grossfabrichacks.entrypoints.PrePrePreLaunch;
import net.devtech.grossfabrichacks.entrypoints.RelaunchEntrypoint;
import net.devtech.grossfabrichacks.instrumentation.InstrumentationApi;
import net.devtech.grossfabrichacks.relaunch.Relauncher;
import net.devtech.grossfabrichacks.relaunch.TransformingClassLoader;
import net.devtech.grossfabrichacks.transformer.TransformerApi;
import net.fabricmc.loader.util.version.SemanticVersionImpl;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

@SuppressWarnings("ConstantConditions")
public class GrossTest implements PrePrePreLaunch, RelaunchEntrypoint {
    private static void relaunch() {
        System.out.println("Relaunching.");

        Relauncher.ensureRelaunched();
    }

    private static void transformerTest() {
        TransformerApi.registerPostMixinAsmClassTransformer((ClassNode node) -> {
            System.out.println(node.name);

            if ((node.access & Opcodes.ACC_INTERFACE) == 0 && !node.name.contains("Xaero")) {
                node.visitField(Opcodes.ACC_PUBLIC, "testfield1223", "I", null, null);

                return true;
            }

            return false;
        });
    }

    private static void instrumentationTest() {
        for (Class<?> klass : InstrumentationApi.instrumentation.getAllLoadedClasses()) {
            System.err.println(klass);
        }
    }

    @Override
    public boolean shouldRelaunch() {
        return false;
    }

    @Override
    public void onPrePrePreLaunch() {
//        relaunch();
//        transformerTest();
//        instrumentationTest();
    }

    @Override
    public void onRelaunch() {
        // This freezes the loader for some reason.
        if (true) return;

        // The breakpoint will not be activated because the debugger is not attached to the new process.
        TransformingClassLoader.registerTransformer((ClassNode klass) -> {
            if (klass.name.equals("net/fabricmc/loader/metadata/V1ModMetadata")) {
                MethodNode method = klass.methods.stream().filter(m -> m.name.equals("getVersion")).findFirst().get();
                method.instructions.clear();

                method.visitTypeInsn(Opcodes.NEW, Type.getInternalName(SemanticVersionImpl.class)); // StringVersion
                method.visitInsn(Opcodes.DUP); // StringVersion StringVersion

                method.visitTypeInsn(Opcodes.NEW, Type.getInternalName(Random.class)); // StringVersion StringVersion Random
                method.visitInsn(Opcodes.DUP); // StringVersion StringVersion Random Random
                method.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(Random.class), "<init>", "()V", false); // StringVersion StringVersion Random
                method.visitVarInsn(Opcodes.ASTORE, 1); // StringVersion StringVersion

                method.visitTypeInsn(Opcodes.NEW, Type.getInternalName(StringBuilder.class)); // StringVersion StringVersion StringBuilder
                method.visitInsn(Opcodes.DUP); // StringVersion StringVersion StringBuilder StringBuilder
                method.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(StringBuilder.class), "<init>", "()V", false); // StringVersion StringVersion StringBuilder

                method.visitInsn(Opcodes.ICONST_0);
                method.visitVarInsn(Opcodes.ISTORE, 2);

                Label start = new Label();
                method.visitLabel(start);

                method.visitVarInsn(Opcodes.ALOAD, 1); // StringVersion StringVersion StringBuilder Random
                method.visitIntInsn(Opcodes.SIPUSH, 1);
                method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Random.class), "nextInt", "(I)I", false);
                method.visitInsn(Opcodes.ICONST_1);
                method.visitInsn(Opcodes.IADD);
                method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(StringBuilder.class), "append", "(I)" + Type.getDescriptor(StringBuilder.class), false); // StringVersion StringVersion StringBuilder
                method.visitIntInsn(Opcodes.BIPUSH, '.');
                method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(StringBuilder.class), "append", "(C)" + Type.getDescriptor(StringBuilder.class), false); // StringVersion StringVersion StringBuilder

                method.visitVarInsn(Opcodes.ILOAD, 2);
                method.visitInsn(Opcodes.ICONST_2);
                method.visitJumpInsn(Opcodes.IF_ICMPLT, start);

                method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(StringBuilder.class), "toString", "()" + Type.getDescriptor(String.class), false); // StringVersion StringVersion String

                method.visitInsn(Opcodes.ICONST_0);
                method.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(SemanticVersionImpl.class), "<init>", '(' + Type.getDescriptor(String.class) + "Z)V", false); // StringVersion

                method.visitInsn(Opcodes.ARETURN);

                return true;
            }

            return false;
        });
    }
}
