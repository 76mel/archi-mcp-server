package net.vheerden.archi.mcp.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelGroup;

/**
 * Tests for {@link RemoveFromViewCommand} (Story 7-8).
 *
 * <p>Uses real EMF objects via {@link IArchimateFactory#eINSTANCE} to test
 * execute (remove + cascade disconnect) and undo (re-add + reconnect).</p>
 */
public class RemoveFromViewCommandTest {

    private IArchimateFactory factory;
    private IArchimateDiagramModel view;
    private IDiagramModelArchimateObject sourceViewObj;
    private IDiagramModelArchimateObject targetViewObj;
    private IDiagramModelArchimateConnection connection;

    @Before
    public void setUp() {
        factory = IArchimateFactory.eINSTANCE;
        IArchimateModel model = factory.createArchimateModel();
        model.setDefaults();

        view = factory.createArchimateDiagramModel();
        view.setName("Test View");
        model.getFolder(FolderType.DIAGRAMS).getElements().add(view);

        IArchimateElement source = factory.createApplicationComponent();
        source.setName("Source");
        model.getFolder(FolderType.APPLICATION).getElements().add(source);

        IArchimateElement target = factory.createApplicationComponent();
        target.setName("Target");
        model.getFolder(FolderType.APPLICATION).getElements().add(target);

        IArchimateRelationship rel = factory.createServingRelationship();
        rel.connect(source, target);
        model.getFolder(FolderType.RELATIONS).getElements().add(rel);

        sourceViewObj = factory.createDiagramModelArchimateObject();
        sourceViewObj.setArchimateElement(source);
        sourceViewObj.setBounds(50, 50, 120, 55);
        view.getChildren().add(sourceViewObj);

        targetViewObj = factory.createDiagramModelArchimateObject();
        targetViewObj.setArchimateElement(target);
        targetViewObj.setBounds(250, 50, 120, 55);
        view.getChildren().add(targetViewObj);

        connection = factory.createDiagramModelArchimateConnection();
        connection.setArchimateRelationship(rel);
        connection.connect(sourceViewObj, targetViewObj);
    }

    @Test
    public void shouldRemoveObjectFromView_whenExecuted() {
        RemoveFromViewCommand cmd = new RemoveFromViewCommand(
                sourceViewObj, view, List.of(connection));

        cmd.execute();

        assertFalse("Source view object should not be in view children",
                view.getChildren().contains(sourceViewObj));
    }

    @Test
    public void shouldDisconnectAttachedConnections_whenExecuted() {
        RemoveFromViewCommand cmd = new RemoveFromViewCommand(
                sourceViewObj, view, List.of(connection));

        cmd.execute();

        assertFalse("Connection should be disconnected from source",
                sourceViewObj.getSourceConnections().contains(connection));
        assertFalse("Connection should be disconnected from target",
                targetViewObj.getTargetConnections().contains(connection));
    }

    @Test
    public void shouldReAddObjectToView_whenUndone() {
        RemoveFromViewCommand cmd = new RemoveFromViewCommand(
                sourceViewObj, view, List.of(connection));
        cmd.execute();

        cmd.undo();

        assertTrue("Source view object should be in view children after undo",
                view.getChildren().contains(sourceViewObj));
    }

    @Test
    public void shouldReconnectConnections_whenUndone() {
        RemoveFromViewCommand cmd = new RemoveFromViewCommand(
                sourceViewObj, view, List.of(connection));
        cmd.execute();

        cmd.undo();

        assertTrue("Connection should be reconnected to source",
                sourceViewObj.getSourceConnections().contains(connection));
        assertTrue("Connection should be reconnected to target",
                targetViewObj.getTargetConnections().contains(connection));
    }

    @Test
    public void shouldHandleNoAttachedConnections() {
        // Remove target (no connections as source)
        RemoveFromViewCommand cmd = new RemoveFromViewCommand(
                targetViewObj, view, List.of());

        cmd.execute();

        assertFalse("Target should be removed from view",
                view.getChildren().contains(targetViewObj));
        // Source should remain
        assertTrue("Source should remain on view",
                view.getChildren().contains(sourceViewObj));
    }

