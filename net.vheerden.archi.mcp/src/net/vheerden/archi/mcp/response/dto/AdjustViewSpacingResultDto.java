package net.vheerden.archi.mcp.response.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Result DTO for the adjust-view-spacing tool.
 * Combines spacing inflation results with routing metrics and assessment summary
 * so the LLM gets everything in one response.
 *
 * <p><strong>Density-aware default-resolution transparency (Story
 * RoutingPreconditions.InterElement.DensityAwareDefault):</strong> the last
 * two fields surface whether the tool's default-resolution code path fired
 * for an omitted {@code interElementDelta}. {@code resolvedInterElementDelta}
 * always reports the actual delta applied (caller value or default-resolved
 * value); {@code defaultResolutionReason} is populated when default-resolution
 * fired or when an informational no-fire condition warrants transparency
 * (e.g. zero connections, trigger-but-already-meets-target).</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdjustViewSpacingResultDto(
        String viewId,
        int groupsAdjusted,
        int elementsRepositioned,
        int connectionsRouted,
        int connectionsFailed,
        int crossingsBefore,
        int crossingsAfter,
        String overallRating,
        Map<String, String> ratingBreakdown,
        int coincidentSegmentCount,
        int nonOrthogonalTerminalCount,
        double averageSpacing,
        List<String> suggestions,
        Integer resolvedInterElementDelta,
        String defaultResolutionReason) {
}
