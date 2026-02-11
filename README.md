# Java Secret Manager (Vault-lite)

A minimal, secure, certificate-based secret manager written in **Java**, inspired by HashiCorp Vault.

This project is designed for **single-VM deployments**, accessed via a **CLI**, and secured exclusively using **mutual TLS (mTLS)** and strong cryptography.

---

## ✨ Features

* 🔐 Mutual TLS (mTLS) authentication
* 🧾 Certificate fingerprint–based identity
* 📜 Path-based authorization policies
* 🧠 Envelope encryption (Master Key + DEKs)
* 🗝️ Secrets encrypted at rest (AES-256-GCM)
* 🖥️ Java CLI client
* 📦 Simple, auditable storage
* 🚫 No passwords, no tokens, no UI

---

## 🧭 Architecture Overview

```
CLI (Java)
  │
  │ mTLS
  ▼
Secret Server (Java API)
  │  ├─ AuthN (Certificates)
  │  ├─ AuthZ (Policies)
  │  ├─ Crypto Engine
  │
  ▼
Encrypted Storage
```

---

## 🔐 Security Model

### Core Principles

* Secrets are **never stored in plaintext**
* Secrets are **never transmitted without TLS**
* Authentication is **certificate-only**
* Authorization is **explicit and deny-by-default**
* The Master Key is **never stored unencrypted**

---

## 🔑 Authentication (mTLS)

* Mutual TLS is enforced on all API endpoints
* Each client uses an **X.509 certificate** issued by a trusted CA
* The server authenticates clients via **certificate fingerprint (SHA-256)**

### Identity

```
client identity = SHA-256 fingerprint of client certificate
```

---

## 📜 Authorization (Policies)

Access is controlled using **path-based policies**, similar to Vault.

### Example Policy

```json
{
  "fingerprint": "9A31F4...",
  "permissions": {
    "read": ["db/prod/*"],
    "write": ["db/staging/*"]
  }
}
```

* Policies are loaded at startup
* Authorization is checked **before any decryption**
* Default behavior is **deny**

---

## 🔒 Cryptography Design

### Envelope Encryption

```
Master Key (MK)
   ↓ encrypts
Data Encryption Key (DEK)
   ↓ encrypts
Secret Value
```

### Algorithms

| Purpose           | Algorithm          |
| ----------------- | ------------------ |
| Secret encryption | AES-256-GCM        |
| Hashing           | SHA-256            |
| Key derivation    | PBKDF2 (or Argon2) |
| Randomness        | SecureRandom       |

---

## 🗝️ Master Key (Unseal Process)

* The server starts in a **sealed** state
* Operator provides a **startup passphrase**
* Passphrase → KDF → Master Key
* Master Key lives **only in memory**
* API remains blocked until unsealed

---

## 💾 Storage

Secrets are stored as encrypted blobs.

### Stored Fields

* Secret path
* Encrypted secret value
* Encrypted DEK
* Version number
* Metadata (timestamps)

### Supported Backends

* SQLite (default)
* PostgreSQL
* File-based storage (early development)

---

## 🌐 API

All endpoints require **mTLS**.

```
PUT    /secrets/{path}
GET    /secrets/{path}
DELETE /secrets/{path}
LIST   /secrets/{path}
```

No sessions, no tokens, no passwords.

---

## 🖥️ CLI

The CLI is a thin Java client responsible for:

* Loading the client certificate (PKCS12)
* Establishing an mTLS connection
* Sending commands to the server

### Example Commands

```bash
java -cp target/classes com.example.vault.cli.Main \
  put ./secrets.properties db/prod/password "supersecret" \
  "startup-passphrase" ./certs/client-cert.pem ./config/policies.json

java -cp target/classes com.example.vault.cli.Main \
  get ./secrets.properties db/prod/password \
  "startup-passphrase" ./certs/client-cert.pem ./config/policies.json

java -cp target/classes com.example.vault.cli.Main \
  list ./secrets.properties db/prod \
  "startup-passphrase" ./certs/client-cert.pem ./config/policies.json
```

---

## 📂 Project Structure

```
secret-manager/
├── cli/
│   ├── Main.java
│   └── Commands.java
├── server/
│   ├── ApiController.java
│   ├── AuthService.java
│   └── PolicyService.java
├── crypto/
│   ├── MasterKeyService.java
│   ├── DekService.java
│   └── CryptoEngine.java
├── storage/
│   ├── SecretRepository.java
│   ├── SecretRecord.java
│   └── StoreFile.java
└── config/
    └── policies.json
```

---

## 🧪 Testing & Validation

### Functional

* Valid cert → access allowed
* Invalid or revoked cert → denied
* Unauthorized path → denied

### Security

* Database leak simulation
* MITM attempt test
* Replay attack test
* Rate-limit enforcement

---

## 🛡️ VM Hardening Checklist

* Firewall restricts exposed ports
* SSH key-based login only
* Root login disabled
* TLS 1.2+ enforced
* Strong cipher suites
* Regular OS patching

---

## 🔄 Rotation & Revocation

* Client certificates are rotatable
* Certificate fingerprints can be revoked
* Master Key rotation supported via re-encryption
* DEKs can be re-wrapped without exposing secrets

---

## 🚀 Implementation Order (Recommended)

1. PKI & certificates
2. mTLS server/client
3. Identity extraction
4. Policy enforcement
5. Envelope encryption
6. Storage
7. CLI UX
8. Logging & auditing
9. VM hardening

---

## ⚠️ Non-Goals

* No HA or clustering
* No browser UI
* No dynamic secrets
* No cloud dependency

---

## 📄 License

MIT (or your preferred license)

---

## 🧠 Final Notes

If an attacker:

* Steals the database → secrets remain encrypted
* Sniffs traffic → TLS protects data
* Steals the CLI → useless without cert
* Steals a cert → revoke fingerprint

This provides a **strong, auditable, and understandable security posture** for VM-scoped secret management.
