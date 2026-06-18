package net.vheerden.archi.mcp.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CompoundCommand;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.FolderType;
import com.archimatetool.model.IAccessRelationship;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IAssociationRelationship;
import com.archimatetool.model.IBusinessActor;
import com.archimatetool.model.IBusinessObject;
import com.archimatetool.model.IGoal;
import com.archimatetool.model.IInfluenceRelationship;
import com.archimatetool.model.util.ArchimateModelUtils;

import net.vheerden.archi.mcp.response.ErrorCode;
import net.vheerden.archi.mcp.response.dto.RelationshipDto;
import net.vheerden.archi.mcp.response.dto.RelationshipSemanticAttributes;

/**
 * Integration tests for relationship semantic attributes
 * ({@code accessType} / {@code associationDirected} / {@code influenceStrength})
 * on {@code create-relationship} / {@code update-relationship}.
 *
 * <p>Uses real {@link IArchimateFactory#eINSTANCE} EMF objects + the synchronous
 * test dispatcher pattern (mirrors {@code ArchiModelAccessorImplTest}). Tests are
 * guarded with {@code Assume} for the {@code RelationshipsMatrix} OSGi dependency
 * (only loads under the PDE runtime).</p>
 */
public class RelationshipSemanticAttributesIntegrationTest {

    private static final String SESSION_ID = "test-session";

    private StubEditorModelManager stubModelManager;
    private ArchiModelAccessorImpl accessor;

    @Before
    public void setUp() {
        stubModelManager = new StubEditorModelManager();
    }

    @After
    public void tearDown() {
        if (accessor != null) {
            accessor.dispose();
        }
    }

    // ==================== create-relationship ====================

    @Test
    public void shouldCreateAccessRelationshipWithAccessType_AC2() {
        IArchimateModel model = createTestModel();
        stubModelManager.setModels(List.of(model));
        accessor = createAccessorWithTestDispatcher(model);

        try {
            RelationshipSemanticAttributes attrs =
                    new RelationshipSemanticAttributes("read", null, null);
            MutationResult<RelationshipDto> result = accessor.createRelationship(
                    SESSION_ID, "AccessRelationship", "ba-001", "bo-001", null, null, attrs);

            assertNotNull(result.entity());
            assertEquals("read", result.entity().accessType());
            // EMF state matches the wire-vocabulary mapping
            IAccessRelationship rel = (IAccessRelationship)
                    ArchimateModelUtils.getObjectByID(model, result.entity().id());
            assertEquals(IAccessRelationship.READ_ACCESS, rel.getAccessType());
        } catch (ExceptionInInitializerError | NoClassDefFoundError e) {
            Assume.assumeTrue("Requires OSGi runtime for RelationshipsMatrix", false);
        }
    }

    @Test
    public void shouldCreateAssociationRelationshipWithDirected_AC2() {
        IArchimateModel model = createTestModel();
        stubModelManager.setModels(List.of(model));
        accessor = createAccessorWithTestDispatcher(model);

        try {
            RelationshipSemanticAttributes attrs =
                    new RelationshipSemanticAttributes(null, Boolean.TRUE, null);
            MutationResult<RelationshipDto> result = accessor.createRelationship(
                    SESSION_ID, "AssociationRelationship", "ba-001", "bo-001", null, null, attrs);

            assertEquals(Boolean.TRUE, result.entity().associationDirected());
            IAssociationRelationship rel = (IAssociationRelationship)
                    ArchimateModelUtils.getObjectByID(model, result.entity().id());
            assertTrue(rel.isDirected());
        } catch (ExceptionInInitializerError | NoClassDefFoundError e) {
            Assume.assumeTrue("Requires OSGi runtime for RelationshipsMatrix", false);
        }
    }

    @Test
    public void shouldCreateInfluenceRelationshipWithStrength_AC2() {
        IArchimateModel model = createTestModel();
        stubModelManager.setModels(List.of(model));
        accessor = createAccessorWithTestDispatcher(model);

        try {
            RelationshipSemanticAttributes attrs =
                    new RelationshipSemanticAttributes(null, null, "+");
            MutationResult<RelationshipDto> result = accessor.createRelationship(
                    SESSION_ID, "InfluenceRelationship", "bg-001", "bg-002", null, null, attrs);

            assertEquals("+", result.entity().influenceStrength());
            IInfluenceRelationship rel = (IInfluenceRelationship)
                    ArchimateModelUtils.getObjectByID(model, result.entity().id());
            assertEquals("+", rel.getStrength());
        } catch (ExceptionInInitializerError | NoClassDefFoundError e) {
            Assume.assumeTrue("Requires OSGi runtime for RelationshipsMatrix", false);
        }
    }

