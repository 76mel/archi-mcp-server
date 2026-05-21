package net.vheerden.archi.mcp.model.routing;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import net.vheerden.archi.mcp.model.RoutingRect;
import net.vheerden.archi.mcp.response.dto.AbsoluteBendpointDto;

/**
 * Unit tests for {@link ChannelNudgingPass} (B69-B Task 3).
 *
 * <p>Pure-geometry: no EMF, no SWT, no PDE. All inputs are hand-constructed synthetic
 * scenarios designed to exercise the specific algorithm branches; no dependency on
 * {@link RoutingPipeline}'s full routing logic.
 *
 * <p>Test naming follows project convention: {@code method_shouldDoX_whenY()}.
 * Semantic assertions preferred over coordinate-equality where the story's AC
 * acceptance shape is semantic.
 */
public class ChannelNudgingPassTest {

    // =====================================================================
    // Helper: build a ConnectionEndpoints with the 7-arg constructor
    // =====================================================================

    private static RoutingPipeline.ConnectionEndpoints conn(
            String id, RoutingRect src, RoutingRect tgt, List<RoutingRect> obstacles) {
        return new RoutingPipeline.ConnectionEndpoints(
                id, src, tgt, obstacles, "", 1, Collections.emptyList());
    }

    private static AbsoluteBendpointDto bp(int x, int y) {
        return new AbsoluteBendpointDto(x, y);
    }

    // =====================================================================
    // T1 — single-occupant run centres on slack midpoint
    // =====================================================================

    @Test
    public void nudge_shouldCentreSingleOccupantRunInCorridor() {
        // Source and target placed BELOW the corridor so they don't contribute to its
        // near-side bound. Top obstacle above, bottom obstacle below the source/target,
        // leaving a clean horizontal corridor strip between the top obstacle and the
        // source's top edge.
        //
        // Source (1000, 500, 30, 30) → center (1015, 515). y∈[500, 530].
        // Target (1200, 500, 30, 30) → center (1215, 515). y∈[500, 530].
        // Top obstacle (1050, 0, 100, 60) → y∈[0, 60], x∈[1050, 1150].
        // Horizontal corridor for run at y in [60, 500]: near=60, far=min(src.top=500, tgt.top=500)=500.
        // Midpoint = 280.
        RoutingRect src = new RoutingRect(1000, 500, 30, 30, "src-1");
        RoutingRect tgt = new RoutingRect(1200, 500, 30, 30, "tgt-1");
        RoutingRect topObs = new RoutingRect(1050, 0, 100, 60, "top");

        // Path: 2 BPs forming a single horizontal run at y=100 (wall-hugging the top
        // obstacle). Both BPs X-aligned with source/target centres, so terminal segments
        // source(1015,515)→BP[0](1015,100) and BP[1](1215,100)→target(1215,515) are
        // vertical. Y-nudge preserves terminal alignment.
        List<AbsoluteBendpointDto> path = new ArrayList<>(Arrays.asList(
                bp(1015, 100),
                bp(1215, 100)));

        List<RoutingRect> allObstacles = List.of(src, tgt, topObs);
        List<RoutingPipeline.ConnectionEndpoints> connections = List.of(
                conn("c-1", src, tgt, List.of(topObs)));
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        paths.add(path);

        ChannelNudgingPass pass = new ChannelNudgingPass();
        int nudges = pass.run(connections, paths, allObstacles);

        // Gap=[60, 500], midpoint=280, delta=|280-100|=180 ≥ 4, nudge attempted.
        // Post-nudge BP[0]=(1015, 280), BP[1]=(1215, 280). Terminal alignment still holds
        // (BP[0].x==src.cx==1015 ✓; BP[1].x==tgt.cx==1215 ✓).
        assertEquals("One nudge expected", 1, nudges);
        assertEquals("No rollbacks expected", 0, pass.getRollbackCount());

        int bp0y = paths.get(0).get(0).y();
        int bp1y = paths.get(0).get(1).y();
        assertTrue("BP[0] y should be near midpoint 280, was " + bp0y,
                Math.abs(bp0y - 280) <= 2);
        assertTrue("BP[1] y should be near midpoint 280, was " + bp1y,
                Math.abs(bp1y - 280) <= 2);
    }

    // =====================================================================
    // T2 — four parallel runs in one corridor fan out evenly
    // =====================================================================

    @Test
    public void nudge_shouldFanOutFourParallelRunsEvenly() {
        // Four routes all routing horizontally through the same obstacle-bounded
        // corridor y∈[100, 300] (midpoint 200, available width after 2*MIN_CLEARANCE=180).
        // All four runs start at y=150 (wall-hugging the top obstacle below).
        // BPs are X-aligned with source/target centres (the terminal segments are vertical),
        // so Y-nudging preserves terminal alignment — the nudges can succeed and fan out.
        RoutingRect topObs = new RoutingRect(500, 0, 400, 100, "top");   // y∈[0, 100]
        RoutingRect botObs = new RoutingRect(500, 300, 400, 100, "bot"); // y∈[300, 400]

        List<RoutingPipeline.ConnectionEndpoints> connections = new ArrayList<>();
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        List<RoutingRect> allObstacles = new ArrayList<>();
        allObstacles.add(topObs);
        allObstacles.add(botObs);

        for (int i = 0; i < 4; i++) {
            // Sources to the LEFT of the corridor, at y=50 (inside topObs? No — above it).
            // Place sources at y=500 below botObs so terminal segments are vertical up into the corridor.
            RoutingRect s = new RoutingRect(440, 485, 30, 30, "s-" + i); // center (455, 500)
            RoutingRect t = new RoutingRect(930, 485, 30, 30, "t-" + i); // center (945, 500)
            allObstacles.add(s);
            allObstacles.add(t);
            connections.add(conn("route-" + i, s, t, List.of(topObs, botObs)));
            // Path: vertical leg up from source, horizontal run at y=150, vertical leg down.
            // BP[0] at (s.cx, 150) — X matches source centreX, terminal seg vertical.
            // BP[1] at (t.cx, 150) — X matches target centreX, terminal seg vertical.
            List<AbsoluteBendpointDto> p = new ArrayList<>(Arrays.asList(
                    bp(s.centerX(), 150),
                    bp(t.centerX(), 150)));
            paths.add(p);
        }

        ChannelNudgingPass pass = new ChannelNudgingPass();
        int nudges = pass.run(connections, paths, allObstacles);

        // Expected fan-out: midpoint=200, available=180, spacing=max(8, 180/3)=60, clamped
        // to MAX_TRACK_SPACING_PX=30. Four tracks at midpoint ± {1.5, 0.5, -0.5, -1.5} × 30
        // = {155, 185, 215, 245}. All 4 nudges should succeed (X-aligned terminals survive).
        assertEquals("All 4 runs should be nudged", 4, nudges);
        assertEquals("No rollbacks expected", 0, pass.getRollbackCount());

        // Collect the final y-coordinate of each route's horizontal run and verify they
        // are (a) all distinct, (b) well-separated, (c) symmetric around the midpoint.
        int[] finalYs = new int[4];
        for (int i = 0; i < 4; i++) {
            finalYs[i] = paths.get(i).get(0).y();
            assertEquals("BP[0] and BP[1] must still share y (horizontal run invariant)",
                    finalYs[i], paths.get(i).get(1).y());
        }
        java.util.Arrays.sort(finalYs);
        assertEquals("Four runs must produce four distinct y-coordinates",
                4, java.util.Arrays.stream(finalYs).distinct().count());
        for (int i = 1; i < 4; i++) {
            int gap = finalYs[i] - finalYs[i - 1];
            assertTrue("Adjacent tracks must be separated by at least MIN_TRACK_SPACING_PX; gap was " + gap,
                    gap >= ChannelNudgingPass.MIN_TRACK_SPACING_PX);
        }
        // Symmetry around midpoint 200: average of all 4 should be very close to 200.
        int avg = (finalYs[0] + finalYs[1] + finalYs[2] + finalYs[3]) / 4;
        assertTrue("Fan-out should be centred on the slack midpoint 200; avg was " + avg,
                Math.abs(avg - 200) <= 2);
    }

