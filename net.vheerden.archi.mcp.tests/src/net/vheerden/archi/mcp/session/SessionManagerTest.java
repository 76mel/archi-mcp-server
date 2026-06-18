package net.vheerden.archi.mcp.session;

import static org.junit.Assert.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link SessionManager}.
 *
 * <p>Pure Java — no MCP SDK or OSGi runtime required.</p>
 */
public class SessionManagerTest {

    private static final Set<String> VALID_TYPES = Set.of(
            "ApplicationComponent", "BusinessProcess", "Node");
    private static final Set<String> VALID_LAYERS = Set.of(
            "Business", "Application", "Technology");

    private SessionManager sessionManager;

    @Before
    public void setUp() {
        sessionManager = new SessionManager(VALID_TYPES, VALID_LAYERS);
    }

    // ---- Task 10.1 ----
    @Test
    public void shouldStoreSessionFilter_whenTypeProvided() {
        SessionManager.SessionState state = sessionManager.setSessionFilter(
                "session-1", "ApplicationComponent", null);

        assertNotNull(state);
        assertEquals("ApplicationComponent", state.typeFilter());
        assertNull(state.layerFilter());
        assertNotNull(state.lastAccessed());
    }

    // ---- Task 10.2 ----
    @Test
    public void shouldStoreSessionFilter_whenLayerProvided() {
        SessionManager.SessionState state = sessionManager.setSessionFilter(
                "session-1", null, "Application");

        assertNotNull(state);
        assertNull(state.typeFilter());
        assertEquals("Application", state.layerFilter());
    }

    // ---- Task 10.3 ----
    @Test
    public void shouldStoreSessionFilter_whenBothTypeAndLayer() {
        SessionManager.SessionState state = sessionManager.setSessionFilter(
                "session-1", "BusinessProcess", "Business");

        assertNotNull(state);
        assertEquals("BusinessProcess", state.typeFilter());
        assertEquals("Business", state.layerFilter());
    }

    // ---- Task 10.4 ----
    @Test
    public void shouldClearSessionFilter_whenClearCalled() {
        sessionManager.setSessionFilter("session-1", "ApplicationComponent", "Application");
        sessionManager.clearSessionFilter("session-1");

        Optional<SessionManager.SessionState> result = sessionManager.getSessionFilter("session-1");
        assertFalse(result.isPresent());
    }

    // ---- Task 10.5 ----
    @Test
    public void shouldReturnEmpty_whenNoFilterSet() {
        Optional<SessionManager.SessionState> result = sessionManager.getSessionFilter("unknown-session");
        assertFalse(result.isPresent());
    }

    // ---- Task 10.6 ----
    @Test
    public void shouldUpdateExistingFilter_whenSetCalledAgain() {
        sessionManager.setSessionFilter("session-1", "ApplicationComponent", "Application");
        SessionManager.SessionState updated = sessionManager.setSessionFilter(
                "session-1", "BusinessProcess", null);

        // Type updated, layer preserved from previous
        assertEquals("BusinessProcess", updated.typeFilter());
        assertEquals("Application", updated.layerFilter());
    }

    // ---- Task 10.7 ----
    @Test
    public void shouldMaintainSeparateSessions_whenDifferentSessionIds() {
        sessionManager.setSessionFilter("session-A", "ApplicationComponent", null);
        sessionManager.setSessionFilter("session-B", "BusinessProcess", "Business");

        Optional<SessionManager.SessionState> stateA = sessionManager.getSessionFilter("session-A");
        Optional<SessionManager.SessionState> stateB = sessionManager.getSessionFilter("session-B");

        assertTrue(stateA.isPresent());
        assertEquals("ApplicationComponent", stateA.get().typeFilter());
        assertNull(stateA.get().layerFilter());

        assertTrue(stateB.isPresent());
        assertEquals("BusinessProcess", stateB.get().typeFilter());
        assertEquals("Business", stateB.get().layerFilter());
    }

    // ---- Task 10.8 ----
    @Test
    public void shouldReturnPerQueryType_whenBothSessionAndPerQueryProvided() {
        sessionManager.setSessionFilter("session-1", "ApplicationComponent", null);

        String effective = sessionManager.getEffectiveType("session-1", "BusinessProcess");
        assertEquals("BusinessProcess", effective);
    }