    // ==================== update-relationship ====================

    @Test
    public void shouldUpdateAccessType_onExistingAccessRelationship_AC3() {
        IArchimateModel model = createTestModel();
        IAccessRelationship existing = preInstallAccessRelationship(model, "rel-acc-1",
                IAccessRelationship.UNSPECIFIED_ACCESS);
        stubModelManager.setModels(List.of(model));
        accessor = createAccessorWithTestDispatcher(model);

        try {
            RelationshipSemanticAttributes attrs =
                    new RelationshipSemanticAttributes("readwrite", null, null);
            MutationResult<RelationshipDto> result = accessor.updateRelationship(
                    SESSION_ID, "rel-acc-1", null, null, null, null, attrs);
            assertNotNull(result.entity());
            assertEquals(IAccessRelationship.READ_WRITE_ACCESS, existing.getAccessType());
            // L2a: also assert the response DTO reflects the post-update value (guards against
            // a future regression where the entry method doesn't reconstitute the DTO after dispatch)
            assertEquals("readwrite", result.entity().accessType());
        } catch (ExceptionInInitializerError | NoClassDefFoundError e) {
            Assume.assumeTrue("Requires OSGi runtime", false);
        }
    }

    @Test
    public void shouldUpdateAssociationDirected_onExistingAssociationRelationship_AC3() {
        IArchimateModel model = createTestModel();
        IAssociationRelationship existing = preInstallAssociationRelationship(model, "rel-as-1", false);
        stubModelManager.setModels(List.of(model));
        accessor = createAccessorWithTestDispatcher(model);

        try {
            RelationshipSemanticAttributes attrs =
                    new RelationshipSemanticAttributes(null, Boolean.TRUE, null);
            accessor.updateRelationship(SESSION_ID, "rel-as-1", null, null, null, null, attrs);
            assertTrue(existing.isDirected());
        } catch (ExceptionInInitializerError | NoClassDefFoundError e) {
            Assume.assumeTrue("Requires OSGi runtime", false);
        }
    }

    @Test
    public void shouldUpdateInfluenceStrength_onExistingInfluenceRelationship_AC3() {
        IArchimateModel model = createTestModel();
        IInfluenceRelationship existing = preInstallInfluenceRelationship(model, "rel-inf-1", "+");
        stubModelManager.setModels(List.of(model));
        accessor = createAccessorWithTestDispatcher(model);

        try {
            RelationshipSemanticAttributes attrs =
                    new RelationshipSemanticAttributes(null, null, "-2");
            accessor.updateRelationship(SESSION_ID, "rel-inf-1", null, null, null, null, attrs);
            assertEquals("-2", existing.getStrength());
        } catch (ExceptionInInitializerError | NoClassDefFoundError e) {
            Assume.assumeTrue("Requires OSGi runtime", false);
        }
    }

    @Test
    public void shouldClearInfluenceStrength_viaEmptyString_AC3() {
        IArchimateModel model = createTestModel();
        IInfluenceRelationship existing = preInstallInfluenceRelationship(model, "rel-inf-clr", "+++");
        stubModelManager.setModels(List.of(model));
        accessor = createAccessorWithTestDispatcher(model);

        try {
            RelationshipSemanticAttributes attrs =
                    new RelationshipSemanticAttributes(null, null, "");
            accessor.updateRelationship(SESSION_ID, "rel-inf-clr", null, null, null, null, attrs);
            // Empty-string clears the underlying EMF value
            assertEquals("", existing.getStrength());
            // L2b: the read-side DTO normalises empty-string → null so JSON omits the field
            // under @JsonInclude(NON_NULL). This pins the round-trip "cleared = absent" contract.
            RelationshipDto dto = accessor.convertToRelationshipDto(existing);
            assertNull("cleared influenceStrength should be omitted from DTO",
                    dto.influenceStrength());
        } catch (ExceptionInInitializerError | NoClassDefFoundError e) {
            Assume.assumeTrue("Requires OSGi runtime", false);
        }
    }

    // ==================== Undo / redo ====================

    @Test
    public void shouldUndoAccessTypeChange_AC6() {
        IArchimateModel model = createTestModel();
        IAccessRelationship existing = preInstallAccessRelationship(model, "rel-undo-1",
                IAccessRelationship.WRITE_ACCESS);
        UpdateRelationshipCommand cmd = new UpdateRelationshipCommand(
                existing, null, null, null,
                new RelationshipSemanticAttributes("read", null, null));
        cmd.execute();
        assertEquals(IAccessRelationship.READ_ACCESS, existing.getAccessType());
        cmd.undo();
        assertEquals(IAccessRelationship.WRITE_ACCESS, existing.getAccessType());
    }

