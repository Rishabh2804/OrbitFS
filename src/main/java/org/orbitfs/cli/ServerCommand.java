package org.orbitfs.cli;

import org.orbitfs.server.OrbitServerImpl;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;

@Command(name = "server",
        description = "Start an OrbitFS server.")
public class ServerCommand extends BaseCommand {

    @Option(names = {"--port"}, description = "Port to listen on (default: ${DEFAULT-VALUE})", defaultValue = "9090")
    private int port = 9090;

    @Option(names = {"--root"}, description = "Root directory to serve. (default: ${DEFAULT-VALUE})",
            defaultValue = "${env:HOME}${sys:file.separator}")
    private Path rootDir;

    @Option(names = {"--no-hidden-files"}, description = "Filter out hidden files (starting with .) from listings.")
    private boolean noHiddenFiles = false;

    @Override
    public Integer call() throws Exception {
        System.out.printf("orbit: server listening on %s:%d, root=%s%n",
                parent.getHost(), port, rootDir);
        new OrbitServerImpl(port, rootDir, noHiddenFiles).start();
        return 0;
    }
}
