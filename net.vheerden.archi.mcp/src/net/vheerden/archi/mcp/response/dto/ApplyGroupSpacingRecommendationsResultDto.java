package net.vheerden.archi.mcp.response.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Result DTO for the apply-group-spacing-recommendations convenience tool.
 *
 * <p>Bundles "read view's current connection count + inter-group connection
 * count + current inter-group spacing → consult inter-group heuristics table
 * (connected/unconnected column selection by inter-group-connection presence)
 * → apply adjust-view-spacing with computed interGroupDelta" into one
 * transactional response. The before/after assess-layout snapshots let the
 * agent see the visual-quality impact in the same envelope.</p>
 *
 * <p>Under {@code dryRun=true} OR {@code interGroupDelta == 0} short-circuit
 * (current spacing already meets/exceeds the heuristic, OR view has fewer
 * than 2 top-level groups), {@code after} and {@code adjustResult} are null
 * and {@code noChangeReason} is populated.</p>
 *
 * @param viewId                    echoed input
 * @param dryRun                    echoed input — under true, the tool
 *                                  computes the recommendation but does NOT
 *                                  mutate (no speculative-mutate-and-undo)
 * @param totalConnectionCount      total connections on the view, sourced
 *                                  from {@code assess-layout}'s
 *                                  connectionCount field (single source of
 *                                  truth)
 * @param interGroupConnectionCount count of connections that cross a
 *                                  top-level group boundary; computed by
 *                                  walking the same connection enumeration
 *                                  as {@code connectionCount} and resolving
 *                                  each endpoint's parent top-level group
 * @param isConnected               true when {@code interGroupConnectionCount
 *                                  > 0}; selects the connected column on the
 *                                  heuristic table
 * @param currentSpacingPx          minimum inter-group spacing across
 *                                  adjacent top-level groups along the
 *                                  dominant axis, detected via
 *                                  {@code GroupLayoutCalculator.detectInterGroupSpacing(...)}
 *                                  (most-tight pair wins; aligns with
 *                                  visual-severity hierarchy where edge-
 *                                  coincident routing forms in tightest
 *                                  inter-group corridor)
 * @param targetSpacingPx           heuristic lookup based on connectionCount
 *                                  tier + connected/unconnected column (≤15 →
 *                                  80/40px, 16–30 → 100/40px, &gt;30 →
 *                                  120/60px) OR the explicit
 *                                  {@code targetSpacingOverride} when provided
 * @param interGroupDelta           computed: {@code max(0, target - current)}
 *                                  — clamped to 0 (this tool widens tight
 *                                  inter-group corridors, never shrinks
 *                                  generous corridors)
 * @param noChangeReason            populated when delta=0 OR fewer-than-2-
 *                                  top-level-groups; describes which short-
 *                                  circuit fired; null otherwise
 * @param heuristicRecommendation   when {@code targetSpacingOverride} was
 *                                  provided, this reports what the heuristic
 *                                  alone would have picked (informational);
 *                                  null when no override was provided
 * @param before                    assess-layout snapshot captured BEFORE any
 *                                  mutation (always populated)
 * @param after                     assess-layout snapshot captured AFTER the
 *                                  mutation; null under dryRun=true OR when
 *                                  interGroupDelta=0 (no mutation occurred)
 * @param adjustResult              delegate result from
 *                                  {@code adjustViewSpacing}; null under
 *                                  dryRun=true OR when interGroupDelta=0 (no
 *                                  mutation occurred)
 */
/**
 * <p><strong>Control-loop fields:</strong> {@code terminationReason} + {@code iterationCount} +
 * {@code appliedDeltas} surface the control-loop trajectory to the LLM agent.
 * Sibling-symmetric with {@link ApplyElementSpacingRecommendationsResultDto}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApplyGroupSpacingRecommendationsResultDto(
        String viewId,
        boolean dryRun,
        int totalConnectionCount,
        int interGroupConnectionCount,
        boolean isConnected,
        int currentSpacingPx,
        int targetSpacingPx,
        int interGroupDelta,
        String noChangeReason,
        Integer heuristicRecommendation,
        AssessLayoutResultDto before,
        AssessLayoutResultDto after,
        AdjustViewSpacingResultDto adjustResult,
        // Control-loop fields. Appended; backwards-compat.
        String terminationReason,
        int iterationCount,
        List<Integer> appliedDeltas,
        // Density-aware-termination field.
        // Actionable PASS-honest reflow-required diagnosis; null otherwise.
        String densityFloorDiagnosis) {

    /**
     * Backwards-compatible 16-arg constructor — preserves every
     * pre-existing 16-arg call site; delegates with
     * {@code densityFloorDiagnosis = null}). Only the control-loop
     * PASS-honest path forwards the real diagnosis via the 17-arg form.
     */
    public ApplyGroupSpacingRecommendationsResultDto(
            String viewId,
            boolean dryRun,
            int totalConnectionCount,
            int interGroupConnectionCount,
            boolean isConnected,
            int currentSpacingPx,
            int targetSpacingPx,
            int interGroupDelta,
            String noChangeReason,
            Integer heuristicRecommendation,
            AssessLayoutResultDto before,
            AssessLayoutResultDto after,
            AdjustViewSpacingResultDto adjustResult,
            String terminationReason,
            int iterationCount,
            List<Integer> appliedDeltas) {
        this(viewId, dryRun, totalConnectionCount, interGroupConnectionCount,
                isConnected, currentSpacingPx, targetSpacingPx,
                interGroupDelta, noChangeReason, heuristicRecommendation,
                before, after, adjustResult, terminationReason,
                iterationCount, appliedDeltas,
                /*densityFloorDiagnosis=*/ null);
    }

    /**
     * Backwards-compatible 13-arg constructor. Pre-Task-3 callers construct without the three
     * control-loop fields; delegating constructor populates with neutral
     * defaults ({@code null / 0 / List.of()}). The Task 3 accessor refactor
     * will switch to the canonical 16-arg form.
     */
    public ApplyGroupSpacingRecommendationsResultDto(
            String viewId,
            boolean dryRun,
            int totalConnectionCount,
            int interGroupConnectionCount,
            boolean isConnected,
            int currentSpacingPx,
            int targetSpacingPx,
            int interGroupDelta,
            String noChangeReason,
            Integer heuristicRecommendation,
            AssessLayoutResultDto before,
            AssessLayoutResultDto after,
            AdjustViewSpacingResultDto adjustResult) {
        this(viewId, dryRun, totalConnectionCount, interGroupConnectionCount,
                isConnected, currentSpacingPx, targetSpacingPx, interGroupDelta,
                noChangeReason, heuristicRecommendation, before, after,
                adjustResult,
                /*terminationReason=*/ null,
                /*iterationCount=*/ 0,
                /*appliedDeltas=*/ List.of());
    }
}
