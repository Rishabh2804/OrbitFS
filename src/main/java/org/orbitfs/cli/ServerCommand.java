package org.orbitfs.cli;

import org.orbitfs.server.OrbitServerImpl;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "server",
        description = "Start an OrbitFS server.")
public class ServerCommand extends BaseCommand {

    @Option(names = {"--port"}, description = "Port to listen on (default: ${DEFAULT-VALUE})", defaultValue = "9090")
    private int port = 9090;

    @Option(names = {"--root"}, description = "Root directory to serve. (default: user home)")
    private String rootDir = System.getProperty("user.home");

    @Override
    public Integer call() throws Exception {
        System.out.printf("orbit: server listening on port %d, root=%s%n", port, rootDir);
        new OrbitServerImpl(port).start();
        return 0;
    }
}
