package net.vheerden.archi.mcp.model;

/**
 * Marker interface stamping a {@code CommandStack} entry as authored by the MCP
 * agent ("origin tagging").
 *
 * <p>A native GEF {@link org.eclipse.gef.commands.CommandStack} carries <strong>no
 * per-entry provenance</strong> — once a {@code Command} is on the stack there is no
 * way to recover who authored it. The only place authorship is knowable is at
 * <em>admission</em>: every agent mutation crosses {@link MutationDispatcher#dispatchImmediate}
 * on its way to {@code CommandStack.execute}, whereas human GUI edits reach Archi's
 * CommandStack directly and never touch our dispatcher. So we wrap each agent command
 * in an {@link AgentAuthoredCompoundCommand} (which implements this interface) at that
 * single chokepoint. Thereafter {@code stack.getUndoCommand() instanceof AgentAuthoredCommand}
 * is the authorship test that powers the scoped-undo "betrayal" guard and, later,
 * staleness messaging and V2 provenance.</p>
 *
 * <p>This is a pure marker — it declares no methods. Human-authored entries simply do
 * not implement it.</p>
 */
public interface AgentAuthoredCommand {
}