    @Test
    public void shouldExposeFieldsViaGetters() {
        RemoveFromViewCommand cmd = new RemoveFromViewCommand(
                sourceViewObj, view, List.of(connection));

        assertSame(sourceViewObj, cmd.getDiagramObject());
        assertSame(view, cmd.getParent());
        assertEquals(1, cmd.getAttachedConnections().size());
        assertSame(connection, cmd.getAttachedConnections().get(0));
    }

    @Test
    public void shouldHaveDescriptiveLabel() {
        RemoveFromViewCommand cmd = new RemoveFromViewCommand(
                sourceViewObj, view, List.of(connection));

        assertTrue("Label should contain element type",
                cmd.getLabel().contains("ApplicationComponent"));
        assertTrue("Label should contain 'from view'",
                cmd.getLabel().contains("from view"));
    }

    // --- Story B (v1.6): nested-parent + SOUND postcondition certificate ---

    /**
     * AC-1: nested ArchiMate-element view-object inside another element acting
     * as a container (Story 10-20 element-as-container nesting). Reproduces the
     * literal 1708 retail-bank failure mode — z/OS placed under Mobile App Binary
     * (a Component) and then removed.
     */
    @Test
    public void shouldRemoveNestedElement_fromContainerComponent() {
        IArchimateModel model = factory.createArchimateModel();
        model.setDefaults();
        IArchimateDiagramModel v = factory.createArchimateDiagramModel();
        model.getFolder(FolderType.DIAGRAMS).getElements().add(v);

        IArchimateElement componentElement = factory.createApplicationComponent();
        model.getFolder(FolderType.APPLICATION).getElements().add(componentElement);
        IDiagramModelArchimateObject componentA = factory.createDiagramModelArchimateObject();
        componentA.setArchimateElement(componentElement);
        componentA.setBounds(50, 50, 300, 200);
        v.getChildren().add(componentA);

        IArchimateElement nestedElement = factory.createApplicationComponent();
        nestedElement.setName("Nested under component");
        model.getFolder(FolderType.APPLICATION).getElements().add(nestedElement);
        IDiagramModelArchimateObject nestedE = factory.createDiagramModelArchimateObject();
        nestedE.setArchimateElement(nestedElement);
        nestedE.setBounds(20, 30, 120, 55);
        componentA.getChildren().add(nestedE);

        RemoveFromViewCommand cmd = new RemoveFromViewCommand(nestedE, componentA, List.of());

        cmd.execute();

        assertFalse("Nested element should be removed from container component",
                componentA.getChildren().contains(nestedE));
        assertTrue("Container component should remain on view",
                v.getChildren().contains(componentA));
    }

    /**
     * AC-2: nested element inside a Node (Story 10-20 node-as-container).
     */
    @Test
    public void shouldRemoveNestedElement_fromContainerNode() {
        IArchimateModel model = factory.createArchimateModel();
        model.setDefaults();
        IArchimateDiagramModel v = factory.createArchimateDiagramModel();
        model.getFolder(FolderType.DIAGRAMS).getElements().add(v);

        IArchimateElement nodeElement = factory.createNode();
        model.getFolder(FolderType.TECHNOLOGY).getElements().add(nodeElement);
        IDiagramModelArchimateObject nodeN = factory.createDiagramModelArchimateObject();
        nodeN.setArchimateElement(nodeElement);
        nodeN.setBounds(50, 50, 300, 200);
        v.getChildren().add(nodeN);

        IArchimateElement nestedTechElement = factory.createApplicationComponent();
        nestedTechElement.setName("Nested under node");
        model.getFolder(FolderType.APPLICATION).getElements().add(nestedTechElement);
        IDiagramModelArchimateObject nestedS = factory.createDiagramModelArchimateObject();
        nestedS.setArchimateElement(nestedTechElement);
        nestedS.setBounds(20, 30, 120, 55);
        nodeN.getChildren().add(nestedS);

        RemoveFromViewCommand cmd = new RemoveFromViewCommand(nestedS, nodeN, List.of());

        cmd.execute();

        assertFalse("Nested element should be removed from container node",
                nodeN.getChildren().contains(nestedS));
        assertTrue("Container node should remain on view",
                v.getChildren().contains(nodeN));
    }

