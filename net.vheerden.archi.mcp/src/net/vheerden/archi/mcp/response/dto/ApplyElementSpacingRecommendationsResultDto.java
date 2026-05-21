package net.vheerden.archi.mcp.response.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Result DTO for the apply-element-spacing-recommendations convenience tool.
 *
 * <p>Bundles "read view's current spacing + connection count → consult inter-
 * element heuristics table → apply adjust-view-spacing with computed delta"
 * into one transactional response. The before/after assess-layout snapshots
 * let the agent see the visual-quality impact in the same envelope.</p>
 *
 * <p>Under {@code dryRun=true} OR {@code interElementDelta == 0} short-circuit
 * (current spacing already meets/exceeds the heuristic), {@code after} and
 * {@code adjustResult} are null and {@code noChangeReason} is populated.</p>
 *
 * <p><strong>Control-loop fields</strong> (Story
 * `backlog-convenience-tool-control-loop-architectural-redesign` AC-2 +
 * AC-5, 2026-05-15): {@code terminationReason} + {@code iterationCount} +
 * {@code appliedDeltas} surface the control-loop trajectory to the LLM agent.
 * On any short-circuit / dryRun / pre-control-loop branch, these fields are
 * null / 0 / empty. On the control-loop happy path, {@code terminationReason}
 * is one of the AC-5 taxonomy strings (see
 * {@code SpacingControlLoop} constants), {@code iterationCount} is the number
 * of ACCEPTED iterations (excludes any back-off-reverted attempt), and
 * {@code appliedDeltas} is the ordered list of per-iteration accepted deltas
 * (always {@code iterationCount} entries; sum equals
 * {@code interElementDelta}).</p>
 *
 * @param viewId             echoed input
 * @param dryRun             echoed input — under true, the tool computes the
 *                           recommendation but does NOT mutate (no
 *                           speculative-mutate-and-undo)
 * @param connectionCount    total connections on the view, sourced from
 *                           {@code assess-layout}'s connectionCount field
 *                           (single source of truth)
 * @param currentSpacingPx   minimum per-group element spacing across all
 *                           top-level groups, detected via
 *                           {@code GroupLayoutCalculator.detectSpacingFromPositions(...)}
 *                           (most-tight group wins; aligns with visual-severity
 *                           hierarchy where coincident segments form where
 *                           spacing is tightest)
 * @param targetSpacingPx    heuristic lookup based on connectionCount tier
 *                           (≤15 → 60px, 16–30 → 80px, &gt;30 → 100px) OR the
 *                           explicit {@code targetSpacingOverride} when provided
 * @param interElementDelta  computed: {@code max(0, target - current)} —
 *                           clamped to 0 (this tool inflates tight spacing,
 *                           never shrinks generous spacing). When the control
 *                           loop fires, this is the SUM of {@code appliedDeltas}
 *                           (i.e., the cumulative spacing increase across all
 *                           ACCEPTED iterations).
 * @param noChangeReason     populated when delta=0; describes which short-
 *                           circuit fired ("already meets heuristic" /
 *                           "view has no connections" / etc.); null otherwise
 * @param heuristicRecommendation when {@code targetSpacingOverride} was
 *                           provided, this reports what the heuristic alone
 *                           would have picked (informational); null when no
 *                           override was provided
 * @param before             assess-layout snapshot captured BEFORE any
 *                           mutation (always populated)
 * @param after              assess-layout snapshot captured AFTER the
 *                           mutation; null under dryRun=true OR when
 *                           interElementDelta=0 (no mutation occurred)
 * @param adjustResult       delegate result from {@code adjustViewSpacing};
 *                           null under dryRun=true OR when
 *                           interElementDelta=0 (no mutation occurred)
 * @param terminationReason  AC-5 taxonomy string surfaced when the control
 *                           loop fires; null on pre-loop short-circuit /
 *                           dryRun paths. See {@code SpacingControlLoop}
 *                           constants for the 5-branch taxonomy.
 * @param iterationCount     number of ACCEPTED control-loop iterations (0 on
 *                           any short-circuit; equals {@code appliedDeltas.size()}
 *                           when the loop fires).
 * @param appliedDeltas      ordered list of per-iteration accepted deltas;
 *                           empty list (not null) on any short-circuit /
 *                           dryRun path; sum equals {@code interElementDelta}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApplyElementSpacingRecommendationsResultDto(
        String viewId,
        boolean dryRun,
        int connectionCount,
        int currentSpacingPx,
        int targetSpacingPx,
        int interElementDelta,
        String noChangeReason,
        Integer heuristicRecommendation,
        AssessLayoutResultDto before,
        AssessLayoutResultDto after,
        AdjustViewSpacingResultDto adjustResult,
        // Control-loop fields (Story
        // backlog-convenience-tool-control-loop-architectural-redesign
        // AC-2 + AC-5, 2026-05-15). Appended; AC-9 backwards-compat.
        String terminationReason,
        int iterationCount,
        List<Integer> appliedDeltas,
        // Density-aware-termination field (Story
        // backlog-control-loop-density-aware-termination AC-6, 2026-05-17).
        // The actionable PASS-honest reflow-required diagnosis; null on
        // every non-PASS-honest path. Appended; backwards-compat preserved.
        String densityFloorDiagnosis) {

    /**
     * Backwards-compatible 14-arg constructor (Story
     * `backlog-control-loop-density-aware-termination` AC-6 — preserve every
     * pre-existing 14-arg call site / pin; delegates with
     * {@code densityFloorDiagnosis = null}). Only the control-loop
     * PASS-honest path forwards the real diagnosis via the 15-arg form.
     */
    public ApplyElementSpacingRecommendationsResultDto(
            String viewId,
            boolean dryRun,
            int connectionCount,
            int currentSpacingPx,
            int targetSpacingPx,
            int interElementDelta,
            String noChangeReason,
            Integer heuristicRecommendation,
            AssessLayoutResultDto before,
            AssessLayoutResultDto after,
            AdjustViewSpacingResultDto adjustResult,
            String terminationReason,
            int iterationCount,
            List<Integer> appliedDeltas) {
        this(viewId, dryRun, connectionCount, currentSpacingPx,
                targetSpacingPx, interElementDelta, noChangeReason,
                heuristicRecommendation, before, after, adjustResult,
                terminationReason, iterationCount, appliedDeltas,
                /*densityFloorDiagnosis=*/ null);
    }

    /**
     * Backwards-compatible 11-arg constructor (Story
     * `backlog-convenience-tool-control-loop-architectural-redesign`
     * AC-9 backwards-compat for the pre-control-loop callers).
     *
     * <p>Pre-Task-3 callers (existing accessor short-circuit + happy-path
     * sites + existing tool-test fixtures) construct the DTO without the
     * three control-loop fields. This delegating constructor populates the
     * appended fields with neutral defaults
     * ({@code terminationReason=null, iterationCount=0, appliedDeltas=List.of()})
     * so existing call sites compile unchanged. The Task 3 accessor refactor
     * will switch to the canonical 14-arg form to forward real values.</p>
     */
    public ApplyElementSpacingRecommendationsResultDto(
            String viewId,
            boolean dryRun,
            int connectionCount,
            int currentSpacingPx,
            int targetSpacingPx,
            int interElementDelta,
            String noChangeReason,
            Integer heuristicRecommendation,
            AssessLayoutResultDto before,
            AssessLayoutResultDto after,
            AdjustViewSpacingResultDto adjustResult) {
        this(viewId, dryRun, connectionCount, currentSpacingPx,
                targetSpacingPx, interElementDelta, noChangeReason,
                heuristicRecommendation, before, after, adjustResult,
                /*terminationReason=*/ null,
                /*iterationCount=*/ 0,
                /*appliedDeltas=*/ List.of());
    }
}
