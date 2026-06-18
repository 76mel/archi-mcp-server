package net.vheerden.archi.mcp.response.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * Result DTO for the assess-layout tool.
 *
 * <p>{@code overlapCount} contains only sibling overlaps (genuine layout problems).
 * {@code containmentOverlaps} tracks expected ancestor-descendant overlaps (informational).
 * {@code orphanedConnections} counts connections with missing source/target view objects.
 * {@code noteOverlapCount} tracks note-element overlaps (informational, not penalizing).
 * {@code hasGroups} indicates whether the view contains group containers.
 * {@code ratingBreakdown} shows per-metric contributions to the overall rating.
 * {@code coincidentSegmentCount} tracks overlapping connection route segments.
 * {@code nonOrthogonalTerminalCount} tracks connections with diagonal terminal segments.
 * {@code contentBounds} is the axis-aligned bounding box of all visual content.
 * {@code labelTruncationCount}, {@code parentLabelObscuredCount}, {@code imageSiblingOverlapCount}
 * are informational detections. {@code parentLabelObscuredCount} promoted to layout Tier 1L;
 * {@code labelTruncationCount} promoted to routing Tier 2R.
 * {@code violatorIds} maps metric names to lists of visual object IDs that violate each metric.
 * Null/omitted when not requested (includeViolatorIds=false). Crossings excluded (emergent property).
 *
 * <p>Routing-quality metrics: {@code interiorTerminationCount},
 * {@code zigzagCount}, {@code connectionEdgeCoincidenceCount}, {@code hubPortQualityScore},
 * {@code hubPortQualityFaces}, {@code layoutRating}, {@code routingRating}. Existing field
 * positions preserved; new fields appended.</p>
 *
 * <p>Corridor Utilisation:
 * {@code corridorUtilisationScore} (occupant-count-weighted mean of per-corridor
 * {@code spread_ratio = span / available}), {@code corridorUtilisationChannels}
 * (per-corridor details when {@code includeViolatorIds=true}). Appended.</p>
 *
 * <p>parallelConnectionGap:
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
        // Routing-quality metrics (appended; backwards-compat)
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
        // Corridor utilisation (appended)
        double corridorUtilisationScore,
        List<CorridorUtilisationDetailDto> corridorUtilisationChannels,
        // parallelConnectionGap (appended; backwards-compat)
        Double vAxisParallelGapP10,
        int vAxisParallelGapNarrow25Count,
        ParallelConnectionGapDetailDto parallelConnectionGapDetail) {

    /**
     * Backwards-compatible 33-arg constructor (preserved through the routing-quality,
     * corridor-utilisation, and parallelConnectionGap appendings). Pre-redesign callers
     * (test fixtures, legacy DTO builders) construct the DTO without those fields. This
     * delegating constructor populates the appended fields with neutral defaults (zero
     * counts, null description lists, hub-port quality 1.0, layout/routing ratings
     * mirror the overall rating, corridor-utilisation score 1.0, parallelConnectionGap null/0/null) so
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
                // Routing-quality defaults + corridor-utilisation default + parallelConnectionGap defaults
                0, null, 0, null, 0, null, 1.0, null,
                overallRating, overallRating, 1.0, null,
                null, 0, null);
    }

    /**
     * Backwards-compatible 45-arg constructor.
     *
     * <p>Preserves call sites that built the DTO with the post-corridor-utilisation /
     * pre-parallelConnectionGap shape. The three new parallelConnectionGap fields populate with neutral defaults
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
                // parallelConnectionGap defaults
                null, 0, null);
    }

    /**
     * Axis-aligned bounding box of all visual content on a view.
     * Uses absolute canvas coordinates.
     */
    public record ContentBoundsDto(double x, double y, double width, double height) {}

    /**
     * Per-face hub-port allocation detail.
     * {@code face} is one of {@code LEFT}, {@code RIGHT}, {@code TOP}, {@code BOTTOM}.
     */
    public record HubFaceDetailDto(String elementId, String face, int connectionsOnFace,
                                   int distinctSlots, double quality) {}

    /**
     * Per-corridor utilisation detail. {@code axis}: 0 = vertical, 1 = horizontal.
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
     * Per-axis aggregate of parallelConnectionGap (mirror of
     * {@link net.vheerden.archi.mcp.model.LayoutAssessmentResult.ParallelConnectionGapAxisDetail}).
     * Violator IDs are surfaced via the top-level {@code violatorIds} map under
     * {@code parallelConnectionGapV} / {@code parallelConnectionGapH}.
     */
    public record ParallelConnectionGapAxisDetailDto(int qualifyingSegmentCount, Double mean,
                                                      Double min, Double p10,
                                                      int narrowGapCount15, int narrowGapCount25,
                                                      int narrowGapCount40) {}

    /**
     * Full per-axis parallelConnectionGap detail. Present only when
     * {@code includeViolatorIds=true}; null otherwise — {@code @JsonInclude(NON_NULL)}
     * on the enclosing class omits the field from JSON output in that case.
     */
    public record ParallelConnectionGapDetailDto(ParallelConnectionGapAxisDetailDto vAxis,
                                                  ParallelConnectionGapAxisDetailDto hAxis) {}
}
