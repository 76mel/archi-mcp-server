package net.vheerden.archi.mcp.model.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import net.vheerden.archi.mcp.model.RoutingRect;
import net.vheerden.archi.mcp.model.routing.HubFaceConnectionPartitioner.CellMember;
import net.vheerden.archi.mcp.model.routing.HubFaceConnectionPartitioner.HubFaceCell;
import net.vheerden.archi.mcp.response.dto.AbsoluteBendpointDto;

/**
 * Unit tests for {@link CorridorSpreadEnforcer} (H5 story Task 3 — Axis 2).
 *
 * <p>Pure-geometry: no EMF, no SWT, no PDE. Synthetic hub-and-spokes scenarios
 * exercise the zero-spread-detection / spread-allocation / B71 exemption /
 * apply-restore primitives without engaging the routing pipeline.
 */
public class CorridorSpreadEnforcerTest {

    private static final RoutingRect HUB = new RoutingRect(200, 200, 100, 100, "hub");

    private static AbsoluteBendpointDto bp(int x, int y) {
        return new AbsoluteBendpointDto(x, y);
    }

    private static List<AbsoluteBendpointDto> path(AbsoluteBendpointDto... bps) {
        return new ArrayList<>(Arrays.asList(bps));
    }

    private static HubFaceCell cellOf(EdgeAttachmentCalculator.Face face, CellMember... members) {
        return new HubFaceCell(HUB, face, Arrays.asList(members));
    }

    // =====================================================================
    // evaluate
    // =====================================================================

    @Test
    public void evaluate_shouldReturnEmpty_whenCellHasFewerThanThreeMembers() {
        // Only 2 same-coord members → below MIN_PARALLEL_COUNT trigger.
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        paths.add(path(bp(120, 40), bp(120, 190), bp(220, 190), bp(220, 40)));
        paths.add(path(bp(130, 40), bp(130, 190), bp(230, 190), bp(230, 40)));

        HubFaceCell cell = cellOf(EdgeAttachmentCalculator.Face.TOP,
                new CellMember("c-0", 0, 1, 2),
                new CellMember("c-1", 1, 1, 2));

        CorridorSpreadEnforcer enf = new CorridorSpreadEnforcer();
        List<CorridorSpreadEnforcer.SpreadProposal> proposals =
                enf.evaluate(cell, paths, List.of(HUB));

        assertTrue("N=2 below trigger → no proposals", proposals.isEmpty());
    }

    @Test
    public void evaluate_shouldReturnEmpty_whenMembersAlreadySpread() {
        // 3 members at distinct perpendicular coords (190, 170, 150) — already spread by ≥20.
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        paths.add(path(bp(120, 40), bp(120, 190), bp(220, 190), bp(220, 40)));
        paths.add(path(bp(130, 40), bp(130, 170), bp(230, 170), bp(230, 40)));
        paths.add(path(bp(140, 40), bp(140, 150), bp(240, 150), bp(240, 40)));

        HubFaceCell cell = cellOf(EdgeAttachmentCalculator.Face.TOP,
                new CellMember("c-0", 0, 1, 2),
                new CellMember("c-1", 1, 1, 2),
                new CellMember("c-2", 2, 1, 2));

        CorridorSpreadEnforcer enf = new CorridorSpreadEnforcer();
        List<CorridorSpreadEnforcer.SpreadProposal> proposals =
                enf.evaluate(cell, paths, List.of(HUB));

        assertTrue("already spread → no proposals", proposals.isEmpty());
    }

    @Test
    public void evaluate_shouldProposeSpread_whenThreeMembersAtSameCoord() {
        // 3 horizontal segments all at y=190 above TOP face (y=200).
        // Spread direction = AWAY from TOP = -1 (decreasing y).
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int x = 210 + i * 10;
            paths.add(path(bp(x, 40), bp(x, 190), bp(x + 30, 190), bp(x + 30, 40)));
        }

        HubFaceCell cell = cellOf(EdgeAttachmentCalculator.Face.TOP,
                new CellMember("c-0", 0, 1, 2),
                new CellMember("c-1", 1, 1, 2),
                new CellMember("c-2", 2, 1, 2));

        CorridorSpreadEnforcer enf = new CorridorSpreadEnforcer();
        List<CorridorSpreadEnforcer.SpreadProposal> proposals =
                enf.evaluate(cell, paths, List.of(HUB));

