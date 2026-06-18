package net.vheerden.archi.mcp.model.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;

import org.junit.Test;

import net.vheerden.archi.mcp.model.routing.CoincidentSegmentDiagnostic.Category;
import net.vheerden.archi.mcp.model.routing.CoincidentSegmentDiagnostic.ElementRect;
import net.vheerden.archi.mcp.model.routing.CoincidentSegmentDiagnostic.GroupRect;

/**
 * Tests for {@link CoincidentSegmentDiagnostic} — pure-geometry classification
 * of coincident segments by location.
 *
 * <p>Priority order under test:
 * TERMINAL_APPROACH > GAP_CROSSING > WITHIN_GROUP > UNCATEGORIZED.</p>
 */
public class CoincidentSegmentDiagnosticTest {

    // ---- Terminal approach: endpoint within 30px of source/target face ----

    @Test
    public void classify_terminalApproach_endpointInsideSourceRect() {
        // Source element at (100,100) 80x40 → covers x=[100,180], y=[100,140].
        // Segment starts at (110,120) which is inside the rect.
        PathOrderer.Segment seg = new PathOrderer.Segment(
                0, 0, 110, 120, 200, 120, true);
        ElementRect source = new ElementRect("src", 100, 100, 80, 40);
        ElementRect target = new ElementRect("tgt", 500, 120, 80, 40);

        Category cat = CoincidentSegmentDiagnostic.classify(
                seg, source, target, List.of());

        assertEquals(Category.TERMINAL_APPROACH, cat);
    }

    @Test
    public void classify_terminalApproach_endpointWithin30pxOfTargetRect() {
        // Source rect x=[20,60] (far from segment start at x=200 → 140px away, no terminal hit).
        // Target rect x=[500,580]. Segment end at (475,120): 500-475=25px from left edge (≤30 → terminal hit).
        PathOrderer.Segment seg = new PathOrderer.Segment(
                0, 0, 200, 120, 475, 120, true);
        ElementRect source = new ElementRect("src", 20, 100, 40, 40);
        ElementRect target = new ElementRect("tgt", 500, 100, 80, 40);

        Category cat = CoincidentSegmentDiagnostic.classify(
                seg, source, target, List.of());

        assertEquals(Category.TERMINAL_APPROACH, cat);
    }

    @Test
    public void classify_notTerminalApproach_bothEndpointsBeyond30pxFromRects() {
        // Source rect x=[20,60], target rect x=[600,680]. Segment from (200,120) to (460,120).
        // Start distance to source right edge (x=60): 200-60 = 140 (>30).
        // End distance to target left edge (x=600): 600-460 = 140 (>30).
        // No groups → falls through to UNCATEGORIZED.
        PathOrderer.Segment seg = new PathOrderer.Segment(
                0, 0, 200, 120, 460, 120, true);
        ElementRect source = new ElementRect("src", 20, 100, 40, 40);
        ElementRect target = new ElementRect("tgt", 600, 100, 80, 40);

        Category cat = CoincidentSegmentDiagnostic.classify(
                seg, source, target, List.of());

        assertEquals(Category.UNCATEGORIZED, cat);
    }

    // ---- Gap crossing: endpoints in two different top-level groups ----

    @Test
    public void classify_gapCrossing_endpointsInDifferentGroups() {
        // Group A x=[0,300], Group B x=[400,700]. Segment from x=250 (in A) to x=450 (in B), y=200.
        // Source/target rects far away so no terminal approach.
        PathOrderer.Segment seg = new PathOrderer.Segment(
                0, 0, 250, 200, 450, 200, true);
        ElementRect source = new ElementRect("src", 900, 900, 40, 40);
        ElementRect target = new ElementRect("tgt", 950, 900, 40, 40);
        GroupRect groupA = new GroupRect("gA", 0, 0, 300, 500);
        GroupRect groupB = new GroupRect("gB", 400, 0, 300, 500);

        Category cat = CoincidentSegmentDiagnostic.classify(
                seg, source, target, List.of(groupA, groupB));

        assertEquals(Category.GAP_CROSSING, cat);
    }

    @Test
    public void classify_gapCrossing_oneEndpointInsideOneOutside() {
        // Single group A x=[0,300]. Segment from (250,200) inside A to (450,200) outside any group.
        PathOrderer.Segment seg = new PathOrderer.Segment(
                0, 0, 250, 200, 450, 200, true);
        ElementRect source = new ElementRect("src", 900, 900, 40, 40);
        ElementRect target = new ElementRect("tgt", 950, 900, 40, 40);
        GroupRect groupA = new GroupRect("gA", 0, 0, 300, 500);

        Category cat = CoincidentSegmentDiagnostic.classify(
                seg, source, target, List.of(groupA));

        assertEquals(Category.GAP_CROSSING, cat);
    }

    // ---- Within group: both endpoints inside the same top-level group ----

    @Test
    public void classify_withinGroup_bothEndpointsInSameGroup() {
        // Group A x=[0,500]. Segment from (100,200) to (400,200) — both inside A.
        PathOrderer.Segment seg = new PathOrderer.Segment(
                0, 0, 100, 200, 400, 200, true);
        ElementRect source = new ElementRect("src", 900, 900, 40, 40);
        ElementRect target = new ElementRect("tgt", 950, 900, 40, 40);
        GroupRect groupA = new GroupRect("gA", 0, 0, 500, 500);

        Category cat = CoincidentSegmentDiagnostic.classify(
                seg, source, target, List.of(groupA));

        assertEquals(Category.WITHIN_GROUP, cat);
    }

