package com.example.vault.server;

import com.example.vault.crypto.CryptoEngine;
import com.example.vault.crypto.DekService;
import com.example.vault.crypto.MasterKeyService;
import com.example.vault.storage.SecretRecord;
import com.example.vault.storage.SecretRepository;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

public class ApiController {
    private final SecretRepository repository;
    private final MasterKeyService masterKeyService;
    private final DekService dekService;
    private final CryptoEngine cryptoEngine;
    private final PolicyService policyService;

    public ApiController(SecretRepository repository,
            MasterKeyService masterKeyService,
            DekService dekService,
            CryptoEngine cryptoEngine,
            PolicyService policyService) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.masterKeyService = Objects.requireNonNull(masterKeyService, "masterKeyService");
        this.dekService = Objects.requireNonNull(dekService, "dekService");
        this.cryptoEngine = Objects.requireNonNull(cryptoEngine, "cryptoEngine");
        this.policyService = Objects.requireNonNull(policyService, "policyService");
    }

    public void putSecret(String path, String secret, RequestContext context)
            throws IOException, GeneralSecurityException {
        requireWrite(path, context);
        SecretKey masterKey = masterKeyService.requireMasterKey();
        SecretKey dek = dekService.generateDek();
        CryptoEngine.EncryptedSecret encryptedSecret = cryptoEngine.encryptSecret(secret, dek);
        DekService.WrappedDek wrappedDek = dekService.wrapDek(dek, masterKey);
        String now = Instant.now().toString();
        SecretRecord record = new SecretRecord(
                path,
                wrappedDek.encryptedDek(),
                wrappedDek.iv(),
                encryptedSecret.iv(),
                encryptedSecret.ciphertext(),
                1,
                now,
                now);
        repository.save(record);
    }

    public String getSecret(String path, RequestContext context)
            throws IOException, GeneralSecurityException {
        requireRead(path, context);
        SecretRecord record = repository.get(path)
                .orElseThrow(() -> new IllegalArgumentException("No secret found for path: " + path));
        SecretKey masterKey = masterKeyService.requireMasterKey();
        SecretKey dek = dekService.unwrapDek(new DekService.WrappedDek(record.encryptedDek(), record.dekIv()),
                masterKey);
        return cryptoEngine.decryptSecret(new CryptoEngine.EncryptedSecret(record.ciphertext(), record.secretIv()), dek);
    }

    public void deleteSecret(String path, RequestContext context) throws IOException {
        requireWrite(path, context);
        repository.delete(path);
    }

    public List<String> listSecrets(String prefix, RequestContext context) throws IOException {
        List<String> paths = repository.list(prefix);
        return paths.stream()
                .filter(path -> policyService.canRead(context.fingerprint(), path))
                .collect(Collectors.toList());
    }

    public List<String> listKeys(String path, RequestContext context) throws IOException {
        List<String> keys = repository.listKeys(path);
        return keys.stream()
                .filter(key -> policyService.canRead(context.fingerprint(), pathWithKey(path, key)))
                .collect(Collectors.toList());
    }

    private String pathWithKey(String path, String key) {
        if (path.endsWith("/")) {
            return path + key;
        }
        return path + "/" + key;
    }

    private void requireRead(String path, RequestContext context) {
        if (!policyService.canRead(context.fingerprint(), path)) {
            throw new SecurityException("Read access denied for path: " + path);
        }
    }

    private void requireWrite(String path, RequestContext context) {
        if (!policyService.canWrite(context.fingerprint(), path)) {
            throw new SecurityException("Write access denied for path: " + path);
        }
    }

    public record RequestContext(String fingerprint) {
    }
}
