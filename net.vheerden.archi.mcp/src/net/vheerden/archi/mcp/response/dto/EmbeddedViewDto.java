package net.vheerden.archi.mcp.response.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Data Transfer Object for an embedded view-reference visual object on a view
 * (Story 14-6 / G8).
 *
 * <p>A view-reference is a typed {@code IDiagramModelReference} visual object
 * that embeds another ArchiMate view as a clickable thumbnail — the
 * agent-driven equivalent of Archi GUI's drag-view-onto-view operation. The
 * referenced view's <em>name</em> is read dynamically by Archi's figure at
 * render time ({@code referencedModel.getName()}), so this DTO intentionally
 * does NOT carry a {@code referencedViewName} field; renaming the referenced
 * view auto-updates every embedding visual without a separate mutation.</p>
 *
 * <p>Field {@code referencedViewId} may be omitted from JSON (via
 * {@code @JsonInclude(NON_NULL)}) if Archi's EMF model has cleaned the
 * cross-reference after a delete-cascade — see Task 0.9 disposition in the
 * Story 14-6 spec.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmbeddedViewDto(
    String viewObjectId,
    String referencedViewId,
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
    String fontName,
    Integer fontSize,
    String fontStyle,
    String gradient,
    Boolean deriveLineColor,
    Integer outlineOpacity,
    String lineStyle,
    String textAlignment,
    String verticalTextAlignment,
    String note
) {

    /**
     * Convenience constructor without styling (back-compat with the bounds-only
     * shape). All styling fields default to null (omitted from JSON via NON_NULL).
     */
    public EmbeddedViewDto(
            String viewObjectId,
            String referencedViewId,
            int x, int y, int width, int height,
            String parentViewObjectId) {
        this(viewObjectId, referencedViewId, x, y, width, height,
                parentViewObjectId,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null);
    }
}