    // =====================================================================
    // T3 — rollback when terminal alignment would break
    // =====================================================================

    @Test
    public void nudge_shouldRollBackRoute_whenTerminalAlignmentBreaks() {
        // Single route whose BP[0] is aligned to source center ONLY via y, and whose
        // BP[last] is aligned to target center ONLY via y. A y-nudge breaks both.
        // Source center (120, 120); target center (520, 120). BP[0]=(200, 120),
        // BP[1]=(400, 120). X's differ from source.cx/target.cx but Y's match → aligned
        // via Y-axis only. Terminal segments from source(120,120)→BP[0](200,120) are
        // horizontal at y=120. Y-nudge breaks this.
        RoutingRect src = new RoutingRect(100, 100, 40, 40, "src"); // center (120, 120)
        RoutingRect tgt = new RoutingRect(500, 100, 40, 40, "tgt"); // center (520, 120)
        RoutingRect topObs = new RoutingRect(100, 40, 500, 40, "top");  // y∈[40, 80], x∈[100,600]
        RoutingRect botObs = new RoutingRect(100, 200, 500, 60, "bot"); // y∈[200, 260], x∈[100,600]

        // Horizontal run at y=120 in corridor y∈[80, 200], midpoint 140. Delta 20 ≥ 4.
        List<AbsoluteBendpointDto> path = new ArrayList<>(Arrays.asList(
                bp(200, 120), bp(400, 120)));

        List<RoutingRect> allObstacles = List.of(src, tgt, topObs, botObs);
        List<RoutingPipeline.ConnectionEndpoints> connections = List.of(
                conn("c-rollback", src, tgt, List.of(topObs, botObs)));
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        paths.add(path);

        ChannelNudgingPass pass = new ChannelNudgingPass();
        pass.run(connections, paths, allObstacles);

        // Rollback expected: nudging BP[0]=(200,120) → (200,140) breaks the constraint
        // BP[0].y==sourceCenterY (120). BP[0].x==200 ≠ sourceCenterX=120 either — so
        // the OR-predicate returns false. Predicate returns false → rollback.
        assertEquals("One rollback expected", 1, pass.getRollbackCount());
        assertEquals("No nudges applied", 0, pass.getNudgeCount());
        assertEquals("BP[0] y unchanged after rollback", 120, paths.get(0).get(0).y());
        assertEquals("BP[1] y unchanged after rollback", 120, paths.get(0).get(1).y());
        assertEquals("BP[0] x unchanged", 200, paths.get(0).get(0).x());
        assertEquals("BP[1] x unchanged", 400, paths.get(0).get(1).x());
    }

    // =====================================================================
    // T4 — rollback when nudge would push run through an obstacle
    // =====================================================================

    @Test
    public void introducesObstaclePassThrough_shouldDetectSegmentCrossing_andExcludeSourceAndTarget() {
        // Tests the static predicate directly rather than the full-pipeline rollback
        // path. The original "construct a full-pipeline scenario where the midpoint is
        // hidden from computeCorridorGap" approach was empirically hard to arrange
        // because computeCorridorGap IS the obstacle-free gap calculator for the
        // segment. Exercising the predicate directly gives tight, unambiguous coverage
        // of the check that the rollback mechanism relies on.
        RoutingRect src = new RoutingRect(100, 35, 30, 30, "src");       // x∈[100,130] y∈[35,65]
        RoutingRect tgt = new RoutingRect(500, 35, 30, 30, "tgt");       // x∈[500,530] y∈[35,65]
        RoutingRect interior = new RoutingRect(250, 100, 100, 60, "int"); // x∈[250,350] y∈[100,160]
        List<RoutingRect> obstacles = List.of(src, tgt, interior);

        // Case A: horizontal path that does NOT cross the interior obstacle → false.
        List<AbsoluteBendpointDto> pathClear = Arrays.asList(
                bp(115, 50), bp(115, 80), bp(515, 80), bp(515, 50));
        assertFalse("Clear path must not trigger pass-through detection",
                ChannelNudgingPass.introducesObstaclePassThrough(pathClear, src, tgt, obstacles));

        // Case B: horizontal run AT y=130, x∈[115,515] → strictly passes through interior.
        List<AbsoluteBendpointDto> pathCrossing = Arrays.asList(
                bp(115, 50), bp(115, 130), bp(515, 130), bp(515, 50));
        assertTrue("Path running through the interior obstacle must trigger detection",
                ChannelNudgingPass.introducesObstaclePassThrough(pathCrossing, src, tgt, obstacles));

        // Case C: path whose terminal segment clips the source element's interior is
        // NOT flagged, because the source is excluded from the obstacle scan.
        // Segment (115,50)→(115,80) passes through src (which contains y∈[35,65] at x=115),
        // but src is the connection's source and is excluded.
        List<AbsoluteBendpointDto> pathSourceClip = Arrays.asList(
                bp(115, 50), bp(115, 80), bp(515, 80), bp(515, 50));
        assertFalse("Source element must be excluded from pass-through detection",
                ChannelNudgingPass.introducesObstaclePassThrough(pathSourceClip, src, tgt, obstacles));

        // Case D: same as C but for target — target excluded from scan.
        List<AbsoluteBendpointDto> pathTargetClip = Arrays.asList(
                bp(115, 80), bp(515, 80), bp(515, 50));
        assertFalse("Target element must be excluded from pass-through detection",
                ChannelNudgingPass.introducesObstaclePassThrough(pathTargetClip, src, tgt, obstacles));

        // Case E: degenerate path (empty / single BP) is trivially safe.
        assertFalse("Empty path is not a pass-through",
                ChannelNudgingPass.introducesObstaclePassThrough(
                        Collections.emptyList(), src, tgt, obstacles));
        assertFalse("Single-BP path is not a pass-through",
                ChannelNudgingPass.introducesObstaclePassThrough(
                        List.of(bp(115, 50)), src, tgt, obstacles));
    }

