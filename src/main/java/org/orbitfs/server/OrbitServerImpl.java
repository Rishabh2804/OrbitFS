package org.orbitfs.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;

import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static org.orbitfs.common.OrbitCore.LOGGER;

public class OrbitServerImpl implements OrbitServer {

    private final int port;
    private final ExecutorService executor;
    private volatile ServerSocket serverSocket;

    public OrbitServerImpl(int port) {
        this.port = port;
        this.executor = newVirtualThreadPerTaskExecutor();
    }

    @Override
    public void start() throws IOException {
        try (ServerSocket listeningSocket = new ServerSocket(port)) {
            serverSocket = listeningSocket;

            while (!listeningSocket.isClosed()) {
                Socket socket = listeningSocket.accept();
                executor.submit(() -> handleAcceptedConnection(socket));
            }
        } catch (SocketException e) {
            if (!isShuttingDown()) throw e;

        } finally {
            tearDown();
        }
    }

    private void handleAcceptedConnection(Socket socket) {
        try {
            handleConnection(socket);
        } catch (IOException ioe) {
            // Connection closed or interrupted while processing.
            LOGGER.info("Connection closed or interrupted while processing: " + socket, ioe);
        }
    }

    @Override
    public void handleConnection(Socket socket) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

        try  {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                if (inputLine.startsWith("PING")) {
                    out.println("PONG");
                    break;
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error handling connection: " + socket, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stop() {
        ServerSocket current = serverSocket;
        if (current != null && !current.isClosed())
            try {
                current.close();
            } catch (IOException ignored) {
                // ignore
            } finally {
                LOGGER.info("Server socket closed.");
            }

        tearDown();
    }

    private void tearDown() {
        serverSocket = null;
        executor.shutdown();
    }

    private boolean isShuttingDown() {
        ServerSocket current = serverSocket;
        return current == null || current.isClosed();
    }
}
