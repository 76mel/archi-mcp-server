package net.vheerden.archi.mcp.model.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import net.vheerden.archi.mcp.model.RoutingRect;
import net.vheerden.archi.mcp.response.dto.AbsoluteBendpointDto;

/**
 * Unit tests for {@link HubFaceConnectionPartitioner} (H5 story Task 1).
 *
 * <p>Pure-geometry: no EMF, no SWT, no PDE. All inputs are hand-constructed
 * synthetic hub-and-spokes scenarios designed to exercise the specific
 * detection / partitioning branches. No dependency on the full routing
 * pipeline.
 */
public class HubFaceConnectionPartitionerTest {

    private static RoutingPipeline.ConnectionEndpoints conn(
            String id, RoutingRect src, RoutingRect tgt) {
        return new RoutingPipeline.ConnectionEndpoints(
                id, src, tgt, Collections.emptyList(), "", 1, Collections.emptyList());
    }

    private static AbsoluteBendpointDto bp(int x, int y) {
        return new AbsoluteBendpointDto(x, y);
    }

    private static List<AbsoluteBendpointDto> path(AbsoluteBendpointDto... bps) {
        return new ArrayList<>(Arrays.asList(bps));
    }

    // =====================================================================
    // detectHubs
    // =====================================================================

    @Test
    public void detectHubs_shouldReturnEmpty_whenNoConnections() {
        RoutingRect el = new RoutingRect(0, 0, 100, 50, "el-1");
        HubFaceConnectionPartitioner p = new HubFaceConnectionPartitioner();

        List<RoutingRect> hubs = p.detectHubs(Collections.emptyList(), List.of(el));

        assertTrue("no connections → no hubs", hubs.isEmpty());
    }

    @Test
    public void detectHubs_shouldIncludeElement_whenDegreeAtThreshold() {
        // Threshold = 5; create exactly 5 incident connections.
        RoutingRect hub = new RoutingRect(500, 500, 100, 50, "hub");
        List<RoutingPipeline.ConnectionEndpoints> conns = new ArrayList<>();
        List<RoutingRect> all = new ArrayList<>();
        all.add(hub);
        for (int i = 0; i < 5; i++) {
            RoutingRect spoke = new RoutingRect(100 * i, 0, 30, 30, "spoke-" + i);
            all.add(spoke);
            conns.add(conn("c-" + i, spoke, hub));
        }

        HubFaceConnectionPartitioner p = new HubFaceConnectionPartitioner();
        List<RoutingRect> hubs = p.detectHubs(conns, all);

        assertEquals(1, hubs.size());
        assertEquals("hub", hubs.get(0).id());
    }

    @Test
    public void detectHubs_shouldExcludeElement_whenDegreeBelowThreshold() {
        // 4 incident connections — one short of the threshold.
        RoutingRect almostHub = new RoutingRect(500, 500, 100, 50, "almost-hub");
        List<RoutingPipeline.ConnectionEndpoints> conns = new ArrayList<>();
        List<RoutingRect> all = new ArrayList<>();
        all.add(almostHub);
        for (int i = 0; i < 4; i++) {
            RoutingRect spoke = new RoutingRect(100 * i, 0, 30, 30, "spoke-" + i);
            all.add(spoke);
            conns.add(conn("c-" + i, spoke, almostHub));
        }

        HubFaceConnectionPartitioner p = new HubFaceConnectionPartitioner();
        List<RoutingRect> hubs = p.detectHubs(conns, all);

        assertTrue("degree=4 → not a hub", hubs.isEmpty());
    }

    // =====================================================================
    // partition
    // =====================================================================

    @Test
    public void partition_shouldReturnEmpty_whenNoHubsDetected() {
        RoutingRect a = new RoutingRect(0, 0, 30, 30, "a");
        RoutingRect b = new RoutingRect(200, 0, 30, 30, "b");
        List<RoutingPipeline.ConnectionEndpoints> conns = List.of(conn("c-1", a, b));
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        paths.add(path(bp(15, 15), bp(215, 15)));

        HubFaceConnectionPartitioner p = new HubFaceConnectionPartitioner();
        List<HubFaceConnectionPartitioner.HubFaceCell> cells =
                p.partition(conns, paths, List.of(a, b));

        assertTrue("no hub → no cells", cells.isEmpty());
    }

