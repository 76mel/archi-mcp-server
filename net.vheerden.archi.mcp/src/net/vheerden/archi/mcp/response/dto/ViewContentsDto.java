package net.vheerden.archi.mcp.response.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Data Transfer Object for the contents of an ArchiMate view (diagram).
 *
 * <p>Returned by the get-view-contents command. Contains the elements
 * and relationships present in a view, plus visual position metadata
 * for each element and connection routing metadata.</p>
 *
 * <p><strong>Story 8-6:</strong> Added groups and notes arrays for
 * visual grouping rectangles and text annotations on diagrams.</p>
 *
 * <p><strong>Story 14-8 / G16:</strong> Added images array for standalone
 * {@code IDiagramModelImage} visuals (sibling to notes / groups /
 * view-references). Populated only when the view contains image visuals;
 * null otherwise (NON_NULL omits from JSON).</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ViewContentsDto(
    String viewId,
    String viewName,
    String viewpoint,
    String connectionRouterType,
    List<ElementDto> elements,
    List<RelationshipDto> relationships,
    List<ViewNodeDto> visualMetadata,
    List<ViewConnectionDto> connections,
    List<ViewGroupDto> groups,
    List<ViewNoteDto> notes,
    List<DiagramImageDto> images
) {
    /**
     * Story 14-8 back-compat constructor — preserves the 10-arg ctor surface
     * from Story 8-6 by delegating with {@code null} for the new {@code images}
     * field (NON_NULL omits from JSON, so views without image visuals serialise
     * byte-identically to their Story 8-6 form).
     */
    public ViewContentsDto(String viewId, String viewName, String viewpoint,
            String connectionRouterType,
            List<ElementDto> elements, List<RelationshipDto> relationships,
            List<ViewNodeDto> visualMetadata, List<ViewConnectionDto> connections,
            List<ViewGroupDto> groups, List<ViewNoteDto> notes) {
        this(viewId, viewName, viewpoint, connectionRouterType, elements, relationships,
                visualMetadata, connections, groups, notes, null);
    }

    /**
     * Convenience constructor without groups, notes, images, and
     * connectionRouterType (backward compat with pre-Story-8-6 callers).
     */
    public ViewContentsDto(String viewId, String viewName, String viewpoint,
            List<ElementDto> elements, List<RelationshipDto> relationships,
            List<ViewNodeDto> visualMetadata, List<ViewConnectionDto> connections) {
        this(viewId, viewName, viewpoint, null, elements, relationships,
                visualMetadata, connections, null, null, null);
    }
}