    /**
     * AC-3 (regression pin): nested element inside a native DiagramModelGroup.
     * Proves the fix is parent-type-agnostic — same behaviour whether the
     * parent is a Group, a Component-as-container, a Node-as-container, or
     * the view itself.
     */
    @Test
    public void shouldRemoveNestedElement_fromGroup() {
        IArchimateModel model = factory.createArchimateModel();
        model.setDefaults();
        IArchimateDiagramModel v = factory.createArchimateDiagramModel();
        model.getFolder(FolderType.DIAGRAMS).getElements().add(v);

        IDiagramModelGroup groupG = factory.createDiagramModelGroup();
        groupG.setName("Group G");
        groupG.setBounds(50, 50, 300, 200);
        v.getChildren().add(groupG);

        IArchimateElement nestedElement = factory.createApplicationComponent();
        nestedElement.setName("Nested under group");
        model.getFolder(FolderType.APPLICATION).getElements().add(nestedElement);
        IDiagramModelArchimateObject nestedE = factory.createDiagramModelArchimateObject();
        nestedE.setArchimateElement(nestedElement);
        nestedE.setBounds(20, 30, 120, 55);
        groupG.getChildren().add(nestedE);

        RemoveFromViewCommand cmd = new RemoveFromViewCommand(nestedE, groupG, List.of());

        cmd.execute();

        assertFalse("Nested element should be removed from group",
                groupG.getChildren().contains(nestedE));
        assertTrue("Group should remain on view",
                v.getChildren().contains(groupG));
    }

    /**
     * AC-3 invariant: undo restores the nested element at its original index
     * inside the real parent container (not at index 0 of the view, and not
     * appended at the end of the parent's children).
     */
    @Test
    public void shouldRestoreNestedElement_atOriginalIndexInsideParent_whenUndone() {
        IArchimateModel model = factory.createArchimateModel();
        model.setDefaults();
        IArchimateDiagramModel v = factory.createArchimateDiagramModel();
        model.getFolder(FolderType.DIAGRAMS).getElements().add(v);

        IArchimateElement componentElement = factory.createApplicationComponent();
        model.getFolder(FolderType.APPLICATION).getElements().add(componentElement);
        IDiagramModelArchimateObject componentA = factory.createDiagramModelArchimateObject();
        componentA.setArchimateElement(componentElement);
        componentA.setBounds(50, 50, 400, 300);
        v.getChildren().add(componentA);

        IArchimateElement firstElement = factory.createApplicationComponent();
        model.getFolder(FolderType.APPLICATION).getElements().add(firstElement);
        IDiagramModelArchimateObject first = factory.createDiagramModelArchimateObject();
        first.setArchimateElement(firstElement);
        componentA.getChildren().add(first);

        IArchimateElement middleElement = factory.createApplicationComponent();
        model.getFolder(FolderType.APPLICATION).getElements().add(middleElement);
        IDiagramModelArchimateObject middle = factory.createDiagramModelArchimateObject();
        middle.setArchimateElement(middleElement);
        componentA.getChildren().add(middle);

        IArchimateElement lastElement = factory.createApplicationComponent();
        model.getFolder(FolderType.APPLICATION).getElements().add(lastElement);
        IDiagramModelArchimateObject last = factory.createDiagramModelArchimateObject();
        last.setArchimateElement(lastElement);
        componentA.getChildren().add(last);

        assertEquals("middle should be at index 1 before remove",
                1, componentA.getChildren().indexOf(middle));

        RemoveFromViewCommand cmd = new RemoveFromViewCommand(middle, componentA, List.of());
        cmd.execute();

        assertFalse(componentA.getChildren().contains(middle));
        assertEquals("two siblings should remain after remove",
                2, componentA.getChildren().size());

        cmd.undo();

        assertEquals("middle should be restored at original index 1 inside parent",
                1, componentA.getChildren().indexOf(middle));
        assertSame("middle should be at parent.getChildren().get(1) after undo",
                middle, componentA.getChildren().get(1));
    }

