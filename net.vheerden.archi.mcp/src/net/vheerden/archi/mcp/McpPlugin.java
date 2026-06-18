package net.vheerden.archi.mcp;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vheerden.archi.mcp.server.KeystorePasswordStore;

/**
 * Plugin activator for the ArchiMate MCP Server.
 * Manages plugin lifecycle and provides access to plugin preferences.
 *
 * <p>This plugin uses lazy activation - it only activates when a class
 * from this bundle is first referenced.</p>
 */
public class McpPlugin extends AbstractUIPlugin {

    private static final Logger logger = LoggerFactory.getLogger(McpPlugin.class);

    /** The plugin ID matching Bundle-SymbolicName in MANIFEST.MF */
    public static final String PLUGIN_ID = "net.vheerden.archi.mcp";

    // Preference keys
    public static final String PREF_PORT = "mcp.server.port";
    public static final String PREF_BIND_ADDRESS = "mcp.server.bindAddress";
    public static final String PREF_AUTO_START = "mcp.server.autoStart";
    public static final String PREF_LOG_LEVEL = "mcp.server.logLevel";
    public static final String PREF_TLS_ENABLED = "mcp.server.tlsEnabled";
    public static final String PREF_KEYSTORE_PATH = "mcp.server.keystorePath";
    public static final String PREF_KEYSTORE_PASSWORD = "mcp.server.keystorePassword";
    /**
     * Whether opt-in bearer-token authentication is enabled. The
     * <em>flag</em> is not a secret, so it lives in the plain store; the token <em>secret</em> lives
     * in Equinox secure storage via {@link net.vheerden.archi.mcp.server.BearerTokenStore}.
     */
    public static final String PREF_AUTH_TOKEN_ENABLED = "mcp.server.authTokenEnabled";
    /**
     * The human's approval-mode setting (control plane → human). This is the durable
     * backing store for the effective runtime bit in {@link net.vheerden.archi.mcp.server.ApprovalMode},
     * which seeds from this value on start so the human's choice survives a restart;
     * it defaults to {@link #DEFAULT_APPROVAL_MODE} = GATED when never configured. The
     * <em>only</em> writer of this key is the SWT toggle handler, via {@code ApprovalMode.setOn(...)}.
     */
    public static final String PREF_APPROVAL_MODE = "mcp.server.approvalMode";

    // Default values
    public static final int DEFAULT_PORT = 18090;
    public static final String DEFAULT_BIND_ADDRESS = "127.0.0.1";
    public static final boolean DEFAULT_AUTO_START = false;
    public static final String DEFAULT_LOG_LEVEL = "INFO";
    public static final boolean DEFAULT_TLS_ENABLED = false;
    public static final String DEFAULT_KEYSTORE_PATH = "";
    public static final String DEFAULT_KEYSTORE_PASSWORD = "";
    /** Auth ships OFF (opt-in): zero behaviour change for existing configs. */
    public static final boolean DEFAULT_AUTH_TOKEN_ENABLED = false;
    /** Approval mode defaults ON (gated) on a fresh install: fail safe, not fail open. Thereafter the human's stored choice is restored on start. */
    public static final boolean DEFAULT_APPROVAL_MODE = true;

    /** Singleton instance */
    private static McpPlugin plugin;

    /**
     * Default constructor required by OSGi.
     */
    public McpPlugin() {
        // Required by OSGi
    }

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
        initializeDefaultPreferences();
        // Restore the human's approval-mode choice from preferences at this
        // guaranteed-safe point — plugin activated, defaults registered, store available.
        net.vheerden.archi.mcp.server.ApprovalMode.getInstance().restoreFromPreference();
        migrateKeystorePasswordIfNeeded();
        logger.info("ArchiMate MCP Server plugin started (version {})",
                    context.getBundle().getVersion());
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        logger.info("ArchiMate MCP Server plugin stopping");

        // Gracefully shut down the MCP server if running
        try {
            var manager = net.vheerden.archi.mcp.server.McpServerManager.getInstance();
            if (manager.isRunning()) {
                logger.info("Stopping MCP Server during plugin shutdown");
                manager.stop();
            }
        } catch (Exception e) {
            logger.error("Error stopping MCP Server during plugin shutdown", e);
        }

        // Dispose status indicator
        try {
            net.vheerden.archi.mcp.ui.McpStatusIndicator.dispose();
        } catch (Exception e) {
            logger.debug("Error disposing status indicator", e);
        }

