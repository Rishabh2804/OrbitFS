package org.orbitfs.cli;

import org.orbitfs.client.CachingOrbitFSClient;
import org.orbitfs.client.NetworkTransportClient;
import org.orbitfs.client.OrbitFSClient;
import org.orbitfs.server.OrbitServerImpl;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.util.concurrent.Callable;

@Command(name = "orbit",
        mixinStandardHelpOptions = true,
        version = "orbit 0.1.0",
        description = "OrbitFS CLI — remote filesystem over TCP",
        subcommands = {
                CatCommand.class,
                LsCommand.class,
                StatCommand.class,
                CpCommand.class,
                RmCommand.class,
                ServerCommand.class
        })
public class OrbitCli implements Callable<Integer> {

    @Option(names = {"--host"}, description = "Server host (default: ${DEFAULT-VALUE})")
    private String host = "127.0.0.1";

    @Option(names = {"--port"}, description = "Server port (default: ${DEFAULT-VALUE})")
    private int port = 9090;

    @Option(names = {"--timeout"}, description = "Timeout in ms (default: ${DEFAULT-VALUE})")
    private long timeoutMs = 30_000;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new OrbitCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        CommandLine.usage(new OrbitCli(), System.out);
        return 0;
    }

    public OrbitFSClient connect() throws IOException {
        NetworkTransportClient transport = new NetworkTransportClient(host, port, timeoutMs);
        transport.connect();
        return new CachingOrbitFSClient(transport);
    }
}
