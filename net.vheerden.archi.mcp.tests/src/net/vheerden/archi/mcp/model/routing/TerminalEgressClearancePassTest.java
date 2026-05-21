package net.vheerden.archi.mcp.model.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import net.vheerden.archi.mcp.model.RoutingRect;
import net.vheerden.archi.mcp.model.routing.TerminalEgressClearancePass.EgressProposal;
import net.vheerden.archi.mcp.model.routing.TerminalEgressClearancePass.Result;
import net.vheerden.archi.mcp.model.routing.TerminalEgressClearancePass.Snapshot;
import net.vheerden.archi.mcp.response.dto.AbsoluteBendpointDto;

/**
 * Unit tests for {@link TerminalEgressClearancePass} (story
 * {@code backlog-terminal-egress-corridor-aware-clearance}, W3 Lever-B successor).
 *
 * <p>Pure-geometry tests, mirroring {@code TerminalSegmentCorridorMigratorTest}. The first
 * three are the validated v1 core transform (push-off-face, no-room-no-op,
 * already-perpendicular). The remaining tests pin the two successor fixes: Fix-2a
 * (connection-gap-aware room search), Fix-2b (cheap view-level pre-gate), and Fix-1
 * (final-state net-improve rollback). The end-to-end M4 drop on D/G is the job of the
 * Gate-2 agent-in-loop paired-arc on the scratch model; these pins lock the geometry.
 */
public class TerminalEgressClearancePassTest {

    private final TerminalEgressClearancePass pass = new TerminalEgressClearancePass();

    private static AbsoluteBendpointDto bp(int x, int y) {
        return new AbsoluteBendpointDto(x, y);
    }

    private static List<AbsoluteBendpointDto> path(AbsoluteBendpointDto... bps) {
        return new ArrayList<>(List.of(bps));
    }

    @SafeVarargs
    private static List<List<AbsoluteBendpointDto>> paths(List<AbsoluteBendpointDto>... ps) {
        List<List<AbsoluteBendpointDto>> list = new ArrayList<>();
        for (List<AbsoluteBendpointDto> p : ps) list.add(p);
        return list;
    }

    private static RoutingPipeline.ConnectionEndpoints conn(String id, RoutingRect src, RoutingRect tgt) {
        return new RoutingPipeline.ConnectionEndpoints(id, src, tgt, List.of(), null, 0);
    }

    /**
     * G-successor overload: supply the per-connection ANCESTOR-EXCLUDED obstacle set
     * ({@code conn.obstacles()}) distinct from the {@code allObstacles} run-param, so the Fix-1
     * separation (Tier-1 passthrough uses {@code conn.obstacles()}; own-face hug detection uses
     * {@code allObstacles}) can be exercised.
     */
    private static RoutingPipeline.ConnectionEndpoints conn(
            String id, RoutingRect src, RoutingRect tgt, List<RoutingRect> connObstacles) {
        return new RoutingPipeline.ConnectionEndpoints(id, src, tgt, connObstacles, null, 0);
    }

    private static List<RoutingPipeline.ConnectionEndpoints> conns(RoutingPipeline.ConnectionEndpoints... cs) {
        List<RoutingPipeline.ConnectionEndpoints> list = new ArrayList<>();
        for (RoutingPipeline.ConnectionEndpoints c : cs) list.add(c);
        return list;
    }

    // Source S: bottom edge y=160, B71 BOTTOM line y=161, center x=140.
    private static final RoutingRect S = new RoutingRect(100, 100, 80, 60, "S");
    private static final RoutingRect T = new RoutingRect(340, 300, 80, 50, "T");

    // ===================================================================
    // 1-3 — the validated v1 core transform (restored from the .reference)
    // ===================================================================