    // =====================================================================
    // T5 — idempotence
    // =====================================================================

    @Test
    public void nudge_shouldBeIdempotent_whenCalledTwice() {
        // Same geometry as T1 — a real nudge happens, so idempotence is tested on an
        // actual change (not vacuously on a no-op).
        RoutingRect src = new RoutingRect(1000, 500, 30, 30, "src");
        RoutingRect tgt = new RoutingRect(1200, 500, 30, 30, "tgt");
        RoutingRect topObs = new RoutingRect(1050, 0, 100, 60, "top");
        List<RoutingRect> allObstacles = List.of(src, tgt, topObs);

        List<AbsoluteBendpointDto> initial = new ArrayList<>(Arrays.asList(
                bp(1015, 100), bp(1215, 100)));
        List<RoutingPipeline.ConnectionEndpoints> connections = List.of(
                conn("c-5", src, tgt, List.of(topObs)));

        // Run 1
        List<List<AbsoluteBendpointDto>> paths1 = new ArrayList<>();
        paths1.add(new ArrayList<>(initial));
        new ChannelNudgingPass().run(connections, paths1, allObstacles);
        List<AbsoluteBendpointDto> afterFirst = new ArrayList<>(paths1.get(0));

        // Run 2 — same inputs, independent pass instance
        List<List<AbsoluteBendpointDto>> paths2 = new ArrayList<>();
        paths2.add(new ArrayList<>(initial));
        new ChannelNudgingPass().run(connections, paths2, allObstacles);
        List<AbsoluteBendpointDto> afterSecond = paths2.get(0);

        assertEquals("Two independent runs on same inputs produce same output",
                afterFirst, afterSecond);

        // Run 3 — second run on the output of run 1, same instance
        new ChannelNudgingPass().run(connections, paths1, allObstacles);
        List<AbsoluteBendpointDto> afterThird = paths1.get(0);
        assertEquals("Running the pass on already-nudged output is a no-op",
                afterFirst, afterThird);
    }

    // =====================================================================
    // T6 — deterministic tie-break by connection ID
    // =====================================================================

    @Test
    public void nudge_shouldBreakTiesByConnectionId_whenTwoRoutesPreferSameTrack() {
        // Two routes in the same corridor, same initial y. Sources placed BELOW the
        // corridor so their y-range doesn't affect the gap computation. Both paths
        // have BPs X-aligned to source/target centres (terminal segments vertical),
        // so Y-nudging preserves alignment and the tie-break becomes observable.
        //
        // Corridor y∈[100, 300], midpoint 200. n=2. spacing=max(8, (300-100-20)/1)=180
        // clamped to MAX_TRACK_SPACING_PX=30. Fan-out: i=0 → 200-15=185, i=1 → 200+15=215.
        RoutingRect topObs = new RoutingRect(300, 0, 600, 100, "top");   // x∈[300,900] y∈[0,100]
        RoutingRect botObs = new RoutingRect(300, 300, 600, 100, "bot"); // x∈[300,900] y∈[300,400]
        // Sources at y=500, targets at y=500 — below the corridor, out of y-range [100,300].
        RoutingRect s1 = new RoutingRect(450, 485, 30, 30, "s1"); // center (465, 500)
        RoutingRect t1 = new RoutingRect(930, 485, 30, 30, "t1"); // center (945, 500)
        RoutingRect s2 = new RoutingRect(450, 485, 30, 30, "s2");
        RoutingRect t2 = new RoutingRect(930, 485, 30, 30, "t2");
        List<RoutingRect> allObstacles = List.of(topObs, botObs, s1, t1, s2, t2);

        List<RoutingPipeline.ConnectionEndpoints> connections = List.of(
                conn("zebra", s1, t1, List.of(topObs, botObs)),
                conn("alpha", s2, t2, List.of(topObs, botObs)));
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        // Route zebra (index 0): BPs X-aligned with s1/t1 centres (465, 945).
        paths.add(new ArrayList<>(Arrays.asList(bp(465, 150), bp(945, 150))));
        // Route alpha (index 1): same shape.
        paths.add(new ArrayList<>(Arrays.asList(bp(465, 150), bp(945, 150))));

        ChannelNudgingPass pass = new ChannelNudgingPass();
        pass.run(connections, paths, allObstacles);

        // Both nudges should succeed (X-aligned terminals survive Y-nudge).
        assertEquals("Two nudges expected", 2, pass.getNudgeCount());
        assertEquals("No rollbacks expected", 0, pass.getRollbackCount());

        // Deterministic tie-break: "alpha" (conn index 1) sorted before "zebra" (conn index 0)
        // lexicographically → alpha gets i=0 track (y=185), zebra gets i=1 track (y=215).
        int alphaY = paths.get(1).get(0).y();
        int zebraY = paths.get(0).get(0).y();
        assertEquals("alpha should land on track 0 (y=185)", 185, alphaY);
        assertEquals("zebra should land on track 1 (y=215)", 215, zebraY);
        assertEquals("alpha BP[1] y matches BP[0] (horizontal run)",
                alphaY, paths.get(1).get(1).y());
        assertEquals("zebra BP[1] y matches BP[0] (horizontal run)",
                zebraY, paths.get(0).get(1).y());
    }

    // =====================================================================
    // T7 — no-op when single route already at its channel midpoint
    // =====================================================================

    @Test
    public void nudge_shouldBeNoOp_whenInputHasNoMultiOccupantCorridors() {
        // Single route with horizontal run ALREADY at corridor midpoint → no nudge needed.
        // Source/target placed far below the corridor (same geometry as T1) so they
        // don't affect the gap computation.
        RoutingRect src = new RoutingRect(1000, 500, 30, 30, "src");
        RoutingRect tgt = new RoutingRect(1200, 500, 30, 30, "tgt");
        RoutingRect topObs = new RoutingRect(1050, 0, 100, 60, "top"); // y∈[0, 60]

        // Corridor: near=60, far=src.top=500 → [60, 500], midpoint=280.
        // Run at y=280 is already centred → no nudge needed.
        List<AbsoluteBendpointDto> path = new ArrayList<>(Arrays.asList(
                bp(1015, 280), bp(1215, 280)));
        List<AbsoluteBendpointDto> snapshot = new ArrayList<>(path);

        List<RoutingRect> allObstacles = List.of(src, tgt, topObs);
        List<RoutingPipeline.ConnectionEndpoints> connections = List.of(
                conn("c-noop", src, tgt, List.of(topObs)));
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        paths.add(path);

        ChannelNudgingPass pass = new ChannelNudgingPass();
        int nudges = pass.run(connections, paths, allObstacles);

        assertEquals("No nudges when already at midpoint", 0, nudges);
        assertEquals("No rollbacks when no nudge attempted", 0, pass.getRollbackCount());
        assertEquals("Path byte-identical", snapshot, paths.get(0));
    }