        plugin = null;
        super.stop(context);
    }

    /**
     * Returns the singleton plugin instance.
     *
     * @return the plugin instance, or null if not yet activated
     */
    public static McpPlugin getDefault() {
        return plugin;
    }

    /**
     * Initializes default preference values.
     * Called once during plugin startup.
     */
    private void initializeDefaultPreferences() {
        var store = getPreferenceStore();
        store.setDefault(PREF_PORT, DEFAULT_PORT);
        store.setDefault(PREF_BIND_ADDRESS, DEFAULT_BIND_ADDRESS);
        store.setDefault(PREF_AUTO_START, DEFAULT_AUTO_START);
        store.setDefault(PREF_LOG_LEVEL, DEFAULT_LOG_LEVEL);
        store.setDefault(PREF_TLS_ENABLED, DEFAULT_TLS_ENABLED);
        store.setDefault(PREF_KEYSTORE_PATH, DEFAULT_KEYSTORE_PATH);
        store.setDefault(PREF_KEYSTORE_PASSWORD, DEFAULT_KEYSTORE_PASSWORD);
        store.setDefault(PREF_AUTH_TOKEN_ENABLED, DEFAULT_AUTH_TOKEN_ENABLED);
        store.setDefault(PREF_APPROVAL_MODE, DEFAULT_APPROVAL_MODE);
    }

    /**
     * Gets the configured MCP server port.
     *
     * @return the port number from preferences, or DEFAULT_PORT if not set
     */
    public int getServerPort() {
        return getPreferenceStore().getInt(PREF_PORT);
    }

    /**
     * Gets the configured network bind address.
     *
     * @return the bind address from preferences, or DEFAULT_BIND_ADDRESS if not set
     */
    public String getBindAddress() {
        return getPreferenceStore().getString(PREF_BIND_ADDRESS);
    }

    /**
     * Checks if auto-start is enabled.
     *
     * @return true if the server should start automatically on plugin activation
     */
    public boolean isAutoStartEnabled() {
        return getPreferenceStore().getBoolean(PREF_AUTO_START);
    }

    /**
     * Gets the configured log level.
     *
     * @return the log level string from preferences
     */
    public String getLogLevel() {
        return getPreferenceStore().getString(PREF_LOG_LEVEL);
    }

    /**
     * Checks if TLS is enabled.
     *
     * @return true if TLS/HTTPS should be used for the server
     */
    public boolean isTlsEnabled() {
        return getPreferenceStore().getBoolean(PREF_TLS_ENABLED);
    }

    /**
     * Gets the configured keystore file path.
     *
     * @return the keystore path from preferences, or empty string if not set
     */
    public String getKeystorePath() {
        return getPreferenceStore().getString(PREF_KEYSTORE_PATH);
    }

    /**
     * Gets the effective keystore password.
     *
     * <p>Reads the secret from Equinox secure storage
     * ({@link net.vheerden.archi.mcp.server.KeystorePasswordStore}) when present; otherwise falls back
     * to the legacy plain {@link #PREF_KEYSTORE_PASSWORD} preference. The legacy fallback is the
     * regression-safety crux: it keeps the change invisible to every existing config — a
     * not-yet-migrated config, or a failed-migration config on a machine with a broken keychain, still
     * starts TLS with the same effective password. If secure storage is present but unreadable
     * (a {@link net.vheerden.archi.mcp.server.KeystorePasswordStore.KeystorePasswordStoreException}),
     * this logs a WARN (no value) and falls back to legacy plain rather than crashing or regressing.</p>
     *
     * <p>Signature and return contract are unchanged: returns the effective password, or an
     * empty string if none is set anywhere. Only the source of the value changed.</p>
     *
     * @return the keystore password from secure storage, else the legacy preference, else empty string
     */
    public String getKeystorePassword() {
        try {
            String secure = KeystorePasswordStore.get();
            if (secure != null && !secure.isBlank()) {
                return secure;
            }
        } catch (RuntimeException e) {
            // Fail SAFE, not closed: an unreadable secure store must not regress a working TLS config.
            // Catch broadly (not just KeystorePasswordStoreException) so even an unexpected unchecked
            // throw from the provider — e.g. a bare SecurityException surfacing the macOS keychain
            // -25300 path, or a null getDefault() in an odd context — still falls back to the legacy
            // plain value instead of crashing TLS start. The value is never in the message.
            logger.warn("Could not read keystore password from secure storage; "
                    + "falling back to the legacy preference", e);
        }
        return getPreferenceStore().getString(PREF_KEYSTORE_PASSWORD);
    }

    /**
     * One-time, fail-safe migration of any legacy plaintext keystore password into Equinox secure
     * storage. Wires the production seams into the pure {@link #migrateKeystorePassword}
     * policy and guarantees it never throws out of {@link #start(BundleContext)} — a
     * secure-storage-unavailable start (e.g. headless) is a safe no-op that retries next launch.
     */
    private void migrateKeystorePasswordIfNeeded() {
        var store = getPreferenceStore();
        try {
            migrateKeystorePassword(
                    store.getString(PREF_KEYSTORE_PASSWORD),
                    KeystorePasswordStore::get,
                    KeystorePasswordStore::set,
                    // Clearing to the empty default removes the key from the on-disk plain store.
                    () -> store.setValue(PREF_KEYSTORE_PASSWORD, DEFAULT_KEYSTORE_PASSWORD));
        } catch (RuntimeException e) {
            // Defensive: migration must NEVER break plugin start. Leave config untouched, retry later.
            logger.warn("Keystore-password migration encountered an unexpected error; "
                    + "continuing startup (legacy value left in place)", e);
        }
    }

    /**
     * Pure, secure-storage-free, fail-safe migration policy, package-visible so the
     * <b>never-lose-data</b> property can be unit-tested without a live plugin or secure storage
     * (mirrors {@code TransportConfig.resolveAuthToken(boolean, Supplier)}). The seams abstract the
     * three side effects: read secure storage, write it, and clear the legacy plain value.
     *
     * <p><b>Invariant (the single most important correctness property):</b> the legacy
     * plain value is cleared ONLY after a confirmed durable secure write. On ANY failure — secure read
     * throws, secure write throws — the legacy value is left exactly as it was so the next start
     * retries, and nothing is ever written to plaintext anywhere new. Idempotent: a no-op when there is
     * nothing to migrate (legacy blank) or it already ran (secure storage already populated).</p>
     *
     * @param legacyPlain   the legacy plaintext value (may be null/blank → nothing to migrate)
     * @param secureReader  supplies the current secure-storage value (null if none; may throw)
     * @param secureWriter  persists the value to secure storage durably (may throw on failure)
     * @param legacyCleaner clears the legacy plain value — invoked ONLY after a durable write
     * @return {@code true} iff a value was migrated and the legacy value cleared; {@code false} for any
     *         no-op or failure
     */
    static boolean migrateKeystorePassword(String legacyPlain,
                                           Supplier<String> secureReader,
                                           Consumer<String> secureWriter,
                                           Runnable legacyCleaner) {
        if (legacyPlain == null || legacyPlain.isBlank()) {
            return false; // nothing to migrate
        }
        String existingSecure;
        try {
            existingSecure = secureReader.get();
        } catch (RuntimeException e) {
            // Could not read secure storage → do NOT migrate, do NOT clear. No value in message.
            logger.warn("Keystore-password migration skipped: secure storage is unreadable; "
                    + "leaving the legacy value in place for retry", e);
            return false;
        }
        if (existingSecure != null && !existingSecure.isBlank()) {
            return false; // already migrated / a secure value exists — never overwrite (idempotent)
        }
        try {
            secureWriter.accept(legacyPlain); // write + flush; throws on failure
        } catch (RuntimeException e) {
            // Write failed (e.g. errSecItemNotFound -25300, locked store) → keep the legacy value.
            logger.warn("Keystore-password migration failed to write to secure storage; "
                    + "leaving the legacy plaintext value in place for retry", e);
            return false;
        }
        // Durable write confirmed → clear the legacy plaintext value LAST. No value logged.
        // The secure write (the security goal) has already succeeded, so even if the clear throws we
        // do NOT treat the migration as failed — we log the residual cleartext accurately rather than
        // the misleading "left in place for retry" (the idempotency guard would no-op the retry).
        try {
            legacyCleaner.run();
        } catch (RuntimeException e) {
            logger.warn("Keystore password was migrated to secure storage, but clearing the legacy "
                    + "plaintext value failed; the plaintext entry may persist until cleared manually", e);
            return true;
        }
        logger.info("Migrated keystore password from plaintext preferences to Equinox secure storage");
        return true;
    }

    /**
     * Checks whether opt-in bearer-token authentication is enabled.
     *
     * <p>The token <em>secret</em> is read separately from secure storage via
     * {@link net.vheerden.archi.mcp.server.BearerTokenStore}; this getter only reflects the
     * plain-store enable flag (default {@link #DEFAULT_AUTH_TOKEN_ENABLED} = false).</p>
     *
     * @return true if the server should require an {@code Authorization: Bearer} token
     */
    public boolean isAuthTokenEnabled() {
        return getPreferenceStore().getBoolean(PREF_AUTH_TOKEN_ENABLED);
    }
}