    /**
     * The View-D shape: terminal on S's BOTTOM face (y=161), an immediate horizontal parallel
     * run at y=161 (1px under the edge — the M4 hug), then down to the target. Expect: one
     * proposal lengthening the perpendicular egress to clearance (y=168), the terminal
     * bendpoint UNCHANGED (B71), and an inserted A' producing a clean L.
     */
    @Test
    public void egressClearance_pushesParallelHugOffOwnFace() {
        List<AbsoluteBendpointDto> p = path(bp(140, 161), bp(360, 161), bp(360, 300));
        List<List<AbsoluteBendpointDto>> paths = paths(p);
        List<AbsoluteBendpointDto> original = new ArrayList<>(p);
        List<RoutingPipeline.ConnectionEndpoints> connections = conns(conn("c1", S, T));
        List<RoutingRect> obstacles = List.of(S, T);

        List<EgressProposal> proposals = pass.evaluate(connections, paths, obstacles);
        assertEquals("one egress proposal expected", 1, proposals.size());

        EgressProposal pr = proposals.get(0);
        // geomEdge=160, away=+1, smallest clearance k=8 -> newOrth=168.
        assertEquals("egress pushed to y=168 (8px clearance from the edge)",
                List.of(bp(140, 161), bp(140, 168), bp(360, 168), bp(360, 300)),
                pr.newPath());

        Snapshot snap = pass.apply(pr, paths);
        assertEquals("terminal anchor unchanged", bp(140, 161), paths.get(0).get(0));
        assertTrue("parallel run cleared the edge", Math.abs(paths.get(0).get(2).y() - 160) > 3);

        pass.restore(snap, paths);
        assertEquals("restore reverts to the original path", original, paths.get(0));
    }

    /**
     * A foreign element directly below S spans the whole push range, so any cleared orthogonal
     * slot would route the parallel run through that element's interior. The Tier-1 self-check
     * rejects it: degrade to a no-op (no proposal), never a passthrough.
     */
    @Test
    public void egressClearance_noRoom_degradesToNoOp() {
        RoutingRect blocker = new RoutingRect(120, 162, 200, 120, "E"); // y=[162,282], x=[120,320]
        List<AbsoluteBendpointDto> p = path(bp(140, 161), bp(360, 161), bp(360, 300));
        List<List<AbsoluteBendpointDto>> paths = paths(p);
        List<RoutingPipeline.ConnectionEndpoints> connections = conns(conn("c1", S, T));
        List<RoutingRect> obstacles = List.of(S, T, blocker);

        List<EgressProposal> proposals = pass.evaluate(connections, paths, obstacles);
        assertEquals("blocked push -> no proposal (no-op, not a passthrough)", 0, proposals.size());
    }

    /**
     * When the terminal already has a real perpendicular egress stub (the first segment runs
     * perpendicular to the face, not parallel), there is no hug to fix.
     */
    @Test
    public void egressClearance_alreadyPerpendicular_noProposal() {
        List<AbsoluteBendpointDto> p = path(bp(140, 161), bp(140, 250), bp(360, 250));
        List<List<AbsoluteBendpointDto>> paths = paths(p);
        List<RoutingPipeline.ConnectionEndpoints> connections = conns(conn("c1", S, T));
        List<RoutingRect> obstacles = List.of(S, T);

        List<EgressProposal> proposals = pass.evaluate(connections, paths, obstacles);
        assertEquals("perpendicular egress -> no hug -> no proposal", 0, proposals.size());
    }

    // ===================================================================
    // Fix-2a — connection-gap-aware room search
    // ===================================================================