    // =====================================================================
    // T8 — empty and short paths handled without exception
    // =====================================================================

    @Test
    public void nudge_shouldHandleEmptyAndShortPaths() {
        RoutingRect src = new RoutingRect(100, 100, 40, 40, "src");
        RoutingRect tgt = new RoutingRect(500, 100, 40, 40, "tgt");
        List<RoutingRect> allObstacles = List.of(src, tgt);

        List<RoutingPipeline.ConnectionEndpoints> connections = Arrays.asList(
                conn("empty", src, tgt, Collections.emptyList()),
                conn("one", src, tgt, Collections.emptyList()),
                conn("two", src, tgt, Collections.emptyList()),
                conn("three", src, tgt, Collections.emptyList()));

        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        paths.add(new ArrayList<>()); // 0 BPs
        paths.add(new ArrayList<>(Arrays.asList(bp(300, 100)))); // 1 BP
        paths.add(new ArrayList<>(Arrays.asList(bp(200, 100), bp(400, 100)))); // 2 BPs
        paths.add(new ArrayList<>(Arrays.asList(bp(200, 100), bp(200, 120), bp(400, 120)))); // 3 BPs

        ChannelNudgingPass pass = new ChannelNudgingPass();
        // Should not throw regardless of the trivial path shapes.
        pass.run(connections, paths, allObstacles);

        // Paths 0 and 1 have no internal segments → no change.
        assertTrue(paths.get(0).isEmpty());
        assertEquals(1, paths.get(1).size());
    }

    // =====================================================================
    // T9 — tracker (Phase 1 grouping invariant check)
    // =====================================================================

    @Test
    public void grouping_shouldClusterRunsInSameObstacleBoundedCorridor() {
        // This is the Task 0.1 spike, converted to a Java unit test.
        // Three runs at different sharedCoords y={301, 378, 450} should all group into
        // ONE channel keyed by the obstacle-bounded corridor they share.
        RoutingRect topObs = new RoutingRect(0, 200, 2500, 50, "top");    // corridor top at y=250
        RoutingRect botObs = new RoutingRect(0, 500, 2500, 50, "bot");    // corridor bottom at y=500
        // Obstacle-bounded corridor: y∈[250, 500].

        // Three paths, each a single horizontal run at a different y in the corridor.
        // All share the same x-parallel range [300, 1200] so they compete for tracks.
        RoutingRect src = new RoutingRect(250, 280, 40, 40, "src");
        RoutingRect tgt = new RoutingRect(1200, 280, 40, 40, "tgt");
        List<RoutingRect> allObstacles = List.of(src, tgt, topObs, botObs);

        List<RoutingPipeline.ConnectionEndpoints> connections = Arrays.asList(
                conn("run-301", src, tgt, List.of(topObs, botObs)),
                conn("run-378", src, tgt, List.of(topObs, botObs)),
                conn("run-450", src, tgt, List.of(topObs, botObs)));

        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        // Each path uses intermediate BPs at different y values to set the sharedCoord.
        paths.add(new ArrayList<>(Arrays.asList(bp(300, 301), bp(1200, 301))));
        paths.add(new ArrayList<>(Arrays.asList(bp(300, 378), bp(1200, 378))));
        paths.add(new ArrayList<>(Arrays.asList(bp(300, 450), bp(1200, 450))));

        ChannelNudgingPass pass = new ChannelNudgingPass();
        List<ChannelNudgingPass.Channel> channels = pass.groupIntoChannels(
                connections, paths, allObstacles);

        // EXPECTATION (the Task 0.1 re-scope validation in Java):
        // All three horizontal runs compute computeCorridorGap(y, true, 300, 1200, obstacles)
        // and get the same obstacle-bounded corridor [250, 500]. They all produce the SAME
        // ChannelKey(H, 250, 500). After Phase 1 grouping, they should all be in ONE channel.
        long horizontalChannels = channels.stream()
                .filter(c -> c.key.axis() == ChannelNudgingPass.Axis.H)
                .count();
        assertEquals("Task 0.1 re-scope validation: three runs at different y's in the same " +
                        "obstacle-bounded corridor MUST group into one channel under the " +
                        "obstacle-bounded ChannelKey. Channel list: " + channels,
                1, horizontalChannels);

        ChannelNudgingPass.Channel horizontalChannel = channels.stream()
                .filter(c -> c.key.axis() == ChannelNudgingPass.Axis.H)
                .findFirst()
                .orElseThrow();
        assertEquals("All three runs must be occupants of the single channel",
                3, horizontalChannel.occupants.size());
        assertEquals("Channel gapLow from computeCorridorGap", 250, horizontalChannel.gapLow());
        assertEquals("Channel gapHigh from computeCorridorGap", 500, horizontalChannel.gapHigh());
    }

    // =====================================================================
    // T10 — short-segment skip (V3 hook fix, 2026-04-13 evening)
    // =====================================================================