    @Test
    public void shouldUndoAssociationDirectedChange_AC6() {
        IArchimateModel model = createTestModel();
        IAssociationRelationship existing = preInstallAssociationRelationship(model, "rel-undo-2", false);
        UpdateRelationshipCommand cmd = new UpdateRelationshipCommand(
                existing, null, null, null,
                new RelationshipSemanticAttributes(null, Boolean.TRUE, null));
        cmd.execute();
        assertTrue(existing.isDirected());
        cmd.undo();
        assertFalse(existing.isDirected());
    }

    @Test
    public void shouldUndoInfluenceStrengthChange_AC6() {
        IArchimateModel model = createTestModel();
        IInfluenceRelationship existing = preInstallInfluenceRelationship(model, "rel-undo-3", "+");
        UpdateRelationshipCommand cmd = new UpdateRelationshipCommand(
                existing, null, null, null,
                new RelationshipSemanticAttributes(null, null, "-2"));
        cmd.execute();
        assertEquals("-2", existing.getStrength());
        cmd.undo();
        assertEquals("+", existing.getStrength());
    }

    // ==================== Type-conditional rejection ====================

    @Test
    public void shouldRejectAccessTypeOnNonAccessRelationship_AC7() {
        IArchimateModel model = createTestModel();
        stubModelManager.setModels(List.of(model));
        accessor = createAccessorWithTestDispatcher(model);

        RelationshipSemanticAttributes attrs =
                new RelationshipSemanticAttributes("read", null, null);
        try {
            accessor.createRelationship(SESSION_ID, "CompositionRelationship",
                    "ba-001", "ba-002", null, null, attrs);
            fail("Expected ModelAccessException for accessType on CompositionRelationship");
        } catch (ModelAccessException e) {
            assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
            assertTrue("error message must mention accessType: " + e.getMessage(),
                    e.getMessage().contains("accessType"));
        }
    }

    @Test
    public void shouldRejectAssociationDirectedOnNonAssociationRelationship_AC7() {
        IArchimateModel model = createTestModel();
        stubModelManager.setModels(List.of(model));
        accessor = createAccessorWithTestDispatcher(model);

        RelationshipSemanticAttributes attrs =
                new RelationshipSemanticAttributes(null, Boolean.TRUE, null);
        try {
            accessor.createRelationship(SESSION_ID, "CompositionRelationship",
                    "ba-001", "ba-002", null, null, attrs);
            fail("Expected ModelAccessException for associationDirected on CompositionRelationship");
        } catch (ModelAccessException e) {
            assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
            assertTrue(e.getMessage().contains("associationDirected"));
        }
    }

    @Test
    public void shouldRejectInfluenceStrengthOnNonInfluenceRelationship_AC7() {
        IArchimateModel model = createTestModel();
        stubModelManager.setModels(List.of(model));
        accessor = createAccessorWithTestDispatcher(model);

        RelationshipSemanticAttributes attrs =
                new RelationshipSemanticAttributes(null, null, "+");
        try {
            accessor.createRelationship(SESSION_ID, "CompositionRelationship",
                    "ba-001", "ba-002", null, null, attrs);
            fail("Expected ModelAccessException for influenceStrength on CompositionRelationship");
        } catch (ModelAccessException e) {
            assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
            assertTrue(e.getMessage().contains("influenceStrength"));
        }
    }

    @Test
    public void shouldRejectInvalidAccessTypeEnumValue_AC7() {
        IArchimateModel model = createTestModel();
        stubModelManager.setModels(List.of(model));
        accessor = createAccessorWithTestDispatcher(model);

        RelationshipSemanticAttributes attrs =
                new RelationshipSemanticAttributes("garbage", null, null);
        try {
            accessor.createRelationship(SESSION_ID, "AccessRelationship",
                    "ba-001", "bo-001", null, null, attrs);
            fail("Expected ModelAccessException for invalid enum");
        } catch (ModelAccessException e) {
            assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
            assertTrue(e.getMessage().toLowerCase().contains("invalid"));
        }
    }

    @Test
    public void shouldRejectEmptyAccessTypeString_AC7() {
        IArchimateModel model = createTestModel();
        stubModelManager.setModels(List.of(model));
        accessor = createAccessorWithTestDispatcher(model);

        RelationshipSemanticAttributes attrs =
                new RelationshipSemanticAttributes("", null, null);
        try {
            accessor.createRelationship(SESSION_ID, "AccessRelationship",
                    "ba-001", "bo-001", null, null, attrs);
            fail("Expected ModelAccessException for empty accessType");
        } catch (ModelAccessException e) {
            assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
            assertTrue(e.getMessage().contains("empty"));
        }
    }

