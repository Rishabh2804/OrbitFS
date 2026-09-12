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
                    System.out.printf("-rw-r--r--  %10d  %s%n", stat.size(), path);
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
                String type = entry.isDir() ? "drwxr-xr-x" : "-rw-r--r--";
                System.out.printf("%s  %10d  %s%n", type, entry.size(), entry.name());
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
