package net.vheerden.archi.mcp.model;

import org.eclipse.gef.commands.Command;

import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelGroup;
import com.archimatetool.model.IDiagramModelObject;
import com.archimatetool.model.IFontAttribute;
import com.archimatetool.model.IGrouping;
import com.archimatetool.model.IIconic;
import com.archimatetool.model.ILineObject;
import com.archimatetool.model.ITextAlignment;
import com.archimatetool.model.ITextContent;
import com.archimatetool.model.ITextPosition;

/**
 * GEF Command that updates the visual bounds (position and size) of a
 * diagram object on a view, and optionally updates text and styling
 * for groups/notes/elements (Stories 7-8, 8-6, 11-2).
 *
 * <p>Captures the old bounds, text, and styling at construction time
 * for undo support. The caller merges partial updates (only provided
 * fields) before creating this command — the command always receives
 * complete bounds and merged styling values.</p>
 *
 * <p><strong>Story 8-6:</strong> Added optional text update support.
 * Groups use {@code setName()} for their label; notes use
 * {@code setContent()} via {@link ITextContent}.</p>
 *
 * <p><strong>Story 11-2:</strong> Added optional styling support
 * (fillColor, lineColor, fontColor, opacity, lineWidth). Uses sentinel
 * value {@code null} in StylingParams fields to indicate "no change".</p>
 *
 * <p><strong>Story {@code backlog-group-element-styling-surface}:</strong> Added
 * optional {@code figureType} (mapped to {@code IBorderType.setBorderType(int)}
 * for native groups or {@code IDiagramModelArchimateObject.setType(int)} for
 * ArchiMate elements), {@code textAlignment} (mapped to
 * {@code ITextAlignment.setTextAlignment(int)}), and {@code verticalTextAlignment}
 * (mapped to {@code ITextPosition.setTextPosition(int)}) to the styling rail.
 * All three ride the existing {@link StylingParams} parameter — same single-
 * undo-unit guarantee as the colour/opacity/lineWidth fields.</p>
 *
 * <p><strong>CRITICAL:</strong> This command MUST be executed via
 * {@code CommandStack.execute()} through {@link MutationDispatcher}.
 * Direct invocation of {@code execute()} bypasses undo tracking.</p>
 */
public class UpdateViewObjectCommand extends Command {

    private final IDiagramModelObject diagramObject;
    private final int oldX;
    private final int oldY;
    private final int oldWidth;
    private final int oldHeight;
    private final int newX;
    private final int newY;
    private final int newWidth;
    private final int newHeight;

    // Text update support (Story 8-6)
    private final String oldText;
    private final String newText;
    private final boolean hasTextChange;

    // Styling update support (Story 11-2)
    private final String oldFillColor;
    private final String newFillColor;
    private final String oldLineColor;
    private final String newLineColor;
    private final String oldFontColor;
    private final String newFontColor;
    private final int oldAlpha;
    private final int newAlpha;
    private final int oldLineWidth;
    private final int newLineWidth;
    private final boolean hasStylingChange;

    // Figure type + horizontal + vertical text alignment (story backlog-group-element-styling-surface).
    // Integer rather than int: null means "not applicable for this target type" (figureType is null
    // for objects that are neither group nor archimateObject; the two alignment fields can in principle
    // be captured for any IDiagramModelObject but we follow the same null-when-unapplicable shape).
    private final Integer oldFigureType;
    private final Integer newFigureType;
    private final Integer oldTextAlignment;
    private final Integer newTextAlignment;
    private final Integer oldVerticalTextAlignment;
    private final Integer newVerticalTextAlignment;

    // Image update support (Story C4)
    private final String oldImagePath;
    private final String newImagePath;
    private final int oldImagePosition;
    private final int newImagePosition;
    private final int oldImageSource;
    private final int newImageSource;
    private final int oldShowIcon;
    private final int newShowIcon;
    private final boolean hasImageChange;

    /**
     * Creates a command to update a diagram object's bounds (no text or styling change).
     *
     * @param diagramObject the diagram object to update
     * @param newX          the new X coordinate
     * @param newY          the new Y coordinate
     * @param newWidth      the new width
     * @param newHeight     the new height
     */
    public UpdateViewObjectCommand(IDiagramModelObject diagramObject,
                                    int newX, int newY, int newWidth, int newHeight) {
        this(diagramObject, newX, newY, newWidth, newHeight, null, null);
    }

    /**
     * Creates a command to update a diagram object's bounds and optionally its text.
     *
     * @param diagramObject the diagram object to update
     * @param newX          the new X coordinate
     * @param newY          the new Y coordinate
     * @param newWidth      the new width
     * @param newHeight     the new height
     * @param newText       new text for groups (label) or notes (content), null to leave unchanged
     */
    public UpdateViewObjectCommand(IDiagramModelObject diagramObject,
                                    int newX, int newY, int newWidth, int newHeight,
                                    String newText) {
        this(diagramObject, newX, newY, newWidth, newHeight, newText, null);
    }