    /**
     * Reproduces the V3 API Management Platform → Branch Teller System pathology
     * discovered during B69-B Task 6 live E2E. Segments with parallel length below
     * {@code MIN_INTERIOR_SEGMENT_LENGTH_PX = 100} are treated as inter-route fan-out
     * micro-jogs and skipped before any nudge is attempted. Both the 50 px vertical
     * jog and the 57 px horizontal body in this fixture fall below the threshold,
     * so the pass leaves the path byte-identical to its pre-nudge state.
     *
     * <p>Scenario mirrors the observed V3 path exactly. Source APIMgmt (x∈[928,1128],
     * y∈[1207,1330]), target BranchTeller (x∈[809,932], y∈[1410,1465]). The 3-BP path
     * is the A* fan-out shape with a short vertical drop from source exit followed by
     * a short horizontal run into target center.
     */
    @Test
    public void nudge_shouldSkipSegment_whenBelowMinInteriorLengthThreshold() {
        RoutingRect src = new RoutingRect(928, 1207, 200, 123, "apiMgmt");
        RoutingRect tgt = new RoutingRect(809, 1410, 123, 55, "branchTeller");
        // Pre-nudge path: 3-BP L-with-micro-jog. Short vertical 50px down from
        // source exit, short horizontal 57px to target center x, implicit vertical
        // down to target center y.
        List<AbsoluteBendpointDto> path = new ArrayList<>(Arrays.asList(
                bp(927, 1268),
                bp(927, 1318),
                bp(870, 1318)));

        List<RoutingRect> allObstacles = List.of(src, tgt);
        List<RoutingPipeline.ConnectionEndpoints> connections = List.of(
                conn("v3-apiMgmt-to-branchTeller", src, tgt, List.of()));
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        paths.add(path);

        ChannelNudgingPass pass = new ChannelNudgingPass();
        pass.run(connections, paths, allObstacles);

        // Post-fix: both short segments fail the length check and stay at their
        // pre-nudge coordinates. Path is byte-identical to input.
        List<AbsoluteBendpointDto> finalPath = paths.get(0);
        assertEquals("Path must still have 3 BPs", 3, finalPath.size());
        assertEquals("BP0 preserved", 927, finalPath.get(0).x());
        assertEquals("BP0 preserved", 1268, finalPath.get(0).y());
        assertEquals("BP1 preserved", 927, finalPath.get(1).x());
        assertEquals("BP1 preserved", 1318, finalPath.get(1).y());
        assertEquals("BP2 preserved", 870, finalPath.get(2).x());
        assertEquals("BP2 preserved", 1318, finalPath.get(2).y());
    }

    // =====================================================================
    // T11 — terminal-crossing skip (V3 ATM hook fix, 2026-04-13 evening)
    // =====================================================================

    /**
     * Reproduces the V3 API Management Platform → ATM System pathology. A LONG
     * horizontal segment (length > 100 so it passes the short-segment check) is
     * eligible for nudging, but its nudge target crosses the source's perpendicular
     * center coordinate — flipping the adjacent short vertical terminal-approach
     * segment from DOWN to UP. The terminal-crossing skip rule catches this.
     *
     * <p>Scenario: Source APIMgmt (centerY=1268). Target ATM (centerY=1437). Path is
     * 3-BP L with short 40 px vertical exit (inside allowed "down hook" direction) and
     * long 258 px horizontal body at y=1308 below source. The horizontal would be
     * nudged to y=1253 — ABOVE source center — which flips the vertical terminal
     * approach from DOWN to UP. The rule rejects this crossing.
     */
    @Test
    public void nudge_shouldSkipNudge_whenTrajectoryCrossesTerminalCenter() {
        RoutingRect src = new RoutingRect(928, 1207, 200, 123, "apiMgmt"); // cy=1268
        RoutingRect tgt = new RoutingRect(609, 1410, 120, 55, "atm");      // cy=1437
        // Top obstacle that would put the corridor midpoint above source's center y,
        // driving Phase 2 to try to nudge the horizontal body upward.
        RoutingRect topObs = new RoutingRect(600, 1100, 400, 100, "top"); // y∈[1100,1200]

        // 3-BP path: short vertical DOWN 40px out of source-exit x, long horizontal
        // LEFT 258px at y=1308 (below source center).
        List<AbsoluteBendpointDto> path = new ArrayList<>(Arrays.asList(
                bp(927, 1268),
                bp(927, 1308),
                bp(669, 1308)));

        List<RoutingRect> allObstacles = List.of(src, tgt, topObs);
        List<RoutingPipeline.ConnectionEndpoints> connections = List.of(
                conn("v3-apiMgmt-to-atm", src, tgt, List.of(topObs)));
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        paths.add(path);

        ChannelNudgingPass pass = new ChannelNudgingPass();
        pass.run(connections, paths, allObstacles);

        // Post-fix: Seg 1 horizontal's computed nudge target crosses src.cy=1268,
        // so the terminal-crossing rule skips the nudge. Seg 0 vertical is skipped
        // by the 100 px length rule. Path stays byte-identical to input.
        List<AbsoluteBendpointDto> finalPath = paths.get(0);
        assertEquals(927, finalPath.get(0).x());
        assertEquals(1268, finalPath.get(0).y());
        assertEquals(927, finalPath.get(1).x());
        assertEquals(1308, finalPath.get(1).y());
        assertEquals(669, finalPath.get(2).x());
        assertEquals(1308, finalPath.get(2).y());
    }

    // =====================================================================
    // T12 — boundary: a 101 px segment IS a nudge candidate
    // =====================================================================

    /**
     * Positive boundary for the short-segment skip. A segment with parallel length
     * exactly one pixel above {@code MIN_INTERIOR_SEGMENT_LENGTH_PX = 100} is NOT
     * skipped by the length rule and, given a corridor with a non-trivial midpoint,
     * will be attempted as a nudge. This guards against regression toward a more
     * aggressive threshold that would under-nudge legitimate short corridor runs.
     *
     * <p>Directly exercises {@link ChannelNudgingPass#crossesCenterLine} semantics
     * via setup too: the run's pre-nudge y (y=60) and the corridor midpoint (y=230)
     * are both on the same side of source/target center (y=500), so no crossing.
     */
    @Test
    public void nudge_shouldAttemptNudge_whenSegmentExceedsMinInteriorLength() {
        // Source and target below the corridor so they do not affect the gap.
        RoutingRect src = new RoutingRect(440, 500, 30, 30, "src"); // cy=515
        RoutingRect tgt = new RoutingRect(541, 500, 30, 30, "tgt"); // cy=515  (101 px apart)
        RoutingRect topObs = new RoutingRect(400, 0, 400, 40, "top");   // y∈[0,40]
        RoutingRect botObs = new RoutingRect(400, 420, 400, 60, "bot"); // y∈[420,480]

        // Horizontal segment at y=60 (wall-hugging the top obstacle), length 101 px,
        // x∈[455, 556] — exactly one pixel over the 100 px threshold, and X-aligned
        // with source/target centres so terminal segments are vertical.
        List<AbsoluteBendpointDto> path = new ArrayList<>(Arrays.asList(
                bp(455, 60),
                bp(556, 60)));

        List<RoutingRect> allObstacles = List.of(src, tgt, topObs, botObs);
        List<RoutingPipeline.ConnectionEndpoints> connections = List.of(
                conn("boundary-101", src, tgt, List.of(topObs, botObs)));
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        paths.add(path);

        ChannelNudgingPass pass = new ChannelNudgingPass();
        int nudges = pass.run(connections, paths, allObstacles);

        // Corridor gap [40, 420], midpoint 230, delta from y=60 is 170 (≥ MIN_NUDGE_PX).
        // 101 > 100 so length rule passes; midpoint 230 > 40 > 515=src.cy is wrong, let me
        // recheck: src.cy=515 is BELOW y=230 (larger y = lower on screen), so y=60 and
        // y=230 are BOTH above src.cy. Same side → no crossing. Nudge attempted.
        assertEquals("101-px segment should be nudged (length rule threshold is 100)",
                1, nudges);
        assertEquals("No rollback expected", 0, pass.getRollbackCount());
    }

