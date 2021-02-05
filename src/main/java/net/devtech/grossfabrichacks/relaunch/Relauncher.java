package net.devtech.grossfabrichacks.relaunch;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.jimfs.Jimfs;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.devtech.grossfabrichacks.GrossFabricHacks;
import net.devtech.grossfabrichacks.entrypoints.RelaunchEntrypoint;
import net.devtech.grossfabrichacks.loader.URLAdder;
import net.devtech.grossfabrichacks.util.ThrowingConsumer;
import net.devtech.grossfabrichacks.util.ThrowingRunnable;
import net.devtech.grossfabrichacks.util.Util;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.discovery.ModResolver;
import net.fabricmc.loader.util.SystemProperties;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.ApiStatus.Experimental;
import user11681.reflect.Accessor;
import user11681.reflect.Classes;

@SuppressWarnings("unused")
@Experimental
@VisibleForTesting
public class Relauncher {
    /**
     * the system property that indicates whether a {@linkplain #ensureRelaunched() relaunch} has occurred or not
     */
    public static final String RELAUNCHED_PROPERTY = "gfh.relaunch.relaunched";
    /**
     * the system property that contains the names of {@linkplain RelaunchEntrypoint relaunch entrypoint} classes
     */
    public static final String ENTRYPOINT_PROPERTY = "gfh.relaunch.entrypoints";

    private static final Path home = Paths.get(System.getProperty("java.home"));
    private static final Path temporaryDirectory = Paths.get(System.getProperty("java.io.tmpdir"));

    public final List<String> virtualMachineArguments = getVMArguments().stream().filter((String argument) -> !argument.startsWith("-agentlib:jdwp")).collect(Collectors.toList());
    public final List<String> programArguments = getProgramArguments();

    protected String debug;
    protected boolean entrypoints;

    protected Relauncher() {
        this.property(SystemProperties.DEVELOPMENT, Boolean.getBoolean(SystemProperties.DEVELOPMENT))
            .property(RELAUNCHED_PROPERTY, true);
    }

    public static Relauncher standard() {
        return new Relauncher().entrypoints().debug().mainClass(Main.NAME);
    }

    public static Relauncher custom() {
        return new Relauncher();
    }

    public static List<String> getProgramArguments() {
        try {
            Class.forName("org.multimc.EntryPoint");

            List<String> mainArgs = ObjectArrayList.wrap(FabricLoader.getInstance().getLaunchArguments(false));

            // replace MultiMC's entry point with Fabric's
            mainArgs.add(0, GrossFabricHacks.Common.getMainClass());

            return mainArgs;
        } catch (ClassNotFoundException exception) {
            return ObjectArrayList.wrap(System.getProperty("sun.java.command").split(" "));
        }
    }

    public static List<String> getVMArguments() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments();
    }

    public static boolean relaunched() {
        return Boolean.getBoolean(RELAUNCHED_PROPERTY);
    }

    public static void ensureRelaunched() {
        if (!relaunched()) {
            standard().relaunch();
        }
    }

    public Relauncher mainClass(String name) {
        return this.programArgument(0, name);
    }

    public Relauncher programArgument(String argument) {
        this.programArguments.add(argument);

        return this;
    }

    public Relauncher programArgument(int index, String argument) {
        this.programArguments.add(index, argument);

        return this;
    }

    public Relauncher entrypoints() {
        this.entrypoints = true;

        return this.property(ENTRYPOINT_PROPERTY);
    }

    public Relauncher debug() {
        return this.debug(false);
    }

    public Relauncher debug(boolean force) {
        if (force || FabricLoader.getInstance().isDevelopmentEnvironment()) {
            this.debug = "-agentlib:jdwp=transport=dt_socket,address=127.0.0.1:0,suspend=y,server=y";
        }

        return this;
    }

    public Relauncher property(String name) {
        return this.property(name, System.getProperty(name));
    }

    public Relauncher property(String name, Object value) {
        return this.property(name, String.valueOf(value));
    }

    public Relauncher property(String name, String value) {
        return this.virtualMachineArgument("D", String.format("%s=%s", name, value));
    }

    public Relauncher virtualMachineArgument(String option, String argument) {
        return this.virtualMachineArgument('-' + option + argument);
    }

    public Relauncher virtualMachineArgument(String argument) {
        this.virtualMachineArguments.add(argument);

        return this;
    }

    public void relaunch() {
        Util.handle(() -> {
            Set<String> newClassPath = new ObjectOpenHashSet<>();
            ThrowingConsumer<Path> copyAction = (Path path) -> newClassPath.add(path.toString());
            URLAdder urlAdder = new URLAdder();

            if (GrossFabricHacks.Common.isJAR && !FabricLoader.getInstance().isDevelopmentEnvironment()) {
                urlAdder.jar(GrossFabricHacks.Common.gfhPath).forEach(temporaryDirectory, copyAction).clear();
            }

            if (this.debug != null) {
                this.virtualMachineArgument(this.debug);
            }

            if (this.entrypoints) {
                Arrays.stream(System.getProperty(ENTRYPOINT_PROPERTY).split(GrossFabricHacks.Common.DELIMITER))
                    .map((String klass) -> Classes.load(klass).getProtectionDomain().getCodeSource().getLocation())
                    .distinct()
                    .forEach((URL jar) -> urlAdder.jar(jar).forEach(temporaryDirectory, copyAction));
            }

            for (URL url : Classes.getURLs(ClassLoader.getSystemClassLoader())) {
                if (!url.toURI().getScheme().equals(Jimfs.URI_SCHEME)) {
                    newClassPath.add(url.getFile());
                }
            }

            // remove built-in Java libraries from the class path
            for (String path : System.getProperty("java.class.path").split(File.pathSeparator)) {
                if (!path.startsWith(home.toString())) {
                    newClassPath.add(path);
                }
            }

            List<String> args = new ReferenceArrayList<>();
            args.add(home.resolve("bin").resolve("java" + OS.operatingSystem.executableExtension).toString());
            args.addAll(this.virtualMachineArguments);
            args.add("-cp");
            args.add(String.join(File.pathSeparator, newClassPath));
            args.addAll(this.programArguments);

            // release lock on log file
            LogManager.shutdown();

            ((FileSystem) Accessor.getObject(ModResolver.class, "inMemoryFs")).close();
            URLAdder.inMemoryFs.close();

            Runtime.getRuntime().addShutdownHook(new Thread((ThrowingRunnable) () -> new ProcessBuilder(args).inheritIO().start()));

            System.exit(0);
        });
    }
}
