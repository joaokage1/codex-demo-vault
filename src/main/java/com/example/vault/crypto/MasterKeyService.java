package com.example.vault.crypto;

import com.example.vault.storage.SecretRepository;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.Properties;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class MasterKeyService {
    private static final int KEY_LENGTH = 256;
    private static final int DEFAULT_ITERATIONS = 120_000;
    private static final int SALT_LENGTH = 16;

    private final SecretRepository repository;
    private SecretKey masterKey;

    public MasterKeyService(SecretRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public synchronized void unseal(char[] passphrase) throws IOException, GeneralSecurityException {
        Objects.requireNonNull(passphrase, "passphrase");
        Properties properties = repository.loadProperties();
        String saltValue = properties.getProperty("master.salt");
        String iterationsValue = properties.getProperty("master.iterations");
        int iterations = iterationsValue == null ? DEFAULT_ITERATIONS : Integer.parseInt(iterationsValue);
        byte[] salt = saltValue == null ? generateSalt() : Base64.getDecoder().decode(saltValue);
        if (saltValue == null) {
            properties.setProperty("master.salt", Base64.getEncoder().encodeToString(salt));
            properties.setProperty("master.iterations", Integer.toString(iterations));
            repository.saveProperties(properties);
        }
        PBEKeySpec spec = new PBEKeySpec(passphrase, salt, iterations, KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        this.masterKey = new SecretKeySpec(keyBytes, "AES");
    }

    public synchronized boolean isSealed() {
        return masterKey == null;
    }

    public synchronized SecretKey requireMasterKey() {
        if (masterKey == null) {
            throw new IllegalStateException("Master key is sealed");
        }
        return masterKey;
    }

    private byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        return salt;
    }
}
