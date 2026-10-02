# OrbitFS Core — Engine Features & Capabilities Specification

> *"High-Performance, Local-First Peer-to-Peer Storage Engine."*

OrbitFS Core is the foundational pure-Java 21 storage and RPC server engine powering the OrbitFS ecosystem.

---

## 📋 Engine Capabilities

### 1. Custom Binary-Framed RPC Protocol
- **4-Byte Length Framing**: Raw TCP socket frames are length-prefixed with a 4-byte big-endian integer header followed by a minified JSON command string (`READ`, `WRITE`, `STAT`, `LIST`, `OPEN`, `CLOSE`).
- **Fast-Fail Header Verification**: `FrameCodec` rejects invalid magic bytes (`ORBT`), corrupted streams, and unexpected payload truncations immediately.
- **Base64 Payload Streaming**: Transmits binary chunk payloads in Base64 strings with zero allocation waste.

### 2. Java 21 Virtual Thread Concurrency Model
- **Unbounded Lightweight Threading**: Uses `Thread.ofVirtual()` for per-connection handling, avoiding OS thread pool sizing constraints.
- **Cheap Socket Parking**: The JVM parks carrier threads automatically during socket wait states, handling thousands of concurrent client socket streams smoothly.

### 3. Path-Level Read/Write Locking (`PathLockRegistry`)
- **Fine-Grained Path Locking**: Maps canonical paths (`Path.toAbsolutePath().normalize()`) to `ReentrantReadWriteLock` instances.
- **Concurrent Readers**: Multiple clients can execute concurrent `READ` operations on the same file without blocking.
- **Exclusive Writers**: `WRITE` operations acquire exclusive write locks, preventing data corruption during concurrent file modifications.

### 4. Sandbox Jail Confinement (`SandboxGuard`)
- **Directory Traversal Defense**: All path parameters are resolved within `sandbox.root`.
- **Automatic Traversal Clamping**: Silently clamps relative path traversal (`../`) to `sandbox.root`.
- **Security Exception Abort**: Rejects absolute path escapes outside `sandbox.root` by throwing `SecurityException`.

### 5. Client-Side LRU Write-Back Cache (`LRChunkCache`)
- **64KB Chunk Buffering**: Caches file data in 64KB chunks using `LinkedHashMap` with access-order eviction.
- **Write-Back Dirty Tracking**: Buffers write operations in memory and flushes dirty chunks in batch to minimize round-trip RPCs.
