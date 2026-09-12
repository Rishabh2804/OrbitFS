package org.orbitfs.cli;

import org.orbitfs.client.OrbitFSClient;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "rm",
        description = "Delete a file or directory.")
public class RmCommand extends BaseCommand {

    @Parameters(index = "0", description = "Path to delete.")
    private String path;

    @Override
    public Integer call() throws Exception {
        try (OrbitFSClient client = parent.connect()) {
            client.delete(path);
            System.err.println("orbit: rm: deleted " + path);
            return 0;
        }
    }
}
