# Usage Guide

This guide explains how to build and use the Vault-lite Java secret manager locally.

## Build

```bash
mvn -q -DskipTests package
```

If Maven cannot download plugins due to network restrictions, use an offline cache or run in an environment with access to Maven Central.

## Run with Docker Compose

Build the image:

```bash
docker compose build
```

Run CLI commands from anywhere with Docker Compose (the repository is mounted into `/work` in the container):

```bash
docker compose run --rm vault-cli \
  put ./secrets.properties db/prod/password "supersecret" \
  "startup-passphrase" ./certs/client-cert.pem ./config/policies.json
```

```bash
docker compose run --rm vault-cli \
  get ./secrets.properties db/prod/password \
  "startup-passphrase" ./certs/client-cert.pem ./config/policies.json
```

You can pass secrets over stdin too:

```bash
printf '%s' "supersecret" | docker compose run --rm -T vault-cli \
  put ./secrets.properties db/prod/password - \
  "startup-passphrase" ./certs/client-cert.pem ./config/policies.json
```

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

### List keys in a path

```bash
java -cp target/classes com.example.vault.cli.Main \
  keys ./secrets.properties db/prod \
  "startup-passphrase" ./certs/client-cert.pem ./config/policies.json
```

This prints immediate key names under `db/prod`.

### Delete a secret

```bash
java -cp target/classes com.example.vault.cli.Main \
  delete ./secrets.properties db/prod/password \
  "startup-passphrase" ./certs/client-cert.pem ./config/policies.json
```


## OpenSSL format error troubleshooting

If OpenSSL prints:

```text
error:1608010C:STORE routines:ossl_store_handle_load_result:unsupported
```

it usually means the input passed to `openssl x509` is not a PEM certificate.
Use the client certificate PEM (`client-cert.pem`), not the private key and not a PKCS#12 bundle.

```bash
# Confirm the certificate file exists first
ls -l ./certs/client-cert.pem

openssl x509 -in ./certs/client-cert.pem -noout -text
head -n 2 ./certs/client-cert.pem
# Expect: -----BEGIN CERTIFICATE-----
```

If you get `No such file or directory`, generate the cert first:

```bash
mkdir -p ./certs
openssl req -x509 -newkey rsa:4096 -sha256 -nodes -days 365 \
  -keyout ./certs/client-key.pem \
  -out ./certs/client-cert.pem \
  -subj "/CN=vault-cli-client"
```

If your cert is inside a `.p12/.pfx`, extract it first:

```bash
openssl pkcs12 -in ./certs/client.p12 -clcerts -nokeys -out ./certs/client-cert.pem
```

## Notes

* Secrets are encrypted with AES-256-GCM using a per-secret DEK.
* DEKs are wrapped by the master key derived from the startup passphrase.
* Authorization is enforced before any decryption occurs.
