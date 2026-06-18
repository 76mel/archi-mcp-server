package net.vheerden.archi.mcp.server;

import static org.junit.Assert.*;

import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.junit.Assume;
import org.junit.Test;

/**
 * Tests for {@link KeystorePasswordStore} (the secure-storage helper for the keystore password).
 *
 * <p><b>Pure-vs-PDE split (mirrors {@code BearerTokenStoreTest}).</b> Only the input-validation guard
 * in {@link KeystorePasswordStore#set(String)} is exercised headlessly here — it rejects null/blank
 * BEFORE touching secure storage, so it needs no provider and keeps this class contributing
 * executed tests in the headless {@code ci-junit} lane (the {@code testsRun > 0} guard). The
 * secure-storage round-trip ({@code set}/{@code get}/{@code clear}) needs the Equinox secure-storage
 * provider and its master-password unlock, which is unavailable in a plain headless JVM
 * ({@code SecurePreferencesFactory.getDefault()} returns null) — those tests {@link Assume} the
 * provider is present and are validated in the PDE {@code AllPluginTestsRunner} lane and the
 * live {@code /mcp reconnect} round-trip. This class is therefore NOT in
 * {@code tools/osgi-excluded-tests.txt} and is display-free.</p>
 */
public class KeystorePasswordStoreTest {

    private static final String SAMPLE = "s3cr3t-keystore-pw";

    // ---- Headless: pure input-validation guard (no secure-storage provider needed) ----

    /** A null password is rejected before any storage access (fail fast, no provider required). */
    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNull_whenSetting() {
        KeystorePasswordStore.set(null);
    }

    /** A blank password is rejected before any storage access. */
    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectBlank_whenSetting() {
        KeystorePasswordStore.set("   ");
    }

    // ---- PDE / manual: secure-storage round-trip (needs the provider; Assume-guarded) ----

    /**
     * Round-trip: a stored password reads back identically, and {@code clear()} removes it. Runs only
     * where the Equinox secure-storage provider is available (Eclipse PDE).
     */
    @Test
    public void shouldRoundTripAndClear_whenProviderAvailable() {
        // Headlessly the provider is absent: SecurePreferencesFactory.getDefault() THROWS (the internal
        // AuthPlugin is uninitialised outside OSGi) rather than returning null — treat either as a skip.
        ISecurePreferences root;
        try {
            root = SecurePreferencesFactory.getDefault();
        } catch (Throwable providerUnavailable) {
            Assume.assumeNoException("Equinox secure-storage provider unavailable (headless); "
                    + "round-trip runs under the Eclipse PDE build", providerUnavailable);
            return; // unreachable: assumeNoException aborts the test as skipped
        }
        Assume.assumeNotNull(root);

        try {
            KeystorePasswordStore.set(SAMPLE);
            assertEquals("stored password must read back identically", SAMPLE, KeystorePasswordStore.get());
        } finally {
            KeystorePasswordStore.clear();
        }
        assertNull("after clear(), the keystore password must be absent", KeystorePasswordStore.get());
    }

    /** Distinct key: the keystore-password key must never collide with the bearer-token key. */
    @Test
    public void shouldUseKeyDistinctFromBearerToken() {
        assertNotEquals("keystore-password key must differ from the bearer-token key",
                BearerTokenStore.KEY_TOKEN, KeystorePasswordStore.KEY_KEYSTORE_PASSWORD);
        assertEquals("must share the same secure-storage node as the bearer token",
                BearerTokenStore.NODE_PATH, KeystorePasswordStore.NODE_PATH);
    }
}
