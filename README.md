# OrbitFS Core — Java 21 Framed RPC File System Engine

> *"High-Performance, Local-First Peer-to-Peer Storage Engine."*

[![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Gradle](https://img.shields.io/badge/Gradle-9.3.1-02303A?logo=gradle&logoColor=white)](https://gradle.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

OrbitFS Core is a high-performance, lightweight peer-to-peer filesystem and transport engine written in **Java 21**. It allows remote clients to discover, open, read, write, seek, and stat files over TCP sockets with near-local filesystem speed and sub-millisecond latencies.

---

## 🌟 Key Technical Highlights

- **Custom Binary-Framed RPC Protocol**: `MAGIC(4B) + LENGTH(4B) + JSON` message frames with strict error handling (`READ`, `WRITE`, `STAT`, `LIST`, `OPEN`, `CLOSE`).
- **Java 21 Virtual Thread Concurrency**: `Thread.ofVirtual()` per-connection server model delivering cheap, scalable concurrency without OS thread-pool sizing constraints.
- **Multiplexed Socket Transport**: Single persistent TCP socket connection per client handling multiple in-flight asynchronous requests mapped via atomic `requestId` futures.
- **Path-Level Read/Write Locking**: `PathLockRegistry` enforces concurrent reader access without blocking while guaranteeing exclusive writer access.
- **Client-Side LRU Write-Back Cache**: `LRUChunkCache` buffers 64KB file chunks in memory to minimize round-trip socket RPCs.
- **Sandbox Confinement (`SandboxGuard`)**: Strict canonical path validation prevents directory traversal attacks (`../`) outside designated root shares.

---

## 🏗️ Architecture & Component Flow

```mermaid
flowchart TB
    subgraph Client["Client Application"]
        App["App / ViewModel"] --> CClient["CachingOrbitFSClient"]
        CClient --> Cache["LRUChunkCache (64KB Chunks)"]
        CClient --> NTC["NetworkTransportClient"]
    end

    NTC <== "Framed TCP Socket (ORBT + 4B Len + JSON)" ==> Server
    
    subgraph Server["OrbitFS Server Engine"]
        Server["OrbitServerImpl (Virtual Threads)"] --> Guard["SandboxGuard"]
        Guard --> Locks["PathLockRegistry"]
        Locks --> Engine["StorageEngine"]
        Engine --> FDT["FileDescriptorTable"]
        FDT --> Disk[("Local File System / Disk")]
    end
```

---

## 💻 Usage & Integration

### 1. Command-Line Interface (CLI)
Build and run the standalone server executable from the command line:

```bash
# Build standalone JAR
./gradlew jar

# Run server on port 9090 sharing /Users/shared
java -jar build/libs/orbitfs-core-0.1.0.jar --port 9090 --root /Users/shared
```

#### CLI Arguments:
- `--port <number>`: TCP port to listen on (Default: `9090`).
- `--root <path>`: Local directory path to expose as server root share.
- `--hide-hidden`: Hide dotfiles/hidden files from directory listings.

---

### 2. Java / JVM API Integration

#### Starting the OrbitFS Server Programmatically:
```java
import org.orbitfs.server.OrbitServerImpl;
import java.nio.file.Path;

public class ServerLauncher {
    public static void main(String[] args) throws Exception {
        int port = 9090;
        Path rootPath = Path.of("/path/to/share");
        boolean hideHidden = true;

        // Initialize and start server
        OrbitServerImpl server = new OrbitServerImpl(port, rootPath, hideHidden);
        server.start();
        System.out.println("OrbitFS Server running on port " + port);
    }
}
```

#### Connecting and Transferring Files Programmatically:
```java
import org.orbitfs.client.NetworkTransportClient;
import org.orbitfs.client.CachingOrbitFSClient;
import org.orbitfs.common.protocol.RPCResponse;

public class ClientLauncher {
    public static void main(String[] args) throws Exception {
        // Connect to remote server
        NetworkTransportClient transport = new NetworkTransportClient("192.168.1.15", 9090, 10000);
        transport.connect();

        CachingOrbitFSClient client = new CachingOrbitFSClient(transport);

        // List files
        var entries = client.listWithStat("/", false);
        for (var entry : entries) {
            System.out.println(entry.name() + " - " + entry.size() + " bytes");
        }

        // Read file bytes
        String handle = client.open("documents/report.pdf");
        byte[] bytes = client.read(handle, 0, 65536);
        client.close(handle);

        transport.close();
    }
}
```

---

## 🛠️ Building & Testing

```bash
# Run unit and integration tests
./gradlew test

# Generate production JAR
./gradlew jar
```

---

## 📄 License
OrbitFS Core is distributed under the [Apache License 2.0](LICENSE).
