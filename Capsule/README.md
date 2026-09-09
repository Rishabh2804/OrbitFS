# Capsule

**Security gatekeeper for OrbitFS.**

Capsule is a lightweight, pluggable security layer that sits in front of the OrbitFS server. It enforces path confinement, authentication, and access control policies.

> **Note:** OrbitFS currently ships with a minimal `SandboxGuard` interface. Capsule replaces this with a complete security implementation.

---

## Overview

| Property | Value |
|----------|-------|
| **Purpose** | Prevent path traversal, enforce authentication, and confine file access |
| **Integration** | Plugs into `OrbitServerImpl` as the `SandboxGuard` implementation |
| **Architecture** | Filter chain: AuthFilter → PathFilter → PolicyFilter |
| **Language** | Java 21 |

---

## Design

```
┌─────────────────────────────────────┐
│          Client Request             │
└──────────┬──────────────────────────┘
           │
┌──────────▼──────────────────────────┐
│      Capsule Security Pipeline      │
│  ┌────────────────────────────────┐ │
│  │  AuthFilter                    │ │
│  │  - Token validation            │ │
│  │  - JWT / API key / mTLS       │ │
│  └────────────────────────────────┘ │
│  ┌────────────────────────────────┐ │
│  │  PathFilter                    │ │
│  │  - Canonicalization            │ │
│  │  - Root confinement            │ │
│  │  - Symlink resolution          │ │
│  └────────────────────────────────┘ │
│  ┌────────────────────────────────┐ │
│  │  PolicyFilter                  │ │
│  │  - Read/write permissions      │ │
│  │  - Hidden file blocking        │ │
│  │  - Rate limiting               │ │
│  └────────────────────────────────┘ │
└──────────┬──────────────────────────┘
           │  ALLOW / DENY
┌──────────▼──────────────────────────┐
│        OrbitServerImpl              │
└─────────────────────────────────────┘
```

### Components

#### 1. PathFilter (always active)
- **Canonical path resolution**: Resolves `..`, symlinks, and relative paths
- **Root confinement**: Rejects any path that escapes the virtual root
- **Hidden file blocking**: Rejects paths with components starting with `.`
- **Frame size cap**: Enforces maximum RPC payload size (prevents OOM)

#### 2. AuthFilter (optional)
- Token-based authentication (JWT recommended)
- API key support for simpler setups
- Per-client access policies

#### 3. PolicyFilter (optional)
- Fine-grained read/write permissions per path pattern
- Rate limiting per client
- Audit logging

---

## Integration

Capsule plugs into OrbitFS via the `SandboxGuard` interface:

```java
// Current OrbitFS (minimal):
OrbitServerImpl server = new OrbitServerImpl(port, root);

// With Capsule (full security):
OrbitServerImpl server = new OrbitServerImpl(
    port,
    new CapsuleSecurityManager(root, authProvider, policyStore)
);
```

The `SandboxGuard` interface in OrbitFS core:
```java
public interface SandboxGuard {
    Path resolve(String path);  // validate + canonicalize
    boolean canWrite(Path path);
    boolean canRead(Path path);
}
```

Capsule implements this interface with additional `authenticate()` and `authorize()` methods.

---

## Configuration

```properties
# capsule.properties
root=/srv/orbitfs
auth.method=none|jwt|apikey|mtls
auth.jwt.public_key=/etc/capsule/jwt-public.pem
auth.apikey.file=/etc/capsule/apikeys.txt
frame.max_size=10485760  # 10MB
policy.file=/etc/capsule/policies.json
audit.enabled=true
```

## Usage

```bash
# Server with Capsule
java -jar capsule-all.jar server \
  --port 9090 \
  --root /srv/orbitfs \
  --auth jwt \
  --jwt-public-key /etc/capsule/public.pem
```
