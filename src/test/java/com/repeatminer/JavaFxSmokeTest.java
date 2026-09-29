package com.repeatminer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * End-to-end smoke test for Phase 1: starts the real application
 * ({@code mvnw javafx:run}) in a separate JVM and verifies that the JavaFX
 * window actually comes up, then asks it to shut down.
 *
 * <p>The test needs a way to "see" a GUI from inside JUnit, so
 * {@code com.repeatminer.Main} exposes a tiny opt-in diagnostic probe: when the
 * environment variable {@code REPEATMINER_PROBE_PORT} is set, the app serves
 * {@code GET / -> REPEATMINER_UP} on 127.0.0.1 and exits on {@code POST /shutdown}.
 * The variable is never set in normal use, so the behaviour of the application
 * for real users is unchanged.
 *
 * <p>On machines without a display (headless CI containers) the test is skipped
 * via a JUnit assumption instead of failing.
 */
class JavaFxSmokeTest {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static Process appProcess;
    private static final List<String> recentOutput = Collections.synchronizedList(new ArrayList<>());

    @BeforeAll
    static void requireDisplay() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),
                "No display available — JavaFX smoke test skipped (headless environment).");
    }

    @Test
    void guiWindowOpensAndRespondsToShutdown() throws Exception {
        int probePort = freePort();

        List<String> command = new ArrayList<>();
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (windows) {
            command.add("cmd");
            command.add("/c");
            command.add(projectRoot().resolve("mvnw.cmd").toAbsolutePath().toString());
        } else {
            command.add(projectRoot().resolve("mvnw").toAbsolutePath().toString());
        }
        command.add("javafx:run");
        command.add("-B");
        command.add("-ntp");

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().put("REPEATMINER_PROBE_PORT", Integer.toString(probePort));
        builder.directory(projectRoot().toFile());
        builder.redirectErrorStream(true);

        appProcess = builder.start();
        Thread drainer = new Thread(JavaFxSmokeTest::drainProcessOutput, "smoke-output-drainer");
        drainer.setDaemon(true);
        drainer.start();

        assertTrue(waitForProbe(probePort, Duration.ofMinutes(4)),
                () -> "GUI did not report startup within 4 minutes.\nLast output:\n" + tailOfOutput());

        sendShutdownRequest(probePort);

        assertTrue(waitForExit(Duration.ofSeconds(30)),
                "Application did not exit after the shutdown request.");
        assertEquals(0, appProcess.exitValue(),
                () -> "Application exited with an error code.\nLast output:\n" + tailOfOutput());
    }

    /** Polls the diagnostic probe until it answers {@code REPEATMINER_UP}. */
    private static boolean waitForProbe(int port, Duration timeout) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (!appProcess.isAlive()) {
                fail("Application process terminated before the GUI started.\nLast output:\n" + tailOfOutput());
            }
            try {
                HttpResponse<String> response =
                        CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 && response.body().contains("REPEATMINER_UP")) {
                    return true;
                }
            } catch (IOException | InterruptedException ignored) {
                if (Thread.currentThread().isInterrupted()) {
                    return false;
                }
                // Probe not up yet — retry.
            }
            sleep(Duration.ofSeconds(1));
        }
        return false;
    }

    private static boolean waitForExit(Duration timeout) throws InterruptedException {
        return appProcess.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Asks the diagnostic probe to close the application. */
    private static void sendShutdownRequest(int probePort) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + probePort + "/shutdown"))
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void drainProcessOutput() {
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(appProcess.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (recentOutput) {
                    recentOutput.add(line);
                    while (recentOutput.size() > 200) {
                        recentOutput.remove(0);
                    }
                }
            }
        } catch (IOException ignored) {
            // Stream closed while the process is shutting down — expected.
        }
    }

    private static String tailOfOutput() {
        synchronized (recentOutput) {
            int from = Math.max(0, recentOutput.size() - 40);
            return String.join("\n", recentOutput.subList(from, recentOutput.size()));
        }
    }

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("pom.xml"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("Could not locate pom.xml above " + Path.of(""));
        }
        return dir;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            return socket.getLocalPort();
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterAll
    static void killLeftoverProcess() {
        Process process = appProcess;
        if (process != null && process.isAlive()) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }
}
