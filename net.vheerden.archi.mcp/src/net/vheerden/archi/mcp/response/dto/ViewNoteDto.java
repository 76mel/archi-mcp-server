package net.vheerden.archi.mcp.response.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Data Transfer Object for a text note on a view (Story 8-6, 11-2).
 *
 * <p>Notes are diagram-only objects (not ArchiMate model elements) used to
 * annotate design decisions, add comments, or provide context on diagrams.</p>
 *
 * <p><strong>Story 11-2:</strong> Added optional styling fields (fillColor,
 * lineColor, fontColor, opacity, lineWidth). Omitted from JSON when null.</p>
 *
 * <p><strong>Story C3 (v1.6):</strong> Closes the read-back symmetry gap. Adds
 * {@code labelExpression} (14-1 G4) and {@code fontName}/{@code fontSize}/{@code fontStyle}/
 * {@code gradient}/{@code borderType}/{@code deriveLineColor}/{@code outlineOpacity}/
 * {@code lineStyle} (14-2 G5) so that {@code get-view-contents} surfaces every v1.5
 * styling field that the write tools accept. {@code borderType} (dogear/rectangle/none)
 * is the note-specific 14-2 G5 surface; {@code figureType} remains absent per the
 * 14-2 Task-2.3 disposition (notes do not expose figureType). All fields are omitted
 * from JSON when null via NON_NULL.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ViewNoteDto(
    String viewObjectId,
    String content,
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
    String note,
    String imagePath,
    String imagePosition,
    String showIcon,
    String textAlignment,
    String verticalTextAlignment,
    String labelExpression,
    String fontName,
    Integer fontSize,
    String fontStyle,
    String gradient,
    String borderType,
    Boolean deriveLineColor,
    Integer outlineOpacity,
    String lineStyle
) {

    /**
     * Constructor matching the pre-{@code backlog-group-element-styling-surface} 16-field shape
     * (styling + note + image fields, no textAlignment/verticalTextAlignment). Delegates to the
     * canonical 27-field constructor with eleven trailing nulls
     * (2 predecessor styling row + 9 Story C3 v1.5 styling fields). Notes do not surface
     * figureType per Task-2.3 disposition.
     */
    public ViewNoteDto(
            String viewObjectId, String content,
            int x, int y, int width, int height,
            String parentViewObjectId,
            String fillColor, String lineColor, String fontColor,
            Integer opacity, Integer lineWidth,
            String note, String imagePath, String imagePosition, String showIcon) {
        this(viewObjectId, content, x, y, width, height,
                parentViewObjectId,
                fillColor, lineColor, fontColor, opacity, lineWidth,
                note, imagePath, imagePosition, showIcon,
                null, null,
                null, null, null, null, null, null, null, null, null);
    }

    /**
     * Constructor matching the pre-{@code Story C3} 18-field shape
     * (predecessor styling row added textAlignment/verticalTextAlignment, but no Story C3
     * labelExpression / fontName / fontSize / fontStyle / gradient / borderType /
     * deriveLineColor / outlineOpacity / lineStyle). Delegates to the canonical 27-field
     * constructor with nine trailing nulls for the Story C3 v1.5 styling fields. Preserves
     * existing prepareAddNoteToView call sites byte-identically.
     */
    public ViewNoteDto(
            String viewObjectId, String content,
            int x, int y, int width, int height,
            String parentViewObjectId,
            String fillColor, String lineColor, String fontColor,
            Integer opacity, Integer lineWidth,
            String note, String imagePath, String imagePosition, String showIcon,
            String textAlignment, String verticalTextAlignment) {
        this(viewObjectId, content, x, y, width, height,
                parentViewObjectId,
                fillColor, lineColor, fontColor, opacity, lineWidth,
                note, imagePath, imagePosition, showIcon,
                textAlignment, verticalTextAlignment,
                null, null, null, null, null, null, null, null, null);
    }

    /**
     * Full constructor without image fields (backward compat with styling + note).
     */
    public ViewNoteDto(
            String viewObjectId,
            String content,
            int x, int y, int width, int height,
            String parentViewObjectId,
            String fillColor, String lineColor, String fontColor,
            Integer opacity, Integer lineWidth,
            String note) {
        this(viewObjectId, content, x, y, width, height,
                parentViewObjectId,
                fillColor, lineColor, fontColor, opacity, lineWidth,
                note, null, null, null);
    }

    /**
     * Full constructor without note or image fields (backward compat with styling).
     */
    public ViewNoteDto(
            String viewObjectId,
            String content,
            int x, int y, int width, int height,
            String parentViewObjectId,
            String fillColor, String lineColor, String fontColor,
            Integer opacity, Integer lineWidth) {
        this(viewObjectId, content, x, y, width, height,
                parentViewObjectId,
                fillColor, lineColor, fontColor, opacity, lineWidth, null);
    }

    /**
     * Convenience constructor without styling, note, or image fields (backward compat).
     * All optional fields default to null (omitted from JSON via NON_NULL).
     */
    public ViewNoteDto(
            String viewObjectId,
            String content,
            int x, int y, int width, int height,
            String parentViewObjectId) {
        this(viewObjectId, content, x, y, width, height,
                parentViewObjectId,
                null, null, null, null, null, null);
    }
}
