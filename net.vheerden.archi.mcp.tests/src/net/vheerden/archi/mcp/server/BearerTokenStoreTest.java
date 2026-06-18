package net.vheerden.archi.mcp.server;

import static org.junit.Assert.*;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

/**
 * Tests for {@link BearerTokenStore} (the token half of the loopback-exposure hardening).
 *
 * <p>Only the pure, JDK-only {@link BearerTokenStore#generate()} is exercised headlessly here
 * (length / charset / uniqueness). The secure-storage round-trip
 * ({@code setToken}/{@code getToken}/{@code clearToken}) needs the Equinox secure-storage provider
 * and its master-password unlock, which is unavailable in a plain headless JVM
 * ({@code SecurePreferencesFactory.getDefault()} returns null) — that round-trip is validated in the
 * PDE {@code AllPluginTestsRunner} lane and the live {@code /mcp reconnect} round-trip, per
 * the documented pure-vs-PDE split. This class therefore stays in the headless {@code ci-junit} lane
 * (NOT in {@code tools/osgi-excluded-tests.txt}).</p>
 */
public class BearerTokenStoreTest {

    /** The token decodes to at least 256 bits (32 bytes) of entropy. */
    @Test
    public void shouldGenerateAtLeast256Bits_whenGenerating() {
        byte[] decoded = Base64.getUrlDecoder().decode(BearerTokenStore.generate());
        assertTrue("token must carry >= 256 bits (32 bytes) of entropy, was " + (decoded.length * 8) + " bits",
                decoded.length >= 32);
    }

    /** URL-safe Base64 WITHOUT padding — pastes cleanly into headers/JSON with no escaping. */
    @Test
    public void shouldUseUrlSafeBase64WithoutPadding_whenGenerating() {
        String token = BearerTokenStore.generate();
        assertFalse("token must not be blank", token.isBlank());
        // URL-safe alphabet only: A-Z a-z 0-9 - _ ; never '+', '/', or '=' padding.
        assertTrue("token must be URL-safe base64 (no +,/,=): " + token,
                token.matches("[A-Za-z0-9_-]+"));
        assertFalse("token must not contain '+'", token.contains("+"));
        assertFalse("token must not contain '/'", token.contains("/"));
        assertFalse("token must not be padded with '='", token.contains("="));
    }

    /** Two generations differ — SecureRandom, not a fixed/derived value. */
    @Test
    public void shouldProduceUniqueTokens_whenGeneratingRepeatedly() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            tokens.add(BearerTokenStore.generate());
        }
        assertEquals("100 generations must all be distinct", 100, tokens.size());
    }
}
