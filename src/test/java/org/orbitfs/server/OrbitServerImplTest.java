package org.orbitfs.server;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OrbitServerImplTest {

    @Test
    void startRespondsToPingAndStopsCleanly() throws Exception {
        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }

        OrbitServerImpl server = new OrbitServerImpl(port);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        serverThread.start();

        waitForPort(port);

        try (Socket socket = new Socket("127.0.0.1", port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println("PING");
            assertEquals("PONG", in.readLine());
        }

        server.stop();
        serverThread.join(Duration.ofSeconds(2).toMillis());

        assertFalse(serverThread.isAlive(), "server thread should stop after stop()");
        assertNull(failure.get(), "server should not fail: " + failure.get());
    }

    private static void waitForPort(int port) throws Exception {
        long deadline = System.currentTimeMillis() + 2_000;
        IOException last = null;
        while (System.currentTimeMillis() < deadline) {
            try (Socket ignored = new Socket("127.0.0.1", port)) {
                return;
            } catch (IOException e) {
                last = e;
                Thread.sleep(25);
            }
        }
        throw new AssertionError("Server did not open port " + port, last);
    }
}

