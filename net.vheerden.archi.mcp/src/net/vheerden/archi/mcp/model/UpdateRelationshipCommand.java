package net.vheerden.archi.mcp.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.gef.commands.Command;

import com.archimatetool.model.IAccessRelationship;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IAssociationRelationship;
import com.archimatetool.model.IInfluenceRelationship;
import com.archimatetool.model.IProperty;

import net.vheerden.archi.mcp.response.dto.RelationshipSemanticAttributes;

/**
 * GEF Command that updates an existing ArchiMate relationship.
 *
 * <p>Supports updating name, documentation, and properties. Only non-null
 * fields are modified; null fields are left unchanged. For properties,
 * a merge semantic applies: non-null values add/update, null values remove.</p>
 *
 * <p>Also supports ArchiMate semantic attributes —
 * {@code accessType} (AccessRelationship), {@code associationDirected}
 * (AssociationRelationship), {@code influenceStrength} (InfluenceRelationship).
 * Same null-means-leave-unchanged semantics; empty-string {@code influenceStrength}
 * clears the underlying EMF value. Type-conditional application is enforced upstream
 * at the prepare boundary; the command itself applies whichever fields match.
 * Idempotence-guarded: same-value sets are skipped to avoid spurious EMF notifications.</p>
 *
 * <p>Source, target, and type are immutable — changing these fundamentally
 * alters the relationship's semantics and should be done via delete + create.</p>
 *
 * <p>Captures old state at construction time for full undo support.
 * Properties are deep-copied because EMF property objects are live references.</p>
 *
 * <p><strong>CRITICAL:</strong> This command MUST be executed via
 * {@code CommandStack.execute()} through {@link MutationDispatcher}.
 * Direct invocation of {@code execute()} bypasses undo tracking.</p>
 */
public class UpdateRelationshipCommand extends Command {

    private final IArchimateRelationship relationship;

    // Old state (captured at construction time for undo)
    private final String oldName;
    private final String oldDocumentation;
    private final List<PropertySnapshot> oldProperties;
    // Old-state snapshots for the 3 semantic-attribute fields.
    // Each is null when the relationship is NOT of the matching subtype.
    private final Integer oldAccessType;
    private final Boolean oldAssociationDirected;
    private final String oldInfluenceStrength;

    // New state (null = don't change)
    private final String newName;
    private final String newDocumentation;
    private final Map<String, String> newProperties; // null value = remove key
    // New-state — each null = leave unchanged.
    private final RelationshipSemanticAttributes newSemanticAttributes;

    /**
     * Back-compat 4-arg constructor preserving the prior signature. Delegates to the
     * 5-arg constructor with {@link RelationshipSemanticAttributes#NONE}.
     */
    public UpdateRelationshipCommand(IArchimateRelationship relationship, String newName,
            String newDocumentation, Map<String, String> newProperties) {
        this(relationship, newName, newDocumentation, newProperties,
                RelationshipSemanticAttributes.NONE);
    }

    /**
     * Creates a command to update a relationship's fields (canonical).
     *
     * @param relationship           the relationship to update
     * @param newName                new name, or null to leave unchanged
     * @param newDocumentation       new documentation, or null to leave unchanged
     * @param newProperties          property merge map (null value = remove key), or null to leave unchanged
     * @param newSemanticAttributes  semantic-attribute bundle; pass NONE for none
     */
    public UpdateRelationshipCommand(IArchimateRelationship relationship, String newName,
            String newDocumentation, Map<String, String> newProperties,
            RelationshipSemanticAttributes newSemanticAttributes) {
        this.relationship = relationship;
        this.newName = newName;
        this.newDocumentation = newDocumentation;
        this.newProperties = newProperties;
        this.newSemanticAttributes = (newSemanticAttributes != null)
                ? newSemanticAttributes : RelationshipSemanticAttributes.NONE;

        // Snapshot old state before any mutation
        this.oldName = relationship.getName();
        this.oldDocumentation = relationship.getDocumentation();
        this.oldProperties = snapshotProperties(relationship);

        // Snapshot semantic-attribute old state
        this.oldAccessType = (relationship instanceof IAccessRelationship ar)
                ? ar.getAccessType() : null;
        this.oldAssociationDirected = (relationship instanceof IAssociationRelationship asr)
                ? asr.isDirected() : null;
        this.oldInfluenceStrength = (relationship instanceof IInfluenceRelationship ir)
                ? ir.getStrength() : null;

        setLabel("Update " + relationship.eClass().getName() + ": " + relationship.getName());
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
        relationship.setName(oldName);
        relationship.setDocumentation(oldDocumentation);
        restoreProperties();
        restoreSemanticAttributes();
    }

    private void applyNewValues() {
        if (newName != null) {
            relationship.setName(newName);
        }
        if (newDocumentation != null) {
            relationship.setDocumentation(newDocumentation);
        }
        if (newProperties != null) {
            mergeProperties();
        }
        applySemanticAttributes();
    }