    // ---- Uncategorized: no groups or both endpoints outside any group ----

    @Test
    public void classify_uncategorized_noGroupsAtAll() {
        PathOrderer.Segment seg = new PathOrderer.Segment(
                0, 0, 100, 200, 400, 200, true);
        ElementRect source = new ElementRect("src", 900, 900, 40, 40);
        ElementRect target = new ElementRect("tgt", 950, 900, 40, 40);

        Category cat = CoincidentSegmentDiagnostic.classify(
                seg, source, target, List.of());

        assertEquals(Category.UNCATEGORIZED, cat);
    }

    @Test
    public void classify_uncategorized_bothEndpointsOutsideAllGroups() {
        // Group at x=[0,300]. Segment from (400,200) to (700,200) — both outside.
        PathOrderer.Segment seg = new PathOrderer.Segment(
                0, 0, 400, 200, 700, 200, true);
        ElementRect source = new ElementRect("src", 900, 900, 40, 40);
        ElementRect target = new ElementRect("tgt", 950, 900, 40, 40);
        GroupRect groupA = new GroupRect("gA", 0, 0, 300, 500);

        Category cat = CoincidentSegmentDiagnostic.classify(
                seg, source, target, List.of(groupA));

        assertEquals(Category.UNCATEGORIZED, cat);
    }

    // ---- Priority: terminal approach wins over gap crossing ----

    @Test
    public void classify_priorityOrder_terminalBeatsGapCrossing() {
        // Segment from (250,200) [inside group A] to (450,200) [inside group B] — would be GAP_CROSSING
        // BUT target rect is at (440,185) within 30px of the segment's end at (450,200) → TERMINAL_APPROACH wins.
        PathOrderer.Segment seg = new PathOrderer.Segment(
                0, 0, 250, 200, 450, 200, true);
        ElementRect source = new ElementRect("src", 900, 900, 40, 40);
        ElementRect target = new ElementRect("tgt", 440, 185, 30, 30);
        GroupRect groupA = new GroupRect("gA", 0, 0, 300, 500);
        GroupRect groupB = new GroupRect("gB", 400, 0, 300, 500);

        Category cat = CoincidentSegmentDiagnostic.classify(
                seg, source, target, List.of(groupA, groupB));

        assertEquals(Category.TERMINAL_APPROACH, cat);
    }

    @Test
    public void classify_priorityOrder_gapCrossingBeatsWithinGroup() {
        // Single group A covering both endpoints would be WITHIN_GROUP, but we configure
        // two separate groups so endpoints land in different groups → GAP_CROSSING wins.
        PathOrderer.Segment seg = new PathOrderer.Segment(
                0, 0, 100, 200, 600, 200, true);
        ElementRect source = new ElementRect("src", 900, 900, 40, 40);
        ElementRect target = new ElementRect("tgt", 950, 900, 40, 40);
        GroupRect groupA = new GroupRect("gA", 0, 0, 300, 500);
        GroupRect groupB = new GroupRect("gB", 400, 0, 300, 500);

        Category cat = CoincidentSegmentDiagnostic.classify(
                seg, source, target, List.of(groupA, groupB));

        assertEquals(Category.GAP_CROSSING, cat);
    }

    // ---- Null rect handling: missing source/target must not throw ----

    @Test
    public void classify_nullSourceOrTarget_doesNotThrow() {
        PathOrderer.Segment seg = new PathOrderer.Segment(
                0, 0, 100, 200, 400, 200, true);
        GroupRect groupA = new GroupRect("gA", 0, 0, 500, 500);

        Category cat = CoincidentSegmentDiagnostic.classify(
                seg, null, null, List.of(groupA));

        // Both endpoints in group A, no terminals to check → WITHIN_GROUP.
        assertEquals(Category.WITHIN_GROUP, cat);
    }

    // ---- distanceToRect: geometric correctness ----

    @Test
    public void distanceToRect_insideRect_isZero() {
        ElementRect rect = new ElementRect("r", 100, 100, 80, 40);
        assertEquals(0.0, CoincidentSegmentDiagnostic.distanceToRect(120, 120, rect), 0.001);
    }

    @Test
    public void distanceToRect_pointToRightEdge_isHorizontalDistance() {
        ElementRect rect = new ElementRect("r", 100, 100, 80, 40); // right edge at x=180
        assertEquals(20.0, CoincidentSegmentDiagnostic.distanceToRect(200, 120, rect), 0.001);
    }

    @Test
    public void distanceToRect_pointDiagonalToCorner_isEuclidean() {
        ElementRect rect = new ElementRect("r", 100, 100, 80, 40); // bottom-right corner (180,140)
        // Point (183,144): dx=3, dy=4 → distance=5
        assertEquals(5.0, CoincidentSegmentDiagnostic.distanceToRect(183, 144, rect), 0.001);
    }

    // ---- findContainingGroup: containment semantics ----

    @Test
    public void findContainingGroup_pointInsideGroup_returnsGroup() {
        GroupRect groupA = new GroupRect("gA", 0, 0, 300, 500);
        GroupRect result = CoincidentSegmentDiagnostic.findContainingGroup(
                150, 250, List.of(groupA));
        assertEquals("gA", result.id());
    }

    @Test
    public void findContainingGroup_pointOutsideAllGroups_returnsNull() {
        GroupRect groupA = new GroupRect("gA", 0, 0, 300, 500);
        GroupRect result = CoincidentSegmentDiagnostic.findContainingGroup(
                500, 250, List.of(groupA));
        assertNull(result);
    }
}
