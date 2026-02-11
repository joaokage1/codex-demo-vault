# Usage Guide

This guide explains how to build and use the Vault-lite Java secret manager locally.

## Build

```bash
mvn -q -DskipTests package
```

If Maven cannot download plugins due to network restrictions, use an offline cache or run in an environment with access to Maven Central.

## Prerequisites

* Java 17+
* A client X.509 certificate (PEM) for identity
* A policies file containing fingerprint-based permissions

## Configure Policies

Create a `policies.json` file that maps certificate fingerprints to path permissions:

```json
[
  {
    "fingerprint": "9A31F4...",
    "permissions": {
      "read": ["db/prod/*"],
      "write": ["db/staging/*"]
    }
  }
]
```

## Commands

All commands require:

* `store` → path to the encrypted properties file
* `passphrase` → startup passphrase used to unseal the master key
* `cert` → client certificate used for identity
* `policies` → JSON policy file

### Put a secret

```bash
java -cp target/classes com.example.vault.cli.Main \
  put ./secrets.properties db/prod/password "supersecret" \
  "startup-passphrase" ./certs/client-cert.pem ./config/policies.json
```

To read the secret from stdin, pass `-` as the value:

```bash
printf '%s' "supersecret" | java -cp target/classes com.example.vault.cli.Main \
  put ./secrets.properties db/prod/password - \
  "startup-passphrase" ./certs/client-cert.pem ./config/policies.json
```

### Get a secret

```bash
java -cp target/classes com.example.vault.cli.Main \
  get ./secrets.properties db/prod/password \
  "startup-passphrase" ./certs/client-cert.pem ./config/policies.json
```

### List secrets

```bash
java -cp target/classes com.example.vault.cli.Main \
  list ./secrets.properties db/prod \
  "startup-passphrase" ./certs/client-cert.pem ./config/policies.json
```

### Delete a secret

```bash
java -cp target/classes com.example.vault.cli.Main \
  delete ./secrets.properties db/prod/password \
  "startup-passphrase" ./certs/client-cert.pem ./config/policies.json
```

## Notes

* Secrets are encrypted with AES-256-GCM using a per-secret DEK.
* DEKs are wrapped by the master key derived from the startup passphrase.
* Authorization is enforced before any decryption occurs.