    /**
     * Apply semantic attributes with idempotence guards.
     * EMF setters fire notifications even on same-value writes; we guard
     * to avoid spurious model-dirty flips on no-op update-relationship calls.
     */
    private void applySemanticAttributes() {
        if (!newSemanticAttributes.hasAny()) {
            return;
        }
        if (newSemanticAttributes.accessType() != null
                && relationship instanceof IAccessRelationship ar) {
            int target = resolveAccessTypeInt(newSemanticAttributes.accessType());
            if (target != ar.getAccessType()) {
                ar.setAccessType(target);
            }
        }
        if (newSemanticAttributes.associationDirected() != null
                && relationship instanceof IAssociationRelationship asr) {
            boolean target = newSemanticAttributes.associationDirected();
            if (target != asr.isDirected()) {
                asr.setDirected(target);
            }
        }
        if (newSemanticAttributes.influenceStrength() != null
                && relationship instanceof IInfluenceRelationship ir) {
            String target = newSemanticAttributes.influenceStrength();
            String current = ir.getStrength();
            // Treat null and "" as equivalent for idempotence (mirrors Archi GUI's
            // free-text field where unset == empty).
            String currentNorm = (current == null) ? "" : current;
            if (!target.equals(currentNorm)) {
                ir.setStrength(target);
            }
        }
    }

    /**
     * Restore old semantic-attribute values on undo.
     *
     * <p><strong>Intentional asymmetry:</strong> the {@code accessType} and
     * {@code associationDirected} blocks are guarded by {@code oldAccessType != null}
     * and {@code oldAssociationDirected != null} (those snapshot fields are non-null
     * iff the relationship is the matching subtype, so the null check IS the
     * type-discriminator). The {@code influenceStrength} block uses null-vs-empty
     * normalisation because both {@code null} and {@code ""} are semantically valid
     * "cleared" states — a {@code null} {@code oldInfluenceStrength} on an
     * {@code IInfluenceRelationship} is meaningful (it was unset before, restore to unset).
     * Guarding on {@code oldInfluenceStrength != null} would skip the legitimate restore-to-null
     * case. The idempotence guard ({@code !oldNorm.equals(currentNorm)}) handles the no-op case.</p>
     */
    private void restoreSemanticAttributes() {
        if (oldAccessType != null && relationship instanceof IAccessRelationship ar) {
            if (oldAccessType != ar.getAccessType()) {
                ar.setAccessType(oldAccessType);
            }
        }
        if (oldAssociationDirected != null && relationship instanceof IAssociationRelationship asr) {
            if (oldAssociationDirected != asr.isDirected()) {
                asr.setDirected(oldAssociationDirected);
            }
        }
        if (relationship instanceof IInfluenceRelationship ir) {
            String oldNorm = (oldInfluenceStrength == null) ? "" : oldInfluenceStrength;
            String currentNorm = (ir.getStrength() == null) ? "" : ir.getStrength();
            if (!oldNorm.equals(currentNorm)) {
                ir.setStrength(oldInfluenceStrength);
            }
        }
    }

    /**
     * Resolves an MCP wire-vocabulary {@code accessType} string to the EMF named-constant int.
     * Mirrors {@code ArchiModelAccessorImpl.resolveAccessTypeInt}; duplicated here to keep the
     * command package self-contained (no upward dependency on the accessor impl).
     */
    private static int resolveAccessTypeInt(String wireValue) {
        return switch (wireValue) {
            case "access" -> IAccessRelationship.UNSPECIFIED_ACCESS;
            case "read" -> IAccessRelationship.READ_ACCESS;
            case "write" -> IAccessRelationship.WRITE_ACCESS;
            case "readwrite" -> IAccessRelationship.READ_WRITE_ACCESS;
            default -> throw new IllegalStateException(
                    "Invalid accessType '" + wireValue + "' — validated upstream at prepare boundary");
        };
    }

    private void mergeProperties() {
        for (Map.Entry<String, String> entry : newProperties.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value == null) {
                relationship.getProperties().removeIf(p -> key.equals(p.getKey()));
            } else {
                Optional<IProperty> existing = relationship.getProperties().stream()
                        .filter(p -> key.equals(p.getKey()))
                        .findFirst();
                if (existing.isPresent()) {
                    existing.get().setValue(value);
                } else {
                    IProperty prop = IArchimateFactory.eINSTANCE.createProperty();
                    prop.setKey(key);
                    prop.setValue(value);
                    relationship.getProperties().add(prop);
                }
            }
        }
    }

    private void restoreProperties() {
        relationship.getProperties().clear();
        for (PropertySnapshot snapshot : oldProperties) {
            IProperty restored = IArchimateFactory.eINSTANCE.createProperty();
            restored.setKey(snapshot.key());
            restored.setValue(snapshot.value());
            relationship.getProperties().add(restored);
        }
    }

    private static List<PropertySnapshot> snapshotProperties(IArchimateRelationship relationship) {
        List<PropertySnapshot> snapshots = new ArrayList<>();
        for (IProperty prop : relationship.getProperties()) {
            snapshots.add(new PropertySnapshot(prop.getKey(), prop.getValue()));
        }
        return snapshots;
    }

    /**
     * Returns the relationship this command updates.
     * Package-visible for testing.
     */
    IArchimateRelationship getRelationship() {
        return relationship;
    }
}