    /**
     * A neighbouring connection has a horizontal parallel run at y=170 — 2px below the naive
     * 8px push target (y=168). With the element-edge-only v1 search the egress would land at
     * y=168, 2px from that neighbour. Fix-2a requires {@code max(8, prePassVp10)=8}px to the
     * neighbour, so the push is moved out to y=178 (exactly 8px from the neighbour). Control:
     * without the neighbour the push lands at y=168.
     */
    @Test
    public void egressClearance_connectionGapAware_pushesPastNeighbour() {
        // Control: single connection, no neighbour -> naive 8px push to y=168.
        List<AbsoluteBendpointDto> solo = path(bp(140, 161), bp(360, 161), bp(360, 300));
        List<EgressProposal> soloProposals =
                pass.evaluate(conns(conn("c1", S, T)), paths(solo), List.of(S, T));
        assertEquals(1, soloProposals.size());
        assertEquals("no neighbour -> 8px push to y=168", 168, soloProposals.get(0).newPath().get(2).y());

        // With a neighbouring horizontal connection run at y=170 (only 2px below y=168).
        List<AbsoluteBendpointDto> hug = path(bp(140, 161), bp(360, 161), bp(360, 300));
        List<AbsoluteBendpointDto> neighbour = path(bp(150, 170), bp(355, 170));
        List<List<AbsoluteBendpointDto>> withNeighbour = paths(hug, neighbour);
        List<EgressProposal> proposals = pass.evaluate(
                conns(conn("c1", S, T), conn("c2", S, T)), withNeighbour, List.of(S, T));

        assertEquals("only the size-3 hug yields a proposal", 1, proposals.size());
        EgressProposal pr = proposals.get(0);
        assertEquals("egress pushed PAST the neighbour to y=178 (8px gap)", 178,
                pr.newPath().get(2).y());
        assertEquals("inserted A' rides the same out-pushed coordinate", 178,
                pr.newPath().get(1).y());
        assertTrue("the cleared run keeps >=8px from the neighbour run at y=170",
                Math.abs(pr.newPath().get(2).y() - 170) >= 8);
    }

    /**
     * Fix-2a no-op: an inflated pre-pass V_p10 (two vertical runs 30px apart) makes
     * {@code requiredConnGap=30}, and a neighbouring horizontal run sits mid-range at y=196 —
     * so EVERY candidate in the [168,224] search window is within 30px of the neighbour. The
     * push is rejected wholesale (no proposal). Control: drop the close neighbour and the same
     * view yields a proposal, proving the no-op is the connection-gap check, not absence of a
     * hug.
     */
    @Test
    public void egressClearance_connectionGapAware_neighbourBlocksWholeRange_noOp() {
        List<AbsoluteBendpointDto> hug = path(bp(140, 161), bp(360, 161), bp(360, 300));
        List<AbsoluteBendpointDto> vPairPartner = path(bp(390, 161), bp(390, 300)); // V_p10 = 30
        List<AbsoluteBendpointDto> closeNeighbour = path(bp(150, 196), bp(370, 196)); // mid-range H

        // Control: V-pair present (requiredConnGap=30) but no close horizontal neighbour.
        List<EgressProposal> control = pass.evaluate(
                conns(conn("c1", S, T), conn("c2", S, T)),
                paths(hug, vPairPartner), List.of(S, T));
        assertEquals("with room, the hug still yields a proposal", 1, control.size());

        // Re-arm the hug (control left it unmutated, but be explicit) and add the blocker neighbour.
        List<AbsoluteBendpointDto> hug2 = path(bp(140, 161), bp(360, 161), bp(360, 300));
        List<EgressProposal> proposals = pass.evaluate(
                conns(conn("c1", S, T), conn("c2", S, T), conn("c3", S, T)),
                paths(hug2, vPairPartner, closeNeighbour), List.of(S, T));
        assertTrue("connection-gap check rejects every candidate -> no-op", proposals.isEmpty());
    }

    // ===================================================================
    // Fix-2b — cheap view-level pre-gate
    // ===================================================================

