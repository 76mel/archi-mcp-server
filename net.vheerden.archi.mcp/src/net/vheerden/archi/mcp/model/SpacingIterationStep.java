package net.vheerden.archi.mcp.model;

import java.util.Objects;

/**
 * Immutable per-iteration forensic record produced by
 * {@link SpacingControlLoop#iterate} for every iteration step executed (whether
 * accepted or backed-off). The ordered list of these records IS the loop's
 * forensic record of one tool invocation.
 *
 * <p><strong>Forensic-only — NOT surfaced in the MCP tool result envelope.</strong>
 * The response DTO surfaces only the
 * aggregate {@code terminationReason} + {@code iterationCount} +
 * {@code appliedDeltas} (the per-step deltas of ACCEPTED iterations).</p>
 *
 * <p><strong>Field semantics:</strong>
 * <ul>
 *   <li>{@code stepIndex} — zero-based; iteration 0 is the first attempt.</li>
 *   <li>{@code deltaApplied} — the per-step delta in pixels applied for THIS
 *       iteration (NOT cumulative). When {@code backedOff==true} this is the
 *       delta that was attempted + reverted.</li>
 *   <li>{@code spacingAfterStepPx} — cumulative spacing after this step's
 *       Apply (would have been observed when reading state post-execute()).
 *       When {@code backedOff==true} this is the spacing the loop briefly
 *       observed before reverting.</li>
 *   <li>{@code preStateMetrics} — observation BEFORE this step's Apply (i.e.,
 *       the cumulative state of all prior accepted iterations).</li>
 *   <li>{@code postStateMetrics} — observation AFTER this step's Apply.</li>
 *   <li>{@code thresholdsMetBefore} — convenience field; {@code preStateMetrics.thresholdsMet()}.</li>
 *   <li>{@code thresholdsMetAfter} — convenience field; {@code postStateMetrics.thresholdsMet()}.</li>
 *   <li>{@code thresholdsMetDelta} — signed delta:
 *       {@code thresholdsMetAfter - thresholdsMetBefore}.</li>
 *   <li>{@code backedOff} — true iff this step was REVERTED (regression
 *       detected by the back-off predicate; the step's mutation command was
 *       undone + discarded from the accepted list).</li>
 *   <li>{@code backOffReason} — populated only when {@code backedOff==true};
 *       human-readable explanation. Null when {@code backedOff==false}.</li>
 * </ul></p>
 *
 * <p>Pinned by {@code SpacingIterationStepTest} (≥4 @Test methods
 * covering record equality + null-safety + ordering).</p>
 */
public record SpacingIterationStep(
        int stepIndex,
        int deltaApplied,
        int spacingAfterStepPx,
        LayoutMetrics preStateMetrics,
        LayoutMetrics postStateMetrics,
        int thresholdsMetBefore,
        int thresholdsMetAfter,
        int thresholdsMetDelta,
        boolean backedOff,
        String backOffReason) {

    /** Compact constructor enforces non-null metric snapshots. */
    public SpacingIterationStep {
        Objects.requireNonNull(preStateMetrics,
                "preStateMetrics cannot be null");
        Objects.requireNonNull(postStateMetrics,
                "postStateMetrics cannot be null");
    }
}
