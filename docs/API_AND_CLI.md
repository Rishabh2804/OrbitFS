# OrbitFS Core — API & CLI Integration Guide

## 💻 Command-Line Interface (CLI Wrapper)

OrbitFS includes a clean CLI wrapper script (`orbit` / `./orbit`) that invokes the underlying JAR automatically.

### 1. Installation

#### Global Install (macOS)
```bash
curl -fsSL https://raw.githubusercontent.com/Rishabh2804/OrbitFS/main/bin/install.sh | bash
```

#### Build from Source
```bash
git clone https://github.com/Rishabh2804/OrbitFS.git
cd OrbitFS
./gradlew jar
```

---

### 2. CLI Usage & Commands

Once installed or compiled, invoke commands directly using `orbit` or `./orbit`:

#### Start Server
```bash
# Launch OrbitFS file server on port 9090 sharing /Users/shared
orbit server --port 9090 --root /Users/shared

# Or via local repository wrapper
./orbit server --port 9090 --root /Users/shared
```

#### Remote Filesystem Operations
```bash
# List remote directory
orbit ls /Users/shared

# Print file contents
orbit cat /Users/shared/report.txt

# Inspect file metadata
orbit stat /Users/shared/report.txt

# Delete file or directory
orbit rm /Users/shared/temp.txt
```

---

## ☕ Java / JVM Programmatic API

### 1. Launching Server Programmatically
```java
import org.orbitfs.server.OrbitServerImpl;
import java.nio.file.Path;

public class ServerExample {
    public static void main(String[] args) throws Exception {
        int port = 9090;
        Path rootPath = Path.of("/path/to/share");
        boolean hideHidden = true;

        OrbitServerImpl server = new OrbitServerImpl(port, rootPath, hideHidden);
        server.start();
        System.out.println("OrbitFS Server running on port " + port);
    }
}
```

### 2. Client Connection & File Operations
```java
import org.orbitfs.client.NetworkTransportClient;
import org.orbitfs.client.CachingOrbitFSClient;

public class ClientExample {
    public static void main(String[] args) throws Exception {
        // Connect to remote server
        NetworkTransportClient transport = new NetworkTransportClient("192.168.1.15", 9090, 10000);
        transport.connect();

        CachingOrbitFSClient client = new CachingOrbitFSClient(transport);

        // List directory
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
