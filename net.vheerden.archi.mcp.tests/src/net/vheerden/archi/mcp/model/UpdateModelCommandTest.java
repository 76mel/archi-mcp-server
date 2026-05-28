package net.vheerden.archi.mcp.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IProperty;

/**
 * Tests for {@link UpdateModelCommand} (Story 14-3, G6).
 *
 * <p>Uses real EMF objects (IArchimateFactory.eINSTANCE) to verify the command
 * correctly sets, clears, undoes, and redoes name/purpose/properties changes
 * on the loaded model. Pure JUnit — no OSGi runtime required.</p>
 *
 * <p>Mirrors {@link UpdateViewCommandTest} test conventions and naming
 * (Story 14-3 AC13). The {@code documentation} field is intentionally NOT
 * exercised — {@code IArchimateModel} is not {@code IDocumentable} (per
 * Story 14-3 Task 0 OUTCOME, outcome C).</p>
 */
public class UpdateModelCommandTest {

    private IArchimateModel model;

    @Before
    public void setUp() {
        model = IArchimateFactory.eINSTANCE.createArchimateModel();
        model.setName("Old Model Name");
        model.setPurpose("Old purpose");
    }

    @Test
    public void shouldSetName_whenExecuted_AC2() {
        UpdateModelCommand cmd = new UpdateModelCommand(model, "New Model Name", null, false, null);
        cmd.execute();
        assertEquals("New Model Name", model.getName());
    }

    @Test
    public void shouldSetPurpose_whenExecuted_AC2() {
        UpdateModelCommand cmd = new UpdateModelCommand(model, null, "New purpose", false, null);
        cmd.execute();
        assertEquals("New purpose", model.getPurpose());
    }

    @Test
    public void shouldClearPurpose_whenEmptyStringPassed_AC4() {
        // Caller passes "" via JSON, which the boundary converts to clearPurpose=true + null payload.
        UpdateModelCommand cmd = new UpdateModelCommand(model, null, null, true, null);
        cmd.execute();
        assertNull(model.getPurpose());
    }

    @Test
    public void shouldMergeProperties_whenPropertyMapProvided_AC5() {
        // Pre-existing property
        IProperty existing = IArchimateFactory.eINSTANCE.createProperty();
        existing.setKey("Author");
        existing.setValue("Old Author");
        model.getProperties().add(existing);

        Map<String, String> props = new LinkedHashMap<>();
        props.put("Author", "Jane Doe"); // update existing
        UpdateModelCommand cmd = new UpdateModelCommand(model, null, null, false, props);
        cmd.execute();

        assertEquals(1, model.getProperties().size());
        assertEquals("Jane Doe", model.getProperties().get(0).getValue());
    }

    @Test
    public void shouldRemoveProperty_whenValueIsNull_AC5() {
        IProperty toRemove = IArchimateFactory.eINSTANCE.createProperty();
        toRemove.setKey("OldKey");
        toRemove.setValue("OldValue");
        model.getProperties().add(toRemove);

        Map<String, String> props = new LinkedHashMap<>();
        props.put("OldKey", null); // null = remove
        UpdateModelCommand cmd = new UpdateModelCommand(model, null, null, false, props);
        cmd.execute();

        assertTrue("OldKey should be removed", model.getProperties().isEmpty());
    }

    @Test
    public void shouldAddNewProperty_whenKeyMissing_AC5() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("Tag", "draft");
        UpdateModelCommand cmd = new UpdateModelCommand(model, null, null, false, props);
        cmd.execute();

        assertEquals(1, model.getProperties().size());
        assertEquals("Tag", model.getProperties().get(0).getKey());
        assertEquals("draft", model.getProperties().get(0).getValue());
    }

    @Test
    public void shouldLeaveNameUnchanged_whenNameNull_AC3() {
        UpdateModelCommand cmd = new UpdateModelCommand(model, null, "Some purpose", false, null);
        cmd.execute();
        assertEquals("Old Model Name", model.getName());
    }

    @Test
    public void shouldLeavePurposeUnchanged_whenPurposeNull_AC3() {
        UpdateModelCommand cmd = new UpdateModelCommand(model, "Renamed", null, false, null);
        cmd.execute();
        assertEquals("Old purpose", model.getPurpose());
    }

    @Test
    public void shouldLeavePropertiesUnchanged_whenPropertiesNull_AC3() {
        IProperty prop = IArchimateFactory.eINSTANCE.createProperty();
        prop.setKey("Keep");
        prop.setValue("Me");
        model.getProperties().add(prop);

        UpdateModelCommand cmd = new UpdateModelCommand(model, "Renamed", null, false, null);
        cmd.execute();

        assertEquals(1, model.getProperties().size());
        assertEquals("Keep", model.getProperties().get(0).getKey());
        assertEquals("Me", model.getProperties().get(0).getValue());
    }

    @Test
    public void shouldRestoreOldState_whenUndone_AC6() {
        IProperty originalProp = IArchimateFactory.eINSTANCE.createProperty();
        originalProp.setKey("Original");
        originalProp.setValue("Value");
        model.getProperties().add(originalProp);

        Map<String, String> newProps = new LinkedHashMap<>();
        newProps.put("Original", null); // remove
        newProps.put("NewKey", "NewValue"); // add

        UpdateModelCommand cmd = new UpdateModelCommand(
                model, "Renamed", "New purpose", false, newProps);
        cmd.execute();

        assertEquals("Renamed", model.getName());
        assertEquals("New purpose", model.getPurpose());
        assertEquals(1, model.getProperties().size());
        assertEquals("NewKey", model.getProperties().get(0).getKey());

        cmd.undo();

        assertEquals("Old Model Name", model.getName());
        assertEquals("Old purpose", model.getPurpose());
        assertEquals(1, model.getProperties().size());
        assertEquals("Original", model.getProperties().get(0).getKey());
        assertEquals("Value", model.getProperties().get(0).getValue());
    }

    @Test
    public void shouldRestoreNullPurpose_whenUndoneAfterSet_AC6() {
        // Start with null purpose
        IArchimateModel m = IArchimateFactory.eINSTANCE.createArchimateModel();
        m.setName("X");
        assertNull(m.getPurpose());

        UpdateModelCommand cmd = new UpdateModelCommand(m, null, "Set purpose", false, null);
        cmd.execute();
        assertEquals("Set purpose", m.getPurpose());

        cmd.undo();
        assertNull(m.getPurpose());
    }

    @Test
    public void shouldReexecute_whenRedoCalled_AC6() {
        UpdateModelCommand cmd = new UpdateModelCommand(
                model, "Renamed", "New purpose", false, null);
        cmd.execute();
        assertEquals("Renamed", model.getName());

        cmd.undo();
        assertEquals("Old Model Name", model.getName());

        cmd.redo();
        assertEquals("Renamed", model.getName());
        assertEquals("New purpose", model.getPurpose());
    }

    @Test
    public void shouldCaptureOldStateAtConstruction_notExecute_AC6() {
        // Mutate AFTER construction; undo must restore the CONSTRUCTION-TIME snapshot
        UpdateModelCommand cmd = new UpdateModelCommand(model, "Renamed", null, false, null);

        // Out-of-band mutation between construction and execute
        model.setName("Tampered");
        assertEquals("Tampered", model.getName());

        cmd.execute();
        assertEquals("Renamed", model.getName());

        cmd.undo();
        // Snapshot was taken at construction time (when name was "Old Model Name"),
        // so undo restores "Old Model Name" — NOT the post-tamper "Tampered".
        assertEquals("Old Model Name", model.getName());
        assertNotNull(model.getName());
    }
}
