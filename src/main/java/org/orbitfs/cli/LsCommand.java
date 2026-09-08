package org.orbitfs.cli;

import org.orbitfs.client.OrbitFSClient;
import org.orbitfs.common.model.FileStat;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "ls",
        description = "List a file or directory from the remote filesystem.")
public class LsCommand extends BaseCommand {

    @Parameters(index = "0", description = "Path to list. (default: ${DEFAULT-VALUE})", defaultValue = "/")
    private String path = "/";

    @Option(names = {"-l", "--long"}, description = "Long format (show metadata).")
    private boolean longFormat = false;

    @Override
    public Integer call() throws Exception {
        try (OrbitFSClient client = parent.connect()) {
            String handle = client.open(path);
            FileStat stat = client.stat(handle);
            if (longFormat) {
                formatLong(path, stat);
            } else {
                formatShort(path, stat);
            }
            client.close(handle);
        }
        return 0;
    }

    private static void formatLong(String path, FileStat stat) {
        String type = stat.isDirectory() ? "drwxr-xr-x" : "-rw-r--r--";
        System.out.printf("%s  %10d  %s%n", type, stat.size(), path);
    }

    private static void formatShort(String path, FileStat stat) {
        String prefix = stat.isDirectory() ? "d" : "-";
        System.out.printf("%s %d %s%n", prefix, stat.size(), path);
    }
}
