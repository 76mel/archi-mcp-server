package net.vheerden.archi.mcp.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * JUnit pin for {@link ElementSpacingHeuristic#targetSpacingForConnectionCount(int, boolean)}.
 * Pure-unit tests (no OSGi).
 *
 * <p>Pins the entire 2x3 heuristic table (no-large-hubs / has-large-hubs ×
 * ≤15 / 16-30 / &gt;30) plus the four 15/16/30/31 boundary transitions.
 * Sibling-symmetric with {@link GroupSpacingHeuristicTest}.</p>
 *
 * <p>Per {@code feedback_metric_and_regression_test_together.md}: any change
 * to the heuristic table requires a coordinated edit across (1) the markdown
 * resource {@code archimate-view-patterns.md} §2, (2)
 * {@link ElementSpacingHeuristic}, (3) this test, (4) the four production
 * callsites that derive {@code hasLargeHubs} upstream and forward it. Edit
 * one without the others and this test fails until they all agree.</p>
 *
 * <p>Per {@code feedback_ceiling_preserved_framing.md} (inversely): the
 * no-large-hubs branch values (60/80/100) are byte-identical to pre-Row-C
 * behaviour. Pre-Row-C callers passing {@code hasLargeHubs=false} (e.g.,
 * views with no element at &gt;6 connections) get the EXACT SAME values as
 * before. This test pins both columns side-by-side so the back-compat claim
 * is mechanically enforced.</p>
 */
public class ElementSpacingHeuristicTest {

    // ---- 2x3 branch matrix — no-large-hubs column (60/80/100) ----

    @Test
    public void targetSpacing_tier1_noLargeHubs_returns60() {
        assertEquals(60,
                ElementSpacingHeuristic.targetSpacingForConnectionCount(10, false));
    }

    @Test
    public void targetSpacing_tier2_noLargeHubs_returns80() {
        assertEquals(80,
                ElementSpacingHeuristic.targetSpacingForConnectionCount(20, false));
    }

    @Test
    public void targetSpacing_tier3_noLargeHubs_returns100() {
        assertEquals(100,
                ElementSpacingHeuristic.targetSpacingForConnectionCount(40, false));
    }

    // ---- 2x3 branch matrix — has-large-hubs column (80/100/120) ----

    @Test
    public void targetSpacing_tier1_hasLargeHubs_returns80() {
        assertEquals(80,
                ElementSpacingHeuristic.targetSpacingForConnectionCount(10, true));
    }

    @Test
    public void targetSpacing_tier2_hasLargeHubs_returns100() {
        assertEquals(100,
                ElementSpacingHeuristic.targetSpacingForConnectionCount(20, true));
    }

    @Test
    public void targetSpacing_tier3_hasLargeHubs_returns120() {
        assertEquals(120,
                ElementSpacingHeuristic.targetSpacingForConnectionCount(40, true));
    }

    // ---- Boundary tests at 15/16 transition (both columns) ----

    @Test
    public void targetSpacing_boundary15_noLargeHubs_topOfTier1_returns60() {
        // ≤15 means 15 itself is in tier 1
        assertEquals(60,
                ElementSpacingHeuristic.targetSpacingForConnectionCount(15, false));
    }

    @Test
    public void targetSpacing_boundary16_noLargeHubs_bottomOfTier2_returns80() {
        // 16 starts tier 2 (16-30 inclusive)
        assertEquals(80,
                ElementSpacingHeuristic.targetSpacingForConnectionCount(16, false));
    }

    @Test
    public void targetSpacing_boundary15_hasLargeHubs_topOfTier1_returns80() {
        assertEquals(80,
                ElementSpacingHeuristic.targetSpacingForConnectionCount(15, true));
    }

    @Test
    public void targetSpacing_boundary16_hasLargeHubs_bottomOfTier2_returns100() {
        assertEquals(100,
                ElementSpacingHeuristic.targetSpacingForConnectionCount(16, true));
    }

    // ---- Boundary tests at 30/31 transition (both columns) ----

    @Test
    public void targetSpacing_boundary30_noLargeHubs_topOfTier2_returns80() {
        assertEquals(80,
                ElementSpacingHeuristic.targetSpacingForConnectionCount(30, false));
    }

    @Test
    public void targetSpacing_boundary31_noLargeHubs_bottomOfTier3_returns100() {
        assertEquals(100,
                ElementSpacingHeuristic.targetSpacingForConnectionCount(31, false));
    }

    @Test
    public void targetSpacing_boundary30_hasLargeHubs_topOfTier2_returns100() {
        assertEquals(100,
                ElementSpacingHeuristic.targetSpacingForConnectionCount(30, true));
    }

    @Test
    public void targetSpacing_boundary31_hasLargeHubs_bottomOfTier3_returns120() {
        assertEquals(120,
                ElementSpacingHeuristic.targetSpacingForConnectionCount(31, true));
    }

    // ---- Edge case: 0 connections falls into tier 1 on both columns ----

    @Test
    public void targetSpacing_zeroConnections_noLargeHubs_returnsTier1_60() {
        // 0 ≤ 15, so heuristic returns tier 1 = 60px. The accessor's no-
        // connections short-circuit decides whether the heuristic value is
        // applied; the heuristic itself is a pure mapping.
        assertEquals(60,
                ElementSpacingHeuristic.targetSpacingForConnectionCount(0, false));
    }

    @Test
    public void targetSpacing_zeroConnections_hasLargeHubs_returnsTier1_80() {
        assertEquals(80,
                ElementSpacingHeuristic.targetSpacingForConnectionCount(0, true));
    }

    // ------------------------------------------------------------------
    // Control-loop honours heuristic target as upper bound
    // ------------------------------------------------------------------
    //
    // The heuristic table values are reused unchanged by
    // the control loop; assert that the control loop's
    // decideNextStepDelta(...) honours the heuristic target as the upper
    // bound across all six element-spacing branches + all nine group-
    // spacing branches.
    //
    // Each branch test asserts: when SpacingIterationDecision.decideNextStep
    // is called with currentSpacingPx == heuristic-target, it returns
    // keepGoing=false + nextStepDelta=0. The ladder cannot propose a step
    // that would carry the cumulative spacing past the heuristic target.

    @Test
    public void controlLoop_honoursTarget_tier1_noLargeHubs_60() {
        int target = ElementSpacingHeuristic.targetSpacingForConnectionCount(10, false);
        assertEquals(60, target);
        SpacingIterationDecision dec = SpacingIterationDecision.decideNextStep(
                /*stepIndex=*/ 0, /*currentSpacingPx=*/ target,
                /*targetSpacingPx=*/ target,
                /*perIterationStepCapPx=*/ Integer.MAX_VALUE);
        assertEquals(false, dec.keepGoing());
        assertEquals(0, dec.nextStepDelta());
    }

    @Test
    public void controlLoop_honoursTarget_tier1_hasLargeHubs_80() {
        int target = ElementSpacingHeuristic.targetSpacingForConnectionCount(10, true);
        assertEquals(80, target);
        SpacingIterationDecision dec = SpacingIterationDecision.decideNextStep(
                0, target, target, Integer.MAX_VALUE);
        assertEquals(false, dec.keepGoing());
        assertEquals(0, dec.nextStepDelta());
    }

    @Test
    public void controlLoop_honoursTarget_tier2_noLargeHubs_80() {
        int target = ElementSpacingHeuristic.targetSpacingForConnectionCount(20, false);
        assertEquals(80, target);
        SpacingIterationDecision dec = SpacingIterationDecision.decideNextStep(
                0, target, target, Integer.MAX_VALUE);
        assertEquals(false, dec.keepGoing());
        assertEquals(0, dec.nextStepDelta());
    }

    @Test
    public void controlLoop_honoursTarget_tier2_hasLargeHubs_100() {
        int target = ElementSpacingHeuristic.targetSpacingForConnectionCount(20, true);
        assertEquals(100, target);
        SpacingIterationDecision dec = SpacingIterationDecision.decideNextStep(
                0, target, target, Integer.MAX_VALUE);
        assertEquals(false, dec.keepGoing());
        assertEquals(0, dec.nextStepDelta());
    }

    @Test
    public void controlLoop_honoursTarget_tier3_noLargeHubs_100() {
        int target = ElementSpacingHeuristic.targetSpacingForConnectionCount(40, false);
        assertEquals(100, target);
        SpacingIterationDecision dec = SpacingIterationDecision.decideNextStep(
                0, target, target, Integer.MAX_VALUE);
        assertEquals(false, dec.keepGoing());
        assertEquals(0, dec.nextStepDelta());
    }

    @Test
    public void controlLoop_honoursTarget_tier3_hasLargeHubs_120() {
        int target = ElementSpacingHeuristic.targetSpacingForConnectionCount(40, true);
        assertEquals(120, target);
        SpacingIterationDecision dec = SpacingIterationDecision.decideNextStep(
                0, target, target, Integer.MAX_VALUE);
        assertEquals(false, dec.keepGoing());
        assertEquals(0, dec.nextStepDelta());
    }

    // ---- Cumulative-ladder upper-bound assertion (one representative
    //      branch — tier2 has-large-hubs, target 100, starting from 40) ----

    @Test
    public void controlLoop_cumulativeLadder_neverExceedsTarget_tier2HasLargeHubs() {
        int target = ElementSpacingHeuristic.targetSpacingForConnectionCount(20, true);
        int currentSpacing = 40;
        // Drive the ladder until decideNextStep says stop.
        for (int stepIndex = 0; stepIndex < 20; stepIndex++) {
            SpacingIterationDecision dec = SpacingIterationDecision.decideNextStep(
                    stepIndex, currentSpacing, target, Integer.MAX_VALUE);
            if (!dec.keepGoing()) {
                break;
            }
            currentSpacing += dec.nextStepDelta();
            assertEquals("currentSpacing should never exceed target after step "
                    + stepIndex,
                    true, currentSpacing <= target);
        }
        // At terminus, currentSpacing should equal target (the ladder hits
        // exactly target when remainingToTarget < ladderDelta).
        assertEquals(target, currentSpacing);
    }
}
