package net.vheerden.archi.mcp.model;

import org.eclipse.gef.commands.Command;

import com.archimatetool.model.IDiagramModelContainer;
import com.archimatetool.model.IDiagramModelReference;

/**
 * GEF Command that adds a view-reference visual object to a view.
 *
 * <p>The view-reference must be fully configured (referencedModel, bounds,
 * styling) before this command is created. The command only handles view
 * placement and removal (undo).</p>
 *
 * <p><strong>CRITICAL:</strong> This command MUST be executed via
 * {@code CommandStack.execute()} through {@link MutationDispatcher}.
 * Direct invocation of {@code execute()} bypasses undo tracking.</p>
 */
public class AddViewReferenceToViewCommand extends Command {

    private final IDiagramModelReference viewReference;
    private final IDiagramModelContainer parent;

    /**
     * Creates a command to add a view-reference to a view or parent group.
     *
     * @param viewReference the fully-configured view-reference to add
     * @param parent        the target container (view or parent group)
     */
    public AddViewReferenceToViewCommand(IDiagramModelReference viewReference,
            IDiagramModelContainer parent) {
        this.viewReference = viewReference;
        this.parent = parent;
        setLabel("Add view reference to view");
    }

    @Override
    public void execute() {
        parent.getChildren().add(viewReference);
    }

    @Override
    public void undo() {
        parent.getChildren().remove(viewReference);
    }

    /** Package-visible for testing. */
    IDiagramModelReference getViewReference() {
        return viewReference;
    }

    /** Package-visible for testing. */
    IDiagramModelContainer getParent() {
        return parent;
    }
}
