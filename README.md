# OrbitFS Core — Java 21 Framed RPC File System Engine

> *"High-Performance, Local-First Peer-to-Peer Storage Engine."*

[![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Gradle](https://img.shields.io/badge/Gradle-9.3.1-02303A?logo=gradle&logoColor=white)](https://gradle.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

OrbitFS Core is a high-performance, lightweight peer-to-peer filesystem and transport engine written in **Java 21**. It allows remote clients to discover, open, read, write, seek, and stat files over TCP sockets with near-local filesystem speed and sub-millisecond latencies.

---

## 📚 Table of Contents

- [Key Technical Highlights](#-key-technical-highlights)
- [Architecture & Flow Summary](#-architecture--flow-summary)
- [CLI & API Quickstart](#-cli--api-quickstart)
- [Repository Structure](#-repository-structure)
- [Documentation Index](#-documentation-index)
- [License](#-license)

---

## 🌟 Key Technical Highlights

Below is a summary of OrbitFS Core capabilities. For detailed specifications, visit [docs/FEATURES.md](docs/FEATURES.md).

- **Custom Binary-Framed RPC Protocol**: `MAGIC(4B) + LENGTH(4B) + JSON` message frames with strict error handling (`READ`, `WRITE`, `STAT`, `LIST`, `OPEN`, `CLOSE`).
- **Java 21 Virtual Thread Concurrency**: `Thread.ofVirtual()` per-connection server model delivering cheap, scalable concurrency without OS thread-pool sizing constraints.
- **Multiplexed Socket Transport**: Single persistent TCP socket connection per client handling multiple in-flight asynchronous requests mapped via atomic `requestId` futures.
- **Path-Level Read/Write Locking**: `PathLockRegistry` enforces concurrent reader access without blocking while guaranteeing exclusive writer access.
- **Client-Side LRU Write-Back Cache**: `LRUChunkCache` buffers 64KB file chunks in memory to minimize round-trip socket RPCs.
- **Sandbox Confinement (`SandboxGuard`)**: Strict canonical path validation prevents directory traversal attacks (`../`) outside designated root shares.

---

## 🏗️ Architecture & Flow Summary

For full architectural breakdown and threading models, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

![Backend Architecture](./docs/diagrams/backend_architecture.svg)

---

## 💻 CLI & API Quickstart

For full code examples and CLI options, see [docs/API_AND_CLI.md](docs/API_AND_CLI.md).

```bash
# Build standalone JAR
./gradlew jar

# Run server on port 9090 sharing /Users/shared
java -jar build/libs/orbitfs-core-0.1.0.jar --port 9090 --root /Users/shared
```

---

## 📁 Repository Structure

```
OrbitFS/
├── src/
│   └── main/java/org/orbitfs/
│       ├── common/          # Wire protocol, FrameCodec, JSON models
│       ├── server/          # OrbitServerImpl, SandboxGuard, PathLockRegistry, StorageEngine
│       └── client/          # CachingOrbitFSClient, NetworkTransportClient, LRUChunkCache
├── docs/                    # Detailed technical sub-documentation & D2 diagrams
│   ├── diagrams/            # D2-generated SVG architecture & sequence diagrams
│   ├── FEATURES.md          # Engine feature specification & capability list
│   ├── ARCHITECTURE.md      # System architecture & threading model
│   ├── DESIGN.md            # Low-Level Design (LLD) & protocol spec
│   └── API_AND_CLI.md       # Java/JVM API & CLI integration guide
└── README.md                # Project README
```

---

## 📑 Documentation Index

- [Engine Feature Specification (docs/FEATURES.md)](docs/FEATURES.md)
- [System Architecture (docs/ARCHITECTURE.md)](docs/ARCHITECTURE.md)
- [Low-Level Design & Protocol (docs/DESIGN.md)](docs/DESIGN.md)
- [API & CLI Guide (docs/API_AND_CLI.md)](docs/API_AND_CLI.md)

---

## 📄 License
OrbitFS Core is distributed under the [Apache License 2.0](LICENSE).
