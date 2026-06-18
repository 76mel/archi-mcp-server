package net.vheerden.archi.mcp.response.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Data Transfer Object for visual position metadata of an element in a view.
 *
 * <p>Captures the view object ID, element ID, and x, y, width, height of
 * an element's visual representation in a diagram. The viewObjectId
 * uniquely identifies the diagram object on the view (distinct from the
 * model element ID), enabling connection placement via add-connection-to-view.</p>
 *
 * <p>Added parentViewObjectId to track elements
 * nested inside visual groups.</p>
 *
 * <p>Added optional styling fields (fillColor,
 * lineColor, fontColor, opacity, lineWidth). Omitted from JSON when null.</p>
 *
 * <p>Added optional image fields (imagePath,
 * imagePosition, showIcon). Omitted from JSON when null.</p>
 *
 * <p><strong>v1.6:</strong> Closes the read-back symmetry gap surfaced by the
 * 2026-05-28 1708 labelExpression probe. Adds {@code figureType}, {@code textAlignment},
 * {@code verticalTextAlignment} (predecessor styling row), {@code labelExpression},
 * and {@code fontName}/{@code fontSize}/{@code fontStyle}/{@code gradient}/
 * {@code deriveLineColor}/{@code outlineOpacity}/{@code lineStyle} so that
 * {@code get-view-contents} surfaces every v1.5 styling field that the corresponding
 * write tools accept. Field order mirrors {@link ViewObjectDto} so JSON property order
 * is identical across add-/update-/read paths. {@code borderType} is intentionally
 * absent (note-only). All fields are omitted from JSON when null via NON_NULL.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ViewNodeDto(
    String viewObjectId,
    String elementId,
    int x,
    int y,
    int width,
    int height,
    String parentViewObjectId,
    String fillColor,
    String lineColor,
    String fontColor,
    Integer opacity,
    Integer lineWidth,
    String imagePath,
    String imagePosition,
    String showIcon,
    String figureType,
    String textAlignment,
    String verticalTextAlignment,
    String labelExpression,
    String fontName,
    Integer fontSize,
    String fontStyle,
    String gradient,
    Boolean deriveLineColor,
    Integer outlineOpacity,
    String lineStyle
) {
    /**
     * Convenience constructor without image fields or v1.5 styling (backward compat).
     * Delegates to the canonical 26-field constructor with 14 trailing nulls
     * (3 image + 11 v1.5 styling fields).
     */
    public ViewNodeDto(String viewObjectId, String elementId,
            int x, int y, int width, int height,
            String parentViewObjectId,
            String fillColor, String lineColor, String fontColor,
            Integer opacity, Integer lineWidth) {
        this(viewObjectId, elementId, x, y, width, height,
                parentViewObjectId, fillColor, lineColor, fontColor,
                opacity, lineWidth, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Convenience constructor without styling or image fields (backward compat).
     */
    public ViewNodeDto(String viewObjectId, String elementId,
            int x, int y, int width, int height,
            String parentViewObjectId) {
        this(viewObjectId, elementId, x, y, width, height,
                parentViewObjectId, null, null, null, null, null);
    }

    /**
     * Convenience constructor without parentViewObjectId, styling, or image (top-level element).
     */
    public ViewNodeDto(String viewObjectId, String elementId,
            int x, int y, int width, int height) {
        this(viewObjectId, elementId, x, y, width, height, null);
    }
}
