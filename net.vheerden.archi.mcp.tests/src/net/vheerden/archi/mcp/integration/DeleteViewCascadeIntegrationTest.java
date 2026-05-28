package net.vheerden.archi.mcp.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.util.Iterator;

import org.eclipse.core.runtime.Platform;
import org.eclipse.emf.ecore.EObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.archimatetool.editor.model.IArchiveManager;
import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModelReference;
import com.archimatetool.model.IFolder;

import net.vheerden.archi.mcp.model.DeleteViewCommand;

/**
 * Integration regression pin for Story 14-6.1.
 *
 * <p>Reproduces the production failure mode that broke
 * {@code Routing Pipeline Comparison.archimate} on 2026-05-27: a view
 * containing an {@link IDiagramModelReference} pointing at another view, the
 * referenced view deleted, the model saved to {@code .archimate}, then
 * reopened — Archi fails with {@code Unresolved reference …} at the
 * dangling EMF cross-reference. Without the cascade in
 * {@link DeleteViewCommand}, this round-trip is unrecoverable.</p>
 *
 * <p>Requires the OSGi/PDE runtime ({@link IEditorModelManager}'s static
 * initializer touches {@code ArchiPlugin.getInstance()}). Guarded by
 * {@link Platform#isRunning()} so non-PDE launches skip cleanly.</p>
 */
public class DeleteViewCascadeIntegrationTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void requireOsgiRuntime() {
        assumeTrue("requires PDE/OSGi runtime", Platform.isRunning());
    }

    /** AC-6. */
    @Test
    public void shouldNotProduceDanglingReferences_whenViewWithPlaceholdersDeletedAndSerialized() throws Exception {
        IArchimateFactory factory = IArchimateFactory.eINSTANCE;

        IArchimateModel model = factory.createArchimateModel();
        model.setDefaults();
        model.setName("Cascade Integration Pin");

        IFolder diagramsFolder = model.getFolder(FolderType.DIAGRAMS);

        IArchimateDiagramModel viewA = factory.createArchimateDiagramModel();
        viewA.setName("View A");
        diagramsFolder.getElements().add(viewA);

        IArchimateDiagramModel viewB = factory.createArchimateDiagramModel();
        viewB.setName("View B");
        diagramsFolder.getElements().add(viewB);

        IDiagramModelReference refOne = factory.createDiagramModelReference();
        refOne.setReferencedModel(viewA);
        refOne.setBounds(0, 0, 185, 80);
        viewB.getChildren().add(refOne);

        IDiagramModelReference refTwo = factory.createDiagramModelReference();
        refTwo.setReferencedModel(viewA);
        refTwo.setBounds(0, 200, 185, 80);
        viewB.getChildren().add(refTwo);

        IArchiveManager archiveManager = IArchiveManager.FACTORY.createArchiveManager(model);
        model.setAdapter(IArchiveManager.class, archiveManager);

        File savedFile = new File(tempFolder.getRoot(), "cascade-pin.archimate");
        model.setFile(savedFile);

        int viewAIndex = diagramsFolder.getElements().indexOf(viewA);
        DeleteViewCommand cmd = new DeleteViewCommand(viewA, diagramsFolder, viewAIndex);
        cmd.execute();

        archiveManager.saveModel();
        assertTrue("Saved file should exist", savedFile.exists());

        IArchimateModel reloaded = IEditorModelManager.INSTANCE.loadModel(savedFile);
        try {
            assertNotNull("Reload should succeed", reloaded);

            int danglingCount = 0;
            IFolder reloadedDiagrams = reloaded.getFolder(FolderType.DIAGRAMS);
            for (Iterator<EObject> iter = reloadedDiagrams.eAllContents(); iter.hasNext(); ) {
                EObject node = iter.next();
                if (node instanceof IDiagramModelReference ref) {
                    if (ref.getReferencedModel() == null || ref.getReferencedModel().eIsProxy()) {
                        danglingCount++;
                    }
                }
            }
            assertEquals("No dangling IDiagramModelReference cross-references after reload "
                    + "(this is the exact failure mode that broke Routing Pipeline Comparison.archimate)",
                    0, danglingCount);
        } finally {
            if (reloaded != null) {
                IEditorModelManager.INSTANCE.closeModel(reloaded);
            }
        }
    }
}
