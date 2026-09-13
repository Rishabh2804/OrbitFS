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
            String perms = s.permissions() != null && !s.permissions().isEmpty() ? s.permissions() : "rw-r--r--";
            String permsDisplay = (s.isDirectory() ? "d" : "-") + perms;
            String owner = s.owner() != null && !s.owner().isEmpty() ? s.owner() : "?";
            String mime = s.mimeType() != null ? s.mimeType() : "unknown";
            System.out.printf("""
                    %n┌─ %s ───────────────────────
                    │  Size:      %d bytes
                    │  Type:      %s
                    │  Modified:  %d
                    │  Created:   %d
                    │  Perms:     %s
                    │  Owner:     %s
                    │  MIME:      %s
                    │  Extension: %s
                    └──────────────────────────────────%n
                    """, path, s.size(),
                    s.isDirectory() ? "directory" : "file",
                    s.lastModifiedMillis(),
                    s.createdMillis(),
                    permsDisplay,
                    owner,
                    mime,
                    s.extension() != null && !s.extension().isEmpty() ? s.extension() : "(none)");
            client.close(handle);
        }
        return 0;
    }
}