    // ---- Task 10.9 ----
    @Test
    public void shouldReturnSessionType_whenPerQueryIsNull() {
        sessionManager.setSessionFilter("session-1", "ApplicationComponent", null);

        String effective = sessionManager.getEffectiveType("session-1", null);
        assertEquals("ApplicationComponent", effective);
    }

    // ---- Task 10.10 ----
    @Test
    public void shouldReturnNull_whenNeitherSessionNorPerQueryProvided() {
        String effective = sessionManager.getEffectiveType("no-such-session", null);
        assertNull(effective);
    }

    // ---- Task 10.8/10.9 for Layer ----
    @Test
    public void shouldReturnPerQueryLayer_whenBothSessionAndPerQueryProvided() {
        sessionManager.setSessionFilter("session-1", null, "Application");

        String effective = sessionManager.getEffectiveLayer("session-1", "Technology");
        assertEquals("Technology", effective);
    }

    @Test
    public void shouldReturnSessionLayer_whenPerQueryIsNull() {
        sessionManager.setSessionFilter("session-1", null, "Application");

        String effective = sessionManager.getEffectiveLayer("session-1", null);
        assertEquals("Application", effective);
    }

    @Test
    public void shouldReturnNullLayer_whenNeitherSessionNorPerQueryProvided() {
        String effective = sessionManager.getEffectiveLayer("no-such-session", null);
        assertNull(effective);
    }

    // ---- Task 10.11 ----
    @Test
    public void shouldClearAllSessions_whenModelChanges() {
        sessionManager.setSessionFilter("session-A", "ApplicationComponent", null);
        sessionManager.setSessionFilter("session-B", "BusinessProcess", "Business");

        sessionManager.onModelChanged("New Model", "model-id-123");

        assertFalse(sessionManager.getSessionFilter("session-A").isPresent());
        assertFalse(sessionManager.getSessionFilter("session-B").isPresent());
    }

    // ---- Task 10.12 ----
    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectInvalidType_whenSettingFilter() {
        sessionManager.setSessionFilter("session-1", "FakeType", null);
    }

    // ---- Task 10.13 ----
    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectInvalidLayer_whenSettingFilter() {
        sessionManager.setSessionFilter("session-1", null, "FakeLayer");
    }

    // ---- Task 10.14 ----
    @Test
    public void shouldHandleNullSessionId_gracefully() {
        // null session ID should use default session
        sessionManager.setSessionFilter(null, "ApplicationComponent", null);

        Optional<SessionManager.SessionState> state = sessionManager.getSessionFilter(null);
        assertTrue(state.isPresent());
        assertEquals("ApplicationComponent", state.get().typeFilter());

        String effective = sessionManager.getEffectiveType(null, null);
        assertEquals("ApplicationComponent", effective);
    }

    // ---- extractSessionId tests ----
    @Test
    public void shouldReturnDefaultSessionId_whenExchangeIsNull() {
        assertEquals(SessionManager.DEFAULT_SESSION_ID,
                SessionManager.extractSessionId(null));
    }

    // ---- dispose test ----
    @Test
    public void shouldClearAllSessions_whenDisposed() {
        sessionManager.setSessionFilter("session-1", "ApplicationComponent", null);
        sessionManager.dispose();
        assertFalse(sessionManager.getSessionFilter("session-1").isPresent());
    }

    // ---- Field selection extension tests ----

    @Test
    public void shouldStoreFieldsPreset_whenFieldsProvided() {
        SessionManager.SessionState state = sessionManager.setSessionFilter(
                "session-1", null, null, "minimal", null);

        assertNotNull(state);
        assertEquals("minimal", state.fieldsPreset());
        assertNull(state.excludeFields());
    }

    @Test
    public void shouldStoreExcludeFields_whenExcludeProvided() {
        SessionManager.SessionState state = sessionManager.setSessionFilter(
                "session-1", null, null, null, Set.of("documentation", "properties"));

        assertNotNull(state);
        assertNull(state.fieldsPreset());
        assertNotNull(state.excludeFields());
        assertTrue(state.excludeFields().contains("documentation"));
        assertTrue(state.excludeFields().contains("properties"));
    }

