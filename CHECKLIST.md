# OrbitFS Project Checklist

> Edit me: mark `[x]` when a ticket is done. Mirror of spec checkpoints.

## Environment (pre-OFS-100)

- [x] IntelliJ project created (`.idea`, `.iml`, `.gitignore`)
- [x] JDK 23 installed (targets Java 21 release)
- [x] Gradle 9.3.1 installed
- [ ] Gradle build script wired to project
- [ ] JUnit 5 wired, first test green
- [ ] IntelliJ imported Gradle project

## Checkpoint 1 — Wire Protocol

- [ ] OFS-101 RPCRequest / RPCResponse DTOs (Jackson roundtrip)
- [ ] OFS-102 FrameCodec — magic `ORBT` + length-prefixed framing, `readFully`
- [ ] OFS-103 Server listener + virtual tasks + PING→PONG (50 clients <100ms)

## Checkpoint 2 — Storage Engine

- [ ] OFS-104 FileDescriptorTable — UUID handles, thread-safe
- [ ] OFS-105 PathLockRegistry — read shared / write exclusive
- [ ] OFS-106 StorageEngine — OPEN/READ/WRITE/CLOSE/SEEK/STAT (10W+20R stress)

## Checkpoint 3 — Client Proxy & Cache

- [ ] OFS-107 NetworkTransportClient + OrbitFSClient interface
- [ ] OFS-108 LRUChunkCache — 64KB blocks, write-back, 1 RPC for 1000×1KB reads

## Checkpoint 4 — Security Sandbox

- [ ] OFS-109 ChrootSandboxGuard — 100% traversal rejection
- [ ] OFS-110 Payload cap 10MB + hidden/system file blocklist + STATUS_SECURITY_VIOLATION

## Notes

- Deviation from spec: Maven (`pom.xml`) → **Gradle**. Build tool orthogonal to code; all JVM targets identical.
- Spec gotcha: client interface line garbled (`未写`) = `close` belongs in interface.
- Error code taxonomy undefined in spec → OFS-110 proposes: 0=OK, 1=NOT_FOUND, 2=IO_ERROR, 3=SECURITY_VIOLATION, 4=BAD_REQUEST.