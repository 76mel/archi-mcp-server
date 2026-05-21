package net.vheerden.archi.mcp.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelGroup;
import com.archimatetool.model.IDiagramModelNote;
import com.archimatetool.model.ITextAlignment;
import com.archimatetool.model.ITextPosition;

import net.vheerden.archi.mcp.response.ErrorCode;

/**
 * Tests for {@link StylingHelper} validate / map / read / apply behaviour for the
 * three new styling fields (figureType, textAlignment, verticalTextAlignment) added
 * by story {@code backlog-group-element-styling-surface}.
 *
 * <p>All tests use real EMF objects via {@link IArchimateFactory#eINSTANCE} to validate
 * end-to-end mapping (string → int → setter / getter → string) on real Archi model
 * targets. JUnit 4 + {@code org.junit.Assert.*} (matches existing convention in this
 * fragment).</p>
 */
public class StylingHelperTest {

    // ------------------------------------------------------------------
    // validateStylingParams — figureType branch (AC-11)
    // ------------------------------------------------------------------

    @Test
    public void validateStylingParams_acceptsRectangularAndTabbedFigureType_AC11() {
        StylingHelper.validateStylingParams(new StylingParams(
                null, null, null, null, null, "rectangular", null, null));
        StylingHelper.validateStylingParams(new StylingParams(
                null, null, null, null, null, "tabbed", null, null));
        StylingHelper.validateStylingParams(new StylingParams(
                null, null, null, null, null, "RECTANGULAR", null, null));
        StylingHelper.validateStylingParams(new StylingParams(
                null, null, null, null, null, "Tabbed", null, null));
        // No exception expected.
    }

    @Test
    public void validateStylingParams_acceptsNullOrEmptyFigureType_AC11() {
        StylingHelper.validateStylingParams(new StylingParams(
                null, null, null, null, null, null, null, null));
        StylingHelper.validateStylingParams(new StylingParams(
                null, null, null, null, null, "", null, null));
        // No exception expected.
    }

    @Test
    public void validateStylingParams_rejectsInvalidFigureType_AC11() {
        try {
            StylingHelper.validateStylingParams(new StylingParams(
                    null, null, null, null, null, "folder", null, null));
            fail("Expected ModelAccessException for invalid figureType");
        } catch (ModelAccessException e) {
            assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        }
    }

    // ------------------------------------------------------------------
    // validateStylingParams — textAlignment branch (AC-11)
    // ------------------------------------------------------------------

    @Test
    public void validateStylingParams_acceptsAllTextAlignmentValues_AC11() {
        for (String v : new String[] {"left", "centre", "center", "right", "LEFT", "Right"}) {
            StylingHelper.validateStylingParams(new StylingParams(
                    null, null, null, null, null, null, v, null));
        }
    }

    @Test
    public void validateStylingParams_rejectsInvalidTextAlignment_AC11() {
        try {
            StylingHelper.validateStylingParams(new StylingParams(
                    null, null, null, null, null, null, "justified", null));
            fail("Expected ModelAccessException for invalid textAlignment");
        } catch (ModelAccessException e) {
            assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        }
    }

    // ------------------------------------------------------------------
    // validateStylingParams — verticalTextAlignment branch (AC-11, AC-17)
    // ------------------------------------------------------------------

    @Test
    public void validateStylingParams_acceptsAllVerticalTextAlignmentValues_AC17() {
        for (String v : new String[] {"top", "centre", "center", "bottom", "TOP", "Bottom"}) {
            StylingHelper.validateStylingParams(new StylingParams(
                    null, null, null, null, null, null, null, v));
        }
    }

    @Test
    public void validateStylingParams_rejectsInvalidVerticalTextAlignment_AC17() {
        try {
            StylingHelper.validateStylingParams(new StylingParams(
                    null, null, null, null, null, null, null, "middle"));
            fail("Expected ModelAccessException for invalid verticalTextAlignment");
        } catch (ModelAccessException e) {
            assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        }
    }

