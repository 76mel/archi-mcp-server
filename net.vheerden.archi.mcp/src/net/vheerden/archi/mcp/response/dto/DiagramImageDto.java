package net.vheerden.archi.mcp.response.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Data Transfer Object for a standalone image visual on an ArchiMate view.
 *
 * <p>Represents an {@code IDiagramModelImage} — a first-class image visual
 * node placed directly on a view (sibling to notes, groups, view-references).
 * Distinct from {@code IIconic}-based imagePath fields on element/group/note
 * view-objects (which are icon overlays on existing elements).</p>
 *
 * <p>The {@code imagePath} resolves to bytes stored in the model archive
 * (use {@code list-model-images} or {@code add-image-to-model} for
 * round-trip).</p>
 *
 * <p>Field surface intentionally minimal per Open Question 5 disposition:
 * only the bounds + identifier core plus the two fields
 * {@code IDiagramModelImage} actually surfaces in EMF
 * ({@code borderColor} via {@code IBorderObject}, {@code documentation}
 * via {@code IDocumentable}). The {@code add-image-to-view} schema advertises
 * the full 16-field styling surface (uniform sibling schemas), but
 * Archi's image renderer silently ignores most font/gradient fields —
 * this DTO omits them to avoid round-trip surprises.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiagramImageDto(
    String viewObjectId,        // EMF object ID of the image visual
    String imagePath,            // archive path (e.g. "images/<sha1>.png"), never null in a populated DTO
    int x,
    int y,
    int width,
    int height,
    String parentViewObjectId,   // null when top-level on the view; non-null when nested in a group/element
    String borderColor,          // #RRGGBB hex; null when default
    String documentation         // null when empty (mirror existing convention)
) {}
