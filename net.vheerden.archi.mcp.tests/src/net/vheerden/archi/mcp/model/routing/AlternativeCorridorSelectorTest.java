package net.vheerden.archi.mcp.model.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import net.vheerden.archi.mcp.model.RoutingRect;
import net.vheerden.archi.mcp.model.routing.HubFaceConnectionPartitioner.CellMember;
import net.vheerden.archi.mcp.model.routing.HubFaceConnectionPartitioner.HubFaceCell;
import net.vheerden.archi.mcp.response.dto.AbsoluteBendpointDto;

/**
 * Unit tests for {@link AlternativeCorridorSelector}.
 *
 * <p>Pure-geometry: no EMF, no SWT, no PDE. Hand-constructed scenarios exercise
 * the proposeShift / apply / restore primitives without engaging the full
 * routing pipeline.
 */
public class AlternativeCorridorSelectorTest {

    private static final RoutingRect HUB = new RoutingRect(200, 200, 100, 100, "hub");

    private static AbsoluteBendpointDto bp(int x, int y) {
        return new AbsoluteBendpointDto(x, y);
    }

    private static List<AbsoluteBendpointDto> path(AbsoluteBendpointDto... bps) {
        return new ArrayList<>(Arrays.asList(bps));
    }

    private static HubFaceCell cellOf(EdgeAttachmentCalculator.Face face,
                                      CellMember... members) {
        return new HubFaceCell(HUB, face, Arrays.asList(members));
    }

    // =====================================================================
    // evaluate
    // =====================================================================

    @Test
    public void evaluate_shouldReturnEmpty_whenCellHasNoMembers() {
        HubFaceCell cell = cellOf(EdgeAttachmentCalculator.Face.TOP);
        AlternativeCorridorSelector sel = new AlternativeCorridorSelector();

        List<AlternativeCorridorSelector.AlternativeProposal> proposals =
                sel.evaluate(cell, List.of(), List.of(), 0.10);

        assertTrue(proposals.isEmpty());
    }

    @Test
    public void evaluate_shouldProposeShiftUp_forSegmentHuggingTopFace() {
        // Hub TOP at y=200. Segment at y=198 (within 5px). Path goes from spoke far above:
        // bp[0]=(120,40) → bp[1]=(120,198) → bp[2]=(280,198) → bp[3]=(280,40).
        // Shifted segment is bp[1]→bp[2] horizontal at y=198.
        // Shifting AWAY from TOP = decreasing y → toward the spokes above. Pre and post
        // stubs SHORTEN (they currently span y=40 to y=198, length 158; after shift to
        // y=190, length 150).
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        paths.add(path(bp(120, 40), bp(120, 198), bp(280, 198), bp(280, 40)));

        CellMember m = new CellMember("c-0", 0, 1, 2);
        HubFaceCell cell = cellOf(EdgeAttachmentCalculator.Face.TOP, m);

        AlternativeCorridorSelector sel = new AlternativeCorridorSelector();
        List<AlternativeCorridorSelector.AlternativeProposal> proposals =
                sel.evaluate(cell, paths, List.of(HUB), 0.10);

        assertEquals(1, proposals.size());
        AlternativeCorridorSelector.AlternativeProposal p = proposals.get(0);
        assertEquals("c-0", p.connectionId());
        assertTrue("horizontal flag", p.horizontal());
        assertEquals(198, p.oldPerpendicularCoord());
        assertTrue("new coord must be above (less than) old", p.newPerpendicularCoord() < p.oldPerpendicularCoord());
        assertTrue("new coord must be at least 10px above TOP",
                HUB.y() - p.newPerpendicularCoord() >= 10);
    }

    @Test
    public void evaluate_shouldProposeShiftDown_forSegmentHuggingBottomFace() {
        // Hub BOTTOM at y=300. Segment at y=302.
        // Path: bp[0]=(120,520) → bp[1]=(120,302) → bp[2]=(280,302) → bp[3]=(280,520).
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        paths.add(path(bp(120, 520), bp(120, 302), bp(280, 302), bp(280, 520)));

        CellMember m = new CellMember("c-1", 0, 1, 2);
        HubFaceCell cell = cellOf(EdgeAttachmentCalculator.Face.BOTTOM, m);

        AlternativeCorridorSelector sel = new AlternativeCorridorSelector();
        List<AlternativeCorridorSelector.AlternativeProposal> proposals =
                sel.evaluate(cell, paths, List.of(HUB), 0.10);

        assertEquals(1, proposals.size());
        AlternativeCorridorSelector.AlternativeProposal p = proposals.get(0);
        assertTrue("new coord must be below (greater than) old", p.newPerpendicularCoord() > p.oldPerpendicularCoord());
        assertTrue("new coord must be at least 10px below BOTTOM",
                p.newPerpendicularCoord() - (HUB.y() + HUB.height()) >= 10);
    }