    // ------------------------------------------------------------------
    // map*ToInt — verified against Archi public constants (AC-7)
    // ------------------------------------------------------------------

    @Test
    public void mapFigureTypeToInt_returnsExpectedConstants_AC7() {
        // BORDER_TABBED = 0 / BORDER_RECTANGLE = 1 per IDiagramModelGroup; same convention
        // as IDiagramModelArchimateObject.setType(int) for elements with alternate figures.
        assertEquals(IDiagramModelGroup.BORDER_TABBED, StylingHelper.mapFigureTypeToInt("tabbed"));
        assertEquals(IDiagramModelGroup.BORDER_RECTANGLE, StylingHelper.mapFigureTypeToInt("rectangular"));
        assertEquals(IDiagramModelGroup.BORDER_TABBED, StylingHelper.mapFigureTypeToInt("TABBED"));
        assertEquals(IDiagramModelGroup.BORDER_RECTANGLE, StylingHelper.mapFigureTypeToInt("Rectangular"));
    }

    @Test
    public void mapTextAlignmentToInt_returnsExpectedSwtConstants_AC7() {
        assertEquals(ITextAlignment.TEXT_ALIGNMENT_LEFT, StylingHelper.mapTextAlignmentToInt("left"));
        assertEquals(ITextAlignment.TEXT_ALIGNMENT_CENTER, StylingHelper.mapTextAlignmentToInt("centre"));
        assertEquals(ITextAlignment.TEXT_ALIGNMENT_CENTER, StylingHelper.mapTextAlignmentToInt("center"));
        assertEquals(ITextAlignment.TEXT_ALIGNMENT_RIGHT, StylingHelper.mapTextAlignmentToInt("right"));
    }

    @Test
    public void mapVerticalTextAlignmentToInt_returnsExpectedConstants_AC17() {
        assertEquals(ITextPosition.TEXT_POSITION_TOP, StylingHelper.mapVerticalTextAlignmentToInt("top"));
        assertEquals(ITextPosition.TEXT_POSITION_CENTRE, StylingHelper.mapVerticalTextAlignmentToInt("centre"));
        assertEquals(ITextPosition.TEXT_POSITION_CENTRE, StylingHelper.mapVerticalTextAlignmentToInt("center"));
        assertEquals(ITextPosition.TEXT_POSITION_BOTTOM, StylingHelper.mapVerticalTextAlignmentToInt("bottom"));
    }

    // ------------------------------------------------------------------
    // read* helpers — return null at Archi default per AC-12
    // ------------------------------------------------------------------

    @Test
    public void readFigureType_returnsNullForGroupAtDefault_AC12() {
        IDiagramModelGroup group = freshGroup();
        // EMF default for BorderType is 0 (BORDER_TABBED) for groups.
        assertEquals(IDiagramModelGroup.BORDER_TABBED, group.getBorderType());
        assertNull(StylingHelper.readFigureType(group));
    }

    @Test
    public void readFigureType_returnsRectangularForFlippedGroup_AC1() {
        IDiagramModelGroup group = freshGroup();
        group.setBorderType(IDiagramModelGroup.BORDER_RECTANGLE);
        assertEquals("rectangular", StylingHelper.readFigureType(group));
    }

    @Test
    public void readFigureType_returnsNullForGroupingElementAtDefault_AC12() {
        IDiagramModelArchimateObject obj = freshGroupingElement();
        assertEquals(0, obj.getType());
        assertNull(StylingHelper.readFigureType(obj));
    }

    @Test
    public void readFigureType_returnsRectangularForGroupingElementAtType1_AC2() {
        IDiagramModelArchimateObject obj = freshGroupingElement();
        obj.setType(1);
        assertEquals("rectangular", StylingHelper.readFigureType(obj));
    }