    /**
     * Two vertical connection runs 5px apart give a view V_p10 of 5 (&lt; the pre-gate
     * threshold of 8) — the tight-hub signature. {@link TerminalEgressClearancePass#run}
     * skips the whole pass: nothing is applied and every path is byte-identical.
     */
    @Test
    public void egressClearance_preGate_skipsTightVp10View() {
        List<AbsoluteBendpointDto> hug = path(bp(140, 161), bp(360, 161), bp(360, 300));
        List<AbsoluteBendpointDto> tightNeighbour = path(bp(365, 161), bp(365, 300)); // gap 5 -> V_p10=5
        List<AbsoluteBendpointDto> hugOriginal = new ArrayList<>(hug);
        List<AbsoluteBendpointDto> neighbourOriginal = new ArrayList<>(tightNeighbour);
        List<List<AbsoluteBendpointDto>> paths = paths(hug, tightNeighbour);

        Result result = pass.run(conns(conn("c1", S, T), conn("c2", S, T)), paths, List.of(S, T));

        assertTrue("pre-gate fired on the tight-V_p10 view", result.skippedByPreGate());
        assertEquals("nothing applied", 0, result.applied());
        assertEquals("nothing rolled", 0, result.rolled());
        // Skipped WHOLESALE: every path byte-identical, not just the hug.
        assertEquals("the hug path is byte-identical", hugOriginal, paths.get(0));
        assertEquals("the neighbour path is byte-identical", neighbourOriginal, paths.get(1));
    }

    /**
     * Pre-gate boundary: the gate is strict {@code prePassVp10 < PRE_GATE_VP10_PX}. At EXACTLY
     * 8.0 (two vertical runs 8px apart) the pass is NOT skipped (it runs, protected by Fix-2a);
     * just below 8 (7px apart) it IS skipped. Pins the off-by-one boundary.
     */
    @Test
    public void egressClearance_preGate_boundaryAtExactly8_doesNotSkip() {
        // V_p10 == 8.0 exactly (x=360 vs x=368 vertical runs, full y-overlap) -> NOT skipped.
        List<AbsoluteBendpointDto> hug = path(bp(140, 161), bp(360, 161), bp(360, 300));
        List<AbsoluteBendpointDto> partner8 = path(bp(368, 161), bp(368, 300));
        Result atEight = pass.run(conns(conn("c1", S, T), conn("c2", S, T)),
                paths(hug, partner8), List.of(S, T));
        assertFalse("V_p10 == 8.0 is NOT < 8.0 -> pass runs", atEight.skippedByPreGate());

        // V_p10 == 7.0 (x=360 vs x=367) -> skipped.
        List<AbsoluteBendpointDto> hug2 = path(bp(140, 161), bp(360, 161), bp(360, 300));
        List<AbsoluteBendpointDto> partner7 = path(bp(367, 161), bp(367, 300));
        Result belowEight = pass.run(conns(conn("c1", S, T), conn("c2", S, T)),
                paths(hug2, partner7), List.of(S, T));
        assertTrue("V_p10 == 7.0 < 8.0 -> pass skipped", belowEight.skippedByPreGate());
    }

    // ===================================================================
    // Target-side egress (sourceSide=false) — the reversal + index branch
    // ===================================================================

    // Target TT: top edge y=300, B71 TOP line y=299, center x=340.
    private static final RoutingRect TT = new RoutingRect(300, 300, 80, 60, "TT");

    /**
     * The hug is on the TARGET face (TT's TOP, y=299), and the source side has a clean
     * perpendicular egress (no source hug) — so the proposal must come from the target-side
     * branch (`sourceSide=false`): A' inserted at lastIdx-1, the target terminal unchanged at
     * lastIdx (B71 via the reversed-path predicate), pushed up to y=292 (8px above the edge).
     */
    @Test
    public void egressClearance_targetSide_pushesHugOffTargetFace() {
        // src (140,161) on S BOTTOM, perpendicular stub down to (140,299) -> no source hug;
        // then horizontal run (140,299)->(340,299) hugging TT's TOP edge (y=300) -> target hug.
        List<AbsoluteBendpointDto> p = path(bp(140, 161), bp(140, 299), bp(340, 299));
        List<List<AbsoluteBendpointDto>> paths = paths(p);
        List<RoutingPipeline.ConnectionEndpoints> connections = conns(conn("c1", S, TT));
        List<RoutingRect> obstacles = List.of(S, TT);

        List<EgressProposal> proposals = pass.evaluate(connections, paths, obstacles);
        assertEquals("one target-side egress proposal expected", 1, proposals.size());

        EgressProposal pr = proposals.get(0);
        // geomEdge=300, away=-1 (TOP), k=8 -> newOrth=292. A' at (term.x, 292) inserted at lastIdx-1.
        assertEquals("target-side egress pushed to y=292 (8px above the top edge)",
                List.of(bp(140, 161), bp(140, 292), bp(340, 292), bp(340, 299)),
                pr.newPath());
        // B71: the target terminal (last bendpoint) is unchanged.
        assertEquals("target terminal anchor unchanged", bp(340, 299),
                pr.newPath().get(pr.newPath().size() - 1));
    }

