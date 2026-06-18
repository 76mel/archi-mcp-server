package net.vheerden.archi.mcp.server;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

import org.junit.After;
import org.junit.Test;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Tests for {@link TransportConfig}.
 *
 * <p>Uses high ports (19090+) to avoid conflicts with real services.
 * Tests that require actual server start/stop exercise the full Jetty + MCP SDK wiring.</p>
 */
public class TransportConfigTest {

    private static final int TEST_PORT = 19090;
    private static final String TEST_BIND_ADDRESS = "127.0.0.1";

    private TransportConfig transportConfig = new TransportConfig();

    @After
    public void tearDown() {
        transportConfig.stopServer();
    }

    @Test
    public void shouldReturnFalse_whenServerNotStarted() {
        assertFalse(transportConfig.isRunning());
    }

    @Test
    public void shouldReturnZeroPort_whenServerNotStarted() {
        assertEquals(0, transportConfig.getPort());
    }

    @Test
    public void shouldReturnTrue_whenServerStarted() throws Exception {
        transportConfig.startServer(TEST_PORT, TEST_BIND_ADDRESS);

        assertTrue(transportConfig.isRunning());
    }

    @Test
    public void shouldCreateServerWithConfiguredPort_whenValidPortProvided() throws Exception {
        transportConfig.startServer(TEST_PORT, TEST_BIND_ADDRESS);

        assertEquals(TEST_PORT, transportConfig.getPort());
    }

    @Test
    public void shouldCreateServerWithConfiguredBindAddress_whenValidAddressProvided() throws Exception {
        transportConfig.startServer(TEST_PORT, TEST_BIND_ADDRESS);

        assertTrue(transportConfig.isRunning());
        assertEquals(TEST_BIND_ADDRESS, transportConfig.getBindAddress());
    }

    @Test
    public void shouldHandlePortConflict_whenPortAlreadyInUse() throws Exception {
        // Bind the port first to create a conflict
        try (ServerSocket blocker = new ServerSocket(TEST_PORT + 1, 1,
                java.net.InetAddress.getByName(TEST_BIND_ADDRESS))) {

            try {
                transportConfig.startServer(TEST_PORT + 1, TEST_BIND_ADDRESS);
                fail("Expected ServerStartException for port conflict");
            } catch (ServerStartException e) {
                assertEquals(ServerStartException.PORT_IN_USE, e.getErrorCode());
                assertFalse(transportConfig.isRunning());
            }
        }
    }

    @Test
    public void shouldStopCleanly_whenServerIsRunning() throws Exception {
        transportConfig.startServer(TEST_PORT + 2, TEST_BIND_ADDRESS);
        assertTrue(transportConfig.isRunning());

        transportConfig.stopServer();

        assertFalse(transportConfig.isRunning());
        assertEquals(0, transportConfig.getPort());
    }

    @Test
    public void shouldNotThrow_whenStoppingServerThatIsNotRunning() {
        assertFalse(transportConfig.isRunning());
        transportConfig.stopServer(); // Should not throw
        assertFalse(transportConfig.isRunning());
    }

    @Test
    public void shouldNotStartTwice_whenAlreadyRunning() throws Exception {
        transportConfig.startServer(TEST_PORT + 3, TEST_BIND_ADDRESS);
        assertTrue(transportConfig.isRunning());

        // Second start should be a no-op (already running)
        transportConfig.startServer(TEST_PORT + 4, TEST_BIND_ADDRESS);
        assertTrue(transportConfig.isRunning());
        // Port should remain the original one
        assertEquals(TEST_PORT + 3, transportConfig.getPort());
    }

    @Test
    public void shouldExposeMcpServerInstances_whenStarted() throws Exception {
        transportConfig.startServer(TEST_PORT + 5, TEST_BIND_ADDRESS);

        assertNotNull(transportConfig.getStreamableMcpServer());
        assertNotNull(transportConfig.getSseMcpServer());
    }

    @Test
    public void shouldReturnNullMcpServers_whenNotStarted() {
        assertNull(transportConfig.getStreamableMcpServer());
        assertNull(transportConfig.getSseMcpServer());
    }

    @Test
    public void shouldReturnNullBindAddress_whenNotStarted() {
        assertNull(transportConfig.getBindAddress());
    }

