package net.vheerden.archi.mcp.response.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * Result DTO for the assess-layout tool (Story 9-2, 9-0d, 10-14, 11-15, 11-17).
 *
 * <p>{@code overlapCount} contains only sibling overlaps (genuine layout problems).
 * {@code containmentOverlaps} tracks expected ancestor-descendant overlaps (informational).
 * {@code orphanedConnections} counts connections with missing source/target view objects (Story 10-14).
 * {@code noteOverlapCount} tracks note-element overlaps (informational, not penalizing — Story 11-15).
 * {@code hasGroups} indicates whether the view contains group containers (Story 11-17).
 * {@code ratingBreakdown} shows per-metric contributions to the overall rating (Story 11-19).
 * {@code coincidentSegmentCount} tracks overlapping connection route segments (Story 11-23).
 * {@code nonOrthogonalTerminalCount} tracks connections with diagonal terminal segments (B38;
 * M1 corrected definition under Assessor.Redesign).
 * {@code contentBounds} is the axis-aligned bounding box of all visual content (Story 11-29).
 * {@code labelTruncationCount}, {@code parentLabelObscuredCount}, {@code imageSiblingOverlapCount}
 * are informational detections added by B53. Under Assessor.Redesign M6:
 * {@code parentLabelObscuredCount} promoted to layout Tier 1L; {@code labelTruncationCount}
 * promoted to routing Tier 2R.
 * {@code violatorIds} maps metric names to lists of visual object IDs that violate each metric (B55).
 * Null/omitted when not requested (includeViolatorIds=false). Crossings excluded (emergent property).
 *
 * <p>Assessor.Redesign (M2-M6, 2026-04-26): {@code interiorTerminationCount},
 * {@code zigzagCount}, {@code connectionEdgeCoincidenceCount}, {@code hubPortQualityScore},
 * {@code hubPortQualityFaces}, {@code layoutRating}, {@code routingRating}. Existing field
 * positions preserved; new fields appended.</p>
 *
 * <p>R8 Corridor Utilisation (Story WCU.RegressionTest, 2026-05-03):
 * {@code corridorUtilisationScore} (occupant-count-weighted mean of per-corridor
 * {@code spread_ratio = span / available}), {@code corridorUtilisationChannels}
 * (per-corridor details when {@code includeViolatorIds=true}). Appended.</p>
 *
 * <p>Successor D parallelConnectionGap (Story
 * backlog-assessor-add-parallelconnectiongap-metric, 2026-05-12):
 * {@code vAxisParallelGapP10} (10th-percentile V-axis parallel gap; perception-anchor
 * primary signal, null when no qualifying V segment exists),
 * {@code vAxisParallelGapNarrow25Count} (count of V-axis segments below 25 px gap),
 * {@code parallelConnectionGapDetail} (full per-axis aggregate, lazy — null unless
 * {@code includeViolatorIds=true}). Informational only — does NOT contribute to the
 * rating. Calibration-anchor pin in {@code ParallelConnectionGapMetricTest} locks
 * V4 manual gold V_p10 = 13.30 &plusmn; 0.5.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssessLayoutResultDto(
        String viewId,
        int elementCount,
        int connectionCount,
        int overlapCount,
        int containmentOverlaps,
        int edgeCrossingCount,
        double crossingsPerConnection,
        double averageSpacing,
        int alignmentScore,
        String overallRating,
        Map<String, String> ratingBreakdown,
        List<String> overlaps,
        List<String> boundaryViolations,
        List<String> connectionPassThroughs,
        List<String> offCanvasWarnings,
        int labelOverlapCount,
        List<String> labelOverlaps,
        int orphanedConnections,
        List<String> orphanedConnectionDescriptions,
        int noteOverlapCount,
        List<String> noteOverlapDescriptions,
        boolean hasGroups,
        int coincidentSegmentCount,
        int nonOrthogonalTerminalCount,
        ContentBoundsDto contentBounds,
        int labelTruncationCount,
        List<String> labelTruncations,
        int parentLabelObscuredCount,
        List<String> parentLabelObscuredDescriptions,
        int imageSiblingOverlapCount,
        List<String> imageSiblingOverlapDescriptions,
        Map<String, List<String>> violatorIds,
        List<String> suggestions,
        // Assessor.Redesign M2-M6 (appended; AC-9 backwards-compat)
        int interiorTerminationCount,
        List<String> interiorTerminationDescriptions,
        int zigzagCount,
        List<String> zigzagDescriptions,
        int connectionEdgeCoincidenceCount,
        List<String> edgeCoincidenceDescriptions,
        double hubPortQualityScore,
        List<HubFaceDetailDto> hubPortQualityFaces,
        String layoutRating,
        String routingRating,
        // R8 (appended)
        double corridorUtilisationScore,
        List<CorridorUtilisationDetailDto> corridorUtilisationChannels,
        // Successor D parallelConnectionGap (appended; AC-16 backwards-compat)
        Double vAxisParallelGapP10,
        int vAxisParallelGapNarrow25Count,
        ParallelConnectionGapDetailDto parallelConnectionGapDetail) {

    /**
     * Backwards-compatible 33-arg constructor (Assessor.Redesign AC-9; preserved through
     * M6 / R8 / Successor D appendings). Pre-redesign callers (test fixtures, legacy DTO
     * builders) construct the DTO without M2-M6 / R8 / Successor D fields. This
     * delegating constructor populates the appended fields with neutral defaults (zero
     * counts, null description lists, hub-port quality 1.0, layout/routing ratings
     * mirror the overall rating, R8 score 1.0, parallelConnectionGap null/0/null) so
     * existing call sites compile unchanged. Production code (the {@code assess-layout}
     * handler) uses the canonical 48-arg form to forward real values.
     */
    public AssessLayoutResultDto(
            String viewId, int elementCount, int connectionCount,
            int overlapCount, int containmentOverlaps, int edgeCrossingCount,
            double crossingsPerConnection, double averageSpacing, int alignmentScore,
            String overallRating, Map<String, String> ratingBreakdown,
            List<String> overlaps, List<String> boundaryViolations,
            List<String> connectionPassThroughs, List<String> offCanvasWarnings,
            int labelOverlapCount, List<String> labelOverlaps,
            int orphanedConnections, List<String> orphanedConnectionDescriptions,
            int noteOverlapCount, List<String> noteOverlapDescriptions,
            boolean hasGroups, int coincidentSegmentCount, int nonOrthogonalTerminalCount,
            ContentBoundsDto contentBounds,
            int labelTruncationCount, List<String> labelTruncations,
            int parentLabelObscuredCount, List<String> parentLabelObscuredDescriptions,
            int imageSiblingOverlapCount, List<String> imageSiblingOverlapDescriptions,
            Map<String, List<String>> violatorIds, List<String> suggestions) {
        this(viewId, elementCount, connectionCount, overlapCount, containmentOverlaps,
                edgeCrossingCount, crossingsPerConnection, averageSpacing, alignmentScore,
                overallRating, ratingBreakdown, overlaps, boundaryViolations,
                connectionPassThroughs, offCanvasWarnings, labelOverlapCount, labelOverlaps,
                orphanedConnections, orphanedConnectionDescriptions, noteOverlapCount,
                noteOverlapDescriptions, hasGroups, coincidentSegmentCount,
                nonOrthogonalTerminalCount, contentBounds,
                labelTruncationCount, labelTruncations, parentLabelObscuredCount,
                parentLabelObscuredDescriptions, imageSiblingOverlapCount,
                imageSiblingOverlapDescriptions, violatorIds, suggestions,
                // Assessor.Redesign M2-M6 defaults + R8 default + Successor D defaults
                0, null, 0, null, 0, null, 1.0, null,
                overallRating, overallRating, 1.0, null,
                null, 0, null);
    }

    /**
     * Backwards-compatible 45-arg constructor (Successor D AC-17).
     *
     * <p>Preserves call sites that built the DTO with the post-R8 / pre-Successor-D
     * shape. The three new parallelConnectionGap fields populate with neutral defaults
     * ({@code null / 0 / null}) so callers compile unchanged. Production code (the
     * {@code assess-layout} handler) uses the canonical 48-arg form to forward real
     * values.</p>
     */
    public AssessLayoutResultDto(
            String viewId, int elementCount, int connectionCount,
            int overlapCount, int containmentOverlaps, int edgeCrossingCount,
            double crossingsPerConnection, double averageSpacing, int alignmentScore,
            String overallRating, Map<String, String> ratingBreakdown,
            List<String> overlaps, List<String> boundaryViolations,
            List<String> connectionPassThroughs, List<String> offCanvasWarnings,
            int labelOverlapCount, List<String> labelOverlaps,
            int orphanedConnections, List<String> orphanedConnectionDescriptions,
            int noteOverlapCount, List<String> noteOverlapDescriptions,
            boolean hasGroups, int coincidentSegmentCount, int nonOrthogonalTerminalCount,
            ContentBoundsDto contentBounds,
            int labelTruncationCount, List<String> labelTruncations,
            int parentLabelObscuredCount, List<String> parentLabelObscuredDescriptions,
            int imageSiblingOverlapCount, List<String> imageSiblingOverlapDescriptions,
            Map<String, List<String>> violatorIds, List<String> suggestions,
            int interiorTerminationCount, List<String> interiorTerminationDescriptions,
            int zigzagCount, List<String> zigzagDescriptions,
            int connectionEdgeCoincidenceCount, List<String> edgeCoincidenceDescriptions,
            double hubPortQualityScore, List<HubFaceDetailDto> hubPortQualityFaces,
            String layoutRating, String routingRating,
            double corridorUtilisationScore,
            List<CorridorUtilisationDetailDto> corridorUtilisationChannels) {
        this(viewId, elementCount, connectionCount, overlapCount, containmentOverlaps,
                edgeCrossingCount, crossingsPerConnection, averageSpacing, alignmentScore,
                overallRating, ratingBreakdown, overlaps, boundaryViolations,
                connectionPassThroughs, offCanvasWarnings, labelOverlapCount, labelOverlaps,
                orphanedConnections, orphanedConnectionDescriptions, noteOverlapCount,
                noteOverlapDescriptions, hasGroups, coincidentSegmentCount,
                nonOrthogonalTerminalCount, contentBounds,
                labelTruncationCount, labelTruncations, parentLabelObscuredCount,
                parentLabelObscuredDescriptions, imageSiblingOverlapCount,
                imageSiblingOverlapDescriptions, violatorIds, suggestions,
                interiorTerminationCount, interiorTerminationDescriptions,
                zigzagCount, zigzagDescriptions,
                connectionEdgeCoincidenceCount, edgeCoincidenceDescriptions,
                hubPortQualityScore, hubPortQualityFaces,
                layoutRating, routingRating,
                corridorUtilisationScore, corridorUtilisationChannels,
                // Successor D defaults
                null, 0, null);
    }

    /**
     * Axis-aligned bounding box of all visual content on a view (Story 11-29).
     * Uses absolute canvas coordinates.
     */
    public record ContentBoundsDto(double x, double y, double width, double height) {}

    /**
     * Per-face hub-port allocation detail (Assessor.Redesign M5).
     * {@code face} is one of {@code LEFT}, {@code RIGHT}, {@code TOP}, {@code BOTTOM}.
     */
    public record HubFaceDetailDto(String elementId, String face, int connectionsOnFace,
                                   int distinctSlots, double quality) {}

    /**
     * Per-corridor R8 utilisation detail. {@code axis}: 0 = vertical, 1 = horizontal.
     * {@code sharedCoord}: occupant midpoint {@code (min + max) / 2.0} of per-occupant
     * shared-coords (NOT the corridor's geometric centre). {@code wallLow/HighId}:
     * AssessmentNode IDs of the bracketing walls in the perpendicular axis.
     * {@code spreadRatio} is clamped to [0.0, 1.0]; pre-clamp values &gt; 1.0 indicate
     * wall-hugging occupants (already flagged by M4 edge-coincidence).
     */
    public record CorridorUtilisationDetailDto(int axis, double sharedCoord, String wallLowId,
                                                String wallHighId, int occupantCount,
                                                double span, double available, double spreadRatio) {}

    /**
     * Per-axis aggregate of Successor D parallelConnectionGap (mirror of
     * {@link net.vheerden.archi.mcp.model.LayoutAssessmentResult.ParallelConnectionGapAxisDetail}).
     * Violator IDs are surfaced via the top-level {@code violatorIds} map under
     * {@code parallelConnectionGapV} / {@code parallelConnectionGapH}.
     */
    public record ParallelConnectionGapAxisDetailDto(int qualifyingSegmentCount, Double mean,
                                                      Double min, Double p10,
                                                      int narrowGapCount15, int narrowGapCount25,
                                                      int narrowGapCount40) {}

    /**
     * Full per-axis Successor D parallelConnectionGap detail. Present only when
     * {@code includeViolatorIds=true}; null otherwise — {@code @JsonInclude(NON_NULL)}
     * on the enclosing class omits the field from JSON output in that case.
     */
    public record ParallelConnectionGapDetailDto(ParallelConnectionGapAxisDetailDto vAxis,
                                                  ParallelConnectionGapAxisDetailDto hAxis) {}
}
