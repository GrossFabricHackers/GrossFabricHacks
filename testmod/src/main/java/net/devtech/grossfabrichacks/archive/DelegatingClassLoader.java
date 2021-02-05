package net.devtech.grossfabrichacks.archive;

import net.devtech.grossfabrichacks.util.Util;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import user11681.reflect.Classes;

public class DelegatingClassLoader {
    public static void hack(ClassLoader preKnotClassLoader, ClassLoader knotClassLoader) {
        try {
            String preKnotClassLoaderName = preKnotClassLoader.getClass().getName().replace('.', '/');
            String name = preKnotClassLoaderName.substring(0, Math.max(preKnotClassLoaderName.lastIndexOf('/'), preKnotClassLoaderName.indexOf('$')) + 1) + "DelegatingClassLoader";
            ClassNode hackedClassLoader = new ClassNode();
            new ClassReader(preKnotClassLoaderName).accept(hackedClassLoader, 0);

            String commonName = "net/devtech/grossfabrichacks/GrossFabricHacks$Common";
            String knotClassLoaderName = knotClassLoader.getClass().getName().replace('.', '/');
            String superclassName = preKnotClassLoader.getClass().getSuperclass().getName().replace('.', '/');
            InsnList instructions = new InsnList();
            MethodNode loadClass = null;

            for (MethodNode method : hackedClassLoader.methods) {
                if (method.name.equals("loadClass") && method.desc.equals("(Ljava/lang/String;Z)Ljava/lang/Class;")) {
                    loadClass = method;

                    break;
                }
            }

            LabelNode fallback = new LabelNode();

            instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, commonName, "knotClassLoader", "L" + knotClassLoaderName + ";"));
            instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, knotClassLoaderName, "isClassLoaded", "(Ljava/lang/String;)Z", false));
            instructions.add(new JumpInsnNode(Opcodes.IFEQ, fallback));
            instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, commonName, "knotClassLoader", "L" + knotClassLoaderName + ";"));
            instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            instructions.add(new InsnNode(Opcodes.ICONST_0));
            instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, knotClassLoaderName, "loadClass", "(Ljava/lang/String;Z)Ljava/lang/Class;", false));
            instructions.add(new InsnNode(Opcodes.ARETURN));
            instructions.add(fallback);

            loadClass.instructions.insert(instructions);

            if (loadClass == null) {
                loadClass = (MethodNode) hackedClassLoader.visitMethod(Opcodes.ACC_PUBLIC, "loadClass", "(Ljava/lang/String;Z)Ljava/lang/Class;", null, null);

                loadClass.visitVarInsn(Opcodes.ALOAD, 1);
                loadClass.visitVarInsn(Opcodes.ILOAD, 2);
                loadClass.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassName, "loadClass", "(Ljava/lang/String;Z)Ljava/lang/Class;", false);
                loadClass.visitInsn(Opcodes.ARETURN);
            }

            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            hackedClassLoader.accept(writer);

            Classes.staticCast(preKnotClassLoader, Classes.defineClass(knotClassLoader, preKnotClassLoaderName.replace('/', '.'), writer.toByteArray(), preKnotClassLoader.getClass().getProtectionDomain()));
        } catch (Throwable throwable) {
            throw Util.crash(throwable);
        }
    }
}