    @Test
    public void evaluate_shouldProposeShiftLeft_forSegmentHuggingLeftFace() {
        // Hub LEFT at x=200. Vertical segment at x=197.
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        paths.add(path(bp(40, 220), bp(197, 220), bp(197, 280), bp(40, 280)));

        CellMember m = new CellMember("c-2", 0, 1, 2);
        HubFaceCell cell = cellOf(EdgeAttachmentCalculator.Face.LEFT, m);

        AlternativeCorridorSelector sel = new AlternativeCorridorSelector();
        List<AlternativeCorridorSelector.AlternativeProposal> proposals =
                sel.evaluate(cell, paths, List.of(HUB), 0.10);

        assertEquals(1, proposals.size());
        AlternativeCorridorSelector.AlternativeProposal p = proposals.get(0);
        assertFalse("vertical flag", p.horizontal());
        assertTrue("new coord must be left of old", p.newPerpendicularCoord() < p.oldPerpendicularCoord());
        assertTrue("new coord must be at least 10px left of LEFT",
                HUB.x() - p.newPerpendicularCoord() >= 10);
    }

    @Test
    public void evaluate_shouldProposeShiftRight_forSegmentHuggingRightFace() {
        // Hub RIGHT at x=300. Vertical segment at x=303.
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        paths.add(path(bp(440, 220), bp(303, 220), bp(303, 280), bp(440, 280)));

        CellMember m = new CellMember("c-3", 0, 1, 2);
        HubFaceCell cell = cellOf(EdgeAttachmentCalculator.Face.RIGHT, m);

        AlternativeCorridorSelector sel = new AlternativeCorridorSelector();
        List<AlternativeCorridorSelector.AlternativeProposal> proposals =
                sel.evaluate(cell, paths, List.of(HUB), 0.10);

        assertEquals(1, proposals.size());
        AlternativeCorridorSelector.AlternativeProposal p = proposals.get(0);
        assertTrue("new coord must be right of old", p.newPerpendicularCoord() > p.oldPerpendicularCoord());
        assertTrue("new coord must be at least 10px right of RIGHT",
                p.newPerpendicularCoord() - (HUB.x() + HUB.width()) >= 10);
    }

    @Test
    public void evaluate_shouldRejectProposal_whenObstacleBlocksAlternativeCorridor() {
        // Hub TOP at y=200. Segment at y=198. Blocker obstacle from y=185 to y=195 occupies
        // the away-corridor exactly where the shift would land.
        RoutingRect blocker = new RoutingRect(210, 185, 80, 10, "blocker");
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        paths.add(path(bp(120, 40), bp(120, 198), bp(280, 198), bp(280, 40)));

        CellMember m = new CellMember("c-0", 0, 1, 2);
        HubFaceCell cell = cellOf(EdgeAttachmentCalculator.Face.TOP, m);

        AlternativeCorridorSelector sel = new AlternativeCorridorSelector();
        List<AlternativeCorridorSelector.AlternativeProposal> proposals =
                sel.evaluate(cell, paths, List.of(HUB, blocker), 0.10);

        assertTrue("blocker leaves no room for target clearance", proposals.isEmpty());
    }

    @Test
    public void evaluate_shouldRejectProposal_whenLengthDeltaExceedsBudget() {
        // Spoke at y=196 BETWEEN old (y=198) and new (y=190) seg-y. Pre/post stubs
        // currently 2px each; after shift they grow to 6px each → Δ=+8.
        // Short shifted segment (x ∈ [210, 260], length 50) keeps origLen small so the
        // ratio escapes the 10% budget.
        //
        // origLen = 2 + 50 + 2 = 54.  shifted to y=190: 6 + 50 + 6 = 62.  Δ=+8.
        // ratio = 8/54 ≈ 0.148 > 0.10 strict budget, but < 0.30 relaxed budget.
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        paths.add(path(bp(210, 196), bp(210, 198), bp(260, 198), bp(260, 196)));

        CellMember m = new CellMember("c-0", 0, 1, 2);
        HubFaceCell cell = cellOf(EdgeAttachmentCalculator.Face.TOP, m);

        AlternativeCorridorSelector sel = new AlternativeCorridorSelector();
        List<AlternativeCorridorSelector.AlternativeProposal> proposals =
                sel.evaluate(cell, paths, List.of(HUB), 0.10);

        assertTrue("ratio ≈ 0.148 must be rejected at 10% budget", proposals.isEmpty());
    }