    /**
     * Creates a command to update a diagram object's bounds, text, and styling.
     *
     * @param diagramObject the diagram object to update
     * @param newX          the new X coordinate
     * @param newY          the new Y coordinate
     * @param newWidth      the new width
     * @param newHeight     the new height
     * @param newText       new text for groups (label) or notes (content), null to leave unchanged
     * @param styling       styling parameters to apply, null or StylingParams.NONE for no styling change
     */
    public UpdateViewObjectCommand(IDiagramModelObject diagramObject,
                                    int newX, int newY, int newWidth, int newHeight,
                                    String newText, StylingParams styling) {
        this(diagramObject, newX, newY, newWidth, newHeight, newText, styling, null);
    }

    /**
     * Creates a command to update a diagram object's bounds, text, styling, and image.
     *
     * @param diagramObject the diagram object to update
     * @param newX          the new X coordinate
     * @param newY          the new Y coordinate
     * @param newWidth      the new width
     * @param newHeight     the new height
     * @param newText       new text for groups (label) or notes (content), null to leave unchanged
     * @param styling       styling parameters to apply, null or StylingParams.NONE for no styling change
     * @param imageParams   image parameters to apply, null or ImageParams.NONE for no image change
     */
    public UpdateViewObjectCommand(IDiagramModelObject diagramObject,
                                    int newX, int newY, int newWidth, int newHeight,
                                    String newText, StylingParams styling,
                                    ImageParams imageParams) {
        this.diagramObject = diagramObject;
        this.oldX = diagramObject.getBounds().getX();
        this.oldY = diagramObject.getBounds().getY();
        this.oldWidth = diagramObject.getBounds().getWidth();
        this.oldHeight = diagramObject.getBounds().getHeight();
        this.newX = newX;
        this.newY = newY;
        this.newWidth = newWidth;
        this.newHeight = newHeight;
        this.newText = newText;
        this.hasTextChange = (newText != null);

        // Capture old text for undo
        if (newText != null) {
            if (diagramObject instanceof IDiagramModelGroup group) {
                this.oldText = group.getName();
            } else if (diagramObject instanceof ITextContent textContent) {
                this.oldText = textContent.getContent();
            } else {
                this.oldText = null;
            }
        } else {
            this.oldText = null;
        }

        // Styling support (Story 11-2)
        this.hasStylingChange = (styling != null && styling.hasAnyValue());

        if (hasStylingChange) {
            // Capture old values for undo
            this.oldFillColor = diagramObject.getFillColor();
            this.oldLineColor = (diagramObject instanceof ILineObject lo) ? lo.getLineColor() : null;
            this.oldFontColor = (diagramObject instanceof IFontAttribute fa) ? fa.getFontColor() : null;
            this.oldAlpha = diagramObject.getAlpha();
            this.oldLineWidth = (diagramObject instanceof ILineObject lo) ? lo.getLineWidth() : 0;
            this.oldFigureType = readFigureTypeInt(diagramObject);
            this.oldTextAlignment = (diagramObject instanceof ITextAlignment ta) ? ta.getTextAlignment() : null;
            this.oldVerticalTextAlignment = (diagramObject instanceof ITextPosition tp) ? tp.getTextPosition() : null;

            // Compute new values — null in StylingParams means "no change", keep old value
            this.newFillColor = (styling.fillColor() != null) ? emptyToNull(styling.fillColor()) : oldFillColor;
            this.newLineColor = (styling.lineColor() != null)
                ? ((diagramObject instanceof ILineObject) ? emptyToNull(styling.lineColor()) : oldLineColor)
                : oldLineColor;
            this.newFontColor = (styling.fontColor() != null)
                ? ((diagramObject instanceof IFontAttribute) ? emptyToNull(styling.fontColor()) : oldFontColor)
                : oldFontColor;
            this.newAlpha = (styling.opacity() != null) ? styling.opacity() : oldAlpha;
            this.newLineWidth = (styling.lineWidth() != null)
                ? ((diagramObject instanceof ILineObject) ? styling.lineWidth() : oldLineWidth)
                : oldLineWidth;
            // Integer.valueOf wrapping avoids JLS §15.25 ternary type-unification unboxing the
            // (potentially-null) old* Integer when the int-returning branch is the other arm.
            this.newFigureType = (styling.figureType() != null && !styling.figureType().isEmpty() && oldFigureType != null)
                ? Integer.valueOf(StylingHelper.mapFigureTypeToInt(styling.figureType()))
                : oldFigureType;
            this.newTextAlignment = (styling.textAlignment() != null && !styling.textAlignment().isEmpty() && oldTextAlignment != null)
                ? Integer.valueOf(StylingHelper.mapTextAlignmentToInt(styling.textAlignment()))
                : oldTextAlignment;
            this.newVerticalTextAlignment = (styling.verticalTextAlignment() != null && !styling.verticalTextAlignment().isEmpty() && oldVerticalTextAlignment != null)
                ? Integer.valueOf(StylingHelper.mapVerticalTextAlignmentToInt(styling.verticalTextAlignment()))
                : oldVerticalTextAlignment;
        } else {
            this.oldFillColor = null;
            this.newFillColor = null;
            this.oldLineColor = null;
            this.newLineColor = null;
            this.oldFontColor = null;
            this.newFontColor = null;
            this.oldAlpha = 0;
            this.newAlpha = 0;
            this.oldLineWidth = 0;
            this.newLineWidth = 0;
            this.oldFigureType = null;
            this.newFigureType = null;
            this.oldTextAlignment = null;
            this.newTextAlignment = null;
            this.oldVerticalTextAlignment = null;
            this.newVerticalTextAlignment = null;
        }

        // Image support (Story C4)
        boolean imageChangeRequested = (imageParams != null && imageParams.hasAnyValue());

        if (imageChangeRequested && diagramObject instanceof IIconic iconic) {
            this.hasImageChange = true;
            // Capture old values for undo
            this.oldImagePath = iconic.getImagePath();
            this.oldImagePosition = iconic.getImagePosition();
            this.oldShowIcon = ImageHelper.readShowIconInt(diagramObject);
            this.oldImageSource = (diagramObject instanceof IDiagramModelArchimateObject archiObj)
                ? archiObj.getImageSource() : 0;

            // Compute new values — null in ImageParams means "no change", keep old value
            this.newImagePath = (imageParams.imagePath() != null)
                ? (imageParams.imagePath().isEmpty() ? null : imageParams.imagePath())
                : oldImagePath;
            this.newImagePosition = (imageParams.imagePosition() != null)
                ? ImageParams.positionToInt(imageParams.imagePosition())
                : oldImagePosition;
            this.newShowIcon = (imageParams.showIcon() != null)
                ? ImageParams.showIconToInt(imageParams.showIcon())
                : oldShowIcon;
            // Compute imageSource based on whether image is being set or cleared
            if (imageParams.imagePath() != null) {
                this.newImageSource = (newImagePath != null && !newImagePath.isEmpty())
                    ? IDiagramModelArchimateObject.IMAGE_SOURCE_CUSTOM
                    : IDiagramModelArchimateObject.IMAGE_SOURCE_PROFILE;
            } else {
                this.newImageSource = oldImageSource;
            }
        } else {
            this.hasImageChange = false;
            this.oldImagePath = null;
            this.newImagePath = null;
            this.oldImagePosition = 0;
            this.newImagePosition = 0;
            this.oldImageSource = 0;
            this.newImageSource = 0;
            this.oldShowIcon = 0;
            this.newShowIcon = 0;
        }

        setLabel("Update view object");
    }

