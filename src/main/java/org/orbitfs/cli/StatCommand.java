package org.orbitfs.cli;

import org.orbitfs.client.OrbitFSClient;
import org.orbitfs.common.model.FileStat;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "stat",
        description = "Show detailed metadata for a file.")
public class StatCommand extends BaseCommand {

    @Parameters(index = "0", description = "Path to file.")
    private String path;

    @Override
    public Integer call() throws Exception {
        try (OrbitFSClient client = parent.connect()) {
            String handle = client.open(path);
            FileStat s = client.stat(handle);
            System.out.printf("""
                    %n┌─ %s ───────────────────────
                    │  Size:      %d bytes
                    │  Type:      %s
                    │  Modified:  %d
                    └──────────────────────────────────%n
                    """, path, s.size(),
                    s.isDirectory() ? "directory" : "file",
                    s.lastModifiedMillis());
            client.close(handle);
        }
        return 0;
    }
}
