package net.vheerden.archi.mcp.model;

/**
 * Read-only source of the global human-in-the-loop approval bit, injected into
 * {@link MutationDispatcher} (control-plane → human-only).
 *
 * <p>This is a plain functional interface with <strong>no setter and no Eclipse,
 * MCP, GEF, or SWT imports</strong>, so the {@code model/} layer stays free of
 * the preference API. Production wiring (server/UI bootstrap) supplies a lambda
 * that reads the human-owned {@code ApprovalMode} holder; tests supply a simple
 * constant ({@code () -> true} / {@code () -> false}).</p>
 *
 * <p><strong>Why read-only:</strong> the agent operates tools; the human owns the
 * gate. The only robust guard against the agent moving the gate is that the gate's
 * setter does not exist on any agent-reachable path — so the dispatcher consults
 * this provider but can never flip it.</p>
 */
@FunctionalInterface
public interface ApprovalModeProvider {

    /**
     * @return {@code true} if approval mode is on (mutations become proposals
     *         awaiting human approval), {@code false} if mutations apply immediately
     */
    boolean isApprovalModeOn();
}