    @Override
    public void execute() {
        diagramObject.setBounds(newX, newY, newWidth, newHeight);
        applyText(newText);
        if (hasStylingChange) {
            applyStyling(newFillColor, newLineColor, newFontColor, newAlpha, newLineWidth,
                    newFigureType, newTextAlignment, newVerticalTextAlignment);
        }
        if (hasImageChange) {
            applyImage(newImagePath, newImagePosition, newImageSource, newShowIcon);
        }
    }

    @Override
    public void undo() {
        diagramObject.setBounds(oldX, oldY, oldWidth, oldHeight);
        applyText(oldText);
        if (hasStylingChange) {
            applyStyling(oldFillColor, oldLineColor, oldFontColor, oldAlpha, oldLineWidth,
                    oldFigureType, oldTextAlignment, oldVerticalTextAlignment);
        }
        if (hasImageChange) {
            applyImage(oldImagePath, oldImagePosition, oldImageSource, oldShowIcon);
        }
    }

    private void applyText(String text) {
        if (!hasTextChange) return;
        if (diagramObject instanceof IDiagramModelGroup group) {
            group.setName(text);
        } else if (diagramObject instanceof ITextContent textContent) {
            textContent.setContent(text);
        }
    }

    private void applyStyling(String fillColor, String lineColor, String fontColor,
                               int alpha, int lineWidth,
                               Integer figureType, Integer textAlignment, Integer verticalTextAlignment) {
        diagramObject.setFillColor(fillColor);
        diagramObject.setAlpha(alpha);
        if (diagramObject instanceof ILineObject lo) {
            lo.setLineColor(lineColor);
            lo.setLineWidth(lineWidth);
        }
        if (diagramObject instanceof IFontAttribute fa) {
            fa.setFontColor(fontColor);
        }
        if (figureType != null) {
            if (diagramObject instanceof IDiagramModelGroup g) {
                g.setBorderType(figureType);
            } else if (diagramObject instanceof IDiagramModelArchimateObject a
                    && a.getArchimateElement() instanceof IGrouping) {
                a.setType(figureType);
            }
        }
        if (textAlignment != null && diagramObject instanceof ITextAlignment ta) {
            ta.setTextAlignment(textAlignment);
        }
        if (verticalTextAlignment != null && diagramObject instanceof ITextPosition tp) {
            tp.setTextPosition(verticalTextAlignment);
        }
    }