    @Test
    public void shouldRejectInvalidBindAddress_whenAddressUnresolvable() {
        try {
            transportConfig.startServer(TEST_PORT + 6, "999.invalid.address");
            fail("Expected ServerStartException for invalid bind address");
        } catch (ServerStartException e) {
            assertEquals(ServerStartException.INVALID_BIND_ADDRESS, e.getErrorCode());
            assertFalse(transportConfig.isRunning());
        }
    }

    @Test
    public void shouldHaveCorrectErrorCode_whenPortInUse() {
        ServerStartException ex = new ServerStartException(
                "Port in use", new java.net.BindException("Address already in use"));
        assertEquals(ServerStartException.PORT_IN_USE, ex.getErrorCode());
    }

    @Test
    public void shouldHaveGenericErrorCode_whenOtherFailure() {
        ServerStartException ex = new ServerStartException(
                "Other failure", new RuntimeException("something else"));
        assertEquals(ServerStartException.SERVER_START_FAILED, ex.getErrorCode());
    }

    @Test
    public void shouldAcceptResourceSpecifications_withoutError() {
        transportConfig.setResourceSpecifications(List.of(createResourceSpec(
                "archimate://test/resource", "Test", "A test resource")));
        // No exception expected
    }

    @Test
    public void shouldAcceptNullResourceSpecifications() {
        transportConfig.setResourceSpecifications(null);
        // No exception expected — null treated as empty list
    }

    @Test
    public void shouldStartServer_withResourceSpecifications() throws Exception {
        transportConfig.setResourceSpecifications(List.of(createResourceSpec(
                "archimate://test/resource", "Test Resource", "A test resource")));
        transportConfig.startServer(TEST_PORT + 7, TEST_BIND_ADDRESS);

        assertTrue(transportConfig.isRunning());
        assertNotNull(transportConfig.getStreamableMcpServer());
        assertNotNull(transportConfig.getSseMcpServer());
    }

    @Test
    public void shouldStartServer_withEmptyResourceSpecifications() throws Exception {
        transportConfig.setResourceSpecifications(Collections.emptyList());
        transportConfig.startServer(TEST_PORT + 8, TEST_BIND_ADDRESS);

        assertTrue(transportConfig.isRunning());
    }

    // ---- TLS Tests ----