    @Test
    public void readFigureType_returnsNullForNonGroupingArchimateElement_AC16() {
        // ApplicationComponent has setType(int) too, but its alternate figure is
        // not "tabbed/rectangular" — the read helper deliberately returns null so
        // the DTO field is omitted for non-Grouping element classes.
        IDiagramModelArchimateObject obj = freshArchimateObject();
        obj.setType(1);
        assertNull(StylingHelper.readFigureType(obj));
    }

    @Test
    public void readFigureType_returnsNullForNote_AC12() {
        IDiagramModelNote note = IArchimateFactory.eINSTANCE.createDiagramModelNote();
        assertNull(StylingHelper.readFigureType(note));
    }

    @Test
    public void readTextAlignment_returnsNullForCenterDefault_AC12() {
        IDiagramModelGroup group = freshGroup();
        // Archi EMF default for textAlignment is TEXT_ALIGNMENT_CENTER (2) on all objects.
        assertEquals(ITextAlignment.TEXT_ALIGNMENT_CENTER, group.getTextAlignment());
        assertNull(StylingHelper.readTextAlignment(group));
    }

    @Test
    public void readTextAlignment_returnsLeftForExplicitLeft_AC3() {
        IDiagramModelGroup group = freshGroup();
        group.setTextAlignment(ITextAlignment.TEXT_ALIGNMENT_LEFT);
        assertEquals("left", StylingHelper.readTextAlignment(group));
    }

    @Test
    public void readTextAlignment_returnsRightForExplicitRight_AC3() {
        IDiagramModelGroup group = freshGroup();
        group.setTextAlignment(ITextAlignment.TEXT_ALIGNMENT_RIGHT);
        assertEquals("right", StylingHelper.readTextAlignment(group));
    }

    @Test
    public void readVerticalTextAlignment_returnsNullForTopDefault_AC17() {
        // Archi EMF default for textPosition on a freshly-created IDiagramModelGroup is
        // TEXT_POSITION_TOP = 0 (label renders in a top header band of the group).
        IDiagramModelGroup group = freshGroup();
        assertEquals(ITextPosition.TEXT_POSITION_TOP, group.getTextPosition());
        assertNull(StylingHelper.readVerticalTextAlignment(group));
    }

    @Test
    public void readVerticalTextAlignment_returnsCentreForExplicitCentre_AC17() {
        IDiagramModelGroup group = freshGroup();
        group.setTextPosition(ITextPosition.TEXT_POSITION_CENTRE);
        assertEquals("centre", StylingHelper.readVerticalTextAlignment(group));
    }

    @Test
    public void readVerticalTextAlignment_returnsBottomForExplicitBottom_AC17() {
        IDiagramModelGroup group = freshGroup();
        group.setTextPosition(ITextPosition.TEXT_POSITION_BOTTOM);
        assertEquals("bottom", StylingHelper.readVerticalTextAlignment(group));
    }

    // ------------------------------------------------------------------
    // applyStylingToNewObject — dispatches by target type (AC-1, AC-2, AC-3, AC-17)
    // ------------------------------------------------------------------

    @Test
    public void applyStylingToNewObject_setsBorderTypeOnGroup_AC1() {
        IDiagramModelGroup group = freshGroup();
        StylingHelper.applyStylingToNewObject(group, new StylingParams(
                null, null, null, null, null, "rectangular", null, null));
        assertEquals(IDiagramModelGroup.BORDER_RECTANGLE, group.getBorderType());
    }

    @Test
    public void applyStylingToNewObject_setsSetTypeOnGroupingElement_AC2() {
        IDiagramModelArchimateObject obj = freshGroupingElement();
        StylingHelper.applyStylingToNewObject(obj, new StylingParams(
                null, null, null, null, null, "rectangular", null, null));
        assertEquals(1, obj.getType());
    }

