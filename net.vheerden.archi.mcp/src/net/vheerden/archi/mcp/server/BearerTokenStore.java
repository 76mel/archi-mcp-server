package net.vheerden.archi.mcp.server;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.equinox.security.storage.StorageException;

/**
 * Generates and persists the opt-in bearer-token secret in Equinox secure storage
 * (the token half of the auth audit finding).
 *
 * <p><b>Why secure storage, and why {@code getDefault()}.</b> The token is a secret; it must never
 * sit in the plain {@code IPreferenceStore} (where {@code grep} of {@code .metadata} would reveal
 * it). {@link SecurePreferencesFactory#getDefault()} returns the install's <em>single shared</em>
 * default secure storage — the SAME tree Archi itself uses (Archi's {@code com.archimatetool.editor}
 * already references this API). Our token is just one NODE ({@value #NODE_PATH}) in that shared tree,
 * so it reuses Archi's existing master password / OS-keychain unlock and the user is asked for <em>no
 * separate password and no second storage</em>. We deliberately never call
 * {@code open(customLocation, ...)} — that would spawn a separate storage with its own master
 * password (more friction). On macOS the {@code org.eclipse.equinox.security.macosx} fragment backs
 * the master password with the Keychain, so typically there is no prompt at all.</p>
 *
 * <p><b>Layering.</b> Mirrors {@link CertificateGenerator}: a {@code server/}-package helper the UI
 * imports (no architecture-boundary breach — {@code server/} has no UI dependency, and the UI
 * already imports {@code server.CertificateGenerator} and {@code server.OriginHostValidationHandler}).
 * Touches only JDK + the Equinox secure-storage API; no Jackson, no EMF, no SWT.</p>
 *
 * <p><b>Fail behaviour.</b> The enable <em>flag</em> is not a secret and lives in the plain
 * store ({@code McpPlugin.PREF_AUTH_TOKEN_ENABLED}); only the token secret lives here. When the
 * secure store is unavailable or unreadable (a null {@code getDefault()} in an odd headless context,
 * a {@link StorageException}, a locked store) these methods throw {@link BearerTokenStoreException}
 * rather than returning a misleading {@code null} — so callers can distinguish "no token set" from
 * "could not read the token" and fail closed (see {@code TransportConfig.startServer()}). The token
 * is <b>never logged</b> at any level, never put in an error message, never echoed to a client.</p>
 */
public final class BearerTokenStore {

    /**
     * Secure-storage node path for this plugin's secrets. A node is just a namespace in the shared
     * default storage — not a separate credential — so this adds zero password ceremony.
     */
    static final String NODE_PATH = "/net/vheerden/archi/mcp";

    /** Secure-storage key holding the bearer-token secret. */
    static final String KEY_TOKEN = "authToken";

    /** Token entropy: 32 bytes = 256 bits (security floor). */
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private BearerTokenStore() {
        // Utility class
    }

    /**
     * Generates a fresh bearer token: {@value #TOKEN_BYTES} bytes ({@value #TOKEN_BYTES}×8 = 256
     * bits) of {@link SecureRandom}, encoded URL-safe Base64 without padding so it pastes cleanly
     * into an {@code Authorization} header value and a JSON config without escaping.
     *
     * <p>Pure / JDK-only — unit-testable headlessly (length, charset, uniqueness).</p>
     *
     * @return a new URL-safe, unpadded Base64 token (43 chars for 256 bits)
     */
    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Reads the stored bearer token from secure storage.
     *
     * @return the token, or {@code null} if the store is reachable but no token has been set
     * @throws BearerTokenStoreException if secure storage is unavailable or cannot be read (so the
     *         caller can fail closed rather than mistake an unreadable store for "no token")
     */
    public static String getToken() {
        try {
            return node().get(KEY_TOKEN, null);
        } catch (StorageException e) {
            // Do NOT include the value in the message — only the failure.
            throw new BearerTokenStoreException("Failed to read bearer token from secure storage", e);
        }
    }

    /**
     * Persists (encrypted) the bearer token to secure storage and flushes it to disk so it is
     * durable immediately (even before the preference page's OK), then any later server start can
     * read it back.
     *
     * @param token the token to store (must be non-null/non-blank — callers generate via
     *              {@link #generate()})
     * @throws BearerTokenStoreException if secure storage is unavailable or the write/flush fails
     */
    public static void setToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Refusing to store a null/blank bearer token");
        }
        try {
            ISecurePreferences node = node();
            node.put(KEY_TOKEN, token, /* encrypt */ true);
            node.flush();
        } catch (StorageException | IOException e) {
            throw new BearerTokenStoreException("Failed to persist bearer token to secure storage", e);
        }
    }

    /**
     * Removes the stored bearer token. Used only on explicit user action; disabling auth does NOT
     * call this (re-enabling reuses the token).
     *
     * @throws BearerTokenStoreException if secure storage is unavailable or the flush fails
     */
    public static void clearToken() {
        try {
            ISecurePreferences node = node();
            node.remove(KEY_TOKEN);
            node.flush();
        } catch (IOException e) {
            throw new BearerTokenStoreException("Failed to clear bearer token from secure storage", e);
        }
    }

    /**
     * Resolves this plugin's secure-storage node from the shared default storage.
     *
     * @return the node under {@value #NODE_PATH}
     * @throws BearerTokenStoreException if the default secure storage is unavailable
     *         ({@code getDefault()} returned {@code null} — e.g. headless with no provider)
     */
    private static ISecurePreferences node() {
        ISecurePreferences root = SecurePreferencesFactory.getDefault();
        if (root == null) {
            throw new BearerTokenStoreException(
                    "Equinox secure storage is unavailable (SecurePreferencesFactory.getDefault() returned null)");
        }
        return root.node(NODE_PATH);
    }

    /**
     * Thrown when secure storage is unavailable or an I/O/storage operation fails. Unchecked so the
     * helper stays a drop-in for the UI and the server start path; the server translates it into a
     * fail-closed enforcement state, and the UI surfaces a clear error to the user. Never
     * carries the token value in its message.
     */
    public static class BearerTokenStoreException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public BearerTokenStoreException(String message) {
            super(message);
        }

        public BearerTokenStoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
