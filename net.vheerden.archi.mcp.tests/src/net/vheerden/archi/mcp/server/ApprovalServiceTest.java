package net.vheerden.archi.mcp.server;

import static org.junit.Assert.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import net.vheerden.archi.mcp.model.ApprovalResult;
import net.vheerden.archi.mcp.model.MutationDispatcher;
import net.vheerden.archi.mcp.model.exceptions.MutationException;
import net.vheerden.archi.mcp.response.dto.ProposalDto;

/**
 * Tests for {@link ApprovalService}, the UI-callable approve/reject seam.
 *
 * <p>Proves the queue still drains without the removed {@code decide-mutation} MCP tool: a
 * non-MCP, human-side caller (here a test; in production the dock view) approves and rejects
 * through this service.</p>
 */
public class ApprovalServiceTest {

    private TestMutationDispatcher dispatcher;
    private ApprovalService service;

    @Before
    public void setUp() {
        dispatcher = new TestMutationDispatcher();
        service = new ApprovalService(dispatcher);
    }

    @Test
    public void shouldApply_whenApprovingPendingProposal() throws Exception {
        String id = storeProposal("create-element", "Create Node", "nodeDto");

        ApprovalResult result = service.approve("default", id);

        assertNotNull(result);
        assertEquals("nodeDto", result.entity());
        assertEquals("create-element", result.tool());
        assertEquals("Command applied to model", 1, dispatcher.dispatchedCommands.size());
        assertTrue("proposal consumed", dispatcher.getPendingProposalDtos("default").isEmpty());
    }

    @Test
    public void shouldDiscardWithNoModelChange_whenRejectingProposal() {
        String id = storeProposal("create-view", "Create view", "viewDto");

        ProposalDto rejected = service.reject("default", id);

        assertNotNull(rejected);
        assertEquals("create-view", rejected.tool());
        assertEquals("rejected", rejected.status());
        assertEquals("no command dispatched on reject", 0, dispatcher.dispatchedCommands.size());
        assertTrue(dispatcher.getPendingProposalDtos("default").isEmpty());
    }

    @Test
    public void shouldReturnNull_whenApprovingMissingProposal() throws Exception {
        assertNull(service.approve("default", "p-999"));
    }

    @Test
    public void shouldReturnNull_whenRejectingMissingProposal() {
        assertNull(service.reject("default", "p-999"));
    }

    @Test
    public void shouldListPending_forTheView() {
        storeProposal("create-element", "Create A", "a");
        storeProposal("create-element", "Create B", "b");

        List<ProposalDto> pending = service.listPending("default");
        assertEquals(2, pending.size());
    }

    @Test(expected = MutationException.class)
    public void shouldSurfaceStale_whenHeldCommandFailsToApply() throws Exception {
        dispatcher.failNextDispatch = true; // simulate a stale held Command
        String id = storeProposal("create-element", "Create Stale", "dto");

        service.approve("default", id); // expected to throw MutationException
    }

    // ---- helpers ----

    private String storeProposal(String tool, String description, Object entity) {
        return dispatcher.storeProposal("default", tool, description,
                new StubCommand(description), entity, null,
                Map.of(), "valid", Instant.now());
    }

    /** MutationDispatcher that records dispatches and avoids Display.syncExec. */
    private static class TestMutationDispatcher extends MutationDispatcher {
        final java.util.List<org.eclipse.gef.commands.Command> dispatchedCommands =
                new java.util.ArrayList<>();
        boolean failNextDispatch = false;

        TestMutationDispatcher() {
            super(() -> null);
        }

        @Override
        protected void dispatchCommand(org.eclipse.gef.commands.Command command)
                throws MutationException {
            if (failNextDispatch) {
                throw new MutationException("simulated stale command");
            }
            dispatchedCommands.add(command);
        }
    }

    private static class StubCommand extends org.eclipse.gef.commands.Command {
        StubCommand(String label) { super(label); }
        @Override public void execute() { /* no-op */ }
    }
}
