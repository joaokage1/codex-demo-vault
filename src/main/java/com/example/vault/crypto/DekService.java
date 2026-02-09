package com.example.vault.crypto;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class DekService {
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    public SecretKey generateDek() throws GeneralSecurityException {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        return generator.generateKey();
    }

    public WrappedDek wrapDek(SecretKey dek, SecretKey masterKey) throws GeneralSecurityException {
        Objects.requireNonNull(dek, "dek");
        Objects.requireNonNull(masterKey, "masterKey");
        byte[] iv = generateIv();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        byte[] ciphertext = cipher.doFinal(dek.getEncoded());
        return new WrappedDek(ciphertext, iv);
    }

    public SecretKey unwrapDek(WrappedDek wrappedDek, SecretKey masterKey) throws GeneralSecurityException {
        Objects.requireNonNull(wrappedDek, "wrappedDek");
        Objects.requireNonNull(masterKey, "masterKey");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_LENGTH, wrappedDek.iv()));
        byte[] keyBytes = cipher.doFinal(wrappedDek.encryptedDek());
        return new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
    }

    private byte[] generateIv() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    public record WrappedDek(byte[] encryptedDek, byte[] iv) {
    }
}