    // =====================================================================
    // T13 — cross-channel coincident-pair detection (H1 fix, 2026-04-13 evening)
    // =====================================================================

    /**
     * Exercises the global nudged-runs log in
     * {@link ChannelNudgingPass#introducesNewCoincidentPair}. Two routes in
     * <em>different</em> obstacle-bounded channels whose per-channel midpoints both
     * happen to be the same y — and whose parallel ranges overlap — must NOT both be
     * nudged to the coinciding coordinate; the second one must be rolled back by the
     * cross-channel coincident detection.
     *
     * <p>The key trick: the two routes have x-ranges that only partially overlap, and
     * the per-route obstacles are placed in the non-overlap regions so each route
     * sees a <em>different</em> pair of nearest-obstacle edges. Different bounds
     * (gapLow, gapHigh) produce different {@code ChannelKey}s — two separate Phase 1
     * channels — but the gap midpoints coincide by construction.
     *
     * <p>Alpha: x∈[0, 300], segment at y=80, obstacle bounds y=50 (above) and y=150
     * (below) from {@code alphaTop}/{@code alphaBot} which only overlap alpha's
     * x-range. Gap [50, 150], midpoint 100. Channel key (H, 50, 150).
     *
     * <p>Beta: x∈[200, 500], segment at y=120, obstacle bounds y=75 (above) and y=125
     * (below) from {@code betaTop}/{@code betaBot} which only overlap beta's x-range.
     * Gap [75, 125], midpoint 100. Channel key (H, 75, 125).
     *
     * <p>Different channel keys, same midpoint y=100, overlapping parallel ranges in
     * x∈[200, 300]. Alpha sorts first by channel gapLow (50 &lt; 75), nudges successfully
     * to y=100. Beta is then attempted, also targeting y=100, and the cross-channel
     * coincident check fires: same axis H, same sharedCoord 100, overlapping parallel
     * ranges → rollback.
     */
    @Test
    public void nudge_shouldDetectCrossChannelCoincidentPair_andRollBackSecondRun() {
        // Obstacles ONLY in alpha's x-range (x ≤ 300 by comfortable margin).
        RoutingRect alphaTop = new RoutingRect(50, 0, 100, 50, "alphaTop");   // y∈[0,50]
        RoutingRect alphaBot = new RoutingRect(50, 150, 100, 50, "alphaBot"); // y∈[150,200]

        // Obstacles ONLY in beta's x-range (x ≥ 350 by comfortable margin).
        RoutingRect betaTop = new RoutingRect(350, 25, 100, 50, "betaTop");   // y∈[25,75]
        RoutingRect betaBot = new RoutingRect(350, 125, 100, 50, "betaBot");  // y∈[125,175]

        // Sources/targets well below all obstacles so they don't affect gaps and
        // terminal segments are vertical (x-aligned, no crossing concern).
        RoutingRect sA = new RoutingRect(-10, 900, 20, 20, "sA");  // cx=0
        RoutingRect tA = new RoutingRect(290, 900, 20, 20, "tA");  // cx=300
        RoutingRect sB = new RoutingRect(190, 900, 20, 20, "sB");  // cx=200
        RoutingRect tB = new RoutingRect(490, 900, 20, 20, "tB");  // cx=500

        List<RoutingRect> allObstacles = List.of(
                alphaTop, alphaBot, betaTop, betaBot, sA, tA, sB, tB);

        List<RoutingPipeline.ConnectionEndpoints> connections = List.of(
                conn("alpha", sA, tA, List.of(alphaTop, alphaBot)),
                conn("beta", sB, tB, List.of(betaTop, betaBot)));

        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        // Alpha horizontal run at y=80, x∈[0, 300], length 300 (≥ 100 threshold).
        paths.add(new ArrayList<>(Arrays.asList(bp(0, 80), bp(300, 80))));
        // Beta horizontal run at y=120, x∈[200, 500], length 300. parRange overlaps
        // alpha's [0, 300] at [200, 300] — the cross-channel collision region.
        paths.add(new ArrayList<>(Arrays.asList(bp(200, 120), bp(500, 120))));

        ChannelNudgingPass pass = new ChannelNudgingPass();
        pass.run(connections, paths, allObstacles);

        // Alpha in channel (H, 50, 150): midpoint 100. Alpha nudges from y=80 to y=100.
        // Beta in channel (H, 75, 125): midpoint 100. Beta attempts to nudge from
        // y=120 to y=100; the cross-channel coincident check finds alpha's entry at
        // (H, 100, parRange [0,300]) and rejects because beta's parRange [200,500]
        // overlaps alpha's at [200,300]. Beta rolls back to y=120.
        int alphaFinalY = paths.get(0).get(0).y();
        int betaFinalY = paths.get(1).get(0).y();
        assertEquals("alpha BP[0] and BP[1] must share y (horizontal invariant)",
                alphaFinalY, paths.get(0).get(1).y());
        assertEquals("beta BP[0] and BP[1] must share y (horizontal invariant)",
                betaFinalY, paths.get(1).get(1).y());
        assertEquals("alpha should be nudged to midpoint y=100", 100, alphaFinalY);
        assertEquals("beta should be rolled back to its pre-nudge y=120",
                120, betaFinalY);
        assertEquals("One rollback expected (beta cross-channel coincident)",
                1, pass.getRollbackCount());
        assertEquals("One nudge expected (alpha)", 1, pass.getNudgeCount());
    }

    // =====================================================================
    // B75 — Inter-group corridor channel nudging
    // =====================================================================

    // ---- T4.1: Group-boundary channel detection ----