    private static Integer readFigureTypeInt(IDiagramModelObject obj) {
        if (obj instanceof IDiagramModelGroup g) return g.getBorderType();
        if (obj instanceof IDiagramModelArchimateObject a
                && a.getArchimateElement() instanceof IGrouping) {
            return a.getType();
        }
        return null;
    }

    private void applyImage(String imagePath, int imagePosition, int imageSource, int showIcon) {
        if (diagramObject instanceof IIconic iconic) {
            iconic.setImagePath(imagePath);
            iconic.setImagePosition(imagePosition);
        }
        if (diagramObject instanceof IDiagramModelArchimateObject archiObj) {
            archiObj.setImageSource(imageSource);
        }
        // showIcon (iconVisibleState) is on IDiagramModelObject, available for all types
        diagramObject.setIconVisibleState(showIcon);
    }

    /**
     * Converts empty string to null (Archi EMF stores null for "use default").
     */
    private static String emptyToNull(String value) {
        return (value != null && value.isEmpty()) ? null : value;
    }

    /** Package-visible for testing. */
    IDiagramModelObject getDiagramObject() { return diagramObject; }

    /** Package-visible for testing. */
    int getOldX() { return oldX; }

    /** Package-visible for testing. */
    int getOldY() { return oldY; }

    /** Package-visible for testing. */
    int getOldWidth() { return oldWidth; }

    /** Package-visible for testing. */
    int getOldHeight() { return oldHeight; }

    /** Package-visible for testing. */
    int getNewX() { return newX; }

    /** Package-visible for testing. */
    int getNewY() { return newY; }

    /** Package-visible for testing. */
    int getNewWidth() { return newWidth; }

    /** Package-visible for testing. */
    int getNewHeight() { return newHeight; }

    /** Package-visible for testing. */
    String getOldText() { return oldText; }

    /** Package-visible for testing. */
    String getNewText() { return newText; }

    /** Package-visible for testing. */
    boolean hasStylingChange() { return hasStylingChange; }

    /** Package-visible for testing. */
    String getOldFillColor() { return oldFillColor; }

    /** Package-visible for testing. */
    String getNewFillColor() { return newFillColor; }

    /** Package-visible for testing. */
    String getOldLineColor() { return oldLineColor; }

    /** Package-visible for testing. */
    String getNewLineColor() { return newLineColor; }

    /** Package-visible for testing. */
    String getOldFontColor() { return oldFontColor; }

    /** Package-visible for testing. */
    String getNewFontColor() { return newFontColor; }

    /** Package-visible for testing. */
    int getOldAlpha() { return oldAlpha; }

    /** Package-visible for testing. */
    int getNewAlpha() { return newAlpha; }

    /** Package-visible for testing. */
    int getOldLineWidth() { return oldLineWidth; }

    /** Package-visible for testing. */
    int getNewLineWidth() { return newLineWidth; }

    /** Package-visible for testing. */
    Integer getOldFigureType() { return oldFigureType; }

    /** Package-visible for testing. */
    Integer getNewFigureType() { return newFigureType; }

    /** Package-visible for testing. */
    Integer getOldTextAlignment() { return oldTextAlignment; }

    /** Package-visible for testing. */
    Integer getNewTextAlignment() { return newTextAlignment; }

    /** Package-visible for testing. */
    Integer getOldVerticalTextAlignment() { return oldVerticalTextAlignment; }

    /** Package-visible for testing. */
    Integer getNewVerticalTextAlignment() { return newVerticalTextAlignment; }

    /** Package-visible for testing. */
    boolean hasImageChange() { return hasImageChange; }

    /** Package-visible for testing. */
    String getOldImagePath() { return oldImagePath; }

    /** Package-visible for testing. */
    String getNewImagePath() { return newImagePath; }

    /** Package-visible for testing. */
    int getOldImagePosition() { return oldImagePosition; }

    /** Package-visible for testing. */
    int getNewImagePosition() { return newImagePosition; }

    /** Package-visible for testing. */
    int getOldImageSource() { return oldImageSource; }

    /** Package-visible for testing. */
    int getNewImageSource() { return newImageSource; }

    /** Package-visible for testing. */
    int getOldShowIcon() { return oldShowIcon; }

    /** Package-visible for testing. */
    int getNewShowIcon() { return newShowIcon; }
}
