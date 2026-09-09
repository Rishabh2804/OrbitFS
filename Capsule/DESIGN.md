# Capsule — Security Architecture

## Vision

Capsule is the security boundary for OrbitFS. It enforces **where** clients can go (path confinement), **who** can connect (authentication), and **what** they can do when there (authorization policies).

## Threat Model

| Threat | Mitigation |
|--------|-----------|
| Path traversal (`../../etc/passwd`) | Canonical path resolution + root prefix check |
| Symlink escape | Symlink resolution within root only |
| Hidden file access (`.env`, `.git/`) | Component-level name filtering |
| Frame amplification / OOM | Payload size limit at decode layer |
| Unauthorized access | Token-based authentication (JWT/API key) |
| Excessive reads | Rate limiting per client |

## Architecture

### Filter Chain

```
Client Request
  ↓
[AuthFilter]      ← Validates identity (optional, fail-open if disabled)
  ↓
[PathFilter]      ← Validates path is within root (ALWAYS ACTIVE)
  ↓
[PolicyFilter]    ← Evaluates read/write permissions (optional)
  ↓
OrbitServerImpl
```

### PathFilter Design

The PathFilter is the core security enforcement point. It implements `SandboxGuard`:

1. **Resolve**: `root.resolve(clientPath).normalize()`
2. **Verify**: `resolved.startsWith(root)`
3. **Filter**: Reject components starting with `.`

This runs on every `OPEN`, `READ`, `WRITE`, `LIST`, `STAT`, `CLOSE` operation.

### AuthFilter Design

Supported auth methods:
- **JWT**: RS256-signed tokens, configurable public key
- **API Key**: Pre-shared keys from a file/headers
- **mTLS**: Client certificate verification

Auth is optional — when disabled, all paths are treated as authenticated.

### PolicyFilter Design

Policies are defined as:
```json
{
  "policies": [
    {
      "path": "/public/*",
      "read": true,
      "write": false
    },
    {
      "path": "/private/user_*/",
      "read": true,
      "write": "user",  // only the matching user segment
      "owner": "user"
    }
  ]
}
```

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

## Integration with OrbitFS

OrbitFS core defines the `SandboxGuard` interface. Capsule provides the full implementation:

```java
// OrbitFS core — minimal sandbox (current)
class SandboxGuard {
    Path resolve(String path);        // path confinement
    boolean isAllowed(String path);   // hidden file check
}

// Capsule — full implementation
class CapsuleSecurityManager implements SandboxGuard {
    // From SandboxGuard
    Path resolve(String path);
    boolean isAllowed(String path);

    // Additional Capsule methods
    Identity authenticate(Token token);
    AuthzResult authorize(Identity identity, Path path, Action action);
    void audit(Identity identity, Path path, Action action, boolean allowed);
}
```

The server constructor accepts either:
```java
// Minimal (current OrbitFS):
new OrbitServerImpl(port, root);

// With Capsule:
new OrbitServerImpl(port, new CapsuleSecurityManager(config));
```

## Error Responses

| Condition | OrbitFS Error Code | Human Response |
|-----------|-------------------|----------------|
| Path escape | 2 | `Access denied: path outside sandbox` |
| Hidden file | 2 | `Access denied: hidden files are blocked` |
| Auth failure | 3 | `Authentication required` |
| Policy deny | 4 | `Permission denied` |
| Frame too large | 5 | `Request too large` |