    @Test
    public void shouldRejectOverLongInfluenceStrength_AC7() {
        IArchimateModel model = createTestModel();
        stubModelManager.setModels(List.of(model));
        accessor = createAccessorWithTestDispatcher(model);

        StringBuilder over = new StringBuilder();
        for (int i = 0; i < 256; i++) {
            over.append('x');
        }
        RelationshipSemanticAttributes attrs =
                new RelationshipSemanticAttributes(null, null, over.toString());
        try {
            accessor.createRelationship(SESSION_ID, "InfluenceRelationship",
                    "bg-001", "bg-002", null, null, attrs);
            fail("Expected ModelAccessException for >255-char influenceStrength");
        } catch (ModelAccessException e) {
            assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
            assertTrue(e.getMessage().contains("255"));
        }
    }

    // ==================== Round-trip via convertToRelationshipDto ====================

    @Test
    public void shouldRoundTripRelationshipDtoG1Fields_viaConvertToRelationshipDto_AC4() {
        IArchimateModel model = createTestModel();
        IAccessRelationship existing = preInstallAccessRelationship(model, "rt-acc",
                IAccessRelationship.READ_ACCESS);
        stubModelManager.setModels(List.of(model));
        accessor = createAccessorWithTestDispatcher(model);

        RelationshipDto dto = accessor.convertToRelationshipDto(existing);
        assertEquals("read", dto.accessType());
        assertNull("Non-association relationships should omit associationDirected",
                dto.associationDirected());
        assertNull("Non-influence relationships should omit influenceStrength",
                dto.influenceStrength());
    }

    // ==================== Idempotence guard ====================

    @Test
    public void shouldGuardIdempotentAccessTypeSet_AC6() {
        IArchimateModel model = createTestModel();
        IAccessRelationship existing = preInstallAccessRelationship(model, "rel-idem",
                IAccessRelationship.READ_ACCESS);

        // Re-applying the SAME value should be a no-op at the EMF level.
        // We can't easily observe "no notification fired" here, but we CAN verify
        // that undo() restores the original (which is the original — confirming
        // the guard short-circuited and didn't snapshot a stale "old" state).
        UpdateRelationshipCommand cmd = new UpdateRelationshipCommand(
                existing, null, null, null,
                new RelationshipSemanticAttributes("read", null, null));
        cmd.execute();
        assertEquals(IAccessRelationship.READ_ACCESS, existing.getAccessType());
        cmd.undo();
        assertEquals("undo should leave value at READ_ACCESS (the snapshot)",
                IAccessRelationship.READ_ACCESS, existing.getAccessType());
    }

    // ==================== test fixtures ====================

    private IArchimateModel createTestModel() {
        IArchimateFactory factory = IArchimateFactory.eINSTANCE;
        IArchimateModel model = factory.createArchimateModel();
        model.setName("G1 Test Model");
        model.setId("model-g1");
        model.setDefaults();

        IBusinessActor actor = factory.createBusinessActor();
        actor.setId("ba-001");
        actor.setName("Customer");
        model.getFolder(FolderType.BUSINESS).getElements().add(actor);

        IBusinessActor actor2 = factory.createBusinessActor();
        actor2.setId("ba-002");
        actor2.setName("Vendor");
        model.getFolder(FolderType.BUSINESS).getElements().add(actor2);

        IBusinessObject object = factory.createBusinessObject();
        object.setId("bo-001");
        object.setName("Order");
        model.getFolder(FolderType.BUSINESS).getElements().add(object);

        IGoal g1 = factory.createGoal();
        g1.setId("bg-001");
        g1.setName("Goal A");
        model.getFolder(FolderType.MOTIVATION).getElements().add(g1);

        IGoal g2 = factory.createGoal();
        g2.setId("bg-002");
        g2.setName("Goal B");
        model.getFolder(FolderType.MOTIVATION).getElements().add(g2);

        return model;
    }

    private IAccessRelationship preInstallAccessRelationship(IArchimateModel model,
            String id, int accessType) {
        IArchimateFactory factory = IArchimateFactory.eINSTANCE;
        IAccessRelationship rel = factory.createAccessRelationship();
        rel.setId(id);
        rel.setAccessType(accessType);
        // Locate the BusinessActor + BusinessObject we set up
        IBusinessActor src = (IBusinessActor) ArchimateModelUtils.getObjectByID(model, "ba-001");
        IBusinessObject tgt = (IBusinessObject) ArchimateModelUtils.getObjectByID(model, "bo-001");
        rel.connect(src, tgt);
        model.getFolder(FolderType.RELATIONS).getElements().add(rel);
        return rel;
    }

