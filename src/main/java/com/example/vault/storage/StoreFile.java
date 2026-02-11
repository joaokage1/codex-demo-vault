package com.example.vault.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class StoreFile {
    private StoreFile() {
    }

    public static Properties load(Path storePath) throws IOException {
        Properties properties = new Properties();
        if (Files.exists(storePath)) {
            try (InputStream input = Files.newInputStream(storePath)) {
                properties.load(input);
            }
        }
        return properties;
    }

    public static void save(Path storePath, Properties properties) throws IOException {
        Path parent = storePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream output = Files.newOutputStream(storePath)) {
            properties.store(output, "Secret Store");
        }
    }
}
