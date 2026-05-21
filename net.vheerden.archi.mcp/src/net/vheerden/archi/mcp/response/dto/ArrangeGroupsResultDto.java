package net.vheerden.archi.mcp.response.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ArrangeGroupsResultDto(
    String viewId,
    int groupsPositioned,
    int layoutWidth,
    int layoutHeight,
    Integer columnsUsed,
    String arrangement,
    Integer resolvedSpacing,
    String defaultResolutionReason,
    int standaloneElementsPlaced
) {
    /**
     * Backwards-compatible constructor (Story backlog-arrange-groups-standalone-element-lane).
     * Pre-existing callers that pre-date the {@code standaloneElementsPlaced} field default it to 0.
     */
    public ArrangeGroupsResultDto(
            String viewId,
            int groupsPositioned,
            int layoutWidth,
            int layoutHeight,
            Integer columnsUsed,
            String arrangement,
            Integer resolvedSpacing,
            String defaultResolutionReason) {
        this(viewId, groupsPositioned, layoutWidth, layoutHeight, columnsUsed,
                arrangement, resolvedSpacing, defaultResolutionReason, 0);
    }
}
