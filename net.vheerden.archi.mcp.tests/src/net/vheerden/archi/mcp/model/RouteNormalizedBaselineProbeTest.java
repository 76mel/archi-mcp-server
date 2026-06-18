package net.vheerden.archi.mcp.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

/**
 * Static-contract pin for the {@code reroute_degraded_input_baseline}
 * pre-loop guard taxonomy. Pure JUnit 4 — no SWT, no EMF, no OSGi.
 *
 * <h2>What this test pins</h2>
 *
 * <p>This test pins the <strong>static contract</strong> of the
 * "guard, don't veto" safety net:</p>
 *
 * <ol>
 * <li>The reason constant value
 *     ({@link SpacingControlLoop#REASON_REROUTE_DEGRADED_INPUT_BASELINE} =
 *     {@code "reroute_degraded_input_baseline"}) — pinned so a rename forces a
 *     coordinated edit across the markdown + tool descriptions + the
 *     sync-guard test {@link SpacingTerminationReasonDocSyncTest}.</li>
 * <li>Sibling-symmetric pin for the 10th termination reason
 *     {@link SpacingPreconditionInfeasibilityCertificate#REASON_DENSITY_PRECONDITION_REFLOW_REQUIRED}
 *     = {@code "density_precondition_infeasible_reflow_required"} — discovered
 *     during the empirical-probe phase (2026-05-20).</li>
 * <li>The two reasons are honestly DISTINCT (the in-loop
 *     {@code density_floor_reflow_required} is also distinct from both pre-loop
 *     reasons).</li>
 * </ol>
 *
 * <h2>What this test does NOT pin (deliberately)</h2>
 *
 * <p>The original spec envisioned a JUnit-headless probe that drives
 * {@code ArchiModelAccessorImpl.routeNormalizedBaseline()} end-to-end against
 * synthetic fixtures to bucket-classify firings (genuinely-protective vs
 * vacuous-on-unrouted-draft). That end-to-end probe is infeasible headless
 * because:</p>
 *
 * <ul>
 * <li>The existing accessor-test infrastructure ({@code ArchiModelAccessorImplTest})
 *     uses a {@code StubEditorModelManager} and deliberately does NOT stand up
 *     the full Archi runtime needed for {@code assessLayout(viewId)} +
 *     {@code mutationDispatcher.dispatchImmediate} that
 *     {@code routeNormalizedBaseline} calls internally.</li>
 * <li>The {@code routeNormalizedBaseline()} method is {@code private} —
 *     refactoring it to package-private for test access would touch
 *     {@code ArchiModelAccessorImpl.java:16241-16287}, which is
 *     out of scope here.</li>
 * <li>Sibling-symmetric work deferred live smoke for
 *     identical infrastructure reasons.</li>
 * </ul>
 *
 * <p>The live empirical probe was instead executed via the MCP server
 * (2026-05-20). Full results +
 * bucket(1)-vs-bucket(2)-vs-bucket(3) classification were captured separately.</p>
 *
 * <h2>Bucket classification (from the live probe)</h2>
 *
 * <p>5 firings observed across 3 synthetic fixtures × 3 spacing-tool surfaces.
 * 0 of 5 firings produced {@code reroute_degraded_input_baseline}. Dominant
 * pre-loop intercept observed instead was the SOUND certificate
 * {@code density_precondition_infeasible_reflow_required} — a NEW bucket-(3)
 * "precert intercepts before reroute-degraded" finding. The bucket-(2)
 * vacuous-on-unrouted-draft hypothesis remains plausible for the high-fan-out
 * hub topology that produced the Retail Bank View A reference firing; the
 * synthetic fixture could not reproduce that topology headless.</p>
 *
 * <h2>Successor recommendation</h2>
 *
 * <p>If bucket(2) is observed empirically in a future smoke run,
 * the surgical narrowing path is at {@code ArchiModelAccessorImpl.routeNormalizedBaseline:16281}
 * (the {@code routeNorm.thresholdsMet() < bare.thresholdsMet()} predicate),
 * NOT inside {@link SpacingControlLoop}. Refinement A
 * (skip the comparison when the bare input is fully unrouted — all connections
 * have zero bendpoints) is a candidate, as a separate successor.</p>
 */
public class RouteNormalizedBaselineProbeTest {

    /**
     * Pins the 9th-branch reason constant value as the public LLM-visible
     * string. A rename of either the constant name OR the value triggers a
     * coordinated edit across:
     * <ul>
     *   <li>{@code routing-preconditions-checklist.md} (10-row table + decision
     *       tree branch + "When a spacing tool says it would have degraded
     *       the input" sub-section)</li>
     *   <li>3 tool descriptions in {@code ViewPlacementHandler.java} (the (i)
     *       clause in each full enumeration + the abridged iterationBudget
     *       summaries)</li>
     *   <li>{@link SpacingTerminationReasonDocSyncTest#CANONICAL_REASON_NAMES}</li>
     * </ul>
     */
    @Test
    public void reasonRerouteDegradedInputBaseline_valueShouldBePinned() {
        assertEquals(
                "reroute_degraded_input_baseline",
                SpacingControlLoop.REASON_REROUTE_DEGRADED_INPUT_BASELINE);
    }

