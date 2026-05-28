package net.vheerden.archi.mcp.model;

import org.eclipse.gef.commands.Command;

import com.archimatetool.model.IDiagramModelContainer;
import com.archimatetool.model.IDiagramModelImage;

/**
 * GEF Command that adds a standalone image visual to a view (Story 14-8 / G16).
 *
 * <p>The image visual must be fully configured (imagePath, bounds, styling)
 * before this command is created. The command only handles view placement
 * and removal (undo). Mirrors {@link AddViewReferenceToViewCommand}.</p>
 *
 * <p><strong>CRITICAL:</strong> This command MUST be executed via
 * {@code CommandStack.execute()} through {@link MutationDispatcher}.
 * Direct invocation of {@code execute()} bypasses undo tracking.</p>
 */
public class AddImageToViewCommand extends Command {

    private final IDiagramModelImage image;
    private final IDiagramModelContainer parent;

    /**
     * Creates a command to add an image visual to a view or parent group.
     *
     * @param image  the fully-configured image visual to add
     * @param parent the target container (view or parent group)
     */
    public AddImageToViewCommand(IDiagramModelImage image,
            IDiagramModelContainer parent) {
        this.image = image;
        this.parent = parent;
        setLabel("Add image to view");
    }

    @Override
    public void execute() {
        parent.getChildren().add(image);
    }

    @Override
    public void undo() {
        parent.getChildren().remove(image);
    }

    /** Package-visible for testing. */
    IDiagramModelImage getImage() {
        return image;
    }

    /** Package-visible for testing. */
    IDiagramModelContainer getParent() {
        return parent;
    }
}