    @Test
    public void partition_shouldGroupSegmentByFace_whenHuggingHubPerimeter() {
        // Hub at (200, 200, 100, 100): faces TOP y=200 / BOTTOM y=300 / LEFT x=200 / RIGHT x=300.
        // 5 spokes (one per face plus one extra) to make hub eligible.
        RoutingRect hub = new RoutingRect(200, 200, 100, 100, "hub");
        RoutingRect[] spokes = new RoutingRect[5];
        List<RoutingPipeline.ConnectionEndpoints> conns = new ArrayList<>();
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            spokes[i] = new RoutingRect(50 + i * 30, 50, 20, 20, "spoke-" + i);
            conns.add(conn("c-" + i, spokes[i], hub));
        }
        // c-0 horizontal segment at y=198 (within 5px above TOP y=200) spanning hub x-range.
        paths.add(path(bp(60, 60), bp(60, 198), bp(290, 198), bp(290, 320)));
        // c-1 horizontal segment at y=302 (within 5px below BOTTOM y=300) spanning hub x-range.
        paths.add(path(bp(80, 60), bp(80, 302), bp(280, 302), bp(280, 320)));
        // c-2 vertical segment at x=197 (within 5px left of LEFT x=200) spanning hub y-range.
        paths.add(path(bp(60, 60), bp(197, 60), bp(197, 280), bp(60, 280)));
        // c-3 vertical segment at x=303 (within 5px right of RIGHT x=300) spanning hub y-range.
        paths.add(path(bp(60, 60), bp(303, 60), bp(303, 280), bp(60, 280)));
        // c-4 no hub-perimeter-proximate segment (far above hub).
        paths.add(path(bp(150, 100), bp(150, 150)));

        List<RoutingRect> all = new ArrayList<>();
        all.add(hub);
        Collections.addAll(all, spokes);

        HubFaceConnectionPartitioner p = new HubFaceConnectionPartitioner();
        List<HubFaceConnectionPartitioner.HubFaceCell> cells = p.partition(conns, paths, all);

