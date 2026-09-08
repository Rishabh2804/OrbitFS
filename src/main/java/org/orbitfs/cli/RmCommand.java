package org.orbitfs.cli;

import org.orbitfs.client.OrbitFSClient;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "rm",
        description = "Delete a file (not yet implemented on server).")
public class RmCommand extends BaseCommand {

    @Parameters(index = "0", description = "Path to delete.")
    private String path;

    @Override
    public Integer call() throws Exception {
        try (OrbitFSClient client = parent.connect()) {
            String handle = client.open(path);
            System.err.println("orbit: rm: not yet implemented (server DELETE method pending)");
            client.close(handle);
        }
        return 1;
    }
}
