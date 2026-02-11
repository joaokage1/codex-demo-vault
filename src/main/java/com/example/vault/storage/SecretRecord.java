package com.example.vault.storage;

public record SecretRecord(
        String path,
        byte[] encryptedDek,
        byte[] dekIv,
        byte[] secretIv,
        byte[] ciphertext,
        int version,
        String createdAt,
        String updatedAt) {
}
