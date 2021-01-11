package net.devtech.grossfabrichacks.loader;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.jimfs.PathType;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import net.devtech.grossfabrichacks.GrossFabricHacks;
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

    public static void addJAR(ClassLoader classLoader, URL root) {
        try {
            Classes.addURL(classLoader, root);

            addNestedJARs(classLoader, Paths.get(root.toURI()));
        } catch (URISyntaxException exception) {
            throw GrossFabricHacks.Common.crash(exception);
        }
    }

    public static void addJAR(ClassLoader classLoader, URI root) {
        try {
            Classes.addURL(classLoader, root.toURL());
        } catch (MalformedURLException exception) {
            throw GrossFabricHacks.Common.crash(exception);
        }

        addNestedJARs(classLoader, Paths.get(root));
    }

    public static void addJAR(ClassLoader classLoader, Path root) {
        try {
            Classes.addURL(classLoader, root.toUri().toURL());
        } catch (MalformedURLException exception) {
            throw GrossFabricHacks.Common.crash(exception);
        }

        addNestedJARs(classLoader, root);
    }

    public static void addNestedJARs(ClassLoader classLoader, Path root) {
        try (JarInputStream stream = new JarInputStream(Files.newInputStream(root))) {
            JarEntry entry = stream.getNextJarEntry();

            while (entry != null) {
                if (entry.getName().endsWith(".jar")) {
                    Path inMemoryPath = inMemoryFs.getPath(entry.getName());

                    if (Files.notExists(inMemoryPath)) {
                        Files.createDirectories(inMemoryPath.getParent());
                        Files.write(inMemoryPath, IOUtils.toByteArray(stream), StandardOpenOption.CREATE, StandardOpenOption.WRITE);

                        Classes.addURL(classLoader, inMemoryPath.toUri().toURL());

                        addNestedJARs(classLoader, inMemoryPath);
                    }
                }

                entry = stream.getNextJarEntry();
            }
        } catch (IOException exception) {
            throw GrossFabricHacks.Common.crash(exception);
        }
    }

    static {
        Classes.load(null, Files.class.getName() + "$FileTypeDetectors");
    }
}