    @Test
    public void shouldClearFieldPreferences_whenClearCalled() {
        sessionManager.setSessionFilter("session-1", "ApplicationComponent", null, "minimal",
                Set.of("documentation"));
        sessionManager.clearSessionFilter("session-1");

        Optional<SessionManager.SessionState> result = sessionManager.getSessionFilter("session-1");
        assertFalse(result.isPresent());
    }

    @Test
    public void shouldReturnPerQueryPreset_whenBothSessionAndPerQueryProvided() {
        sessionManager.setSessionFilter("session-1", null, null, "minimal", null);

        String effective = sessionManager.getEffectiveFieldsPreset("session-1", "full");
        assertEquals("full", effective);
    }

    @Test
    public void shouldReturnSessionPreset_whenPerQueryIsNull() {
        sessionManager.setSessionFilter("session-1", null, null, "minimal", null);

        String effective = sessionManager.getEffectiveFieldsPreset("session-1", null);
        assertEquals("minimal", effective);
    }

    @Test
    public void shouldReturnPerQueryExclude_whenBothSessionAndPerQueryProvided() {
        sessionManager.setSessionFilter("session-1", null, null, null,
                Set.of("documentation"));

        Set<String> effective = sessionManager.getEffectiveExcludeFields(
                "session-1", List.of("properties"));
        assertEquals(Set.of("properties"), effective); // Full replacement, not merge
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectInvalidFieldsPreset_whenInvalidValue() {
        sessionManager.setSessionFilter("session-1", null, null, "compact", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectInvalidExcludeField_whenInvalidFieldName() {
        sessionManager.setSessionFilter("session-1", null, null, null,
                Set.of("invalidField"));
    }

    @Test
    public void shouldPreserveExistingTypeLayerFilters_whenSettingFieldPreferences() {
        sessionManager.setSessionFilter("session-1", "ApplicationComponent", "Application");
        sessionManager.setSessionFilter("session-1", null, null, "minimal",
                Set.of("documentation"));

        Optional<SessionManager.SessionState> state = sessionManager.getSessionFilter("session-1");
        assertTrue(state.isPresent());
        assertEquals("ApplicationComponent", state.get().typeFilter());
        assertEquals("Application", state.get().layerFilter());
        assertEquals("minimal", state.get().fieldsPreset());
        assertTrue(state.get().excludeFields().contains("documentation"));
    }

    @Test
    public void shouldReturnNullPreset_whenNoSessionFieldsSet() {
        String effective = sessionManager.getEffectiveFieldsPreset("no-such-session", null);
        assertNull(effective);
    }

    @Test
    public void shouldReturnNullExclude_whenNoSessionExcludeSet() {
        Set<String> effective = sessionManager.getEffectiveExcludeFields("no-such-session", null);
        assertNull(effective);
    }

    // ---- Model version tracking integration tests ----

    @Test
    public void shouldReturnFalse_whenFirstVersionCheck() {
        boolean changed = sessionManager.checkModelVersionChanged("session-1", "1");
        assertFalse("First version check should return false", changed);
    }

    @Test
    public void shouldReturnTrue_whenModelVersionChanges() {
        sessionManager.checkModelVersionChanged("session-1", "1");
        boolean changed = sessionManager.checkModelVersionChanged("session-1", "2");
        assertTrue("Changed version should return true", changed);
    }

    @Test
    public void shouldReturnFalse_whenModelVersionUnchanged() {
        sessionManager.checkModelVersionChanged("session-1", "5");
        boolean changed = sessionManager.checkModelVersionChanged("session-1", "5");
        assertFalse("Same version should return false", changed);
    }

    @Test
    public void shouldClearVersionTracking_whenModelChanges() {
        sessionManager.checkModelVersionChanged("session-1", "1");
        sessionManager.onModelChanged("New Model", "model-id-123");

        // After model change clearAll, first call returns false (no prior version)
        boolean changed = sessionManager.checkModelVersionChanged("session-1", "99");
        assertFalse("First check after model change should return false", changed);
    }

    @Test
    public void shouldClearVersionTracking_whenDisposed() {
        sessionManager.checkModelVersionChanged("session-1", "1");
        sessionManager.dispose();

        // After dispose, first call returns false
        boolean changed = sessionManager.checkModelVersionChanged("session-1", "99");
        assertFalse("First check after dispose should return false", changed);
    }

    // ---- Session caching integration tests ----

    @Test
    public void shouldReturnNull_whenNoCacheEntryExists() {
        assertNull(sessionManager.getCacheEntry("session-1", "search|query:test"));
    }

    @Test
    public void shouldStoreCacheEntry_andReturnOnGet() {
        sessionManager.putCacheEntry("session-1", "search|query:test", "{\"result\":\"data\"}");
        assertEquals("{\"result\":\"data\"}",
                sessionManager.getCacheEntry("session-1", "search|query:test"));
    }

    @Test
    public void shouldMaintainSeparateCachesPerSession() {
        sessionManager.putCacheEntry("session-A", "key-1", "data-A");
        sessionManager.putCacheEntry("session-B", "key-1", "data-B");

        assertEquals("data-A", sessionManager.getCacheEntry("session-A", "key-1"));
        assertEquals("data-B", sessionManager.getCacheEntry("session-B", "key-1"));
    }

    @Test
    public void shouldInvalidateSessionCache_whenCalled() {
        sessionManager.putCacheEntry("session-1", "key-1", "data-1");
        sessionManager.putCacheEntry("session-1", "key-2", "data-2");

        sessionManager.invalidateSessionCache("session-1");

        assertNull(sessionManager.getCacheEntry("session-1", "key-1"));
        assertNull(sessionManager.getCacheEntry("session-1", "key-2"));
    }

    @Test
    public void shouldNotAffectOtherSessions_whenOneInvalidated() {
        sessionManager.putCacheEntry("session-A", "key-1", "data-A");
        sessionManager.putCacheEntry("session-B", "key-1", "data-B");

        sessionManager.invalidateSessionCache("session-A");

        assertNull(sessionManager.getCacheEntry("session-A", "key-1"));
        assertEquals("data-B", sessionManager.getCacheEntry("session-B", "key-1"));
    }

    @Test
    public void shouldClearAllCaches_whenModelChanges() {
        sessionManager.putCacheEntry("session-A", "key-1", "data-A");
        sessionManager.putCacheEntry("session-B", "key-1", "data-B");

        sessionManager.onModelChanged("New Model", "model-id-123");

        assertNull(sessionManager.getCacheEntry("session-A", "key-1"));
        assertNull(sessionManager.getCacheEntry("session-B", "key-1"));
    }

    @Test
    public void shouldClearAllCaches_whenDisposed() {
        sessionManager.putCacheEntry("session-1", "key-1", "data-1");
        sessionManager.dispose();

        assertNull(sessionManager.getCacheEntry("session-1", "key-1"));
    }

    @Test
    public void shouldHandleNullSessionId_inCacheOperations() {
        sessionManager.putCacheEntry(null, "key-1", "data-1");
        assertEquals("data-1", sessionManager.getCacheEntry(null, "key-1"));
        sessionManager.invalidateSessionCache(null);
        assertNull(sessionManager.getCacheEntry(null, "key-1"));
    }

    @Test
    public void shouldNotThrow_whenInvalidatingNonexistentSession() {
        sessionManager.invalidateSessionCache("no-such-session");
        // No exception = pass
    }

    // ---- idle-TTL eviction ----

    @Test
    public void shouldEvictFromBothMaps_whenSessionIdleBeyondTtl() {
        // A session with BOTH a filter and a cache entry is fully removed when idle past the TTL.
        sessionManager.setSessionFilter("session-1", "ApplicationComponent", null);
        sessionManager.putCacheEntry("session-1", "key-1", "data-1");
        assertEquals(1, sessionManager.sessionStateCount());
        assertEquals(1, sessionManager.cachedSessionCount());
        assertEquals(1, sessionManager.trackedSessionCount());

        // Inject a clock 25h ahead so the 24h TTL is exceeded — no sleeping.
        List<String> evicted = sessionManager.sweepExpired(Instant.now().plus(Duration.ofHours(25)));

        assertEquals(List.of("session-1"), evicted);
        assertFalse(sessionManager.getSessionFilter("session-1").isPresent());
        assertNull(sessionManager.getCacheEntry("session-1", "key-1"));
        assertEquals(0, sessionManager.sessionStateCount());
        assertEquals(0, sessionManager.cachedSessionCount());
        assertEquals(0, sessionManager.trackedSessionCount());
    }

    @Test
    public void shouldSurvive_whenSessionAccessedWithinTtlWindow() {
        sessionManager.setSessionFilter("session-1", "ApplicationComponent", null);

        // 23h < 24h TTL — the session is still within its idle window.
        List<String> evicted = sessionManager.sweepExpired(Instant.now().plus(Duration.ofHours(23)));

        assertTrue(evicted.isEmpty());
        assertTrue(sessionManager.getSessionFilter("session-1").isPresent());
        assertEquals(1, sessionManager.trackedSessionCount());
    }

    @Test
    public void shouldEvictCacheOnlySession_whenIdleBeyondTtl() {
        // A session that only ever caches (never sets a filter) has no SessionState, yet is still
        // TTL-tracked via the unified last-access clock and evicted when idle.
        sessionManager.putCacheEntry("cache-only", "key-1", "data-1");
        assertEquals(0, sessionManager.sessionStateCount());
        assertEquals(1, sessionManager.cachedSessionCount());
        assertEquals(1, sessionManager.trackedSessionCount());

        List<String> evicted = sessionManager.sweepExpired(Instant.now().plus(Duration.ofHours(25)));

        assertEquals(List.of("cache-only"), evicted);
        assertNull(sessionManager.getCacheEntry("cache-only", "key-1"));
        assertEquals(0, sessionManager.cachedSessionCount());
        assertEquals(0, sessionManager.trackedSessionCount());
    }

    @Test
    public void shouldSurviveCacheOnlySession_whenAccessedWithinTtlWindow() {
        sessionManager.putCacheEntry("cache-only", "key-1", "data-1");

        List<String> evicted = sessionManager.sweepExpired(Instant.now().plus(Duration.ofHours(23)));

        assertTrue(evicted.isEmpty());
        assertEquals("data-1", sessionManager.getCacheEntry("cache-only", "key-1"));
    }

    @Test
    public void shouldNeverEvictDefaultSession_whenIdleBeyondTtl() {
        // The shared fallback (used when an exchange is null / carries no session id) is immune.
        sessionManager.setSessionFilter(null, "ApplicationComponent", null);
        sessionManager.putCacheEntry(null, "key-1", "data-1");

        List<String> evicted = sessionManager.sweepExpired(Instant.now().plus(Duration.ofHours(72)));

        assertTrue("default session must never be evicted", evicted.isEmpty());
        assertTrue(sessionManager.getSessionFilter(null).isPresent());
        assertEquals("data-1", sessionManager.getCacheEntry(null, "key-1"));
    }

    @Test
    public void shouldRefreshLastAccessOnRead_soActivelyQueriedSessionSurvives() {
        // The critical property: a session that is only ever *read* (getEffective*) — never re-set —
        // must NOT look idle. Proven deterministically with an injected clock: the read at t0+23h refreshes
        // last-access, so a sweep at t0+30h sees age 7h (<24h) and keeps it. WITHOUT the read-path touch the
        // entry would still read t0 and the same sweep would evict it (age 30h) — so this is not a tautology.
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        sessionManager.setClock(() -> t0);
        sessionManager.setSessionFilter("session-1", "ApplicationComponent", null);

        Instant t23 = t0.plus(Duration.ofHours(23));
        sessionManager.setClock(() -> t23);
        sessionManager.getEffectiveType("session-1", null); // pure read — refreshes last-access to t23

        List<String> evicted = sessionManager.sweepExpired(t0.plus(Duration.ofHours(30)));

        assertTrue("a read within the window refreshed last-access", evicted.isEmpty());
        assertTrue(sessionManager.getSessionFilter("session-1").isPresent());
    }

    @Test
    public void shouldEvictReadStaleSession_whenNotReadWithinWindow() {
        // The negative control for the test above: with NO intervening read, the same session created at t0
        // IS evicted by a sweep at t0+30h. Pins that the survival above is caused by the read, not the setup.
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        sessionManager.setClock(() -> t0);
        sessionManager.setSessionFilter("session-1", "ApplicationComponent", null);

        List<String> evicted = sessionManager.sweepExpired(t0.plus(Duration.ofHours(30)));

        assertEquals(List.of("session-1"), evicted);
    }

    @Test
    public void shouldDropLastAccess_whenClearSessionFilterAndNoCacheRemains() {
        // Lockstep: clearing the only state must fully reclaim the last-access clock entry (no orphan,
        // no later "idle-evicted" log for a session that already holds nothing).
        sessionManager.setSessionFilter("session-1", "ApplicationComponent", null);
        assertEquals(1, sessionManager.trackedSessionCount());

        sessionManager.clearSessionFilter("session-1");

        assertEquals(0, sessionManager.trackedSessionCount());
    }

    @Test
    public void shouldKeepLastAccess_whenClearSessionFilterButCacheRemains() {
        // A cache-only remnant must stay TTL-tracked — dropping the clock entry here would orphan the cache
        // from the sweep (which iterates lastAccess) and leak it forever.
        sessionManager.setSessionFilter("session-1", "ApplicationComponent", null);
        sessionManager.putCacheEntry("session-1", "key-1", "data-1");

        sessionManager.clearSessionFilter("session-1");

        assertEquals(1, sessionManager.trackedSessionCount());
        assertEquals(1, sessionManager.cachedSessionCount());
        // And it is still evictable on idle (the invariant the tracking preserves).
        assertEquals(List.of("session-1"),
                sessionManager.sweepExpired(Instant.now().plus(Duration.ofHours(25))));
    }

    @Test
    public void shouldDropLastAccess_whenInvalidateCacheAndNoFilterRemains() {
        sessionManager.putCacheEntry("session-1", "key-1", "data-1");
        assertEquals(1, sessionManager.trackedSessionCount());

        sessionManager.invalidateSessionCache("session-1");

        assertEquals(0, sessionManager.trackedSessionCount());
    }

    @Test
    public void shouldKeepLastAccess_whenInvalidateCacheButFilterRemains() {
        sessionManager.setSessionFilter("session-1", "ApplicationComponent", null);
        sessionManager.putCacheEntry("session-1", "key-1", "data-1");

        sessionManager.invalidateSessionCache("session-1");

        assertEquals(1, sessionManager.trackedSessionCount());
        assertEquals(1, sessionManager.sessionStateCount());
    }

    @Test
    public void shouldNotMintPhantomEntry_whenReadingUntrackedSession() {
        // A read against a session that never created state must not register a last-access key
        // (otherwise a read-only fabricated-id flood would grow the map between write-path sweeps).
        sessionManager.getEffectiveType("never-seen", null);
        sessionManager.getCacheEntry("never-seen", "key");
        sessionManager.getSessionFilter("never-seen");

        assertEquals(0, sessionManager.trackedSessionCount());
    }

    @Test
    public void shouldApplyStrictlyExceedsSemantics_inIdlePredicate() {
        // The pure predicate is the deterministic TTL core (mirrors MutationContext.isExpired).
        Instant now = Instant.now();
        Duration ttl = Duration.ofHours(24);

        assertFalse("fresh (now) is not idle", SessionManager.isIdle(now, now, ttl));
        assertFalse("exactly at the TTL is NOT idle (strictly-exceeds)",
                SessionManager.isIdle(now.minus(ttl), now, ttl));
        assertTrue("past the TTL is idle",
                SessionManager.isIdle(now.minus(Duration.ofHours(26)), now, ttl));
        assertFalse("null timestamp is defensively not-idle",
                SessionManager.isIdle(null, now, ttl));
        assertFalse("null now is defensively not-idle",
                SessionManager.isIdle(now.minus(Duration.ofHours(26)), null, ttl));
        assertFalse("null ttl is defensively not-idle",
                SessionManager.isIdle(now.minus(Duration.ofHours(26)), now, null));
    }
}
