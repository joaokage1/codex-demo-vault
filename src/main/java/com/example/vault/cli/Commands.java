package com.example.vault.cli;

import com.example.vault.crypto.CryptoEngine;
import com.example.vault.crypto.DekService;
import com.example.vault.crypto.MasterKeyService;
import com.example.vault.server.ApiController;
import com.example.vault.server.AuthService;
import com.example.vault.server.PolicyService;
import com.example.vault.storage.SecretRepository;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;

public class Commands {
    private final ApiController apiController;
    private final AuthService authService;

    public Commands(ApiController apiController, AuthService authService) {
        this.apiController = apiController;
        this.authService = authService;
    }

    public static Commands create(Path storePath, Path policiesPath, char[] passphrase)
            throws IOException, GeneralSecurityException {
        SecretRepository repository = new SecretRepository(storePath);
        MasterKeyService masterKeyService = new MasterKeyService(repository);
        masterKeyService.unseal(passphrase);
        PolicyService policyService = new PolicyService(policiesPath);
        ApiController apiController = new ApiController(
                repository,
                masterKeyService,
                new DekService(),
                new CryptoEngine(),
                policyService);
        return new Commands(apiController, new AuthService());
    }

    public void put(Path certificatePath, String path, String secret)
            throws IOException, GeneralSecurityException {
        String fingerprint = authService.fingerprintForCertificate(certificatePath);
        apiController.putSecret(path, secret, new ApiController.RequestContext(fingerprint));
    }

    public String get(Path certificatePath, String path)
            throws IOException, GeneralSecurityException {
        String fingerprint = authService.fingerprintForCertificate(certificatePath);
        return apiController.getSecret(path, new ApiController.RequestContext(fingerprint));
    }

    public void delete(Path certificatePath, String path) throws IOException {
        String fingerprint;
        try {
            fingerprint = authService.fingerprintForCertificate(certificatePath);
        } catch (GeneralSecurityException exception) {
            throw new IOException("Unable to read certificate fingerprint", exception);
        }
        apiController.deleteSecret(path, new ApiController.RequestContext(fingerprint));
    }

    public List<String> list(Path certificatePath, String prefix) throws IOException {
        String fingerprint;
        try {
            fingerprint = authService.fingerprintForCertificate(certificatePath);
        } catch (GeneralSecurityException exception) {
            throw new IOException("Unable to read certificate fingerprint", exception);
        }
        return apiController.listSecrets(prefix, new ApiController.RequestContext(fingerprint));
    }

    public List<String> keys(Path certificatePath, String path) throws IOException {
        String fingerprint;
        try {
            fingerprint = authService.fingerprintForCertificate(certificatePath);
        } catch (GeneralSecurityException exception) {
            throw new IOException("Unable to read certificate fingerprint", exception);
        }
        return apiController.listKeys(path, new ApiController.RequestContext(fingerprint));
    }
}
