package net.vheerden.archi.mcp.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.gef.commands.Command;

import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IProperty;

/**
 * GEF Command that updates the loaded ArchiMate model's own metadata.
 *
 * <p>Supports updating name, purpose, and custom properties. Only non-null
 * fields are modified; null fields are left unchanged. For properties, a
 * merge semantic applies: non-null values add/update, null values remove.</p>
 *
 * <p>Captures old state at construction time for full undo support.
 * Properties are deep-copied because EMF property objects are live references.</p>
 *
 * <p>The {@code documentation} field
 * (mentioned in some jArchi documentation) is OUT-OF-SCOPE here because
 * {@code IArchimateModel} does NOT extend {@code IDocumentable} in Archi 5.7/5.8
 * — there is no model-level documentation API. The {@code purpose} field is the
 * model-level free-text Archi exposes (shown in File → Properties → "Purpose").
 * jArchi's {@code model.documentation} is most plausibly an alias to {@code model.purpose}
 * at the jArchi layer.</p>
 *
 * <p><strong>CRITICAL:</strong> This command MUST be executed via
 * {@code CommandStack.execute()} through {@link MutationDispatcher}.
 * Direct invocation of {@code execute()} bypasses undo tracking.</p>
 */
public class UpdateModelCommand extends Command {

    private final IArchimateModel model;

    // Old state (captured at construction time for undo)
    private final String oldName;
    private final String oldPurpose;
    private final List<PropertySnapshot> oldProperties;

    // New state (null = don't change)
    private final String newName;
    private final String newPurpose;
    private final boolean clearPurpose;
    private final Map<String, String> newProperties; // null value = remove key

    /**
     * Creates a command to update a model's metadata fields.
     *
     * @param model           the model to update
     * @param newName         new name, or null to leave unchanged
     * @param newPurpose      new purpose, or null to leave unchanged
     * @param clearPurpose    true to clear the purpose (set to null)
     * @param newProperties   property merge map (null value = remove key), or null to leave unchanged
     */
    public UpdateModelCommand(IArchimateModel model, String newName,
            String newPurpose, boolean clearPurpose,
            Map<String, String> newProperties) {
        this.model = model;
        this.newName = newName;
        this.newPurpose = newPurpose;
        this.clearPurpose = clearPurpose;
        this.newProperties = newProperties;

        // Snapshot old state before any mutation
        this.oldName = model.getName();
        this.oldPurpose = model.getPurpose();
        this.oldProperties = snapshotProperties(model);

        setLabel("Update model: " + model.getName());
    }

    @Override
    public void execute() {
        applyNewValues();
    }

    @Override
    public void redo() {
        applyNewValues();
    }

    @Override
    public void undo() {
        model.setName(oldName);
        // Note: oldPurpose may be "" (how Archi internally stores "no purpose")
        // while clearPurpose sets null. Both are functionally equivalent in Archi —
        // the undo/redo cycle is not strictly idempotent at the EMF level but is
        // semantically correct. Do not "normalize" this to null. (Mirrors the
        // UpdateViewCommand viewpoint comment.)
        model.setPurpose(oldPurpose);
        restoreProperties();
    }

    private void applyNewValues() {
        if (newName != null) {
            model.setName(newName);
        }
        if (clearPurpose) {
            model.setPurpose(null);
        } else if (newPurpose != null) {
            model.setPurpose(newPurpose);
        }
        if (newProperties != null) {
            mergeProperties();
        }
    }

    private void mergeProperties() {
        for (Map.Entry<String, String> entry : newProperties.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value == null) {
                model.getProperties().removeIf(p -> key.equals(p.getKey()));
            } else {
                Optional<IProperty> existing = model.getProperties().stream()
                        .filter(p -> key.equals(p.getKey()))
                        .findFirst();
                if (existing.isPresent()) {
                    existing.get().setValue(value);
                } else {
                    IProperty prop = IArchimateFactory.eINSTANCE.createProperty();
                    prop.setKey(key);
                    prop.setValue(value);
                    model.getProperties().add(prop);
                }
            }
        }
    }

    private void restoreProperties() {
        model.getProperties().clear();
        for (PropertySnapshot snapshot : oldProperties) {
            IProperty restored = IArchimateFactory.eINSTANCE.createProperty();
            restored.setKey(snapshot.key());
            restored.setValue(snapshot.value());
            model.getProperties().add(restored);
        }
    }

    private static List<PropertySnapshot> snapshotProperties(IArchimateModel model) {
        List<PropertySnapshot> snapshots = new ArrayList<>();
        for (IProperty prop : model.getProperties()) {
            snapshots.add(new PropertySnapshot(prop.getKey(), prop.getValue()));
        }
        return snapshots;
    }

    /**
     * Returns the model this command updates.
     * Package-visible for testing.
     */
    IArchimateModel getModel() {
        return model;
    }
}