    @Test
    public void evaluate_shouldAcceptProposal_atRelaxedBudget_whenStrictBudgetWouldReject() {
        // Same path as the rejection test, but widened budget (retry path) admits.
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        paths.add(path(bp(210, 196), bp(210, 198), bp(260, 198), bp(260, 196)));

        CellMember m = new CellMember("c-0", 0, 1, 2);
        HubFaceCell cell = cellOf(EdgeAttachmentCalculator.Face.TOP, m);

        AlternativeCorridorSelector sel = new AlternativeCorridorSelector();
        List<AlternativeCorridorSelector.AlternativeProposal> proposals = sel.evaluate(
                cell, paths, List.of(HUB),
                AlternativeCorridorSelector.ALTERNATIVE_CORRIDOR_COST_PREMIUM_MAX);

        assertEquals(1, proposals.size());
    }

    @Test
    public void apply_shouldMutatePath_inPlace() {
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        paths.add(path(bp(120, 40), bp(120, 198), bp(280, 198), bp(280, 40)));

        CellMember m = new CellMember("c-0", 0, 1, 2);
        HubFaceCell cell = cellOf(EdgeAttachmentCalculator.Face.TOP, m);
        AlternativeCorridorSelector sel = new AlternativeCorridorSelector();
        var proposals = sel.evaluate(cell, paths, List.of(HUB), 0.10);
        assertEquals(1, proposals.size());

        var snapshot = sel.apply(proposals.get(0), paths);

        assertNotNull(snapshot);
        assertEquals("bp[1] perpendicular shifted", proposals.get(0).newPerpendicularCoord(),
                paths.get(0).get(1).y());
        assertEquals("bp[2] perpendicular shifted", proposals.get(0).newPerpendicularCoord(),
                paths.get(0).get(2).y());
    }

    @Test
    public void restore_shouldRevertPath_fromSnapshot() {
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        paths.add(path(bp(120, 40), bp(120, 198), bp(280, 198), bp(280, 40)));
        List<AbsoluteBendpointDto> original = new ArrayList<>(paths.get(0));

        CellMember m = new CellMember("c-0", 0, 1, 2);
        HubFaceCell cell = cellOf(EdgeAttachmentCalculator.Face.TOP, m);
        AlternativeCorridorSelector sel = new AlternativeCorridorSelector();
        var proposals = sel.evaluate(cell, paths, List.of(HUB), 0.10);
        var snapshot = sel.apply(proposals.get(0), paths);

        sel.restore(snapshot, paths);

        assertEquals(original.get(1), paths.get(0).get(1));
        assertEquals(original.get(2), paths.get(0).get(2));
    }

    @Test
    public void evaluate_shouldSkipMember_whenSegmentInsideHubBounds() {
        // Inside-hub passthrough defect (y=298 inside hub y=[200,300]).
        // The "AWAY from face" direction (down for BOTTOM) is INTO the hub, which would be wrong.
        // Selector must skip — let other defect classes (PathStraightener pass-through) handle this.
        List<List<AbsoluteBendpointDto>> paths = new ArrayList<>();
        paths.add(path(bp(120, 250), bp(120, 298), bp(280, 298), bp(280, 250)));

        CellMember m = new CellMember("c-0", 0, 1, 2);
        HubFaceCell cell = cellOf(EdgeAttachmentCalculator.Face.BOTTOM, m);

        AlternativeCorridorSelector sel = new AlternativeCorridorSelector();
        List<AlternativeCorridorSelector.AlternativeProposal> proposals =
                sel.evaluate(cell, paths, List.of(HUB), 0.10);

        assertTrue("inside-hub segments must be skipped (defect of a different class)",
                proposals.isEmpty());
    }

}
