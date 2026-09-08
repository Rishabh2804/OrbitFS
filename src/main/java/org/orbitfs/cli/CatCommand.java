package org.orbitfs.cli;

import org.orbitfs.client.OrbitFSClient;
import org.orbitfs.common.model.FileStat;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "cat",
        description = "Read and print a file from the remote filesystem.")
public class CatCommand extends BaseCommand {

    @Parameters(index = "0", description = "Path to file.")
    private String path;

    @Override
    public Integer call() throws Exception {
        try (OrbitFSClient client = parent.connect()) {
            String handle = client.open(path);
            FileStat stat = client.stat(handle);
            if (stat.isDirectory()) {
                System.err.println("orbit: " + path + ": Is a directory");
                client.close(handle);
                return 1;
            }
            long size = stat.size();
            int offset = 0;
            int remaining = (int) size;
            while (remaining > 0) {
                int toRead = Math.min(remaining, 64 * 1024);
                byte[] data = client.read(handle, offset, toRead);
                System.out.write(data, 0, data.length);
                offset += data.length;
                remaining -= data.length;
            }
            System.out.flush();
            client.close(handle);
        }
        return 0;
    }
}