    @Test
    public void shouldStartWithTls_whenKeystoreConfigured() throws Exception {
        // Generate a keystore first
        String keystorePath = System.getProperty("java.io.tmpdir") + "/test-tls-keystore-" + System.nanoTime() + ".p12";
        CertificateGenerator.Result certResult = CertificateGenerator.generate(keystorePath);

        try {
            transportConfig.startServer(TEST_PORT + 20, TEST_BIND_ADDRESS,
                    true, certResult.keystorePath(), certResult.password());

            assertTrue(transportConfig.isRunning());
            assertTrue(transportConfig.isTlsEnabled());
            assertEquals(TEST_PORT + 20, transportConfig.getPort());
        } finally {
            transportConfig.stopServer();
            java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(keystorePath));
        }
    }

    @Test
    public void shouldFailWithTlsError_whenKeystoreInvalid() {
        try {
            transportConfig.startServer(TEST_PORT + 21, TEST_BIND_ADDRESS,
                    true, "/nonexistent/keystore.p12", "password");
            fail("Expected ServerStartException for invalid keystore");
        } catch (ServerStartException e) {
            assertEquals(ServerStartException.INVALID_TLS_CONFIG, e.getErrorCode());
            assertFalse(transportConfig.isRunning());
        }
    }

    @Test
    public void shouldFailWithTlsError_whenKeystorePathEmpty() {
        try {
            transportConfig.startServer(TEST_PORT + 22, TEST_BIND_ADDRESS,
                    true, "", "password");
            fail("Expected ServerStartException for empty keystore path");
        } catch (ServerStartException e) {
            assertEquals(ServerStartException.INVALID_TLS_CONFIG, e.getErrorCode());
            assertFalse(transportConfig.isRunning());
        }
    }

    @Test
    public void shouldFailWithTlsError_whenPasswordEmpty() throws Exception {
        // Create a real keystore file to pass the file-exists check
        String keystorePath = System.getProperty("java.io.tmpdir") + "/test-tls-nopw-" + System.nanoTime() + ".p12";
        CertificateGenerator.Result certResult = CertificateGenerator.generate(keystorePath);

        try {
            transportConfig.startServer(TEST_PORT + 23, TEST_BIND_ADDRESS,
                    true, certResult.keystorePath(), "");
            fail("Expected ServerStartException for empty password");
        } catch (ServerStartException e) {
            assertEquals(ServerStartException.INVALID_TLS_CONFIG, e.getErrorCode());
            assertFalse(transportConfig.isRunning());
        } finally {
            java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(keystorePath));
        }
    }

    @Test
    public void shouldStartWithoutTls_whenTlsDisabled() throws Exception {
        transportConfig.startServer(TEST_PORT + 24, TEST_BIND_ADDRESS,
                false, null, null);

        assertTrue(transportConfig.isRunning());
        assertFalse(transportConfig.isTlsEnabled());
    }

    @Test
    public void shouldReturnTlsDisabled_whenNotRunning() {
        assertFalse(transportConfig.isTlsEnabled());
    }

    // ---- Runtime keystore-load classification ----
    // validateTlsConfig only pre-flights filesystem facts (path/exists/readable/blank-password). A
    // keystore that EXISTS + is READABLE but cannot be LOADED (wrong password, corrupt bytes) fails
    // only inside jettyServer.start() SSL init — that failure must be classified INVALID_TLS_CONFIG,
    // not swept into the generic SERVER_START_FAILED bucket.
    // Port-offset map (avoid collisions): base TLS tests use +20..+24, these runtime keystore-load
    // tests use +25/+26, the resource-guardrail tests use +30..+34. (These two tests expect the
    // server NOT to start, so the port is never actually bound.)

    @Test
    public void shouldFailWithTlsError_whenKeystorePasswordWrong() throws Exception {
        // A valid keystore protected by one password, opened with a different (non-blank) one.
        String keystorePath = System.getProperty("java.io.tmpdir") + "/test-tls-wrongpw-" + System.nanoTime() + ".p12";
        CertificateGenerator.Result certResult = CertificateGenerator.generate(keystorePath);

        try {
            transportConfig.startServer(TEST_PORT + 25, TEST_BIND_ADDRESS,
                    true, certResult.keystorePath(), certResult.password() + "-not-the-password");
            fail("Expected ServerStartException for a wrong keystore password");
        } catch (ServerStartException e) {
            assertEquals(ServerStartException.INVALID_TLS_CONFIG, e.getErrorCode());
            assertFalse(transportConfig.isRunning());
        } finally {
            java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(keystorePath));
        }
    }

    @Test
    public void shouldFailWithTlsError_whenKeystoreFileCorrupt() throws Exception {
        // A readable, non-blank-named .p12 whose bytes are not a valid keystore — passes the
        // pre-flight checks, fails when SslContextFactory tries to load it.
        java.nio.file.Path keystore = java.nio.file.Path.of(
                System.getProperty("java.io.tmpdir"), "test-tls-corrupt-" + System.nanoTime() + ".p12");
        java.nio.file.Files.write(keystore, "not a real keystore".getBytes(StandardCharsets.UTF_8));

        try {
            transportConfig.startServer(TEST_PORT + 26, TEST_BIND_ADDRESS,
                    true, keystore.toString(), "anyPassword");
            fail("Expected ServerStartException for a corrupt keystore file");
        } catch (ServerStartException e) {
            assertEquals(ServerStartException.INVALID_TLS_CONFIG, e.getErrorCode());
            assertFalse(transportConfig.isRunning());
        } finally {
            java.nio.file.Files.deleteIfExists(keystore);
        }
    }

    // ---- Pure start-failure classifier (over-classification guard) ----
    // classifyStartFailure walks the FULL cause chain; tested directly with hand-built chains so the
    // nested-BindException case is deterministic (independent of how Jetty happens to wrap it).

    @Test
    public void shouldClassifyPortInUse_whenBindExceptionNestedDeepInChain() {
        Throwable nested = new RuntimeException("wrap",
                new java.io.IOException("io", new java.net.BindException("Address already in use")));
        assertEquals(ServerStartException.PORT_IN_USE,
                TransportConfig.classifyStartFailure(nested, false));
        // A bind conflict wins even on a TLS run (checked before the keystore branch).
        assertEquals(ServerStartException.PORT_IN_USE,
                TransportConfig.classifyStartFailure(nested, true));
    }

    @Test
    public void shouldClassifyPortInUse_whenBindExceptionIsRoot() {
        assertEquals(ServerStartException.PORT_IN_USE,
                TransportConfig.classifyStartFailure(new java.net.BindException("in use"), false));
    }

    @Test
    public void shouldClassifyTlsConfig_whenWrongPasswordChainUnderTls() {
        // Empirical wrong-password shape: IOException caused by UnrecoverableKeyException.
        Throwable wrongPw = new java.io.IOException("keystore password was incorrect",
                new java.security.UnrecoverableKeyException("failed to decrypt safe contents entry"));
        assertEquals(ServerStartException.INVALID_TLS_CONFIG,
                TransportConfig.classifyStartFailure(wrongPw, true));
    }

    @Test
    public void shouldClassifyTlsConfig_whenCorruptKeystoreChainUnderTls() {
        // Empirical corrupt-file shape: a bare EOFException, no JSSE-specific type.
        assertEquals(ServerStartException.INVALID_TLS_CONFIG,
                TransportConfig.classifyStartFailure(new java.io.EOFException(), true));
    }

    @Test
    public void shouldNotClassifyTlsConfig_whenKeystoreShapedFailureButTlsDisabled() {
        // The same IOException must NOT be called a TLS problem when TLS is off (no keystore loaded).
        assertEquals(ServerStartException.SERVER_START_FAILED,
                TransportConfig.classifyStartFailure(new java.io.EOFException(), false));
    }

    @Test
    public void shouldNotClassifyTlsConfig_whenNonKeystoreProgrammingErrorUnderTls() {
        // A RuntimeException with no IOException/GeneralSecurityException in the chain is NOT a
        // keystore failure even under TLS — it stays the generic code (over-classification guard).
        assertEquals(ServerStartException.SERVER_START_FAILED,
                TransportConfig.classifyStartFailure(new IllegalStateException("bug"), true));
    }

    @Test
    public void shouldClassifyGeneric_whenNullFailure() {
        assertEquals(ServerStartException.SERVER_START_FAILED,
                TransportConfig.classifyStartFailure(null, true));
        assertEquals(ServerStartException.SERVER_START_FAILED,
                TransportConfig.classifyStartFailure(null, false));
    }

    @Test
    public void shouldTerminate_whenCauseChainIsCyclic() {
        // A self-referential chain must not loop forever; the identity-visited set breaks the cycle.
        // Note: Throwable.initCause only forbids DIRECT self-causation (cause == this); it does not
        // walk the chain, so a.initCause(b) where b.getCause()==a succeeds and forms a real a->b->a
        // cycle. hasCauseOfType must return (not hang) — proven by this completing.
        Throwable a = new RuntimeException("a");
        Throwable b = new RuntimeException("b", a);
        a.initCause(b); // a -> b -> a cycle (initCause does NOT throw here)
        assertFalse(TransportConfig.hasCauseOfType(a, java.net.BindException.class));
    }

    @Test
    public void shouldStopAtDepthBound_whenTargetDeeperThanMaxCauseDepth() {
        // The depth bound (MAX_CAUSE_DEPTH) is a safety stop for pathological chains: a target buried
        // far deeper than any realistic Jetty wrapping (~2-4 levels) is intentionally NOT found, so a
        // runaway/forged chain can't cost an unbounded walk. Build a BindException 30 levels down.
        Throwable t = new java.net.BindException("deep");
        for (int i = 0; i < 30; i++) {
            t = new RuntimeException("layer " + i, t);
        }
        assertFalse("BindException beyond the depth bound must not be found",
                TransportConfig.hasCauseOfType(t, java.net.BindException.class));
        // Sanity: the SAME type shallow in the chain IS found (the bound is the only reason above).
        assertTrue(TransportConfig.hasCauseOfType(
                new RuntimeException("shallow", new java.net.BindException("x")),
                java.net.BindException.class));
    }

    // ---- Resource guardrail tests ----

    @Test
    public void shouldEnforceConfiguredIdleTimeout_whenServerStarted() throws Exception {
        transportConfig.startServer(TEST_PORT + 30, TEST_BIND_ADDRESS);

        assertEquals(TransportConfig.IDLE_TIMEOUT_MS, transportConfig.getIdleTimeout());
    }

    @Test
    public void shouldUseBoundedThreadPool_whenServerStarted() throws Exception {
        transportConfig.startServer(TEST_PORT + 31, TEST_BIND_ADDRESS);

        assertEquals(TransportConfig.MAX_THREADS, transportConfig.getMaxThreads());
    }

    @Test
    public void shouldReturnSentinelGuardrails_whenNotStarted() {
        assertEquals(-1L, transportConfig.getIdleTimeout());
        assertEquals(-1, transportConfig.getMaxThreads());
    }

    @Test
    public void shouldReturnSentinelGuardrails_afterServerStopped() throws Exception {
        transportConfig.startServer(TEST_PORT + 34, TEST_BIND_ADDRESS);
        transportConfig.stopServer();

        // Both accessors gate on isRunning(), so the sentinels stay consistent once stopped.
        assertEquals(-1L, transportConfig.getIdleTimeout());
        assertEquals(-1, transportConfig.getMaxThreads());
    }

    @Test
    public void shouldReject413_whenRequestBodyExceedsMax() throws Exception {
        int port = TEST_PORT + 32;
        transportConfig.startServer(port, TEST_BIND_ADDRESS);

        // Declare a Content-Length one byte over the cap. SizeLimitHandler rejects on the declared
        // length up-front (413) before reading the body, so no oversized payload is actually sent.
        long oversized = TransportConfig.MAX_REQUEST_BODY_BYTES + 1;
        int status = postStatus(port, "/mcp", oversized, new byte[0]);

        assertEquals(413, status);
    }

    @Test
    public void shouldNotReject413_whenRequestBodyUnderMax() throws Exception {
        int port = TEST_PORT + 33;
        transportConfig.startServer(port, TEST_BIND_ADDRESS);

        // A small, well-under-limit body must not trip the size guard. The MCP servlet may answer
        // with its own status (e.g. 400/406) for a non-protocol body — we only assert it is NOT 413.
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        int status = postStatus(port, "/mcp", body.length, body);

        assertNotEquals(413, status);
    }

    // ---- Helpers ----

    /**
     * Sends a raw HTTP/1.1 POST with an explicit {@code Content-Length} header and the given body
     * bytes, then returns the numeric HTTP status from the response status line. Uses a raw socket
     * (not HttpURLConnection) so the declared Content-Length can exceed the bytes actually written —
     * this exercises {@link TransportConfig}'s SizeLimitHandler up-front 413 without transmitting an
     * oversized payload. {@code Connection: close} keeps it single-shot.
     */
    private int postStatus(int port, String path, long contentLength, byte[] body) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(TEST_BIND_ADDRESS, port), 5000);
            socket.setSoTimeout(5000);

            String requestHead = "POST " + path + " HTTP/1.1\r\n"
                    + "Host: " + TEST_BIND_ADDRESS + ":" + port + "\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Content-Length: " + contentLength + "\r\n"
                    + "Connection: close\r\n"
                    + "\r\n";

            OutputStream out = socket.getOutputStream();
            out.write(requestHead.getBytes(StandardCharsets.US_ASCII));
            if (body.length > 0) {
                out.write(body);
            }
            out.flush();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            String statusLine;
            try {
                statusLine = reader.readLine(); // e.g. "HTTP/1.1 413 Payload Too Large"
            } catch (java.net.SocketTimeoutException e) {
                // Cleaner diagnostic than an opaque errored test if the server never responds.
                fail("Server did not send an HTTP status line within the read timeout: " + e.getMessage());
                return -1; // unreachable (fail() throws)
            }
            assertNotNull("No HTTP status line received", statusLine);
            String[] parts = statusLine.split(" ");
            assertTrue("Malformed status line: " + statusLine, parts.length >= 2);
            return Integer.parseInt(parts[1]);
        }
    }

    private McpServerFeatures.SyncResourceSpecification createResourceSpec(
            String uri, String name, String description) {
        McpSchema.Resource resource = McpSchema.Resource.builder()
                .uri(uri)
                .name(name)
                .description(description)
                .mimeType("text/markdown")
                .build();

        BiFunction<McpSyncServerExchange, McpSchema.ReadResourceRequest, McpSchema.ReadResourceResult> handler =
                (exchange, request) -> new McpSchema.ReadResourceResult(List.of(
                        new McpSchema.TextResourceContents(request.uri(), "text/markdown", "test")));

        return new McpServerFeatures.SyncResourceSpecification(resource, handler);
    }
}
