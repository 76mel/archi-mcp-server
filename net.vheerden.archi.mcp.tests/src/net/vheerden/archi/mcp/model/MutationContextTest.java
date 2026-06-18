package net.vheerden.archi.mcp.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.eclipse.gef.commands.Command;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link MutationContext} approval and proposal management.
 *
 * <p>MutationContext is package-private, so this test class resides in the
 * same package within the test fragment. Uses a StubCommand for proposal
 * construction.</p>
 */
public class MutationContextTest {

    private MutationContext context;

    @Before
    public void setUp() {
        context = new MutationContext();
    }

    // ---- Approval flag tests removed: the approval bit is no longer per-session ----
    // It is a single global, human-owned switch read via ApprovalModeProvider in
    // MutationDispatcher (see MutationDispatcherTest). MutationContext stores only proposals.

    // ---- Proposal storage tests ----

    @Test
    public void shouldStoreAndRetrieveProposal() {
        PendingProposal proposal = makeProposal("create-element", "Create Actor");
        String id = context.storeProposal(proposal);

        assertNotNull(id);
        assertTrue(id.startsWith("p-"));

        PendingProposal stored = context.getProposal(id);
        assertNotNull(stored);
        assertEquals(id, stored.proposalId());
        assertEquals("create-element", stored.tool());
        assertEquals("Create Actor", stored.description());
    }

    @Test
    public void shouldAssignIncrementingProposalIds() {
        String id1 = context.storeProposal(makeProposal("create-element", "desc1"));
        String id2 = context.storeProposal(makeProposal("create-element", "desc2"));
        String id3 = context.storeProposal(makeProposal("create-element", "desc3"));

        assertEquals("p-1", id1);
        assertEquals("p-2", id2);
        assertEquals("p-3", id3);
    }

    @Test
    public void shouldRemoveProposal() {
        String id = context.storeProposal(makeProposal("create-element", "desc"));

        PendingProposal removed = context.removeProposal(id);

        assertNotNull(removed);
        assertEquals(id, removed.proposalId());
        assertNull(context.getProposal(id));
    }

    @Test
    public void shouldReturnNullForNonExistentProposal() {
        assertNull(context.getProposal("p-999"));
        assertNull(context.removeProposal("p-999"));
    }

    @Test
    public void shouldListPendingProposals() {
        context.storeProposal(makeProposal("create-element", "desc1"));
        context.storeProposal(makeProposal("create-relationship", "desc2"));

        List<PendingProposal> pending = context.getPendingProposals();

        assertEquals(2, pending.size());
        assertEquals("create-element", pending.get(0).tool());
        assertEquals("create-relationship", pending.get(1).tool());
    }

    @Test
    public void shouldClearProposals() {
        context.storeProposal(makeProposal("create-element", "desc1"));
        context.storeProposal(makeProposal("create-element", "desc2"));
        assertEquals(2, context.getPendingCount());

        context.clearProposals();

        assertEquals(0, context.getPendingCount());
        assertTrue(context.getPendingProposals().isEmpty());
    }

    @Test
    public void shouldPreserveProposalsAcrossBatchReset() {
        context.storeProposal(makeProposal("create-element", "desc"));

        // Simulate batch cycle
        context.beginBatch("test batch");
        context.reset();

        // Pending proposals should survive batch commit/rollback
        assertEquals(1, context.getPendingCount());
    }

    @Test
    public void shouldThrowWhenMaxProposalsReached() {
        for (int i = 0; i < MutationContext.MAX_PENDING_PROPOSALS; i++) {
            context.storeProposal(makeProposal("create-element", "desc-" + i));
        }

        try {
            context.storeProposal(makeProposal("create-element", "one too many"));
            fail("Expected IllegalStateException for max proposals");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Maximum pending proposals reached"));
        }
    }

    // ---- batch intent ----

    @Test
    public void shouldRecordBatchIntent_distinctFromDescription_andClearOnReset() {
        assertNull(context.getBatchIntent());
        context.beginBatch("undo-history label", "Wire the fraud-check path into checkout");
        // Intent is kept separate from the undo-history description — never merged.
        assertEquals("Wire the fraud-check path into checkout", context.getBatchIntent());
        context.reset();
        assertNull("reset clears batch intent", context.getBatchIntent());
    }

    @Test
    public void shouldLeaveBatchIntentNull_whenBeginBatchHasNoIntent() {
        context.beginBatch("undo-history label");
        assertNull(context.getBatchIntent());
    }

    @Test
    public void shouldPreserveEffectAndIntent_whenStoringProposal() {
        PendingProposal p = new PendingProposal(
                null, "create-relationship", "Create ServingRelationship: id-src → id-tgt",
                new StubCommand("x"), "entity", null, Map.of(), "Valid", Instant.now(),
                "Create ServingRelationship: 'A' → 'B'", "Wire the fraud-check path");
        String id = context.storeProposal(p);

        PendingProposal stored = context.getProposal(id);
        assertEquals(id, stored.proposalId());
        assertEquals("Create ServingRelationship: 'A' → 'B'", stored.effectDescription());
        assertEquals("Wire the fraud-check path", stored.intent());
    }

    @Test
    public void shouldLeaveEffectAndIntentNull_forBackCompatProposal() {
        String id = context.storeProposal(makeProposal("create-element", "Create Foo"));
        PendingProposal stored = context.getProposal(id);
        assertNull(stored.effectDescription());
        assertNull(stored.intent());
    }

    // ---- TTL expiry predicate + sweep ----

    @Test
    public void shouldExpire_whenOlderThanTtl() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Instant now = created.plus(java.time.Duration.ofMinutes(31));
        assertTrue(MutationContext.isExpired(created, now, java.time.Duration.ofMinutes(30)));
    }

    @Test
    public void shouldNotExpire_whenWithinTtl() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Instant now = created.plus(java.time.Duration.ofMinutes(29));
        assertFalse(MutationContext.isExpired(created, now, java.time.Duration.ofMinutes(30)));
    }

    @Test
    public void shouldTreatNullCreatedAt_asNotExpired() {
        assertFalse(MutationContext.isExpired(null, Instant.now(), java.time.Duration.ofMinutes(30)));
    }

    @Test
    public void shouldSweepExpiredProposals_keepingFreshOnes() {
        Instant now = Instant.now();
        String oldId = context.storeProposal(
                makeProposalAt("create-element", "old", now.minus(java.time.Duration.ofHours(1))));
        String freshId = context.storeProposal(
                makeProposalAt("create-element", "fresh", now));

        List<String> swept = context.sweepExpired(now, java.time.Duration.ofMinutes(30));

        assertEquals("only the abandoned proposal is swept", List.of(oldId), swept);
        assertNull("expired proposal removed", context.getProposal(oldId));
        assertNotNull("fresh proposal retained", context.getProposal(freshId));
    }

    // ---- Helpers ----

    private PendingProposal makeProposal(String tool, String description) {
        return new PendingProposal(
                null, tool, description, new StubCommand(description),
                "entity", null, Map.of("key", "value"), "Valid", Instant.now());
    }

    private PendingProposal makeProposalAt(String tool, String description, Instant createdAt) {
        return new PendingProposal(
                null, tool, description, new StubCommand(description),
                "entity", null, Map.of("key", "value"), "Valid", createdAt);
    }

    private static class StubCommand extends Command {
        StubCommand(String label) {
            super(label);
        }

        @Override
        public void execute() {
            // no-op for testing
        }
    }
}
