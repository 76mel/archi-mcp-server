package net.vheerden.archi.mcp.model;

/**
 * Minimal mutation-command interface the {@link SpacingControlLoop} drives
 * during iteration. Kept EMF-free so the loop is pure-unit testable.
 *
 * <p>The accessor's
 * {@link SpacingControlLoop.Callbacks#buildMutationCommand(int)} closure
 * adapts the underlying GEF
 * {@code org.eclipse.gef.commands.Command} (or a {@code NonNotifyingCompoundCommand})
 * to this interface. The loop calls {@link #execute()} speculatively per
 * iteration, calls {@link #observeLayout()} via the callbacks, and either
 * accepts (keeps the command in the accepted-list) or reverts (calls
 * {@link #undo()} on the regressing command + discards it).</p>
 *
 * <p>After the loop terminates, the accessor is responsible for: (a) resetting
 * the model to pre-loop state if any iterations were accepted (the loop does
 * this automatically by calling {@link #undo()} on accepted commands in
 * reverse order before returning); (b) building a single
 * {@code NonNotifyingCompoundCommand} wrapping the accepted commands; (c)
 * pushing that compound through the public command stack so the stack records
 * exactly ONE undo entry per AC-6.</p>
 *
 * <p><strong>Contract:</strong> {@link #undo()} MUST perfectly reverse
 * {@link #execute()}'s mutation. Standard GEF {@code Command} contract; the
 * accessor's
 * {@code buildAdjustViewSpacingCommand(...)} helper inherits this from
 * {@code adjustViewSpacing(...)}'s existing command construction.</p>
 */
public interface SpacingMutationCommand {

    /** Apply the mutation. Must be reversible by a subsequent {@link #undo()}. */
    void execute();

    /** Reverse the most recent {@link #execute()} call. */
    void undo();
}
