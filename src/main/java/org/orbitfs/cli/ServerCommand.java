package org.orbitfs.cli;

import org.orbitfs.server.OrbitServerImpl;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "server",
        description = "Start an OrbitFS server.")
public class ServerCommand extends BaseCommand {

    @Option(names = {"--port"}, description = "Port to listen on (default: ${DEFAULT-VALUE})", defaultValue = "9090")
    private int port = 9090;

    @Override
    public Integer call() throws Exception {
        System.out.printf("orbit: server listening on port %d%n", port);
        new OrbitServerImpl(port).start();
        return 0;
    }
}
