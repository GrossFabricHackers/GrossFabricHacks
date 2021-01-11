package net.devtech.grossfabrichacks.loader;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.devtech.grossfabrichacks.GrossFabricHacks;
import net.fabricmc.loader.discovery.ModResolver;
import user11681.reflect.Accessor;
import user11681.reflect.Classes;

public class URLAdder {
    public static final FileSystem inMemoryFs = Accessor.getObject(ModResolver.class, "inMemoryFs");

    public static void addURL(ClassLoader classLoader, URL root) {
        try {
            Classes.addURL(classLoader, root);

            addIncludedJARs(classLoader, Paths.get(root.toURI()));
        } catch (URISyntaxException exception) {
            throw GrossFabricHacks.Common.crash(exception);
        }
    }

    public static void addURL(ClassLoader classLoader, URI root) {
        try {
            Classes.addURL(classLoader, root.toURL());
        } catch (MalformedURLException exception) {
            throw GrossFabricHacks.Common.crash(exception);
        }

        addIncludedJARs(classLoader, Paths.get(root));
    }

    public static void addURL(ClassLoader classLoader, Path root) {
        try {
            Classes.addURL(classLoader, root.toUri().toURL());
        } catch (MalformedURLException exception) {
            throw GrossFabricHacks.Common.crash(exception);
        }

        addIncludedJARs(classLoader, root);
    }

    public static void addIncludedJARs(ClassLoader classLoader, Path root) {
        if (root.toString().endsWith(".jar")) {
            try (FileSystem filesystem = FileSystems.newFileSystem(root, GrossFabricHacks.Common.targetClassLoader)) {
                Path jars = filesystem.getPath("META-INF", "jars");

                if (Files.exists(jars)) {
                    try (Stream<Path> stream = Files.list(jars)) {
                        for (Path jar : stream.collect(Collectors.toList())) {
                            Path inMemoryPath = inMemoryFs.getPath(jar.getFileName().toString());

                            Files.copy(jar, inMemoryPath);

                            Classes.addURL(classLoader, inMemoryPath.toUri().toURL());
                        }
                    }
                }
            } catch (IOException exception) {
                throw GrossFabricHacks.Common.crash(exception);
            }
        }
    }

    static {
        Classes.load(null, Files.class.getName() + "$FileTypeDetectors");
    }
}
