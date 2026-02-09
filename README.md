# Codex Demo Vault

A minimal, file-backed secret manager in Java. Secrets are encrypted with a per-entry AES key, and that key is wrapped using an X.509 certificate. Retrieving the secret requires the matching private key in a PKCS#12 keystore.

## CLI Usage

Build:

```bash
mvn -q -e -DskipTests package
```

Add a secret (encrypt with certificate public key):

```bash
java -cp target/classes com.example.vault.cli.Main \
  add ./secrets.properties db.password "super-secret" ./certs/public-cert.pem
```

Retrieve a secret (requires private key in PKCS#12 keystore):

```bash
java -cp target/classes com.example.vault.cli.Main \
  get ./secrets.properties db.password ./certs/private-keystore.p12 "changeit" my-key-alias
```

## Notes

- The secrets file is a Java `Properties` file containing base64-encoded wrapped keys, IVs, and ciphertext.
- Use a strong PKCS#12 keystore password and keep the private key secure.