    /**
     * Target-side already perpendicular (the last segment runs perpendicular into the target
     * face, not parallel) AND no source hug -> no proposal from either side.
     */
    @Test
    public void egressClearance_targetSide_alreadyPerpendicular_noProposal() {
        List<AbsoluteBendpointDto> p = path(bp(140, 161), bp(140, 250), bp(340, 250), bp(340, 299));
        List<List<AbsoluteBendpointDto>> paths = paths(p);
        List<RoutingPipeline.ConnectionEndpoints> connections = conns(conn("c1", S, TT));
        List<RoutingRect> obstacles = List.of(S, TT);

        List<EgressProposal> proposals = pass.evaluate(connections, paths, obstacles);
        assertEquals("perpendicular egress on both terminals -> no proposal", 0, proposals.size());
    }

    // ===================================================================
    // Fix-1 — final-state net-improve rollback
    // ===================================================================

    /**
     * A connection hugs at its source face (terminal-incident) AND has a second hug on an
     * interior horizontal segment (against element F's top edge). The source-side egress fixes
     * only the first hug, so the connection stays M4-flagged (M4 does not drop at the view
     * level). {@link TerminalEgressClearancePass#run} rolls the apply back byte-identical
     * (Fix-1: keep only when M4 strictly drops).
     */
    @Test
    public void egressClearance_run_rollsBackWhenM4DoesNotDrop() {
        RoutingRect f = new RoutingRect(360, 300, 110, 60, "F"); // top edge y=300, x=[360,470]
        List<AbsoluteBendpointDto> p = path(
                bp(140, 161), bp(360, 161), bp(360, 300), bp(500, 300), bp(500, 400));
        List<AbsoluteBendpointDto> original = new ArrayList<>(p);
        List<List<AbsoluteBendpointDto>> paths = paths(p);

        Result result = pass.run(conns(conn("c1", S, f)), paths, List.of(S, f));

        assertFalse("not a pre-gate skip", result.skippedByPreGate());
        assertEquals("a proposal was evaluated", 1, result.proposalsEvaluated());
        assertEquals("nothing committed (M4 did not drop)", 0, result.applied());
        assertEquals("the proposal was rolled back", 1, result.rolled());
        assertEquals("path restored byte-identical after rollback", original, paths.get(0));
    }

    /**
     * Positive path: a single clean hug with room. {@link TerminalEgressClearancePass#run}
     * applies the egress (M4 1 -&gt; 0), pushing the parallel run to y=168 and keeping the
     * terminal byte-identical.
     */
    @Test
    public void egressClearance_run_appliesWhenM4Drops() {
        List<AbsoluteBendpointDto> p = path(bp(140, 161), bp(360, 161), bp(360, 300));
        List<List<AbsoluteBendpointDto>> paths = paths(p);

        Result result = pass.run(conns(conn("c1", S, T)), paths, List.of(S, T));

        assertFalse("not a pre-gate skip", result.skippedByPreGate());
        assertEquals("the egress was committed", 1, result.applied());
        assertEquals("nothing rolled back", 0, result.rolled());
        assertEquals("terminal anchor unchanged (B71)", bp(140, 161), paths.get(0).get(0));
        assertEquals("parallel run pushed to 8px clearance", 168, paths.get(0).get(2).y());
    }