    /**
     * Pins the 10th-branch reason constant value as the public LLM-visible
     * string. The 10th reason lives on
     * {@link SpacingPreconditionInfeasibilityCertificate}, NOT on
     * {@link SpacingControlLoop} — the sync-guard test reflects on BOTH
     * classes to catch this.
     *
     * <p>Sibling-symmetric pin to
     * {@link #reasonRerouteDegradedInputBaseline_valueShouldBePinned()}.</p>
     */
    @Test
    public void reasonDensityPreconditionReflowRequired_valueShouldBePinned() {
        assertEquals(
                "density_precondition_infeasible_reflow_required",
                SpacingPreconditionInfeasibilityCertificate
                        .REASON_DENSITY_PRECONDITION_REFLOW_REQUIRED);
    }

    /**
     * The three reflow-and-no-mutation reasons are honestly DISTINCT — none
     * collapses to another. Important because the markdown + tool
     * descriptions explicitly differentiate them in the "When a spacing tool
     * says the view needs a structural reflow" section (and the new "When a
     * spacing tool says it would have degraded the input" section); if a
     * future refactor accidentally collapsed two of these to the same value,
     * the LLM-visible MCP surface would silently drift back to the original
     * doc-gap class.
     */
    @Test
    public void threeReflowAndNoMutationReasons_shouldBeDistinct() {
        String inLoopFloor = SpacingControlLoop
                .REASON_DENSITY_FLOOR_REFLOW_REQUIRED;
        String preLoopRerouteDegraded = SpacingControlLoop
                .REASON_REROUTE_DEGRADED_INPUT_BASELINE;
        String preLoopPrecert = SpacingPreconditionInfeasibilityCertificate
                .REASON_DENSITY_PRECONDITION_REFLOW_REQUIRED;

        assertNotEquals("In-loop (h) density_floor_reflow_required must "
                + "NOT equal pre-loop (i) reroute_degraded_input_baseline",
                inLoopFloor, preLoopRerouteDegraded);
        assertNotEquals("In-loop (h) density_floor_reflow_required must "
                + "NOT equal pre-loop (j) "
                + "density_precondition_infeasible_reflow_required (both "
                + "carry 'reflow_required' suffix but mean different things "
                + "— (h) is 'loop ran, reached in-regime density floor'; (j) "
                + "is 'loop never entered, SOUND infeasibility certificate "
                + "from pure-canvas-bound formula')",
                inLoopFloor, preLoopPrecert);
        assertNotEquals("Pre-loop (i) reroute_degraded_input_baseline must "
                + "NOT equal pre-loop (j) "
                + "density_precondition_infeasible_reflow_required (both "
                + "are pre-loop sibling safety nets but trigger on different "
                + "predicates — (i) is bare-vs-routeNorm thresholdsMet "
                + "comparison; (j) is idealUniformAvg formula)",
                preLoopRerouteDegraded, preLoopPrecert);
    }

    /**
     * Pre-loop guards return 0 iterations + empty appliedDeltas[] by
     * contract. This pin documents the static contract (without driving the
     * accessor end-to-end): both pre-loop reasons (i) + (j) carry the SAME
     * shape — view-untouched, iterationCount=0, appliedDeltas=[] — so an LLM
     * agent can pattern-match either reason against the same response shape.
     *
     * <p>Sibling-symmetric with the in-loop {@code dry_run_recommendation_not_applied}
     * which also returns iterationCount=0 + appliedDeltas=[] by contract.</p>
     */
    @Test
    public void preLoopReasons_shouldShareTheUntouchedZeroIterationContract() {
        // The three pre-loop reasons (f) dryRun + (i) reroute-degraded + (j)
        // density-precondition all share the same DTO shape contract:
        //   iterationCount = 0
        //   appliedDeltas  = [] (empty)
        //   no mutation
        // Pinned here as a documentation invariant. The actual zero-iteration
        // / empty-deltas DTO shape is verified by the existing
        // pin SpacingControlLoopPartialCommitRegressionTest:917 (which holds
        // for (i)), the
        // SpacingPreconditionInfeasibilityCertificateTest (which holds for
        // (j)), and the existing dryRun-guard pins.
        //
        // This pin's value: it documents that the contract is SHARED, so the
        // markdown's "When a spacing tool says X" sub-sections can use the
        // same "iterationCount=0, appliedDeltas=[], view untouched" pattern
        // for all three pre-loop reasons.
        //
        // NOTE: (f) dry_run_recommendation_not_applied is emitted as a string
        // literal at ArchiModelAccessorImpl.java:16328 — it is NOT exposed as
        // a public constant on SpacingControlLoop (unlike (i) and (j) which
        // are public constants on their respective classes). The sync-guard
        // test SpacingTerminationReasonDocSyncTest handles (f) via its
        // CANONICAL_REASON_NAMES list + markdown-text-search; the reflective
        // seam picks up (i) + (j).
        //
        // The two complete-name pre-loop constants this test pins above
        // ((i) reroute-degraded + (j) density-precondition) are the
        // architectural surface this work (2026-05-20) added documentation
        // for. (f) dryRun's contract is verified by the existing
        // SpacingControlLoopTest dry-run pins.
        assertEquals("Sanity: (i) reroute_degraded_input_baseline value is "
                + "stable across this test + the sync-guard test",
                SpacingControlLoop.REASON_REROUTE_DEGRADED_INPUT_BASELINE,
                "reroute_degraded_input_baseline");
    }
}
