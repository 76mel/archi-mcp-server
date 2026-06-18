package net.vheerden.archi.mcp.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.gef.commands.Command;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelConnection;
import com.archimatetool.model.IDiagramModelContainer;
import com.archimatetool.model.IDiagramModelObject;
import com.archimatetool.model.IDiagramModelReference;
import com.archimatetool.model.IFolder;

/**
 * GEF Command that deletes an ArchiMate view (diagram) from the model.
 *
 * <p>On execute, captures all children and connections (same pattern as
 * {@link ClearViewCommand}), disconnects connections, clears children, and
 * removes the view from its parent folder. The underlying model elements
 * and relationships are NOT deleted.</p>
 *
 * <p><strong>Cascade:</strong> also captures every
 * {@link IDiagramModelReference} placeholder elsewhere in the model whose
 * {@code getReferencedModel()} is this view, and removes them on execute /
 * restores them on undo. Mirrors Archi GUI's
 * {@code com.archimatetool.editor/.../DeleteCommandHandler
 * .getDiagramModelReferencesToDelete()}. Without this cascade, the EMF
 * cross-reference becomes a dangling {@code model="..."} attribute on
 * {@code .archimate} save and Archi cannot reopen the file
 * ({@code "Unresolved reference ..."}). Placeholders contained inside the
 * view being deleted are NOT cascaded — they disappear with the view via
 * the existing {@code view.getChildren().clear()} step.</p>
 *
 * <p>State is captured once on first {@code execute()} and reused on
 * subsequent {@code redo()} calls.</p>
 *
 * <p><strong>CRITICAL:</strong> This command MUST be executed via
 * {@code CommandStack.execute()} through {@link MutationDispatcher}.
 * Direct invocation of {@code execute()} bypasses undo tracking.</p>
 */
public class DeleteViewCommand extends Command {

    private final IDiagramModel view;
    private final IFolder viewFolder;
    private final int viewIndex;

    // Captured on first execute for undo/redo
    private List<IDiagramModelObject> capturedChildren;
    private List<IDiagramModelConnection> capturedConnections;
    private List<Integer> capturedIndices;
    private List<CascadedRef> capturedCascadedRefs;

    /** Captured external placeholder for cascade undo. */
    private record CascadedRef(IDiagramModelContainer parent, int index, IDiagramModelReference ref) { }

    /**
     * Creates a command to delete a view from the model.
     *
     * @param view       the view to delete
     * @param viewFolder the folder containing the view
     * @param viewIndex  the view's index in the folder
     */
    public DeleteViewCommand(IDiagramModel view, IFolder viewFolder, int viewIndex) {
        this.view = Objects.requireNonNull(view, "view must not be null");
        this.viewFolder = Objects.requireNonNull(viewFolder, "viewFolder must not be null");
        this.viewIndex = viewIndex;
        setLabel("Delete view: " + view.getName());
    }

    @Override
    public void execute() {
        // Capture state only on first execution; reuse on redo
        if (capturedChildren == null) {
            capturedChildren = new ArrayList<>(view.getChildren());
            capturedIndices = new ArrayList<>();
            for (int i = 0; i < capturedChildren.size(); i++) {
                capturedIndices.add(i);
            }

            Set<IDiagramModelConnection> uniqueConnections = new LinkedHashSet<>();
            for (IDiagramModelObject child : capturedChildren) {
                for (Object conn : child.getSourceConnections()) {
                    if (conn instanceof IDiagramModelConnection dc) {
                        uniqueConnections.add(dc);
                    }
                }
                for (Object conn : child.getTargetConnections()) {
                    if (conn instanceof IDiagramModelConnection dc) {
                        uniqueConnections.add(dc);
                    }
                }
            }
            capturedConnections = new ArrayList<>(uniqueConnections);

            capturedCascadedRefs = captureExternalPlaceholders();
        }

        // 1. Disconnect all connections
        for (IDiagramModelConnection conn : capturedConnections) {
            conn.disconnect();
        }

        // 2. Clear all children
        view.getChildren().clear();

        // 3. Remove view from folder
        viewFolder.getElements().remove(view);

        // 4. Cascade: remove every external IDiagramModelReference placeholder
        //    that pointed at this view, otherwise serialization writes a
        //    dangling model="<deleted-id>" attribute and reload fails.
        for (CascadedRef cascaded : capturedCascadedRefs) {
            cascaded.parent().getChildren().remove(cascaded.ref());
        }
    }