    /**
     * AC-5 + accessor-prepare-stage regression-pin proxy. Reproduces the v1.5
     * bug shape at the command layer: a nested element lives at
     * {@code view → componentA → nestedE}, but the command is constructed with
     * the top-level view as the prepared parent (instead of componentA). This
     * is exactly what {@code ArchiModelAccessorImpl.prepareRemoveFromView} did
     * for nested elements before Story B's {@code findParentContainer} rewire
     * at the element branch. The SOUND postcondition certificate must fire,
     * proving that any future accessor-prepare regression (e.g. someone reverts
     * the {@code findParentContainer} call) would be caught at execute time
     * instead of silently no-op'ing and lying via {@code {action:"removed"}}.
     *
     * <p>This test is a proxy regression-pin for the accessor's element-branch
     * parent-resolution fix at {@code ArchiModelAccessorImpl.java:14573} — the
     * Story B unit tests + integration test all construct the command directly
     * with the correct parent (which would still pass even if the accessor's
     * parent-resolution regressed), so this test closes that coverage gap by
     * asserting the certificate fires for the bug-shape input.</p>
     */
    @Test
    public void shouldThrowIllegalStateException_whenConstructedWithWrongParent() {
        IArchimateModel model = factory.createArchimateModel();
        model.setDefaults();
        IArchimateDiagramModel v = factory.createArchimateDiagramModel();
        model.getFolder(FolderType.DIAGRAMS).getElements().add(v);

        IArchimateElement componentElement = factory.createApplicationComponent();
        model.getFolder(FolderType.APPLICATION).getElements().add(componentElement);
        IDiagramModelArchimateObject componentA = factory.createDiagramModelArchimateObject();
        componentA.setArchimateElement(componentElement);
        componentA.setBounds(50, 50, 300, 200);
        v.getChildren().add(componentA);

        IArchimateElement nestedElement = factory.createApplicationComponent();
        model.getFolder(FolderType.APPLICATION).getElements().add(nestedElement);
        IDiagramModelArchimateObject nestedE = factory.createDiagramModelArchimateObject();
        nestedE.setArchimateElement(nestedElement);
        nestedE.setBounds(20, 30, 120, 55);
        // Nested under componentA — the bug-surfacing topology. The nested
        // element is NOT in v.getChildren(), it is in componentA.getChildren().
        componentA.getChildren().add(nestedE);

        // Construct command with WRONG parent (v instead of componentA) —
        // mimics the pre-Story-B prepareRemoveFromView element-branch bug.
        RemoveFromViewCommand cmdWithWrongParent = new RemoveFromViewCommand(
                nestedE, v, List.of());

        try {
            cmdWithWrongParent.execute();
            fail("SOUND certificate must fire when the command is constructed with "
                    + "the wrong parent (this is the v1.5 bug shape — pre-fix this returned "
                    + "{action:\"removed\"} silently while the ghost persisted)");
        } catch (IllegalStateException ex) {
            // Refuse-to-mutate contract: nestedE must remain in its real parent
            // (componentA) untouched after the certificate fires.
            assertTrue("nestedE must remain in its real parent (componentA) after refuse-to-mutate",
                    componentA.getChildren().contains(nestedE));
            String msg = ex.getMessage();
            assertNotNull("Exception message should be populated", msg);
            assertTrue("Message should name the diagram object id (" + nestedE.getId() + "): " + msg,
                    msg.contains(nestedE.getId()));
            assertTrue("Message should name the prepared (wrong) parent id (" + v.getId() + "): " + msg,
                    msg.contains(v.getId()));
        }
    }

