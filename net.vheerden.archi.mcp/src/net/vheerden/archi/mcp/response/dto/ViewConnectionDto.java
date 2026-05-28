package net.vheerden.archi.mcp.response.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Data Transfer Object for a visual connection on a view (Story 7-7, 8-0d, 11-2).
 *
 * <p>Represents a connection between two view objects, linked to a model
 * relationship. The optional bendpoints list defines routing control points;
 * null means a straight line (omitted from JSON via NON_NULL).</p>
 *
 * <p><strong>Story 8-0d:</strong> Added absolute bendpoint coordinates and
 * anchor points for easier LLM consumption. The server converts between
 * absolute canvas coordinates and Archi's native relative-offset format.</p>
 *
 * <p><strong>Story 11-2:</strong> Added optional connection styling fields
 * (lineColor, lineWidth, fontColor). Omitted from JSON when null.</p>
 *
 * <p><strong>Story 14-2 (G5):</strong> Added optional typography fields
 * ({@code fontName}, {@code fontSize}, {@code fontStyle}). Note: {@code lineStyle} is a
 * view-object property in Archi 5.8 (see {@link ViewObjectDto}), NOT a connection
 * property — Task-9 empirical correction.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ViewConnectionDto(
    String viewConnectionId,
    String relationshipId,
    String relationshipType,
    String sourceViewObjectId,
    String targetViewObjectId,
    List<BendpointDto> bendpoints,
    List<AbsoluteBendpointDto> absoluteBendpoints,
    AnchorPointDto sourceAnchor,
    AnchorPointDto targetAnchor,
    Integer textPosition,
    String lineColor,
    Integer lineWidth,
    String fontColor,
    Boolean nameVisible,
    String fontName,
    Integer fontSize,
    String fontStyle
) {

    /**
     * Constructor matching the pre-{@code 14-2} 14-field shape (no typography).
     * Delegates to the canonical 17-field constructor with three trailing nulls.
     * Preserves existing call sites byte-identically.
     */
    public ViewConnectionDto(
            String viewConnectionId,
            String relationshipId,
            String relationshipType,
            String sourceViewObjectId,
            String targetViewObjectId,
            List<BendpointDto> bendpoints,
            List<AbsoluteBendpointDto> absoluteBendpoints,
            AnchorPointDto sourceAnchor,
            AnchorPointDto targetAnchor,
            Integer textPosition,
            String lineColor,
            Integer lineWidth,
            String fontColor,
            Boolean nameVisible) {
        this(viewConnectionId, relationshipId, relationshipType,
                sourceViewObjectId, targetViewObjectId, bendpoints,
                absoluteBendpoints, sourceAnchor, targetAnchor, textPosition,
                lineColor, lineWidth, fontColor, nameVisible,
                null, null, null);
    }

    /**
     * Convenience constructor without absolute bendpoint or styling fields (Story 7-7 compat).
     * New fields default to null (omitted from JSON via NON_NULL).
     */
    public ViewConnectionDto(
            String viewConnectionId,
            String relationshipId,
            String relationshipType,
            String sourceViewObjectId,
            String targetViewObjectId,
            List<BendpointDto> bendpoints) {
        this(viewConnectionId, relationshipId, relationshipType,
                sourceViewObjectId, targetViewObjectId, bendpoints,
                null, null, null, null, null, null, null, null,
                null, null, null);
    }

    /**
     * Convenience constructor without styling fields (Story 8-0d compat).
     * Styling fields default to null (omitted from JSON via NON_NULL).
     */
    public ViewConnectionDto(
            String viewConnectionId,
            String relationshipId,
            String relationshipType,
            String sourceViewObjectId,
            String targetViewObjectId,
            List<BendpointDto> bendpoints,
            List<AbsoluteBendpointDto> absoluteBendpoints,
            AnchorPointDto sourceAnchor,
            AnchorPointDto targetAnchor,
            Integer textPosition) {
        this(viewConnectionId, relationshipId, relationshipType,
                sourceViewObjectId, targetViewObjectId, bendpoints,
                absoluteBendpoints, sourceAnchor, targetAnchor, textPosition,
                null, null, null, null,
                null, null, null);
    }
}