    // Default redo() calls execute() — safe because the lazy capture guard
    // (capturedChildren == null) ensures state is only captured once.

    @Override
    public void undo() {
        // 4. Restore cascaded placeholders first, sorted by index ascending
        //    so each insertion targets a position that already has every
        //    smaller-index sibling restored. Each entry was captured at its
        //    pre-removal index in its parent's children list.
        List<CascadedRef> sortedCascaded = new ArrayList<>(capturedCascadedRefs);
        sortedCascaded.sort((a, b) -> Integer.compare(a.index(), b.index()));
        for (CascadedRef cascaded : sortedCascaded) {
            IDiagramModelContainer parent = cascaded.parent();
            int idx = cascaded.index();
            if (idx >= 0 && idx <= parent.getChildren().size()) {
                parent.getChildren().add(idx, cascaded.ref());
            } else {
                parent.getChildren().add(cascaded.ref());
            }
        }

        // 3. Re-add view to folder
        if (viewIndex >= 0 && viewIndex <= viewFolder.getElements().size()) {
            viewFolder.getElements().add(viewIndex, view);
        } else {
            viewFolder.getElements().add(view);
        }

        // 2. Re-add children at original indices
        for (int i = 0; i < capturedChildren.size(); i++) {
            int idx = capturedIndices.get(i);
            IDiagramModelObject child = capturedChildren.get(i);
            if (idx >= 0 && idx <= view.getChildren().size()) {
                view.getChildren().add(idx, child);
            } else {
                view.getChildren().add(child);
            }
        }

        // 1. Reconnect all connections
        for (IDiagramModelConnection conn : capturedConnections) {
            conn.reconnect();
        }
    }

    /**
     * Scan the model's DIAGRAMS folder for every {@link IDiagramModelReference}
     * whose {@code getReferencedModel() == this.view}, excluding refs contained
     * inside the view being deleted (those die with the view).
     */
    private List<CascadedRef> captureExternalPlaceholders() {
        List<CascadedRef> out = new ArrayList<>();
        IArchimateModel model = view.getArchimateModel();
        if (model == null) {
            return out;
        }
        IFolder diagramsFolder = model.getFolder(FolderType.DIAGRAMS);
        if (diagramsFolder == null) {
            return out;
        }
        for (Iterator<EObject> iter = diagramsFolder.eAllContents(); iter.hasNext(); ) {
            EObject node = iter.next();
            if (!(node instanceof IDiagramModelReference ref)) {
                continue;
            }
            if (ref.getReferencedModel() != view) {
                continue;
            }
            if (isContainedIn(ref, view)) {
                continue;
            }
            EObject container = ref.eContainer();
            if (!(container instanceof IDiagramModelContainer parent)) {
                continue;
            }
            int index = parent.getChildren().indexOf(ref);
            out.add(new CascadedRef(parent, index, ref));
        }
        return out;
    }

    /** True iff {@code node}'s containment chain reaches {@code ancestor}. */
    private static boolean isContainedIn(EObject node, EObject ancestor) {
        for (EObject cur = node.eContainer(); cur != null; cur = cur.eContainer()) {
            if (cur == ancestor) {
                return true;
            }
        }
        return false;
    }

    /** Package-visible for testing. */
    IDiagramModel getView() { return view; }

    /** Package-visible for testing. */
    IFolder getViewFolder() { return viewFolder; }

    /** Package-visible for testing. */
    List<CascadedRef> getCapturedCascadedRefs() {
        return capturedCascadedRefs;
    }
}
