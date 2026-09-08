package org.orbitfs.cli;

import org.orbitfs.client.OrbitFSClient;
import org.orbitfs.common.model.FileStat;

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

            List<String> entries = client.list(handle);
            String basePath = path.endsWith("/") ? path : path + "/";

            if (longFormat) {
                System.out.printf("total %d%n", entries.size());
                for (String name : entries) {
                    String entryPath = basePath + name;
                    try {
                        String subHandle = client.open(entryPath);
                        FileStat eStat = client.stat(subHandle);
                        String type = eStat.isDirectory() ? "drwxr-xr-x" : "-rw-r--r--";
                        System.out.printf("%s  %10d  %s%n", type, eStat.size(), name);
                        client.close(subHandle);
                    } catch (Exception e) {
                        System.out.printf("-??????????  %12s  %s%n", "", name);
                    }
                }
            } else {
                for (String name : entries) {
                    System.out.println(name);
                }
            }
            client.close(handle);
        }
        return 0;
    }
}