        assertTrue("expected TOP cell with c-0", hasMember(cells, "hub",
                EdgeAttachmentCalculator.Face.TOP, "c-0"));
        assertTrue("expected BOTTOM cell with c-1", hasMember(cells, "hub",
                EdgeAttachmentCalculator.Face.BOTTOM, "c-1"));
        assertTrue("expected LEFT cell with c-2", hasMember(cells, "hub",
                EdgeAttachmentCalculator.Face.LEFT, "c-2"));
        assertTrue("expected RIGHT cell with c-3", hasMember(cells, "hub",
                EdgeAttachmentCalculator.Face.RIGHT, "c-3"));
        assertFalse("c-4 has no proximate segment",
                cells.stream().flatMap(c -> c.members().stream())
                        .anyMatch(m -> "c-4".equals(m.connectionId())));
    }

    @Test
    public void partition_shouldExcludeSegment_whenBeyondProximityTolerance() {
        // Hub TOP at y=200; interior segment at y=190 → 10px above → outside 5px tolerance.
        // 4-bp path ensures the partitioner's terminal-incident filter does not auto-exclude
        // the candidate — the proximity check is what must drive exclusion.
        RoutingRect hub = new RoutingRect(200, 200, 100, 100, "hub");
        List<RoutingPipeline.ConnectionEndpoints> conns = new ArrayList<>();
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            RoutingRect spoke = new RoutingRect(50 + i * 30, 50, 20, 20, "spoke-" + i);
            conns.add(conn("c-" + i, spoke, hub));
            if (i == 0) {
                paths.add(path(bp(210, 60), bp(210, 190), bp(290, 190), bp(290, 60)));
            } else {
                paths.add(path(bp(150, 100), bp(150, 150)));
            }
        }
        List<RoutingRect> all = new ArrayList<>(List.of(hub));
        for (int i = 0; i < 5; i++) all.add(new RoutingRect(50 + i * 30, 50, 20, 20, "spoke-" + i));

        HubFaceConnectionPartitioner p = new HubFaceConnectionPartitioner();
        List<HubFaceConnectionPartitioner.HubFaceCell> cells = p.partition(conns, paths, all);

        assertFalse("c-0 interior segment outside proximity should not be in any TOP cell",
                hasMember(cells, "hub", EdgeAttachmentCalculator.Face.TOP, "c-0"));
    }

    @Test
    public void partition_shouldExcludeSegment_whenInsufficientParallelOverlap() {
        // Hub at x ∈ [200, 300]; interior horizontal segment from x=290 to x=298 → overlap = 8px.
        // 4-bp path so terminal filter does not pre-empt the overlap check.
        RoutingRect hub = new RoutingRect(200, 200, 100, 100, "hub");
        List<RoutingPipeline.ConnectionEndpoints> conns = new ArrayList<>();
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            RoutingRect spoke = new RoutingRect(50 + i * 30, 50, 20, 20, "spoke-" + i);
            conns.add(conn("c-" + i, spoke, hub));
            if (i == 0) {
                paths.add(path(bp(290, 60), bp(290, 198), bp(298, 198), bp(298, 60)));
            } else {
                paths.add(path(bp(150, 100), bp(150, 150)));
            }
        }
        List<RoutingRect> all = new ArrayList<>(List.of(hub));
        for (int i = 0; i < 5; i++) all.add(new RoutingRect(50 + i * 30, 50, 20, 20, "spoke-" + i));

        HubFaceConnectionPartitioner p = new HubFaceConnectionPartitioner();
        List<HubFaceConnectionPartitioner.HubFaceCell> cells = p.partition(conns, paths, all);

        assertFalse("c-0 interior segment with 8px overlap should be excluded",
                hasMember(cells, "hub", EdgeAttachmentCalculator.Face.TOP, "c-0"));
    }

    @Test
    public void partition_shouldSkipSegment_whenDiagonal() {
        // Interior diagonal segment from (210, 198) to (290, 210). dx=80, dy=12 — neither
        // axis-aligned. 4-bp path so the terminal filter does not pre-empt the diagonal check.
        RoutingRect hub = new RoutingRect(200, 200, 100, 100, "hub");
        List<RoutingPipeline.ConnectionEndpoints> conns = new ArrayList<>();
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            RoutingRect spoke = new RoutingRect(50 + i * 30, 50, 20, 20, "spoke-" + i);
            conns.add(conn("c-" + i, spoke, hub));
            if (i == 0) {
                paths.add(path(bp(210, 60), bp(210, 198), bp(290, 210), bp(290, 60)));
            } else {
                paths.add(path(bp(150, 100), bp(150, 150)));
            }
        }
        List<RoutingRect> all = new ArrayList<>(List.of(hub));
        for (int i = 0; i < 5; i++) all.add(new RoutingRect(50 + i * 30, 50, 20, 20, "spoke-" + i));

        HubFaceConnectionPartitioner p = new HubFaceConnectionPartitioner();
        List<HubFaceConnectionPartitioner.HubFaceCell> cells = p.partition(conns, paths, all);

        assertFalse("diagonal interior segment is not axis-aligned → not classified",
                cells.stream().flatMap(c -> c.members().stream())
                        .anyMatch(m -> "c-0".equals(m.connectionId())));
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private static boolean hasMember(List<HubFaceConnectionPartitioner.HubFaceCell> cells,
                                     String hubId,
                                     EdgeAttachmentCalculator.Face face,
                                     String connectionId) {
        return cells.stream().anyMatch(c ->
                hubId.equals(c.hub().id())
                        && c.face() == face
                        && c.members().stream().anyMatch(m -> connectionId.equals(m.connectionId())));
    }
}
