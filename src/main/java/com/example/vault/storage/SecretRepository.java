package com.example.vault.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

public class SecretRepository {
    private static final String SECRET_PREFIX = "secret.";

    private final Path storePath;

    public SecretRepository(Path storePath) {
        this.storePath = Objects.requireNonNull(storePath, "storePath");
    }

    public Optional<SecretRecord> get(String path) throws IOException {
        Properties properties = StoreFile.load(storePath);
        String encodedPath = encodePath(path);
        String baseKey = keyPrefix(encodedPath);
        String ciphertext = properties.getProperty(baseKey + "ciphertext");
        if (ciphertext == null) {
            return Optional.empty();
        }
        return Optional.of(new SecretRecord(
                path,
                decode(properties, baseKey + "encryptedDek"),
                decode(properties, baseKey + "dekIv"),
                decode(properties, baseKey + "secretIv"),
                decode(properties, baseKey + "ciphertext"),
                Integer.parseInt(properties.getProperty(baseKey + "version", "1")),
                properties.getProperty(baseKey + "createdAt"),
                properties.getProperty(baseKey + "updatedAt")));
    }

    public void save(SecretRecord record) throws IOException {
        Properties properties = StoreFile.load(storePath);
        String encodedPath = encodePath(record.path());
        String baseKey = keyPrefix(encodedPath);
        String existingVersion = properties.getProperty(baseKey + "version");
        int nextVersion = existingVersion == null ? record.version() : Integer.parseInt(existingVersion) + 1;
        String createdAt = properties.getProperty(baseKey + "createdAt");
        String now = Instant.now().toString();
        properties.setProperty(baseKey + "encryptedDek", encode(record.encryptedDek()));
        properties.setProperty(baseKey + "dekIv", encode(record.dekIv()));
        properties.setProperty(baseKey + "secretIv", encode(record.secretIv()));
        properties.setProperty(baseKey + "ciphertext", encode(record.ciphertext()));
        properties.setProperty(baseKey + "version", Integer.toString(nextVersion));
        properties.setProperty(baseKey + "createdAt", createdAt == null ? now : createdAt);
        properties.setProperty(baseKey + "updatedAt", now);
        properties.setProperty(baseKey + "path", record.path());
        StoreFile.save(storePath, properties);
    }

    public void delete(String path) throws IOException {
        Properties properties = StoreFile.load(storePath);
        String encodedPath = encodePath(path);
        String baseKey = keyPrefix(encodedPath);
        boolean removed = false;
        for (String suffix : List.of("encryptedDek", "dekIv", "secretIv", "ciphertext", "version", "createdAt",
                "updatedAt", "path")) {
            removed |= properties.remove(baseKey + suffix) != null;
        }
        if (removed) {
            StoreFile.save(storePath, properties);
        }
    }

    public List<String> list(String prefix) throws IOException {
        Properties properties = StoreFile.load(storePath);
        List<String> results = new ArrayList<>();
        String encodedPrefix = SECRET_PREFIX;
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(encodedPrefix) || !key.endsWith(".path")) {
                continue;
            }
            String value = properties.getProperty(key);
            if (value != null && value.startsWith(prefix)) {
                results.add(value);
            }
        }
        results.sort(Comparator.naturalOrder());
        return results;
    }

    public Properties loadProperties() throws IOException {
        return StoreFile.load(storePath);
    }

    public void saveProperties(Properties properties) throws IOException {
        StoreFile.save(storePath, properties);
    }

    public Path storePath() {
        return storePath;
    }

    private String keyPrefix(String encodedPath) {
        return SECRET_PREFIX + encodedPath + ".";
    }

    private String encodePath(String path) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(path.getBytes(StandardCharsets.UTF_8));
    }

    private String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private byte[] decode(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            return new byte[0];
        }
        return Base64.getDecoder().decode(value);
    }
}
