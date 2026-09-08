package org.orbitfs.cli;

import org.orbitfs.client.CachingOrbitFSClient;
import org.orbitfs.client.NetworkTransportClient;
import org.orbitfs.client.OrbitFSClient;
import org.orbitfs.common.model.FileStat;

import java.io.IOException;
import java.util.Arrays;

/**
 * orbit — simple CLI for OrbitFS remote filesystem.
 *
 * Usage: java -jar orbitfs.jar <command> [args] [options]
 *
 * Commands:
 *   orbit ls [path]           List a directory
 *   orbit cat <path>          Concatenate and print a file
 *   orbit read <path>         Read file (alias for cat)
 *   orbit stat <path>         Show file metadata
 *   orbit cp <src> <dst>      Copy remote file
 *   orbit rm <path>           Delete a file
 *   orbit server [--port p]   Start server
 *
 * Options (after command args):
 *   --host <h>   Default: 127.0.0.1
 *   --port <p>   Default: 9090
 *   --timeout <ms>  Default: 30000
 */
public class OrbitCli {

    private final String host;
    private final int port;
    private final long timeoutMs;

    public OrbitCli(String host, int port, long timeoutMs) {
        this.host = host;
        this.port = port;
        this.timeoutMs = timeoutMs;
    }

    private OrbitFSClient connect() throws IOException {
        NetworkTransportClient transport = new NetworkTransportClient(host, port, timeoutMs);
        transport.connect();
        return new CachingOrbitFSClient(transport);
    }

    public void cat(String path) throws IOException {
        try (OrbitFSClient client = connect()) {
            String handle = client.open(path);
            FileStat stat = client.stat(handle);
            if (stat.isDirectory()) {
                System.err.println("orbit: " + path + ": Is a directory");
                client.close(handle);
                return;
            }
            long size = stat.size();
            int offset = 0;
            int remaining = (int) size;
            while (remaining > 0) {
                int toRead = Math.min(remaining, 64 * 1024);
                byte[] data = client.read(handle, offset, toRead);
                System.out.write(data, 0, data.length);
                offset += data.length;
                remaining -= data.length;
            }
            System.out.flush();
            client.close(handle);
        }
    }

    public void ls(String path) throws IOException {
        try (OrbitFSClient client = connect()) {
            String handle = client.open(path);
            FileStat stat = client.stat(handle);
            System.out.println((stat.isDirectory() ? "d" : "-") + " " +
                    stat.size() + " " + path);
            client.close(handle);
        }
    }

    public void stat(String path) throws IOException {
        try (OrbitFSClient client = connect()) {
            String handle = client.open(path);
            FileStat s = client.stat(handle);
            System.out.println("  Path:     " + path);
            System.out.println("  Size:     " + s.size() + " bytes");
            System.out.println("  Type:     " + (s.isDirectory() ? "directory" : "file"));
            System.out.println("  Modified: " + s.lastModifiedMillis());
            client.close(handle);
        }
    }

    public void cp(String src, String dst) throws IOException {
        try (OrbitFSClient client = connect()) {
            String readHandle = client.open(src);
            FileStat stat = client.stat(readHandle);
            if (stat.isDirectory()) {
                System.err.println("orbit: cp: source is a directory");
                client.close(readHandle);
                return;
            }

            String writeHandle = client.open(dst);
            long size = stat.size();
            int offset = 0;
            int remaining = (int) size;
            while (remaining > 0) {
                int toRead = Math.min(remaining, 64 * 1024);
                byte[] data = client.read(readHandle, offset, toRead);
                client.write(writeHandle, offset, data);
                offset += data.length;
                remaining -= data.length;
            }

            client.close(readHandle);
            client.close(writeHandle);
            System.out.println("orbit: copied " + size + " bytes: " + src + " -> " + dst);
        }
    }

    public void rm(String path) throws IOException {
        try (OrbitFSClient client = connect()) {
            String handle = client.open(path);
            System.err.println("orbit: rm: not yet implemented (server DELETE method pending)");
            client.close(handle);
        }
    }

    public void startServer(int port) throws IOException {
        System.out.println("orbit: server listening on port " + port);
        new org.orbitfs.server.OrbitServerImpl(port).start();
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        String host = "127.0.0.1";
        int port = 9090;
        long timeout = 30000;
        java.util.List<String> positional = new java.util.ArrayList<>();
        String nextOpt = null;

        for (String arg : args) {
            if (arg.equals("--host") || arg.equals("--port") || arg.equals("--timeout")) {
                nextOpt = arg;
            } else if (nextOpt != null) {
                switch (nextOpt) {
                    case "--host" -> host = arg;
                    case "--port" -> port = Integer.parseInt(arg);
                    case "--timeout" -> timeout = Long.parseLong(arg);
                }
                nextOpt = null;
            } else {
                positional.add(arg);
            }
        }

        if (positional.isEmpty()) {
            printUsage();
            System.exit(1);
        }

        String cmd = positional.get(0);
        OrbitCli cli = new OrbitCli(host, port, timeout);

        try {
            switch (cmd) {
                case "cat", "read" -> {
                    if (positional.size() < 2) { System.err.println("orbit: missing <path>"); System.exit(1); }
                    cli.cat(positional.get(1));
                }
                case "ls" -> {
                    String path = positional.size() >= 2 ? positional.get(1) : "/";
                    cli.ls(path);
                }
                case "stat" -> {
                    if (positional.size() < 2) { System.err.println("orbit: missing <path>"); System.exit(1); }
                    cli.stat(positional.get(1));
                }
                case "cp" -> {
                    if (positional.size() < 3) { System.err.println("orbit: cp <src> <dst>"); System.exit(1); }
                    cli.cp(positional.get(1), positional.get(2));
                }
                case "rm" -> {
                    if (positional.size() < 2) { System.err.println("orbit: missing <path>"); System.exit(1); }
                    cli.rm(positional.get(1));
                }
                case "server" -> cli.startServer(port);
                default -> {
                    System.err.println("orbit: unknown command: " + cmd);
                    printUsage();
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            System.err.println("orbit: error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.err.println("""
                orbit — OrbitFS CLI

                Usage: orbit <command> [args] [options]

                Commands:
                  ls [path]            List a directory (default: /)
                  cat <path>           Print file contents
                  read <path>          Alias for cat
                  stat <path>          Show file metadata
                  cp <src> <dst>       Copy remote file
                  rm <path>            Delete a file (not yet implemented)
                  server               Start OrbitFS server

                Options:
                  --host <h>       Server host (default: 127.0.0.1)
                  --port <p>       Server port (default: 9090)
                  --timeout <ms>   Timeout ms (default: 30000)

                Examples:
                  orbit ls --host 192.168.1.5 /
                  orbit cat /Users/me/file.txt
                  orbit server --port 8080
                """);
    }
}