    /**
     * AC-5: SOUND postcondition certificate. Simulates a stale prepare-vs-execute
     * condition — the diagram object is removed from the prepared parent before
     * the command's execute runs (e.g. a future findParentContainer regression
     * or out-of-band mutation). Execute must refuse-to-mutate with a structured
     * IllegalStateException naming the diagram object and the prepared parent —
     * instead of silently no-op'ing and lying via {action:"removed"}.
     */
    @Test
    public void shouldThrowIllegalStateException_whenParentMutatedBeforeExecute() {
        IArchimateModel model = factory.createArchimateModel();
        model.setDefaults();
        IArchimateDiagramModel v = factory.createArchimateDiagramModel();
        model.getFolder(FolderType.DIAGRAMS).getElements().add(v);

        IArchimateElement componentElement = factory.createApplicationComponent();
        model.getFolder(FolderType.APPLICATION).getElements().add(componentElement);
        IDiagramModelArchimateObject componentA = factory.createDiagramModelArchimateObject();
        componentA.setArchimateElement(componentElement);
        componentA.setBounds(50, 50, 300, 200);
        v.getChildren().add(componentA);

        IArchimateElement nestedElement = factory.createApplicationComponent();
        model.getFolder(FolderType.APPLICATION).getElements().add(nestedElement);
        IDiagramModelArchimateObject nestedE = factory.createDiagramModelArchimateObject();
        nestedE.setArchimateElement(nestedElement);
        nestedE.setBounds(20, 30, 120, 55);
        componentA.getChildren().add(nestedE);

        RemoveFromViewCommand cmd = new RemoveFromViewCommand(nestedE, componentA, List.of());

        // Out-of-band mutation: remove the nested element from its parent before
        // the command's execute runs. The SOUND certificate must fire because
        // List.remove(diagramObject) will return false.
        componentA.getChildren().remove(nestedE);

        try {
            cmd.execute();
            fail("Expected IllegalStateException for postcondition violation");
        } catch (IllegalStateException ex) {
            String msg = ex.getMessage();
            assertNotNull("Exception message should be populated", msg);
            assertTrue("Message should name the diagram object id (" + nestedE.getId() + "): " + msg,
                    msg.contains(nestedE.getId()));
            assertTrue("Message should name the prepared parent id (" + componentA.getId() + "): " + msg,
                    msg.contains(componentA.getId()));
        }
    }

    /**
     * Refuse-to-mutate contract: when the SOUND postcondition certificate fires,
     * attached connections MUST remain connected. Execute runs the certificate
     * BEFORE disconnecting any connections — so a certificate failure leaves the
     * model byte-unchanged from its pre-execute state. Without this ordering
     * guarantee, a partial-mutation could leave connections disconnected while
     * the certificate's INTERNAL_ERROR signal suggests "no change was made" to
     * the agent, putting the model into an inconsistent state.
     */
    @Test
    public void shouldNotDisconnectAttachedConnections_whenSoundCertificateFires() {
        // Construct the command against the setUp() fixture, then mutate the
        // view's child list out-of-band so the certificate fires on execute.
        RemoveFromViewCommand cmd = new RemoveFromViewCommand(
                sourceViewObj, view, List.of(connection));

        // Verify the connection is connected before execute.
        assertTrue("Pre-state: connection should be attached to source",
                sourceViewObj.getSourceConnections().contains(connection));
        assertTrue("Pre-state: connection should be attached to target",
                targetViewObj.getTargetConnections().contains(connection));

        // Out-of-band remove of sourceViewObj from the view — certificate will fire.
        view.getChildren().remove(sourceViewObj);

        try {
            cmd.execute();
            fail("Expected IllegalStateException for postcondition violation");
        } catch (IllegalStateException expected) {
            // Refuse-to-mutate: connection must STILL be connected because the
            // certificate fired before the disconnect loop ran.
            assertTrue("Refuse-to-mutate: connection must remain attached to source after cert failure",
                    sourceViewObj.getSourceConnections().contains(connection));
            assertTrue("Refuse-to-mutate: connection must remain attached to target after cert failure",
                    targetViewObj.getTargetConnections().contains(connection));
        }
    }
}