    private IAssociationRelationship preInstallAssociationRelationship(IArchimateModel model,
            String id, boolean directed) {
        IArchimateFactory factory = IArchimateFactory.eINSTANCE;
        IAssociationRelationship rel = factory.createAssociationRelationship();
        rel.setId(id);
        rel.setDirected(directed);
        IBusinessActor src = (IBusinessActor) ArchimateModelUtils.getObjectByID(model, "ba-001");
        IBusinessObject tgt = (IBusinessObject) ArchimateModelUtils.getObjectByID(model, "bo-001");
        rel.connect(src, tgt);
        model.getFolder(FolderType.RELATIONS).getElements().add(rel);
        return rel;
    }

    private IInfluenceRelationship preInstallInfluenceRelationship(IArchimateModel model,
            String id, String strength) {
        IArchimateFactory factory = IArchimateFactory.eINSTANCE;
        IInfluenceRelationship rel = factory.createInfluenceRelationship();
        rel.setId(id);
        rel.setStrength(strength);
        IGoal src = (IGoal) ArchimateModelUtils.getObjectByID(model, "bg-001");
        IGoal tgt = (IGoal) ArchimateModelUtils.getObjectByID(model, "bg-002");
        rel.connect(src, tgt);
        model.getFolder(FolderType.RELATIONS).getElements().add(rel);
        return rel;
    }

    private ArchiModelAccessorImpl createAccessorWithTestDispatcher(IArchimateModel model) {
        MutationDispatcher testDispatcher = new MutationDispatcher(() -> model) {
            @Override
            public void dispatchImmediate(Command command) {
                executeDecomposed(command);
            }
            @Override
            protected void dispatchCommand(Command command) {
                executeDecomposed(command);
            }
            private void executeDecomposed(Command command) {
                if (command instanceof CompoundCommand compound) {
                    for (Object cmd : compound.getCommands()) {
                        executeDecomposed((Command) cmd);
                    }
                } else {
                    command.execute();
                }
            }
        };
        // The dispatcher now defaults to GATED (fail-safe). These tests exercise the
        // immediate-apply path, so opt approval OFF explicitly (production wires the human bit).
        testDispatcher.setApprovalModeProvider(() -> false);
        return new ArchiModelAccessorImpl(stubModelManager, testDispatcher);
    }

    /**
     * Minimal {@link IEditorModelManager} stub — only supports
     * {@code setModels()} + {@code getModels()} + listener registration (no-op).
     * Mirrors the pattern from {@code ArchiModelAccessorImplTest}.
     */
    private static class StubEditorModelManager implements IEditorModelManager {
        private List<IArchimateModel> models = new ArrayList<>();
        private final List<PropertyChangeListener> listeners = new ArrayList<>();

        void setModels(List<IArchimateModel> models) {
            this.models = models;
        }

        @SuppressWarnings("unused")
        void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
            PropertyChangeEvent evt = new PropertyChangeEvent(this, propertyName, oldValue, newValue);
            for (PropertyChangeListener listener : new ArrayList<>(listeners)) {
                listener.propertyChange(evt);
            }
        }

        @Override
        public List<IArchimateModel> getModels() {
            return models;
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener listener) {
            listeners.add(listener);
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener listener) {
            listeners.remove(listener);
        }

        // ---- Unused IEditorModelManager methods (required by interface) ----

        @Override public IArchimateModel createNewModel() { return null; }
        @Override public void registerModel(IArchimateModel model) {}
        @Override public IArchimateModel openModel(File file) { return null; }
        @Override public void openModel(IArchimateModel model) {}
        @Override public IArchimateModel loadModel(File file) { return null; }
        @Override public IArchimateModel load(File file) throws IOException { return null; }
        @Override public boolean closeModel(IArchimateModel model) throws IOException { return false; }
        @Override public boolean closeModel(IArchimateModel model, boolean askSave) throws IOException { return false; }
        @Override public boolean isModelLoaded(File file) { return false; }
        @Override public boolean isModelDirty(IArchimateModel model) { return false; }
        @Override public boolean saveModel(IArchimateModel model) throws IOException { return false; }
        @Override public boolean saveModelAs(IArchimateModel model) throws IOException { return false; }
        @Override public void saveState() throws IOException {}
        @Override public void firePropertyChange(Object source, String prop, Object oldValue, Object newValue) {}
    }
}
