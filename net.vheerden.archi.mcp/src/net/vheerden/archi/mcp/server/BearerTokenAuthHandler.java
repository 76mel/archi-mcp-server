package net.vheerden.archi.mcp.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opt-in Jetty core {@link Handler.Wrapper} that requires an {@code Authorization: Bearer <token>}
 * header on every request, returning {@code 401} (constant-time token compare) before the request
 * reaches either MCP transport servlet (the token half of the auth audit finding).
 *
 * <p><b>Why this exists.</b> {@link OriginHostValidationHandler} only stops a <em>browser</em>
 * (DNS-rebinding) and only on a <em>loopback</em> bind; it does nothing against a non-browser local
 * process and nothing on a LAN bind. Bearer auth is the orthogonal control: a shared secret enforced
 * on <em>every</em> request regardless of bind. The two layers compose — Origin/Host (403,
 * browser/loopback) outer, token (401, everyone, every bind) inner.</p>
 *
 * <p><b>Opt-in / fail-closed semantics.</b> Auth ships OFF (no upgrade breakage). The
 * wrapper is {@code enforcing} <em>iff</em> it was constructed with a non-blank token; otherwise it
 * passes every request straight through (existing clients are unaffected). Once a user opts
 * in, the failure mode is fail-closed: if the token cannot be read from secure storage at start, the
 * server constructs this wrapper with an unmatchable random token (see
 * {@code TransportConfig.startServer()}), so every request is rejected rather than silently served
 * unauthenticated.</p>
 *
 * <p>Mirrors {@link OriginHostValidationHandler}: it lives in the {@code server/} package, touches
 * only Jetty/JDK types (never Jackson), and exposes a Jetty-free package-visible decision method
 * ({@link #isAuthorized(String)}) for unit testing. The 401 is emitted via {@link Response#writeError}
 * so the server's configured {@link JsonErrorHandler} renders a JSON-RPC error envelope identical in
 * shape to every other HTTP-level error (401 &rarr; 4xx &rarr; {@code -32600},
 * {@code data.httpStatus: 401}). A {@code WWW-Authenticate: Bearer} challenge is set per RFC 7235.</p>
 */
public class BearerTokenAuthHandler extends Handler.Wrapper {

    private static final Logger logger = LoggerFactory.getLogger(BearerTokenAuthHandler.class);

    /** RFC 7235 auth-scheme keyword plus its mandatory single space, matched case-insensitively. */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Whether token enforcement is active. True iff a non-blank token was supplied at construction
     * (auth enabled and a token set, or the fail-closed unmatchable token). When false, every
     * request passes through — the opt-in default.
     */
    private final boolean enforcing;

    /** The expected token. Held only when {@link #enforcing}; never logged. */
    private final String expectedToken;

    /**
     * @param wrapped the downstream handler (the servlet context holding both transports)
     * @param token   the expected bearer token, or {@code null}/blank to disable enforcement
     *                (pass-through). The 5-arg {@code TransportConfig.startServer} delegates with a
     *                {@code null} token so every existing/test caller stays auth-off.
     */
    public BearerTokenAuthHandler(Handler wrapped, String token) {
        super(wrapped);
        this.enforcing = token != null && !token.isBlank();
        this.expectedToken = enforcing ? token : null;

        if (enforcing) {
            // INFO, value never logged — only that enforcement is on.
            logger.info("Bearer-token authentication ENABLED: every request must present a valid "
                    + "Authorization: Bearer token (401 otherwise), on both /mcp/* and /sse/*.");
        }
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
        if (!enforcing) {
            return super.handle(request, response, callback);
        }

        String authHeader = request.getHeaders().get(HttpHeader.AUTHORIZATION);
        if (isAuthorized(authHeader)) {
            return super.handle(request, response, callback);
        }

        String uri = request.getHttpURI() != null ? request.getHttpURI().toString() : "<unknown>";
        // NEVER log the presented or expected token — only that a rejection happened, and where.
        logger.warn("Rejected request with missing/invalid bearer token (401): uri={}", uri);

        // RFC 7235 §4.1 / RFC 6750 §3: a 401 SHOULD carry a WWW-Authenticate challenge naming the
        // scheme and a realm. Set before writeError so it is present when the error handler commits.
        response.getHeaders().put(HttpHeader.WWW_AUTHENTICATE, "Bearer realm=\"MCP Server\"");

        // Route through the server's configured JsonErrorHandler so the 401 body is a JSON-RPC
        // envelope (401 -> 4xx -> -32600, data.httpStatus:401), consistent with all other errors.
        Response.writeError(request, response, callback, HttpStatus.UNAUTHORIZED_401,
                "Missing or invalid bearer token");
        return true;
    }

    /**
     * The token decision, Jetty-free for direct unit testing (mirrors
     * {@link OriginHostValidationHandler#isAllowed}).
     *
     * <p>Requires a case-insensitive {@code Bearer } scheme prefix (RFC 7235) followed by a non-blank
     * credential, compared to the stored token in {@linkplain #constantTimeEquals constant time}. The
     * credential itself is matched exactly (case-sensitive, full-length). When not enforcing (no
     * token configured), every header — including a missing one — is authorized.</p>
     *
     * @param authHeader the raw {@code Authorization} header value (may be null)
     * @return true if the request is authorized to reach the transport servlet
     */
    boolean isAuthorized(String authHeader) {
        if (!enforcing) {
            return true;
        }
        if (authHeader == null) {
            return false;
        }
        String header = authHeader.trim();
        if (header.length() < BEARER_PREFIX.length()) {
            return false;
        }
        // Scheme keyword is case-insensitive; the value after it is not.
        if (!header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return false;
        }
        String presented = header.substring(BEARER_PREFIX.length()).trim();
        if (presented.isEmpty()) {
            return false;
        }
        return constantTimeEquals(presented, expectedToken);
    }

    /**
     * Constant-time equality of two secrets. Both are SHA-256-digested to a fixed 32-byte
     * length and compared with {@link MessageDigest#isEqual} (constant-time since JDK 6u17), so
     * neither the value nor the length of the presented token leaks via timing. Never uses
     * {@code String.equals}/{@code ==}/{@code Arrays.equals} on the raw secret.
     */
    private static boolean constantTimeEquals(String presented, String expected) {
        byte[] a = sha256(presented);
        byte[] b = sha256(expected);
        return MessageDigest.isEqual(a, b);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated on every conformant JDK; this is unreachable.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** True if token enforcement is active for this wrapper. Package-visible for tests. */
    boolean isEnforcing() {
        return enforcing;
    }
}