    /**
     * B75 AC-1: Two groups arranged horizontally with a 120px X-gap between them.
     * A vertical segment crossing the gap should get a ChannelKey tightened by
     * group X-edges (perpendicular axis for vertical segments is X).
     *
     * <p>Note: horizontal segments crossing L-to-R inter-group gaps are NOT tightened
     * because their perpendicular axis is Y, and groups spanning the full Y range
     * "contain" the segment on Y — the contains check skips them. The feature
     * targets vertical segments where group X-edges form genuine corridor walls.
     */
    @Test
    public void groupBoundaryChannels_shouldDetectInterGroupCorridor() {
        // Group G1: x=[0, 200], y=[0, 400]
        // Group G2: x=[320, 520], y=[0, 400]
        // Inter-group gap: x=[200, 320] (120px wide, midpoint=260)
        RoutingRect g1 = new RoutingRect(0, 0, 200, 400, "g1");
        RoutingRect g2 = new RoutingRect(320, 0, 200, 400, "g2");

        RoutingRect src = new RoutingRect(100, 180, 40, 40, "src");
        RoutingRect tgt = new RoutingRect(380, 180, 40, 40, "tgt");

        List<RoutingRect> allObstacles = List.of(src, tgt);
        List<RoutingRect> topLevelGroupBounds = List.of(g1, g2);
        List<RoutingPipeline.ConnectionEndpoints> connections = List.of(
                conn("c-1", src, tgt, allObstacles));
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();

        // Vertical segment crossing the inter-group gap: x=260 (in the gap),
        // y=[100, 300] (parallel range). axis=V, sharedCoord=260, perpendicular=X.
        // computeCorridorGap returns default gap [160, 360] (no element obstacles near x=260).
        // Group tightening: G1.maxX=200 → nearBound=200, G2.minX=320 → farBound=320.
        // Tightened gap: [200, 320].
        List<AbsoluteBendpointDto> path = new ArrayList<>(Arrays.asList(
                bp(260, 100),
                bp(260, 300)));
        paths.add(path);

        ChannelNudgingPass pass = new ChannelNudgingPass();
        List<ChannelNudgingPass.Channel> channels = pass.groupIntoChannels(
                connections, paths, allObstacles, topLevelGroupBounds);

        long verticalChannels = channels.stream()
                .filter(c -> c.key.axis() == ChannelNudgingPass.Axis.V)
                .count();
        assertTrue("At least one vertical channel expected for inter-group corridor",
                verticalChannels >= 1);

        ChannelNudgingPass.Channel vChannel = channels.stream()
                .filter(c -> c.key.axis() == ChannelNudgingPass.Axis.V)
                .findFirst()
                .orElseThrow();
        assertEquals("Channel gapLow should be G1.maxX=200", 200, vChannel.gapLow());
        assertEquals("Channel gapHigh should be G2.minX=320", 320, vChannel.gapHigh());
    }

    // ---- T4.2: Element-channel priority (AC-3) ----

    /**
     * B75 AC-3: When an element obstacle is between a group edge and the segment,
     * the element-level bound is preserved. The both-sides gate also prevents
     * tightening when only one side has a group-provided bound — this ensures
     * element-level corridors are not partially overridden by a single group edge.
     */
    @Test
    public void groupBoundaryChannels_shouldPreserveElementChannelPriority() {
        // Group G1: x=[0, 200], Group G2: x=[320, 520]
        // Element obstacle in the gap at x=[240, 280] (between the groups)
        RoutingRect g1 = new RoutingRect(0, 0, 200, 400, "g1");
        RoutingRect g2 = new RoutingRect(320, 0, 200, 400, "g2");
        RoutingRect gapElement = new RoutingRect(240, 50, 40, 300, "gap-elem");

        RoutingRect src = new RoutingRect(100, 180, 40, 40, "src");
        RoutingRect tgt = new RoutingRect(380, 180, 40, 40, "tgt");

        // Vertical segment at x=300 (right of the gap element, between element and G2)
        // Element-level gap from computeCorridorGap:
        //   nearBound=280 (gapElement.maxX), farBound=380 (tgt.minX)
        // Group tightening candidates:
        //   G1.maxX=200 < nearBound=280 → NOT tighter (element already closer)
        //   G2.minX=320 < farBound=380 → tighter candidate, but near side has no group
        // Both-sides gate: only far side has a group → gate blocks → gap unchanged.
        // Result: element-level gap [280, 380] preserved — element takes priority.
        List<AbsoluteBendpointDto> path = new ArrayList<>(Arrays.asList(
                bp(300, 100),
                bp(300, 300)));

        List<RoutingRect> allObstacles = List.of(src, tgt, gapElement);
        List<RoutingRect> topLevelGroupBounds = List.of(g1, g2);
        List<RoutingPipeline.ConnectionEndpoints> connections = List.of(
                conn("c-1", src, tgt, allObstacles));
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        paths.add(path);

        ChannelNudgingPass pass = new ChannelNudgingPass();
        List<ChannelNudgingPass.Channel> channels = pass.groupIntoChannels(
                connections, paths, allObstacles, topLevelGroupBounds);

        ChannelNudgingPass.Channel vChannel = channels.stream()
                .filter(c -> c.key.axis() == ChannelNudgingPass.Axis.V)
                .findFirst()
                .orElseThrow();
        assertEquals("gapLow should be element edge 280 (element takes priority over G1 at 200)",
                280, vChannel.gapLow());
        assertEquals("gapHigh should be element-level 380 (both-sides gate blocks single-sided tightening)",
                380, vChannel.gapHigh());
    }

    // ---- T4.3: Fan-out across wide corridor (AC-2) ----

    /**
     * B75 AC-2: Five vertical segments crossing a 120px inter-group gap should
     * fan out across the corridor width instead of stacking coincident.
     */
    @Test
    public void groupBoundaryChannels_shouldFanOutFiveConnectionsInInterGroupGap() {
        RoutingRect g1 = new RoutingRect(0, 0, 200, 600, "g1");
        RoutingRect g2 = new RoutingRect(320, 0, 200, 600, "g2");

        // Source center=(120, 70), target center=(400, 70).
        RoutingRect src = new RoutingRect(100, 50, 40, 40, "src");
        RoutingRect tgt = new RoutingRect(380, 50, 40, 40, "tgt");

        List<RoutingRect> allObstacles = List.of(src, tgt);
        List<RoutingRect> topLevelGroupBounds = List.of(g1, g2);

        List<RoutingPipeline.ConnectionEndpoints> connections = new ArrayList<>();
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();

        // Five 4-BP paths with interior vertical segments crossing the gap at x=260.
        // bp[0] shares x=120 with source center; bp[3] shares x=400 with target center.
        // The vertical segment bp[1]→bp[2] is the nudge candidate.
        for (int i = 0; i < 5; i++) {
            String id = "c-" + i;
            connections.add(conn(id, src, tgt, allObstacles));
            int yTop = 100 + i * 10;
            int yBot = 300 + i * 10;
            List<AbsoluteBendpointDto> path = new ArrayList<>(Arrays.asList(
                    bp(120, yTop),   // shares x=120 with source center
                    bp(260, yTop),   // horizontal approach → vertical segment start
                    bp(260, yBot),   // vertical segment end (gap-crossing, length ≥ 200)
                    bp(400, yBot))); // shares x=400 with target center
            paths.add(path);
        }

        ChannelNudgingPass pass = new ChannelNudgingPass();
        int nudges = pass.run(connections, paths, allObstacles, topLevelGroupBounds);

        // Gap=[200, 320], available=120-2*10=100. 5 occupants, spacing=100/4=25.
        assertTrue("Nudges should be applied for fan-out, got " + nudges, nudges > 0);

        // Verify the vertical segments have DIFFERENT x-coordinates (fanned, not coincident).
        // The vertical segment is bp[1]→bp[2]; check bp[1].x().
        java.util.Set<Integer> xCoords = new java.util.HashSet<>();
        for (List<AbsoluteBendpointDto> path : paths) {
            xCoords.add(path.get(1).x());
        }
        assertTrue("At least 3 of 5 connections should have distinct x-coordinates after fan-out, got "
                + xCoords.size(), xCoords.size() >= 3);
    }