    // ===================================================================
    // G successor — Fix-1 (ancestor-aware Tier-1) + Fix-2 (retry past tier1-rejected)
    // (story backlog-terminal-egress-corridor-aware-g-container-nested-hub, AC-8)
    //
    // Geometry mirrors pin #1 (S BOTTOM face, source-side, push DOWN y=160+k). The container /
    // foreign elements live in `allObstacles`; `conn.obstacles()` is supplied DISTINCTLY (the
    // ancestor-excluded set) so the Fix-1 separation is exercised. The cleared coordinate (y=168,
    // 8px off S's bottom edge) lies INSIDE the ancestor container's band — the predecessor's
    // dominant blocker.
    // ===================================================================

    /** Ancestor container enclosing S, extending below far enough that the WHOLE [8,64] push range
     *  (y 168..224) lies inside its interior (y 90..290). */
    private static final RoutingRect CONTAINER = new RoutingRect(80, 90, 400, 200, "C");
    /** A genuine foreign element well clear of the hug span (x 600..640) — used only to make
     *  conn.obstacles() non-empty (so the empty-set fallback does not fire). */
    private static final RoutingRect FAR = new RoutingRect(600, 400, 40, 40, "FAR");

    /**
     * AC-8(a) — container-nested hub. The only clean approach (y=168) lies inside the target's
     * ancestor container CONTAINER. With the ancestor EXCLUDED from conn.obstacles() the pass now
     * clears it; with the ancestor present in the passthrough set (the predecessor's behaviour) every
     * candidate in [8,64] pierces the container interior → sound no-op.
     */
    @Test
    public void egressClearance_containerNestedHub_clearsWhenAncestorExcluded() {
        List<RoutingRect> allObstacles = List.of(S, CONTAINER, FAR);

        // Ancestor EXCLUDED (conn.obstacles() = the genuine-foreign FAR only) → clears at y=168
        // even though y=168 is inside CONTAINER's band.
        List<EgressProposal> cleared = pass.evaluate(
                conns(conn("c1", S, T, List.of(FAR))),
                paths(path(bp(140, 161), bp(360, 161), bp(360, 300))),
                allObstacles);
        assertEquals("ancestor excluded → the container-nested hug clears", 1, cleared.size());
        assertEquals("pushed to y=168 (8px off the face, inside the container band)",
                List.of(bp(140, 161), bp(140, 168), bp(360, 168), bp(360, 300)),
                cleared.get(0).newPath());
        assertEquals("terminal unchanged (B71)", bp(140, 161), cleared.get(0).newPath().get(0));

        // Ancestor PRESENT in the passthrough set → predecessor behaviour: every candidate pierces
        // the container interior → sound no-op.
        List<EgressProposal> noop = pass.evaluate(
                conns(conn("c1", S, T, List.of(FAR, CONTAINER))),
                paths(path(bp(140, 161), bp(360, 161), bp(360, 300))),
                allObstacles);
        assertTrue("ancestor in the passthrough set → sound no-op (predecessor)", noop.isEmpty());
    }