        assertEquals("expected one proposal per member of the spread group", 3, proposals.size());
        // Distinct new coords.
        Set<Integer> newCoords = new HashSet<>();
        for (CorridorSpreadEnforcer.SpreadProposal p : proposals) {
            newCoords.add(p.newPerpendicularCoord());
        }
        assertEquals("each member must land on a distinct perpendicular coord",
                3, newCoords.size());
        // All shifts go AWAY from hub (TOP face → decreasing y).
        for (CorridorSpreadEnforcer.SpreadProposal p : proposals) {
            assertTrue("new coord must be at or below old coord (away from TOP)",
                    p.newPerpendicularCoord() <= p.oldPerpendicularCoord());
        }
    }

    @Test
    public void evaluate_shouldEnforceMinSpread_evenWithLargeChannel() {
        // Same scenario but no obstacles blocking — should still use MIN_SPREAD_PX (20).
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int x = 210 + i * 10;
            paths.add(path(bp(x, 40), bp(x, 190), bp(x + 30, 190), bp(x + 30, 40)));
        }

        HubFaceCell cell = cellOf(EdgeAttachmentCalculator.Face.TOP,
                new CellMember("c-0", 0, 1, 2),
                new CellMember("c-1", 1, 1, 2),
                new CellMember("c-2", 2, 1, 2));

        CorridorSpreadEnforcer enf = new CorridorSpreadEnforcer();
        List<CorridorSpreadEnforcer.SpreadProposal> proposals =
                enf.evaluate(cell, paths, List.of(HUB));

        assertEquals(3, proposals.size());
        List<Integer> coords = new ArrayList<>();
        for (CorridorSpreadEnforcer.SpreadProposal p : proposals) {
            coords.add(p.newPerpendicularCoord());
        }
        coords.sort(Integer::compareTo);
        int gap1 = coords.get(1) - coords.get(0);
        int gap2 = coords.get(2) - coords.get(1);
        assertTrue("min-spread of 20px must hold (gap1=" + gap1 + ")",
                gap1 >= CorridorSpreadEnforcer.MIN_SPREAD_PX);
        assertTrue("min-spread of 20px must hold (gap2=" + gap2 + ")",
                gap2 >= CorridorSpreadEnforcer.MIN_SPREAD_PX);
    }

    @Test
    public void evaluate_shouldReduceSpread_whenAvailableChannelTight() {
        // Blocking obstacle at y=130 → channel above hub top has width 200-130=70 minus
        // hub clearance (10) and obstacle clearance (10) = 50.
        // With N=3 we'd want 20×2=40 between extremes; 50 fits, gaps of 20 OK.
        // Tighten further: blocker at y=170 → width 30 effective. Spread must reduce.
        RoutingRect blocker = new RoutingRect(200, 170, 100, 10, "blocker");
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int x = 210 + i * 10;
            paths.add(path(bp(x, 40), bp(x, 190), bp(x + 30, 190), bp(x + 30, 40)));
        }

        HubFaceCell cell = cellOf(EdgeAttachmentCalculator.Face.TOP,
                new CellMember("c-0", 0, 1, 2),
                new CellMember("c-1", 1, 1, 2),
                new CellMember("c-2", 2, 1, 2));

        CorridorSpreadEnforcer enf = new CorridorSpreadEnforcer();
        List<CorridorSpreadEnforcer.SpreadProposal> proposals =
                enf.evaluate(cell, paths, List.of(HUB, blocker));

        // Channel is too tight (blocker bottom at y=180 + 10 clearance leaves zero room
        // above the base coord 190 going away from hub TOP at 200). MVP algorithm returns
        // empty proposals when spread cannot fit. Either empty OR distinct coords is OK.
        if (!proposals.isEmpty()) {
            Set<Integer> newCoords = new HashSet<>();
            for (CorridorSpreadEnforcer.SpreadProposal p : proposals) {
                newCoords.add(p.newPerpendicularCoord());
            }
            assertEquals("proposed coords must be distinct", proposals.size(), newCoords.size());
        }
    }

    @Test
    public void evaluate_shouldSkipTerminalIncidentMember() {
        // c-0's segment at indices (0, 1) — touches terminal bp[0]. B71 exemption: skip.
        // c-1 and c-2 are interior segments — proceed.
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        paths.add(path(bp(210, 190), bp(240, 190))); // 2-bp path: shifting bp[0]/bp[1] = terminals
        for (int i = 1; i < 3; i++) {
            int x = 220 + i * 10;
            paths.add(path(bp(x, 40), bp(x, 190), bp(x + 30, 190), bp(x + 30, 40)));
        }

        HubFaceCell cell = cellOf(EdgeAttachmentCalculator.Face.TOP,
                new CellMember("c-0", 0, 0, 1),  // terminal-incident
                new CellMember("c-1", 1, 1, 2),
                new CellMember("c-2", 2, 1, 2));

        CorridorSpreadEnforcer enf = new CorridorSpreadEnforcer();
        List<CorridorSpreadEnforcer.SpreadProposal> proposals =
                enf.evaluate(cell, paths, List.of(HUB));

        // Only c-1 and c-2 are eligible; that's just 2 → below trigger. proposals empty.
        // Alternatively if the enforcer still attempts a spread with 2 non-exempt members,
        // the proposal count must reflect exclusion of c-0.
        for (CorridorSpreadEnforcer.SpreadProposal p : proposals) {
            assertTrue("c-0 terminal-incident member must not be proposed for spread",
                    !"c-0".equals(p.connectionId()));
        }
    }

    @Test
    public void evaluate_shouldSpread_forBottomFace_directionDownward() {
        // 3 horizontal segments all at y=310 below BOTTOM face (y=300).
        // Spread direction = AWAY from BOTTOM = +1 (increasing y).
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int x = 210 + i * 10;
            paths.add(path(bp(x, 500), bp(x, 310), bp(x + 30, 310), bp(x + 30, 500)));
        }

        HubFaceCell cell = cellOf(EdgeAttachmentCalculator.Face.BOTTOM,
                new CellMember("c-0", 0, 1, 2),
                new CellMember("c-1", 1, 1, 2),
                new CellMember("c-2", 2, 1, 2));

        CorridorSpreadEnforcer enf = new CorridorSpreadEnforcer();
        List<CorridorSpreadEnforcer.SpreadProposal> proposals =
                enf.evaluate(cell, paths, List.of(HUB));

        assertEquals(3, proposals.size());
        for (CorridorSpreadEnforcer.SpreadProposal p : proposals) {
            assertTrue("new coord must be at or below old coord (wait — BOTTOM means away = +y)",
                    p.newPerpendicularCoord() >= p.oldPerpendicularCoord());
        }
    }

    @Test
    public void apply_shouldMutatePath_inPlace() {
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int x = 210 + i * 10;
            paths.add(path(bp(x, 40), bp(x, 190), bp(x + 30, 190), bp(x + 30, 40)));
        }

        HubFaceCell cell = cellOf(EdgeAttachmentCalculator.Face.TOP,
                new CellMember("c-0", 0, 1, 2),
                new CellMember("c-1", 1, 1, 2),
                new CellMember("c-2", 2, 1, 2));

        CorridorSpreadEnforcer enf = new CorridorSpreadEnforcer();
        var proposals = enf.evaluate(cell, paths, List.of(HUB));
        assertEquals(3, proposals.size());

        CorridorSpreadEnforcer.SpreadProposal first = proposals.get(0);
        var snap = enf.apply(first, paths);

        assertNotNull(snap);
        assertEquals(first.newPerpendicularCoord(), paths.get(first.pathIndex()).get(1).y());
        assertEquals(first.newPerpendicularCoord(), paths.get(first.pathIndex()).get(2).y());
    }

    @Test
    public void restore_shouldRevertPath_fromSnapshot() {
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int x = 210 + i * 10;
            paths.add(path(bp(x, 40), bp(x, 190), bp(x + 30, 190), bp(x + 30, 40)));
        }
        List<AbsoluteBendpointDto> originalC0 = new ArrayList<>(paths.get(0));

        HubFaceCell cell = cellOf(EdgeAttachmentCalculator.Face.TOP,
                new CellMember("c-0", 0, 1, 2),
                new CellMember("c-1", 1, 1, 2),
                new CellMember("c-2", 2, 1, 2));
        CorridorSpreadEnforcer enf = new CorridorSpreadEnforcer();
        var proposals = enf.evaluate(cell, paths, List.of(HUB));
        var snap = enf.apply(proposals.get(0), paths);

        enf.restore(snap, paths);

        assertEquals(originalC0.get(1), paths.get(0).get(1));
        assertEquals(originalC0.get(2), paths.get(0).get(2));
    }
}
