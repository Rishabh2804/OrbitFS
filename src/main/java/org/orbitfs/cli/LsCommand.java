package org.orbitfs.cli;

import org.orbitfs.client.OrbitFSClient;
import org.orbitfs.common.model.FileStat;
import org.orbitfs.common.protocol.RPCResponse;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;

@Command(name = "ls",
        description = "List a file or directory from the remote filesystem.")
public class LsCommand extends BaseCommand {

    @Parameters(index = "0", description = "Path to list. (default: /)", defaultValue = "/")
    private String path = "/";

    @Option(names = {"-l", "--long"}, description = "Long format (show metadata).")
    private boolean longFormat = false;

    @Option(names = {"-a", "--all", "--hidden"}, description = "Include hidden files.")
    private boolean showHidden = false;

    @Override
    public Integer call() throws Exception {
        try (OrbitFSClient client = parent.connect()) {
            String handle = client.open(path);
            FileStat stat = client.stat(handle);

            if (!stat.isDirectory()) {
                if (longFormat) {
                    String perms = stat.permissions() != null && !stat.permissions().isEmpty() ? stat.permissions() : "rw-r--r--";
                    String permsDisplay = "-" + perms;
                    String owner = stat.owner() != null && !stat.owner().isEmpty() ? stat.owner() : "?";
                    System.out.printf("%s  %10d  %-10s  %s%n", permsDisplay, stat.size(), owner, path);
                } else {
                    System.out.println(path);
                }
                client.close(handle);
                return 0;
            }

        List<RPCResponse.RPCEntry> entries = client.listWithStat(handle, showHidden);
        String basePath = path.endsWith("/") ? path : path + "/";

        if (longFormat) {
            System.out.printf("total %d%n", entries.size());
            for (RPCResponse.RPCEntry entry : entries) {
                String perms = entry.permissions() != null && !entry.permissions().isEmpty()
                        ? entry.permissions() : (entry.isDir() ? "rwxr-xr-x" : "rw-r--r--");
                String permsDisplay = (entry.isDir() ? "d" : "-") + perms;
                String owner = entry.owner() != null && !entry.owner().isEmpty() ? entry.owner() : "?";
                System.out.printf("%s  %10d  %-10s  %s%n", permsDisplay, entry.size(), owner, entry.name());
            }
        } else {
            for (RPCResponse.RPCEntry entry : entries) {
                System.out.println(entry.name());
            }
        }
            client.close(handle);
        }
        return 0;
    }
}
