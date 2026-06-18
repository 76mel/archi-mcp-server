package net.vheerden.archi.mcp.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * JUnit pin for {@link GroupSpacingHeuristic#targetSpacingForConnectionCount(int, boolean, boolean)}.
 * Pure-unit tests (no OSGi).
 *
 * <p>Pins the entire 3x3 heuristic table (connected-no-hubs / connected-has-hubs
 * / unconnected × ≤15 / 16-30 / &gt;30) plus the 15/16/30/31 boundary
 * transitions on each column plus the unconnected hub-agnostic invariant
 * ({@code (N, false, true)} returns the SAME value as
 * {@code (N, false, false)}). Sibling-symmetric with
 * {@link ElementSpacingHeuristicTest}.</p>
 *
 * <p>Any change
 * to the heuristic table requires a coordinated edit across (1) the markdown
 * resource {@code archimate-view-patterns.md} §2, (2)
 * {@link GroupSpacingHeuristic}, (3) this test, (4) the four production
 * callsites that derive {@code hasLargeHubs} upstream and forward it. Edit
 * one without the others and this test fails until they all agree.</p>
 *
 * <p>Per {@code feedback_ceiling_preserved_framing.md} (inversely): the
 * connected-no-hubs branch values (80/100/120) and the unconnected branch
 * values (40/40/60) are byte-identical to pre-Row-C behaviour. Pre-Row-C
 * callers passing {@code hasLargeHubs=false} get the EXACT SAME connected
 * values as before; the unconnected column ignores {@code hasLargeHubs}
 * entirely (hub-agnostic — there are no inter-group corridors to widen on
 * an unconnected view).</p>
 */
public class GroupSpacingHeuristicTest {

    // ---- 3x3 branch matrix — connected, no large hubs (80/100/120) ----

    @Test
    public void targetSpacing_tier1_connected_noLargeHubs_returns80() {
        assertEquals(80,
                GroupSpacingHeuristic.targetSpacingForConnectionCount(10, true, false));
    }

    @Test
    public void targetSpacing_tier2_connected_noLargeHubs_returns100() {
        assertEquals(100,
                GroupSpacingHeuristic.targetSpacingForConnectionCount(20, true, false));
    }

    @Test
    public void targetSpacing_tier3_connected_noLargeHubs_returns120() {
        assertEquals(120,
                GroupSpacingHeuristic.targetSpacingForConnectionCount(40, true, false));
    }

    // ---- 3x3 branch matrix — connected, has large hubs (100/140/160) ----

    @Test
    public void targetSpacing_tier1_connected_hasLargeHubs_returns100() {
        assertEquals(100,
                GroupSpacingHeuristic.targetSpacingForConnectionCount(10, true, true));
    }

    @Test
    public void targetSpacing_tier2_connected_hasLargeHubs_returns140() {
        assertEquals(140,
                GroupSpacingHeuristic.targetSpacingForConnectionCount(20, true, true));
    }

    @Test
    public void targetSpacing_tier3_connected_hasLargeHubs_returns160() {
        assertEquals(160,
                GroupSpacingHeuristic.targetSpacingForConnectionCount(40, true, true));
    }

    // ---- 3x3 branch matrix — unconnected, hub-agnostic (40/40/60) ----

    @Test
    public void targetSpacing_tier1_unconnected_noLargeHubs_returns40() {
        assertEquals(40,
                GroupSpacingHeuristic.targetSpacingForConnectionCount(10, false, false));
    }

    @Test
    public void targetSpacing_tier2_unconnected_noLargeHubs_returns40() {
        assertEquals(40,
                GroupSpacingHeuristic.targetSpacingForConnectionCount(20, false, false));
    }

    @Test
    public void targetSpacing_tier3_unconnected_noLargeHubs_returns60() {
        assertEquals(60,
                GroupSpacingHeuristic.targetSpacingForConnectionCount(40, false, false));
    }

    // ---- Unconnected hub-agnostic invariant — (N, false, true) returns
    //      SAME values as (N, false, false). Per Task 4 § Synthesis-note:
    //      the hub-aware tier doesn't fire on unconnected views — there are
    //      no inter-group corridors to widen.

    @Test
    public void targetSpacing_tier1_unconnected_hasLargeHubs_stillReturns40() {
        assertEquals(40,
                GroupSpacingHeuristic.targetSpacingForConnectionCount(10, false, true));
    }

    @Test
    public void targetSpacing_tier2_unconnected_hasLargeHubs_stillReturns40() {
        assertEquals(40,
                GroupSpacingHeuristic.targetSpacingForConnectionCount(20, false, true));
    }

    @Test
    public void targetSpacing_tier3_unconnected_hasLargeHubs_stillReturns60() {
        assertEquals(60,
                GroupSpacingHeuristic.targetSpacingForConnectionCount(40, false, true));
    }

    // ---- Boundary tests at 15/16 transition × all three columns ----

    @Test
    public void targetSpacing_boundary15_connected_noLargeHubs_returns80() {
        assertEquals(80,
                GroupSpacingHeuristic.targetSpacingForConnectionCount(15, true, false));
    }

    @Test
    public void targetSpacing_boundary16_connected_noLargeHubs_returns100() {
        assertEquals(100,
                GroupSpacingHeuristic.targetSpacingForConnectionCount(16, true, false));
    }

    @Test
    public void targetSpacing_boundary15_connected_hasLargeHubs_returns100() {
        assertEquals(100,
                GroupSpacingHeuristic.targetSpacingForConnectionCount(15, true, true));
    }

    @Test
    public void targetSpacing_boundary16_connected_hasLargeHubs_returns140() {
        assertEquals(140,
                GroupSpacingHeuristic.targetSpacingForConnectionCount(16, true, true));
    }

    @Test
    public void targetSpacing_boundary15_unconnected_returns40() {
        assertEquals(40,
                GroupSpacingHeuristic.targetSpacingForConnectionCount(15, false, false));
    }

    @Test
    public void targetSpacing_boundary16_unconnected_returns40() {
        // 16 stays at 40 on unconnected — both ≤15 and 16-30 unconnected
        // tiers return 40 (no transition until 30/31 boundary).
        assertEquals(40,
                GroupSpacingHeuristic.targetSpacingForConnectionCount(16, false, false));
    }

    // ---- Boundary tests at 30/31 transition × all three columns ----

    @Test
    public void targetSpacing_boundary30_connected_noLargeHubs_returns100() {
        assertEquals(100,
                GroupSpacingHeuristic.targetSpacingForConnectionCount(30, true, false));
    }

    @Test
    public void targetSpacing_boundary31_connected_noLargeHubs_returns120() {
        assertEquals(120,
                GroupSpacingHeuristic.targetSpacingForConnectionCount(31, true, false));
    }

    @Test
    public void targetSpacing_boundary30_connected_hasLargeHubs_returns140() {
        assertEquals(140,
                GroupSpacingHeuristic.targetSpacingForConnectionCount(30, true, true));
    }

    @Test
    public void targetSpacing_boundary31_connected_hasLargeHubs_returns160() {
        assertEquals(160,
                GroupSpacingHeuristic.targetSpacingForConnectionCount(31, true, true));
    }

    @Test
    public void targetSpacing_boundary30_unconnected_returns40() {
        assertEquals(40,
                GroupSpacingHeuristic.targetSpacingForConnectionCount(30, false, false));
    }

    @Test
    public void targetSpacing_boundary31_unconnected_returns60() {
        assertEquals(60,
                GroupSpacingHeuristic.targetSpacingForConnectionCount(31, false, false));
    }

    // ------------------------------------------------------------------
    // Control-loop honours heuristic target as upper bound
    // ------------------------------------------------------------------
    //
    // Each of the 9 group-spacing heuristic branches (3 tiers × 3
    // columns = connected-no-hubs / connected-has-hubs / unconnected) is
    // pinned: when SpacingIterationDecision.decideNextStep is called with
    // currentSpacingPx == heuristic-target, it returns keepGoing=false +
    // nextStepDelta=0. The ladder cannot propose a step that would carry
    // the cumulative spacing past the heuristic target.

    // ---- connected no-large-hubs (3 tiers: 80, 100, 120) ----

    @Test
    public void controlLoop_honoursTarget_connected_noLargeHubs_tier1_80() {
        int target = GroupSpacingHeuristic.targetSpacingForConnectionCount(10, true, false);
        assertEquals(80, target);
        SpacingIterationDecision dec = SpacingIterationDecision.decideNextStep(
                0, target, target, Integer.MAX_VALUE);
        assertEquals(false, dec.keepGoing());
        assertEquals(0, dec.nextStepDelta());
    }

    @Test
    public void controlLoop_honoursTarget_connected_noLargeHubs_tier2_100() {
        int target = GroupSpacingHeuristic.targetSpacingForConnectionCount(20, true, false);
        assertEquals(100, target);
        SpacingIterationDecision dec = SpacingIterationDecision.decideNextStep(
                0, target, target, Integer.MAX_VALUE);
        assertEquals(false, dec.keepGoing());
        assertEquals(0, dec.nextStepDelta());
    }

    @Test
    public void controlLoop_honoursTarget_connected_noLargeHubs_tier3_120() {
        int target = GroupSpacingHeuristic.targetSpacingForConnectionCount(40, true, false);
        assertEquals(120, target);
        SpacingIterationDecision dec = SpacingIterationDecision.decideNextStep(
                0, target, target, Integer.MAX_VALUE);
        assertEquals(false, dec.keepGoing());
        assertEquals(0, dec.nextStepDelta());
    }

    // ---- connected has-large-hubs (3 tiers: 100, 140, 160) ----

    @Test
    public void controlLoop_honoursTarget_connected_hasLargeHubs_tier1_100() {
        int target = GroupSpacingHeuristic.targetSpacingForConnectionCount(10, true, true);
        assertEquals(100, target);
        SpacingIterationDecision dec = SpacingIterationDecision.decideNextStep(
                0, target, target, Integer.MAX_VALUE);
        assertEquals(false, dec.keepGoing());
        assertEquals(0, dec.nextStepDelta());
    }

    @Test
    public void controlLoop_honoursTarget_connected_hasLargeHubs_tier2_140() {
        int target = GroupSpacingHeuristic.targetSpacingForConnectionCount(20, true, true);
        assertEquals(140, target);
        SpacingIterationDecision dec = SpacingIterationDecision.decideNextStep(
                0, target, target, Integer.MAX_VALUE);
        assertEquals(false, dec.keepGoing());
        assertEquals(0, dec.nextStepDelta());
    }

    @Test
    public void controlLoop_honoursTarget_connected_hasLargeHubs_tier3_160() {
        int target = GroupSpacingHeuristic.targetSpacingForConnectionCount(40, true, true);
        assertEquals(160, target);
        SpacingIterationDecision dec = SpacingIterationDecision.decideNextStep(
                0, target, target, Integer.MAX_VALUE);
        assertEquals(false, dec.keepGoing());
        assertEquals(0, dec.nextStepDelta());
    }

    // ---- unconnected hub-agnostic (3 tiers: 40, 40, 60) ----

    @Test
    public void controlLoop_honoursTarget_unconnected_tier1_40() {
        int target = GroupSpacingHeuristic.targetSpacingForConnectionCount(10, false, false);
        assertEquals(40, target);
        SpacingIterationDecision dec = SpacingIterationDecision.decideNextStep(
                0, target, target, Integer.MAX_VALUE);
        assertEquals(false, dec.keepGoing());
        assertEquals(0, dec.nextStepDelta());
    }

    @Test
    public void controlLoop_honoursTarget_unconnected_tier2_40() {
        int target = GroupSpacingHeuristic.targetSpacingForConnectionCount(20, false, false);
        assertEquals(40, target);
        SpacingIterationDecision dec = SpacingIterationDecision.decideNextStep(
                0, target, target, Integer.MAX_VALUE);
        assertEquals(false, dec.keepGoing());
        assertEquals(0, dec.nextStepDelta());
    }

    @Test
    public void controlLoop_honoursTarget_unconnected_tier3_60() {
        int target = GroupSpacingHeuristic.targetSpacingForConnectionCount(40, false, false);
        assertEquals(60, target);
        SpacingIterationDecision dec = SpacingIterationDecision.decideNextStep(
                0, target, target, Integer.MAX_VALUE);
        assertEquals(false, dec.keepGoing());
        assertEquals(0, dec.nextStepDelta());
    }

    // ---- Cumulative-ladder upper-bound assertion (one representative
    //      branch — connected has-large-hubs tier 2, target 140) ----

    @Test
    public void controlLoop_cumulativeLadder_neverExceedsTarget_connectedHasLargeHubsTier2() {
        int target = GroupSpacingHeuristic.targetSpacingForConnectionCount(20, true, true);
        int currentSpacing = 40;
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
        assertEquals(target, currentSpacing);
    }
}