    // ---- T4.4: Minimum margin from group boundary (AC-5) ----

    /**
     * B75 AC-5: Fanned connections must respect MIN_CLEARANCE_PX (10px) margin
     * from group boundary walls.
     */
    @Test
    public void groupBoundaryChannels_shouldRespectMinClearanceFromGroupWalls() {
        RoutingRect g1 = new RoutingRect(0, 0, 200, 400, "g1");
        RoutingRect g2 = new RoutingRect(320, 0, 200, 400, "g2");

        // Source center=(120, 200), target center=(400, 200).
        RoutingRect src = new RoutingRect(100, 180, 40, 40, "src");
        RoutingRect tgt = new RoutingRect(380, 180, 40, 40, "tgt");

        List<RoutingRect> allObstacles = List.of(src, tgt);
        List<RoutingRect> topLevelGroupBounds = List.of(g1, g2);

        List<RoutingPipeline.ConnectionEndpoints> connections = new ArrayList<>();
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();

        // Two 4-BP paths with interior vertical segments at x=260.
        for (int i = 0; i < 2; i++) {
            connections.add(conn("c-" + i, src, tgt, allObstacles));
            paths.add(new ArrayList<>(Arrays.asList(
                    bp(120, 100),    // shares x=120 with source center
                    bp(260, 100),    // vertical segment start
                    bp(260, 300),    // vertical segment end (length=200)
                    bp(400, 300)))); // shares x=400 with target center
        }

        ChannelNudgingPass pass = new ChannelNudgingPass();
        pass.run(connections, paths, allObstacles, topLevelGroupBounds);

        // Gap=[200, 320]. With MIN_CLEARANCE_PX=10, usable range is [210, 310].
        // Check the vertical segment (bp[1] and bp[2]).
        for (int i = 0; i < paths.size(); i++) {
            int x = paths.get(i).get(1).x();
            assertTrue("Connection " + i + " x=" + x + " must be >= 210 (gapLow + clearance)",
                    x >= 200 + ChannelNudgingPass.MIN_CLEARANCE_PX);
            assertTrue("Connection " + i + " x=" + x + " must be <= 310 (gapHigh - clearance)",
                    x <= 320 - ChannelNudgingPass.MIN_CLEARANCE_PX);
        }
    }

    // ---- T4.5: Nested group exclusion (AC-4) ----

    /**
     * B75 AC-4: Nested group boundaries must NOT create inter-group channels.
     * Only top-level group edges serve as channel walls.
     */
    @Test
    public void groupBoundaryChannels_shouldExcludeNestedGroupBounds() {
        // G1 is top-level: x=[0, 200]
        // G2 is top-level: x=[320, 520]
        // G1_nested is inside G1: x=[50, 150] — should be excluded by top-level filter
        RoutingRect g1 = new RoutingRect(0, 0, 200, 400, "g1");
        RoutingRect g2 = new RoutingRect(320, 0, 200, 400, "g2");
        RoutingRect g1Nested = new RoutingRect(50, 50, 100, 300, "g1-nested");

        // extractTopLevelGroupBounds should filter out g1Nested
        List<RoutingPipeline.ConnectionEndpoints> connections = List.of(
                new RoutingPipeline.ConnectionEndpoints("c-1",
                        new RoutingRect(100, 180, 40, 40, "src"),
                        new RoutingRect(380, 180, 40, 40, "tgt"),
                        List.of(), "", 1, List.of(g1, g2, g1Nested)));

        List<RoutingRect> topLevel = RoutingPipeline.extractTopLevelGroupBounds(connections);

        assertEquals("Only 2 top-level groups expected (nested excluded)", 2, topLevel.size());
        assertTrue("g1 should be in top-level set",
                topLevel.stream().anyMatch(r -> "g1".equals(r.id())));
        assertTrue("g2 should be in top-level set",
                topLevel.stream().anyMatch(r -> "g2".equals(r.id())));
        assertFalse("g1-nested should NOT be in top-level set",
                topLevel.stream().anyMatch(r -> "g1-nested".equals(r.id())));
    }

    // ---- T4.6: Axis-aware gating (AC-6) ----

    /**
     * B75 AC-6: A horizontal segment crossing a horizontal inter-group gap
     * (groups arranged L-to-R) should NOT get group-boundary tightening on
     * the Y-axis (perpendicular for horizontal segments) because the groups
     * span the full Y range. Only vertical segments get tightened by X-axis
     * group edges.
     */
    @Test
    public void groupBoundaryChannels_shouldOnlyTightenOnPerpendicularAxis() {
        // Groups arranged L-to-R, spanning full Y range.
        RoutingRect g1 = new RoutingRect(0, 0, 200, 400, "g1");
        RoutingRect g2 = new RoutingRect(320, 0, 200, 400, "g2");

        RoutingRect src = new RoutingRect(100, 180, 40, 40, "src");
        RoutingRect tgt = new RoutingRect(380, 180, 40, 40, "tgt");

        List<RoutingRect> topLevelGroupBounds = List.of(g1, g2);

        // Horizontal segment at y=200 crossing x=[140, 400]
        // axis=H, perpendicular=Y. Groups span Y=[0,400], both contain y=200.
        // tightenGapWithGroupBounds should skip both groups → gap unchanged.
        int[] elementGap = new int[]{100, 300}; // synthetic element-level gap
        int[] result = ChannelNudgingPass.tightenGapWithGroupBounds(
                elementGap, 200, true, topLevelGroupBounds);

        assertSame("Horizontal segment: gap should be unchanged (groups contain segment on Y-axis)",
                elementGap, result);

        // Vertical segment at x=260 in the gap, y=[100, 300]
        // axis=V, perpendicular=X. G1.maxX=200, G2.minX=320 — both outside x=260.
        int[] vElementGap = new int[]{160, 360}; // synthetic element-level gap
        int[] vResult = ChannelNudgingPass.tightenGapWithGroupBounds(
                vElementGap, 260, false, topLevelGroupBounds);

        assertEquals("Vertical segment: gapLow tightened to G1.maxX=200", 200, vResult[0]);
        assertEquals("Vertical segment: gapHigh tightened to G2.minX=320", 320, vResult[1]);
    }
}
