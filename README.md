# OrbitFS

A distributed file system on a custom RPC protocol over TCP, in Java 21. Clients open/read/write/seek/stat files on a remote server as if local.

**Highlights:** hand-rolled binary framing, virtual-thread-per-connection server, multiplexed persistent client connection, per-path read/write locking, client-side LRU write-back cache.

Built ticket-by-ticket from a written spec — one GitHub issue → one branch → one PR per unit of work, protected `main`, tests green before merge.

## Architecture

```
Client App → OrbitFSClient → NetworkTransportClient → FrameCodec → TCP → Server Dispatch
                                    ↕                                      ↓
                              LRUChunkCache                        PathLockRegistry → StorageEngine → FileDescriptorTable → Disk
                              (built, not wired in)
```

- **Wire protocol:** `MAGIC(4B) + LENGTH(4B) + JSON` frames. Magic mismatch / truncation fails fast.
- **Server:** one virtual thread per connection — cheap concurrency at scale, no thread-pool sizing.
- **Client:** one TCP connection, many in-flight requests, matched by request ID to a `Future`.
- **Storage:** per-path read/write locks so concurrent readers don't block each other, writers get exclusion.

## Status

| 1 | Wire + transport + listener | Built — concurrency proof pending |
| 2 | Storage + handles + locking | Built — corruption proof pending |
| 3 | Client proxy + LRU cache | Both built standalone — not wired together |
| 4 | Security sandbox | Not started |

**No blocking issue — server dispatch speaks real frames.** The client and server can now communicate end-to-end. Next: wire the LRU cache into the client (Phase 3), then the security sandbox (Phase 4).

## Stack

Java 21 · Gradle 9.3.1 · Jackson 2.18.2 · JUnit 5.11.4 · SLF4J 2.0.17 · GitHub Actions (macOS, Temurin 21)

## Installation

### Homebrew (macOS)

```bash
brew tap Rishabh2804/orbitfs
brew install orbitfs
```

### Install script (macOS)

```bash
curl -fsSL https://raw.githubusercontent.com/Rishabh2804/OrbitFS/main/bin/install.sh | bash
```

### Build from source

```bash
git clone https://github.com/Rishabh2804/OrbitFS.git
cd OrbitFS && ./gradlew jar
java -jar build/libs/orbitfs-*.jar --help
```

## Run it

```bash
git clone https://github.com/Rishabh2804/OrbitFS.git
cd OrbitFS && ./gradlew test
```

40 tests pass. Full design spec: `DESIGN.md`, original spec: `OrbitFS_Technical_Specification.pdf` (repo root).

## Layout

```
org.orbitfs.common   — wire protocol, shared models
org.orbitfs.server   — listener, dispatch, storage engine, locking
org.orbitfs.client   — client interface, transport, cache
```

See [`DESIGN.md`](./DESIGN.md) for protocol details, concurrency model, and known issues.