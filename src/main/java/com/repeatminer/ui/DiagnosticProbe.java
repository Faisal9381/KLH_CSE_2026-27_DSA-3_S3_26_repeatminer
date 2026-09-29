package com.repeatminer.ui;

import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Opt-in diagnostic probe for automated smoke tests.
 *
 * <p>When the environment variable {@code REPEATMINER_PROBE_PORT} is set, the app
 * serves {@code GET / -> REPEATMINER_UP} on 127.0.0.1 and exits cleanly on
 * {@code POST /shutdown}. This lets a test "see" that the GUI window really came
 * up and then close it — without changing behaviour for normal users, who never
 * set that variable.
 *
 * <p>Started from {@link RepeatMinerApp#start} rather than from a plain
 * {@code main}: the {@code javafx:run} goal launches {@code Application} directly
 * and never calls {@code Main.main}, so the probe must live on the JavaFX side to
 * cover both launch paths.
 */
final class DiagnosticProbe {

    static void startIfEnabled() {
        String probePort = System.getenv("REPEATMINER_PROBE_PORT");
        if (probePort != null && probePort.matches("\\d+")) {
            start(Integer.parseInt(probePort));
        }
    }

    private static void start(int port) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            server.createContext("/", exchange -> {
                byte[] body = "REPEATMINER_UP".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())
                        && "/shutdown".equals(exchange.getRequestURI().getPath())) {
                    new Thread(() -> {
                        try {
                            Thread.sleep(200); // let the HTTP response flush first
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                        Platform.exit();
                        System.exit(0);
                    }, "repeatminer-shutdown").start();
                }
            });
            server.setExecutor(null);
            server.start();
            System.out.println("Diagnostic probe listening on http://127.0.0.1:" + port + "/");
        } catch (IOException e) {
            System.err.println("Diagnostic probe could not start: " + e);
        }
    }

    private DiagnosticProbe() {
        // utility class
    }
}
