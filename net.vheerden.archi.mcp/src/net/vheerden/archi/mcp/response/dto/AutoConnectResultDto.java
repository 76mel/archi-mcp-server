package net.vheerden.archi.mcp.response.dto;

import java.util.List;

/**
 * Result DTO for auto-connect-view (Story 9-6).
 *
 * @param viewId                  the view that was auto-connected
 * @param connectionsCreated      number of new visual connections created
 * @param connectionsSkipped      number of connections skipped (already exist on view)
 * @param relationshipIdsConnected list of relationship IDs for newly created connections
 * @param skippedDueToNesting     pairs whose visual connection was skipped because one
 *                                endpoint is visually nested inside the other on the view
 *                                (would render as a self-pass-through). Empty when no
 *                                such pairs were skipped — never {@code null}.
 *                                Added in Story 14-11.
 */
public record AutoConnectResultDto(
        String viewId,
        int connectionsCreated,
        int connectionsSkipped,
        List<String> relationshipIdsConnected,
        List<SkippedNestingPair> skippedDueToNesting) {

    /**
     * One entry per pair whose visual connection {@code auto-connect-view} declined
     * to draw because one endpoint was an ancestor of the other on the view.
     *
     * <p>IDs are view-object IDs (not archimate-element IDs) so the agent can pass
     * them directly to {@code remove-from-view}, {@code update-view-object}, or a
     * {@code bulk-mutate parentViewObjectId} re-nesting call without a lookup.
     *
     * @param sourceViewObjectId  view-object ID of the relationship's source endpoint
     * @param targetViewObjectId  view-object ID of the relationship's target endpoint
     * @param relationshipType    ArchiMate class name (e.g. {@code "RealizationRelationship"})
     * @param reason              fixed enum string; currently always
     *                            {@code "ancestor_descendant_on_view"}
     */
    public static record SkippedNestingPair(
            String sourceViewObjectId,
            String targetViewObjectId,
            String relationshipType,
            String reason) {}
}
