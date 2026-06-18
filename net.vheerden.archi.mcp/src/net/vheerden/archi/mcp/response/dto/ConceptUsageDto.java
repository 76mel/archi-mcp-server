package net.vheerden.archi.mcp.response.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Data Transfer Object for the cross-view "where used" footprint of an ArchiMate
 * concept (element or relationship).
 *
 * <p>Returned by the find-concept-usage tool. Given a concept
 * ID, lists every view + visual object/connection that references the concept.
 * Inverse of get-view-contents (which is view-&gt;contents); find-concept-usage
 * is concept-&gt;views.</p>
 *
 * <p><strong>Field semantics:</strong></p>
 * <ul>
 *   <li>{@code conceptKind} — {@code "element"} for IArchimateElement,
 *       {@code "relationship"} for IArchimateRelationship.</li>
 *   <li>{@code viewReferenceCount} — number of distinct views the concept appears on.</li>
 *   <li>{@code visualReferenceCount} — total number of visual objects/connections
 *       across all views (a concept placed multiple times on one view counts each
 *       placement separately).</li>
 *   <li>{@code embeddingViewReferences} — reserved for future
 *       view-reference embedding; always {@code null} today. When that ships,
 *       this field will list views that embed this concept's view as a thumbnail
 *       via {@code view.createViewReference()}. The field is typed
 *       {@code List<?>} (wildcard) so it can later populate with a concrete
 *       {@code List<EmbeddingViewReferenceDto>} without changing the record's
 *       declared type — additive, non-breaking.</li>
 * </ul>
 *
 * <p><strong>Ordering invariants:</strong> {@code viewReferences} ordered by
 * {@code viewName} ascending with {@code viewId} as tiebreaker;
 * {@code visualObjects} within a view ordered by {@code viewObjectId} ascending.
 * Deterministic regardless of EMF iteration order.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConceptUsageDto(
    String conceptId,
    String conceptName,
    String conceptType,
    String conceptKind,
    int viewReferenceCount,
    int visualReferenceCount,
    List<ViewReferenceDto> viewReferences,
    List<?> embeddingViewReferences
) {
    /**
     * Per-view reference: one view that contains at least one visual placement
     * of the concept, plus the list of placements within that view.
     *
     * @param viewKind one of {@code "archimate" | "sketch" | "other"}. Canvas
     *        views collapse to {@code "other"} because {@code ICanvasModel} is
     *        in a separate Eclipse plugin not on this bundle's classpath.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ViewReferenceDto(
        String viewId,
        String viewName,
        String viewpointType,
        String viewKind,
        List<VisualObjectReferenceDto> visualObjects
    ) {}

    /**
     * One visual placement of the concept on a view.
     *
     * @param viewObjectId the diagram-object ID (NOT the concept ID — used for
     *        cross-reference with get-view-contents).
     * @param kind {@code "object"} for an element placement,
     *        {@code "connection"} for a relationship line.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VisualObjectReferenceDto(
        String viewObjectId,
        String kind
    ) {}
}
