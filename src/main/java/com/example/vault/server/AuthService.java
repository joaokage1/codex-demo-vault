package com.example.vault.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class AuthService {
    public String fingerprintForCertificate(Path certificatePath) throws IOException, GeneralSecurityException {
        try (InputStream input = Files.newInputStream(certificatePath)) {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            X509Certificate certificate = (X509Certificate) factory.generateCertificate(input);
            return fingerprint(certificate);
        }
    }

    public String fingerprint(X509Certificate certificate) throws GeneralSecurityException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(certificate.getEncoded());
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte value : hash) {
            builder.append(String.format("%02X", value));
        }
        return builder.toString();
    }
}
