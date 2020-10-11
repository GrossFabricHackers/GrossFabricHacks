package net.devtech.grossfabrichacks.relaunch;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.devtech.grossfabrichacks.GrossFabricHacks;
import net.devtech.grossfabrichacks.entrypoints.PrePrePrePreLaunch;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.discovery.ModResolver;
import net.fabricmc.loader.game.MinecraftGameProvider;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import net.fabricmc.loader.launch.knot.Knot;
import net.fabricmc.loader.util.Arguments;
import net.gudenau.lib.unsafe.Unsafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import user11681.dynamicentry.DynamicEntry;

import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ListIterator;
import java.util.function.Consumer;

public class Relauncher {

    private static final Logger LOGGER = LogManager.getLogger("Relauncher");

    public static void relaunchIfNeeded() {
        // We don't use isAnnotationPresent because Knot won't
        // load the RelaunchMarker class from the AppClassLoader
        boolean isRelaunched = Arrays.asList(Knot.class.getAnnotations()).stream().anyMatch(a -> a.annotationType().getName().equals(RelaunchMarker.class.getName()));
        relaunch:
        if(!isRelaunched) {
            try {
                // get entrypoints
                ReferenceArrayList<PrePrePrePreLaunch> entrypoints = ReferenceArrayList.wrap((PrePrePrePreLaunch[]) Array.newInstance(PrePrePrePreLaunch.class, 5), 0);
                DynamicEntry.executeOptionalEntrypoint("gfh:prePrePrePreLaunch", PrePrePrePreLaunch.class, entrypoints::add);

                // don't relaunch if there is no point in doing so
                // if(entrypoints.size() == 0) break relaunch;

                LOGGER.info("Relaunching...");

                // close the in-memory file system to avoid later collision
                Field inMemoryFsField = ModResolver.class.getDeclaredField("inMemoryFs");
                inMemoryFsField.setAccessible(true);
                ((Closeable) inMemoryFsField.get(null)).close();

                boolean isJava9OrHigher = !System.getProperty("java.version").startsWith("1.");

                // look up the classloader hierarchy until we find Launcher$ExtClassLoader
                final String launcherClassName = isJava9OrHigher ? "jdk.internal.loader.ClassLoaders" : "sun.misc.Launcher";
                final String appClassLoaderClassName = launcherClassName + "$AppClassLoader";
                final String extClassLoaderClassName = launcherClassName + (isJava9OrHigher ? "$PlatformClassLoader" : "$ExtClassLoader");
                final String appClassPatcherClassName = "net/devtech/grossfabrichacks/relaunch/AppClassPatcher";
                ClassLoader extClassLoader = FabricLoader.class.getClassLoader();
                while(!extClassLoader.getClass().getName().equals(extClassLoaderClassName)) {
                    extClassLoader = extClassLoader.getParent();
                }
                Class<?> extClassLoaderClass = Class.forName(extClassLoaderClassName);

                // Make new ExtClassLoader
                Method getExtClassLoader = extClassLoaderClass.getDeclaredMethod("getExtClassLoader");
                getExtClassLoader.setAccessible(true);
                ClassLoader newExtClassLoader = (ClassLoader) getExtClassLoader.invoke(null);

                // Make a new AppClassLoader class
                Consumer<MethodNode> patchSyntheticCalls = methodNode -> {
                    if(!isJava9OrHigher) {
                        // Patch inner class synthetic methods
                        for (AbstractInsnNode insn : methodNode.instructions) {
                            if (insn instanceof MethodInsnNode) {
                                MethodInsnNode mInsn = (MethodInsnNode) insn;
                                if (mInsn.owner.equals(launcherClassName.replace('.', '/')) && mInsn.name.contains("$")) {
                                    mInsn.owner = appClassPatcherClassName;
                                    switch (mInsn.desc) {
                                        case "()Ljava/net/URLStreamHandlerFactory;": {
                                            mInsn.name = "getFactory";
                                            break;
                                        }
                                        case "(Ljava/lang/String;)[Ljava/io/File;": {
                                            mInsn.name = "getClassPath";
                                            break;
                                        }
                                        case "([Ljava/io/File;)[Ljava/net/URL;": {
                                            mInsn.name = "pathToURLs";
                                            break;
                                        }
                                        case "()Ljava/lang/String;": {
                                            mInsn.name = "getBootClassPath";
                                            break;
                                        }
                                    }
                                } else if(mInsn.owner.equals("sun/misc/URLClassPath")) {
                                    mInsn.setOpcode(Opcodes.INVOKESTATIC);
                                    mInsn.owner = appClassPatcherClassName;
                                    switch (mInsn.desc) {
                                        case "(Ljava/lang/ClassLoader;)V": {
                                            mInsn.name = "initLookupCache";
                                            mInsn.desc = "(Lsun/misc/URLClassPath;Ljava/lang/ClassLoader;)V";
                                            break;
                                        }
                                        case "(Ljava/lang/String;)Z": {
                                            mInsn.name = "knownToNotExist";
                                            mInsn.desc = "(Lsun/misc/URLClassPath;Ljava/lang/String;)Z";
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                };
                byte[] appClassLoaderBytecode = getClassBytecode(appClassLoaderClassName, newExtClassLoader);
                ClassReader appReader = new ClassReader(appClassLoaderBytecode);
                ClassNode appNode = new ClassNode();
                appReader.accept(appNode, 0);
                appNode.methods.forEach(methodNode -> {
                    if(methodNode.name.equals("loadClass")) {
                        // find original first node
                        LabelNode origHead = null;
                        ListIterator<AbstractInsnNode> origInsns = methodNode.instructions.iterator();
                        while(origInsns.hasNext()) {
                            AbstractInsnNode node = origInsns.next();
                            if(node instanceof LabelNode) {
                                origHead = (LabelNode) node;
                                // we use an extra local for our injection, so we need to chop it off
                                origInsns.add(new FrameNode(Opcodes.F_CHOP, 0, new String[] {"Ljava/lang/Class;"}, 0, new String[0]));
                                break;
                            }
                        }
                        ArrayList<AbstractInsnNode> insns = new ArrayList<>();
                        insns.add(new LabelNode());
                        insns.add(new VarInsnNode(Opcodes.ALOAD, 1));
                        insns.add(new VarInsnNode(Opcodes.ILOAD, 2));
                        insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, appClassPatcherClassName, "patchClass", "(Ljava/lang/String;Z)Ljava/lang/Class;"));
                        insns.add(new VarInsnNode(Opcodes.ASTORE, 3));
                        insns.add(new VarInsnNode(Opcodes.ALOAD, 3));
                        insns.add(new JumpInsnNode(Opcodes.IFNULL, origHead));
                        insns.add(new VarInsnNode(Opcodes.ALOAD, 3));
                        insns.add(new InsnNode(Opcodes.ARETURN));
                        InsnList insnsList = new InsnList();
                        insns.forEach(insnsList::add);
                        methodNode.instructions.insert(insnsList);
                    }
                    patchSyntheticCalls.accept(methodNode);
                });
                ClassWriter appWriter = new ClassWriter(0);
                appNode.accept(appWriter);
                Class<?> newAppClass = defineClass(appClassLoaderClassName, appWriter.toByteArray(), newExtClassLoader);

                // Redefine AppClassLoader$1
                byte[] appClassLoader1Bytecode = getClassBytecode(appClassLoaderClassName + "$1", newExtClassLoader);
                ClassReader app1Reader = new ClassReader(appClassLoader1Bytecode);
                ClassNode app1Node = new ClassNode();
                app1Reader.accept(app1Node, 0);
                ClassWriter app1Writer = new ClassWriter(0);
                app1Node.methods.forEach(patchSyntheticCalls);
                app1Node.accept(app1Writer);
                defineClass(appClassLoaderClassName + "$1", app1Writer.toByteArray(), newExtClassLoader);

                // Define AppClassPatcher
                byte[] appClassPatcherBytecode = getClassBytecode(appClassPatcherClassName, Relauncher.class.getClassLoader());
                defineClass(appClassPatcherClassName, appClassPatcherBytecode, newExtClassLoader);

                // Make new AppClassLoader
                Method getAppClassLoader = newAppClass.getDeclaredMethod("getAppClassLoader", ClassLoader.class);
                getAppClassLoader.setAccessible(true);
                ClassLoader newAppClassLoader = (ClassLoader) getAppClassLoader.invoke(null, extClassLoader);
                Thread.currentThread().setContextClassLoader(newAppClassLoader);

                // execute entrypoints
                defineClass(PrePrePrePreLaunch.class.getName(), FabricLauncherBase.getLauncher().getClassByteArray(PrePrePrePreLaunch.class.getName(), false), newAppClassLoader);
                entrypoints.forEach((entrypoint) -> {
                    String binaryName = entrypoint.getClass().getName();
                    try {
                        Class<?> entrypointClass = defineClass(binaryName, FabricLauncherBase.getLauncher().getClassByteArray(binaryName, false), newAppClassLoader);
                        Method onPrePrePrePreLaunch = entrypointClass.getMethod("onPrePrePrePreLaunch");
                        onPrePrePrePreLaunch.invoke(entrypointClass.getConstructor().newInstance());
                    } catch (InvocationTargetException e) {
                        LOGGER.fatal(String.format("An error was encountered in the prePrePrePre entrypoint of class %s", binaryName), e);
                        System.exit(-1);
                    } catch (ReflectiveOperationException | IOException e) {
                        throw new RuntimeException(e);
                    }
                });

                // grab main args
                Field mcArguments = MinecraftGameProvider.class.getDeclaredField("arguments");
                mcArguments.setAccessible(true);
                Arguments args = (Arguments) mcArguments.get(((net.fabricmc.loader.FabricLoader) FabricLoader.getInstance()).getGameProvider());
                if(System.getProperty("fabric.side") == null) {
                    System.setProperty("fabric.side", FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT ? "client" : "server");
                }

                // Set RelaunchLatch to true with ASM
                String relaunchClassName = "net.fabricmc.loader.launch.knot.Knot";
                ClassReader relaunchReader = new ClassReader(FabricLauncherBase.getLauncher().getClassByteArray(relaunchClassName, false));
                ClassNode relaunchNode = new ClassNode();
                relaunchReader.accept(relaunchNode, 0);
                if(relaunchNode.visibleAnnotations == null) relaunchNode.visibleAnnotations = new ArrayList<>();
                relaunchNode.visibleAnnotations.add(new AnnotationNode("Lnet/devtech/grossfabrichacks/relaunch/RelaunchMarker;"));
                ClassWriter relaunchWriter = new ClassWriter(0);
                relaunchNode.accept(relaunchWriter);
                Class<?> newKnotClass = defineClass(relaunchClassName, relaunchWriter.toByteArray(), newAppClassLoader);

                // run Knot
                Method knotMain = newKnotClass.getMethod("main", String[].class);
                try {
                    knotMain.invoke(null, (Object) args.toArray());
                } catch (Throwable t) {
                    t.printStackTrace();
                    System.exit(-1);
                }
                System.exit(0);
            } catch (final Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }
    }

    private static Class<?> defineClass(String name, byte[] bytecode, ClassLoader classLoader) {
        return Unsafe.defineClass(name, bytecode, 0, bytecode.length, classLoader, GrossFabricHacks.class.getProtectionDomain());
    }

    private static byte[] getClassBytecode(String name, ClassLoader source) throws IOException, ClassNotFoundException {
        InputStream inputStream = source.getResourceAsStream(name.replace('.', '/') + ".class");
        if (inputStream == null) {
            throw new ClassNotFoundException();
        } else {
            int a = inputStream.available();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(Math.max(a, 32));
            byte[] buffer = new byte[8192];
            int len;
            while((len = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, len);
            }
            inputStream.close();
            return outputStream.toByteArray();
        }
    }

}