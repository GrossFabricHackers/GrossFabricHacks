package net.devtech.grossfabrichacks.loader;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.jimfs.PathType;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import net.devtech.grossfabrichacks.util.ThrowingConsumer;
import net.devtech.grossfabrichacks.util.Util;
import org.apache.commons.io.IOUtils;
import user11681.reflect.Classes;

import static com.google.common.jimfs.Feature.FILE_CHANNEL;
import static com.google.common.jimfs.Feature.SECURE_DIRECTORY_STREAM;

public class URLAdder {
    public static final FileSystem inMemoryFs = Jimfs.newFileSystem(
        "grossFileSystem",
        Configuration.builder(PathType.unix())
            .setRoots("/")
            .setWorkingDirectory("/")
            .setAttributeViews("basic")
            .setSupportedFeatures(SECURE_DIRECTORY_STREAM, FILE_CHANNEL)
            .build()
    );

    protected final Map<Path, URL> jars = new LinkedHashMap<>();
    protected final List<ClassLoader> classLoaders = new ArrayList<>();
    protected final List<OpenClassLoader> openClassLoaders = new ArrayList<>();

    public URLAdder loader(ClassLoader loader) {
        this.classLoaders.add(loader);

        return this;
    }

    public URLAdder loader(OpenClassLoader loader) {
        this.openClassLoaders.add(loader);

        return this;
    }

    public URLAdder jar(URL root) {
        Util.handle(() -> this.jars.put(Paths.get(root.toURI()), root));

        return this;
    }

    public URLAdder jar(URI root) {
        Util.handle(() -> this.jars.put(Paths.get(root), root.toURL()));

        return this;
    }

    public URLAdder jar(Path root) {
        Util.handle(() -> this.jars.put(root, root.toUri().toURL()));

        return this;
    }

    public URLAdder add() {
        for (Map.Entry<Path, URL> jar : this.jars.entrySet()) {
            for (ClassLoader loader : this.classLoaders) {
                Classes.addURL(loader, jar.getValue());
            }

            for (OpenClassLoader loader : this.openClassLoaders) {
                loader.addURL(jar.getValue());
            }
        }

        return this;
    }

    public URLAdder addNested(URL copyLocation) {
        return Util.handle(() -> this.addNested(Paths.get(copyLocation.toURI())));
    }

    public URLAdder addNested(URI copyLocation) {
        return this.addNested(Paths.get(copyLocation));
    }

    public URLAdder addNested(String copyRoot) {
        return this.addNested(Paths.get(copyRoot));
    }

    public URLAdder addNested(Path copyRoot) {
        this.forEach(copyRoot, (Path copy) -> {
            for (ClassLoader loader : this.classLoaders) {
                Classes.addURL(loader, copy.toUri().toURL());
            }

            for (OpenClassLoader loader : this.openClassLoaders) {
                loader.addURL(copy.toUri().toURL());
            }

            this.addNested(copy);
        });

        return this;
    }

    public URLAdder clear() {
        this.jars.clear();
        this.classLoaders.clear();
        this.openClassLoaders.clear();

        return this;
    }

    public URLAdder forEach(Path copyRoot, ThrowingConsumer<Path> action) {
        Util.handle(() -> {
            for (Map.Entry<Path, URL> root : this.jars.entrySet()) {
                copy(copyRoot.resolve(root.getKey().getFileName().toString()), Files.newInputStream(root.getKey()), action);

                try (JarInputStream stream = new JarInputStream(Files.newInputStream(root.getKey()))) {
                    JarEntry entry = stream.getNextJarEntry();

                    while (entry != null) {
                        if (entry.getName().endsWith(".jar")) {
                            copy(copyRoot.resolve(entry.getName()), stream, action);
                        }

                        entry = stream.getNextJarEntry();
                    }
                }
            }
        });

        return this;
    }

    private static void copy(Path path, InputStream input, ThrowingConsumer<Path> action) {
        Util.handle(() -> {
            Files.createDirectories(path.getParent());
            Files.write(path, IOUtils.toByteArray(input), StandardOpenOption.CREATE, StandardOpenOption.WRITE);

            action.accept(path);
        });
    }

    static {
        Util.handle(() -> Class.forName(Files.class.getName() + "$FileTypeDetectors", true, null));
    }
}