    @Test
    public void applyStylingToNewObject_silentlyIgnoresFigureTypeOnNonGroupingElement_AC16() {
        IDiagramModelArchimateObject obj = freshArchimateObject();
        int before = obj.getType();
        StylingHelper.applyStylingToNewObject(obj, new StylingParams(
                null, null, null, null, null, "rectangular", null, null));
        assertEquals(before, obj.getType());
    }

    @Test
    public void applyStylingToNewObject_silentlyIgnoresFigureTypeOnNote() {
        IDiagramModelNote note = IArchimateFactory.eINSTANCE.createDiagramModelNote();
        int beforeBorderType = note.getBorderType();
        StylingHelper.applyStylingToNewObject(note, new StylingParams(
                null, null, null, null, null, "rectangular", null, null));
        // Note's IBorderType is NOT routed by figureType (rectangle/dogear/none — different semantics).
        assertEquals(beforeBorderType, note.getBorderType());
    }

    @Test
    public void applyStylingToNewObject_setsTextAlignmentOnGroup_AC3() {
        IDiagramModelGroup group = freshGroup();
        StylingHelper.applyStylingToNewObject(group, new StylingParams(
                null, null, null, null, null, null, "left", null));
        assertEquals(ITextAlignment.TEXT_ALIGNMENT_LEFT, group.getTextAlignment());
    }

    @Test
    public void applyStylingToNewObject_setsVerticalTextAlignmentOnGroup_AC17() {
        IDiagramModelGroup group = freshGroup();
        // TOP is the default — apply CENTRE which is a non-default value to verify the apply path.
        StylingHelper.applyStylingToNewObject(group, new StylingParams(
                null, null, null, null, null, null, null, "centre"));
        assertEquals(ITextPosition.TEXT_POSITION_CENTRE, group.getTextPosition());
    }

    @Test
    public void applyStylingToNewObject_doesNothingForNullStyling() {
        IDiagramModelGroup group = freshGroup();
        int beforeBorder = group.getBorderType();
        int beforeAlign = group.getTextAlignment();
        int beforePos = group.getTextPosition();

        StylingHelper.applyStylingToNewObject(group, null);
        StylingHelper.applyStylingToNewObject(group, StylingParams.NONE);
        StylingHelper.applyStylingToNewObject(group, new StylingParams(
                null, null, null, null, null, null, null, null));

        assertEquals(beforeBorder, group.getBorderType());
        assertEquals(beforeAlign, group.getTextAlignment());
        assertEquals(beforePos, group.getTextPosition());
    }

    // ------------------------------------------------------------------
    // Test helpers
    // ------------------------------------------------------------------

    private IDiagramModelGroup freshGroup() {
        IArchimateModel model = IArchimateFactory.eINSTANCE.createArchimateModel();
        model.setDefaults();
        IDiagramModelGroup group = IArchimateFactory.eINSTANCE.createDiagramModelGroup();
        return group;
    }

    private IDiagramModelArchimateObject freshArchimateObject() {
        IArchimateModel model = IArchimateFactory.eINSTANCE.createArchimateModel();
        model.setDefaults();
        IArchimateElement element = IArchimateFactory.eINSTANCE.createApplicationComponent();
        model.getFolder(FolderType.APPLICATION).getElements().add(element);
        IDiagramModelArchimateObject obj = IArchimateFactory.eINSTANCE.createDiagramModelArchimateObject();
        obj.setArchimateElement(element);
        return obj;
    }

    private IDiagramModelArchimateObject freshGroupingElement() {
        IArchimateModel model = IArchimateFactory.eINSTANCE.createArchimateModel();
        model.setDefaults();
        IArchimateElement grouping = IArchimateFactory.eINSTANCE.createGrouping();
        model.getFolder(FolderType.OTHER).getElements().add(grouping);
        IDiagramModelArchimateObject obj = IArchimateFactory.eINSTANCE.createDiagramModelArchimateObject();
        obj.setArchimateElement(grouping);
        return obj;
    }
}
