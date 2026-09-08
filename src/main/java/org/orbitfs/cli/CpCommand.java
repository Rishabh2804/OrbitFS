package org.orbitfs.cli;

import org.orbitfs.client.OrbitFSClient;
import org.orbitfs.common.model.FileStat;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "cp",
        description = "Copy a file (remote-to-remote).")
public class CpCommand extends BaseCommand {

    @Parameters(index = "0", description = "Source path.")
    private String src;

    @Parameters(index = "1", description = "Destination path.")
    private String dst;

    @Override
    public Integer call() throws Exception {
        try (OrbitFSClient client = parent.connect()) {
            String readHandle = client.open(src);
            FileStat stat = client.stat(readHandle);
            if (stat.isDirectory()) {
                System.err.println("orbit: cp: source is a directory");
                client.close(readHandle);
                return 1;
            }

            String writeHandle = client.open(dst);
            long size = stat.size();
            System.out.printf("orbit: copying %d bytes: %s -> %s%n", size, src, dst);

            int offset = 0;
            int remaining = (int) size;
            while (remaining > 0) {
                int toRead = Math.min(remaining, 64 * 1024);
                byte[] data = client.read(readHandle, offset, toRead);
                client.write(writeHandle, offset, data);
                offset += data.length;
                remaining -= data.length;

                if (size >= 1_000_000) {
                    int pct = (int) ((offset * 100L) / size);
                    if (pct % 25 == 0 && pct > 0) {
                        System.out.printf("  %d%%%n", pct);
                    }
                }
            }

            client.close(readHandle);
            client.close(writeHandle);
            System.out.printf("orbit: done%n");
        }
        return 0;
    }
}