    /**
     * AC-8(b) — G-Fix-2 retry. A GENUINE foreign element F (y 162..186) sits between the face and the
     * corridor: the naive candidate y=168 (8px off the S BOTTOM face at y=160) clears element edges +
     * connection gaps but is Tier-1-rejected (pierces F's interior). The search advances past F to the
     * smallest clean coordinate within the 64px cap: y=191, which clears F's bottom edge (y=186) by
     * edgeRequired = EDGE_COINCIDENCE_TOLERANCE_PX(3) + CLEAR_SAFETY_MARGIN_PX(2) = 5px. Control: drop F
     * and the push lands at the naive y=168 — proving the further push is the Tier-1 retry, not absence
     * of room.
     */
    @Test
    public void egressClearance_retriesPastTier1RejectedForeignElement() {
        RoutingRect f = new RoutingRect(150, 162, 200, 24, "F"); // y[162,186], spans the hug X

        // Control: no F → naive 8px push to y=168.
        List<EgressProposal> control = pass.evaluate(
                conns(conn("c1", S, T, List.of(FAR))),
                paths(path(bp(140, 161), bp(360, 161), bp(360, 300))),
                List.of(S, FAR));
        assertEquals(1, control.size());
        assertEquals("no foreign blocker → 8px push to y=168", 168, control.get(0).newPath().get(2).y());

        // With F (genuine foreign, in conn.obstacles()): y=168 pierces F → retry to y=191.
        List<EgressProposal> retried = pass.evaluate(
                conns(conn("c1", S, T, List.of(f))),
                paths(path(bp(140, 161), bp(360, 161), bp(360, 300))),
                List.of(S, f));
        assertEquals("a clean coordinate exists past F within the cap", 1, retried.size());
        assertEquals("retried past F to y=191 (clears F's bottom edge y=186 by edgeRequired=5 = 3+2)",
                191, retried.get(0).newPath().get(2).y());
        assertTrue("the retry pushed FURTHER than the naive 8px candidate",
                retried.get(0).newPath().get(2).y() > 168);
    }

    /**
     * AC-8(c) — over-exclusion negative guard. A GENUINE foreign element (y 162..230) spans the whole
     * [8,64] push range and is present in conn.obstacles() (it is NOT an ancestor). Fix-1 must NOT
     * exclude it: every candidate pierces its interior → sound no-op (a real passthrough is still
     * rejected). Proves the ancestor-exclusion does not leak into genuine foreign elements.
     */
    @Test
    public void egressClearance_genuineForeignPassthrough_stillRejected() {
        RoutingRect foreign = new RoutingRect(150, 162, 200, 68, "FOREIGN"); // y[162,230] spans 168..224

        List<EgressProposal> proposals = pass.evaluate(
                conns(conn("c1", S, T, List.of(foreign))),
                paths(path(bp(140, 161), bp(360, 161), bp(360, 300))),
                List.of(S, foreign));
        assertTrue("genuine foreign element spanning the range → still rejected (no over-exclusion)",
                proposals.isEmpty());
    }

    /**
     * AC-8(d) — ancestor excluded but a genuine SIBLING blocks → sound no-op (the deferred-relocation
     * case, AC-7). The ancestor CONTAINER is correctly excluded (control: with no sibling it clears),
     * but a genuine sibling element packed against the face spans the whole [8,64] range, so no clean
     * coordinate exists within the 64px cap. Relocation beyond the cap is OUT OF SCOPE (a future
     * lever) → the pass soundly no-ops rather than forcing a long detour.
     */
    @Test
    public void egressClearance_ancestorExcludedButSiblingBlocks_soundNoOp() {
        RoutingRect sibling = new RoutingRect(150, 162, 200, 68, "SIB"); // y[162,230] spans 168..224

        // Control: ancestor excluded, NO sibling → clears at y=168 (proves the container exclusion works).
        List<EgressProposal> control = pass.evaluate(
                conns(conn("c1", S, T, List.of(FAR))),
                paths(path(bp(140, 161), bp(360, 161), bp(360, 300))),
                List.of(S, CONTAINER, FAR));
        assertEquals("ancestor excluded, no sibling → clears", 1, control.size());
        assertEquals(168, control.get(0).newPath().get(2).y());

        // Ancestor excluded, but a genuine sibling spans the range → no clean coordinate in [8,64]
        // → sound no-op (deferred relocation, AC-7).
        List<EgressProposal> noop = pass.evaluate(
                conns(conn("c1", S, T, List.of(sibling))),
                paths(path(bp(140, 161), bp(360, 161), bp(360, 300))),
                List.of(S, CONTAINER, sibling));
        assertTrue("ancestor excluded but sibling blocks → sound no-op (deferred relocation)",
                noop.isEmpty());
    }
}
