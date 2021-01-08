package net.devtech.grossfabrichacks.transformer.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

/**
 * a transformer using the ASM node
 */
public interface AsmClassTransformer extends RawClassTransformer {
    boolean transform(ClassNode node);

    @Override
    default byte[] transform(String name, byte[] data) {
        final ClassNode node = new ClassNode();
        new ClassReader(data).accept(node, 0);

        this.transform(node);

        final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(writer);

        return writer.toByteArray();
    }

    default AsmClassTransformer andThen(AsmClassTransformer fixer) {
        return (node) -> this.transform(node) | fixer.transform(node);
    }
}
