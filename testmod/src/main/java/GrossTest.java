import net.devtech.grossfabrichacks.entrypoints.PrePrePreLaunch;
import net.devtech.grossfabrichacks.instrumentation.InstrumentationApi;
import net.devtech.grossfabrichacks.relaunch.Relauncher;
import net.devtech.grossfabrichacks.transformer.TransformerApi;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

public class GrossTest implements PrePrePreLaunch {
    @Override
    public void onPrePrePreLaunch() {
        transformerTest();
        instrumentationTest();
//        relaunch();
    }

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
}
