# OrbitFS Core — Low-Level Design (LLD) & Protocol Specification

## 📐 Protocol Message Framing

OrbitFS communicates over raw TCP sockets using a 4-byte length-prefixed binary header followed by a minified JSON command payload.

![RPC Framing](./diagrams/rpc_framing.svg)

---

## 🔄 Read/Write Sequence Flow

![Sequence Read/Write](./diagrams/sequence_read_write.svg)

---

## 🛡️ Sandbox Path Jail Security (`SandboxGuard`)

`SandboxGuard` enforces strict path resolution within `root` to prevent directory traversal attacks:

```java
public Path resolve(String path) {
    if (path == null || path.isEmpty()) return root;
    String normalized = path.replace('\\', '/').trim();
    if (normalized.equals("/") || normalized.isEmpty()) return root;

    if (normalized.startsWith("/")) {
        Path resolved = Path.of(normalized).normalize();
        if (resolved.startsWith(root)) return resolved;
        throw new SecurityException("Path escape detected: " + path);
    }

    var components = new java.util.ArrayDeque<String>();
    for (String part : normalized.split("/")) {
        if (part.isEmpty() || part.equals(".")) continue;
        if (part.equals("..")) {
            if (!components.isEmpty()) components.removeLast();
            continue;
        }
        components.add(part);
    }

    Path result = root;
    for (String component : components) {
        result = result.resolve(component);
    }
    return result;
}
```

---

## 📌 Known Issues & GitHub Issue Tracker

- **Issue #63**: `Concurrency: PathLockRegistry unlock safety and instance retention` — Always unlock via `LockResult.lock().unlock()` rather than re-resolving by path string.
- **Issue #64**: `Thread Safety: OrbitSerializer lazy ObjectMapper initialization synchronization` — Tighten lazy `ObjectMapper` thread safety under concurrent requests.
