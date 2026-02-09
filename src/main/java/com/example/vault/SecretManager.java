package com.example.vault;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Base64;
import java.util.Objects;
import java.util.Properties;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class SecretManager {
    private static final String STORE_ENTRY_DELIMITER = ":";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    public void addSecret(Path storePath, String alias, String secret, Path certificatePath)
            throws IOException, GeneralSecurityException {
        Objects.requireNonNull(storePath, "storePath");
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(secret, "secret");
        Objects.requireNonNull(certificatePath, "certificatePath");

        Certificate certificate = loadCertificate(certificatePath);
        SecretKey dataKey = generateDataKey();
        byte[] iv = generateIv();
        byte[] ciphertext = encryptSecret(secret, dataKey, iv);
        byte[] wrappedKey = wrapKey(dataKey, certificate.getPublicKey());

        Properties store = loadStore(storePath);
        store.setProperty(alias, encodeStoreValue(wrappedKey, iv, ciphertext));
        saveStore(storePath, store);
    }

    public String getSecret(Path storePath, String alias, Path keyStorePath, char[] keyStorePassword,
            String keyAlias) throws IOException, GeneralSecurityException {
        Objects.requireNonNull(storePath, "storePath");
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(keyStorePath, "keyStorePath");
        Objects.requireNonNull(keyStorePassword, "keyStorePassword");
        Objects.requireNonNull(keyAlias, "keyAlias");

        Properties store = loadStore(storePath);
        String storedValue = store.getProperty(alias);
        if (storedValue == null) {
            throw new IllegalArgumentException("No secret found for alias: " + alias);
        }

        StoreEntry entry = decodeStoreValue(storedValue);
        PrivateKey privateKey = loadPrivateKey(keyStorePath, keyStorePassword, keyAlias);
        SecretKey dataKey = unwrapKey(entry.wrappedKey(), privateKey);
        return decryptSecret(entry.ciphertext(), dataKey, entry.iv());
    }

    private Certificate loadCertificate(Path certificatePath) throws IOException, GeneralSecurityException {
        try (InputStream input = Files.newInputStream(certificatePath)) {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return factory.generateCertificate(input);
        }
    }

    private PrivateKey loadPrivateKey(Path keyStorePath, char[] keyStorePassword, String keyAlias)
            throws IOException, GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(keyStorePath)) {
            keyStore.load(input, keyStorePassword);
        }
        Key key = keyStore.getKey(keyAlias, keyStorePassword);
        if (!(key instanceof PrivateKey)) {
            throw new GeneralSecurityException("Key alias does not contain a private key: " + keyAlias);
        }
        return (PrivateKey) key;
    }

    private SecretKey generateDataKey() throws GeneralSecurityException {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        return generator.generateKey();
    }

    private byte[] generateIv() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    private byte[] encryptSecret(String secret, SecretKey key, byte[] iv) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        return cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
    }

    private String decryptSecret(byte[] ciphertext, SecretKey key, byte[] iv) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        byte[] plaintext = cipher.doFinal(ciphertext);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    private byte[] wrapKey(SecretKey key, java.security.PublicKey publicKey) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.WRAP_MODE, publicKey);
        return cipher.wrap(key);
    }

    private SecretKey unwrapKey(byte[] wrappedKey, PrivateKey privateKey) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.UNWRAP_MODE, privateKey);
        Key key = cipher.unwrap(wrappedKey, "AES", Cipher.SECRET_KEY);
        if (!(key instanceof SecretKey)) {
            throw new GeneralSecurityException("Unwrapped key is not a SecretKey");
        }
        return (SecretKey) key;
    }

    private Properties loadStore(Path storePath) throws IOException {
        Properties properties = new Properties();
        if (Files.exists(storePath)) {
            try (InputStream input = Files.newInputStream(storePath)) {
                properties.load(input);
            }
        }
        return properties;
    }

    private void saveStore(Path storePath, Properties properties) throws IOException {
        try (OutputStream output = Files.newOutputStream(storePath)) {
            properties.store(output, "Secret Store");
        }
    }

    private String encodeStoreValue(byte[] wrappedKey, byte[] iv, byte[] ciphertext) {
        Base64.Encoder encoder = Base64.getEncoder();
        return encoder.encodeToString(wrappedKey)
                + STORE_ENTRY_DELIMITER
                + encoder.encodeToString(iv)
                + STORE_ENTRY_DELIMITER
                + encoder.encodeToString(ciphertext);
    }

    private StoreEntry decodeStoreValue(String storedValue) {
        String[] parts = storedValue.split(STORE_ENTRY_DELIMITER);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid secret store entry format");
        }
        Base64.Decoder decoder = Base64.getDecoder();
        return new StoreEntry(decoder.decode(parts[0]), decoder.decode(parts[1]), decoder.decode(parts[2]));
    }

    private record StoreEntry(byte[] wrappedKey, byte[] iv, byte[] ciphertext) {}
}
