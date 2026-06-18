package net.vheerden.archi.mcp.model;

import java.util.Objects;

import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CompoundCommand;

/**
 * Inert single-child wrapper that stamps a command as {@link AgentAuthoredCommand}
 * at admission.
 *
 * <p>Extends the <strong>plain</strong> GEF {@link CompoundCommand} — deliberately
 * <em>not</em> Archi's {@code NonNotifyingCompoundCommand}, which overrides
 * {@code execute/undo/redo} to fire {@code IEditorModelManager} {@code ECORE_EVENTS_START/END}
 * notifications. Extending that would inject notification-suppression around every single
 * immediate op (a behaviour change for every mutation). Plain {@code CompoundCommand} with a
 * single child is behaviourally transparent: {@code execute/undo/redo/canExecute/canUndo} all
 * delegate to the one child.</p>
 *
 * <p>It wraps <strong>exactly one</strong> delegate command and represents <strong>one</strong>
 * stack entry, preserving the {@code 1 dispatch == 1 stack entry} invariant the speculative
 * undo/redo arithmetic in {@code ArchiModelAccessorImpl} depends on. {@link #getLabel()} returns
 * the delegate's label so the undo/redo menu and the MCP tool labels are unchanged.</p>
 */
public final class AgentAuthoredCompoundCommand extends CompoundCommand implements AgentAuthoredCommand {

	private final Command delegate;

	/**
	 * Wraps {@code delegate} as the single child of this compound.
	 *
	 * @param delegate the agent-authored command to tag and execute (must not be null)
	 */
	public AgentAuthoredCompoundCommand(Command delegate) {
		// Seed the CompoundCommand's own label with the delegate's for GEF debug/toString; the
		// authoritative label is always the dynamic getLabel() override below (so it stays correct
		// even if the delegate's label changes after construction).
		super(Objects.requireNonNull(delegate, "delegate must not be null").getLabel());
		this.delegate = delegate;
		add(delegate);
	}

	/**
	 * @return the single wrapped command (the agent's actual mutation)
	 */
	public Command getDelegate() {
		return delegate;
	}

	/**
	 * Returns the delegate's label so undo/redo menus and MCP tool labels are unchanged
	 * (read dynamically rather than relying on the label captured at construction).
	 */
	@Override
	public String getLabel() {
		return delegate.getLabel();
	}
}
