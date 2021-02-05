package net.fabricmc.loader.launch.knot;

import java.security.CodeSource;
import java.util.jar.Manifest;

public class MetadataAccess extends KnotClassDelegate.Metadata {
    public MetadataAccess(KnotClassDelegate.Metadata metadata) {
        super(metadata.manifest, metadata.codeSource);
    }

    public Manifest manifest() {
        return this.manifest;
    }

    public CodeSource codeSource() {
        return this.codeSource;
    }
}
