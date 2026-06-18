package net.vheerden.archi.mcp.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
import java.util.function.Supplier;

import org.eclipse.gef.commands.Command;
import org.junit.Test;

import net.vheerden.archi.mcp.model.exceptions.MutationException;

/**
 * Headless tests for the {@link ProposalBuilder} — the shared "stored request → fresh command +
 * preconditions" rebuilder. Verifies that a proposal's deferred rebuild
 * handle is re-invoked to produce a fresh {@link PreparedMutation}, and that a missing/incompatible target
 * surfaces as a clean {@link MutationException} (stale) — never an NPE or a raw exception.
 */
public class ProposalBuilderTest {

    private final ProposalBuilder builder = new ProposalBuilder();

    @Test
    public void shouldRebuildFreshCommand_fromDeferredHandle() {
        Command cmd = new NoOp("Update element: id-1");
        PendingProposal p = proposal(() -> new PreparedMutation<>(cmd, "entityDto", "id-1"));

        PreparedMutation<?> fresh = builder.rebuild(p);

        assertSame("the handle's command is returned", cmd, fresh.command());
        assertEquals("entityDto", fresh.entity());
    }

    @Test
    public void shouldReRunHandle_eachRebuild() {
        // Proves the rebuild RE-INVOKES the handle (fresh resolution), not a cached value.
        int[] calls = {0};
        PendingProposal p = proposal(() -> {
            calls[0]++;
            return new PreparedMutation<>(new NoOp("op"), "e", "id");
        });
        builder.rebuild(p);
        builder.rebuild(p);
        assertEquals("handle invoked on every rebuild", 2, calls[0]);
    }

    @Test
    public void shouldTranslateModelAccessException_toMutationException() {
        // A vanished/incompatible target makes the re-invoked prepareXxx throw; the builder must surface
        // it as a stale MutationException, NOT a raw RuntimeException or NPE.
        PendingProposal p = proposal(() -> {
            throw new RuntimeException("getObjectByID returned null for a deleted element");
        });
        try {
            builder.rebuild(p);
            fail("expected MutationException for an unresolvable target");
        } catch (MutationException e) {
            assertTrue("plain-language stale message",
                    e.getMessage().toLowerCase().contains("can no longer be applied"));
        }
    }

    @Test
    public void shouldPreserveMutationException_fromHandle() {
        MutationException original = new MutationException("Element exists but type changed");
        PendingProposal p = proposal(() -> {
            throw original;
        });
        try {
            builder.rebuild(p);
            fail("expected the original MutationException");
        } catch (MutationException e) {
            assertSame("a MutationException from the handle passes through unchanged", original, e);
        }
    }

    @Test
    public void shouldTreatNullCommand_asStale() {
        PendingProposal p = proposal(() -> new PreparedMutation<>(null, "e", "id"));
        try {
            builder.rebuild(p);
            fail("a null rebuilt command must surface as stale, not NPE downstream");
        } catch (MutationException e) {
            assertTrue(e.getMessage().toLowerCase().contains("can no longer be applied"));
        }
    }

    // ---- helpers ----

    private static PendingProposal proposal(Supplier<PreparedMutation<?>> rebuild) {
        return new PendingProposal("p-1", "update-element", "Update element: id-1",
                rebuild, StalenessCapture.EMPTY, "entityDto",
                null, null, "valid", Instant.now(), null, null);
    }

    private static final class NoOp extends Command {
        NoOp(String label) {
            super(label);
        }
        @Override public void execute() { /* no-op */ }
    }
}
