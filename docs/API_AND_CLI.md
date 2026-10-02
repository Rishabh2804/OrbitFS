# OrbitFS Core — API & CLI Integration Guide

## 💻 Command-Line Interface (CLI)

Build and launch the standalone OrbitFS server directly from the command line:

```bash
# Build standalone JAR executable
./gradlew jar

# Launch server on port 9090 exposing /Users/shared
java -jar build/libs/orbitfs-core-0.1.0.jar --port 9090 --root /Users/shared
```

### CLI Arguments
- `--port <number>`: TCP port to listen on (Default: `9090`).
- `--root <path>`: Local filesystem directory to share.
- `--hide-hidden`: Filter hidden dotfiles (`.DS_Store`, `.git`) from directory listings.

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
