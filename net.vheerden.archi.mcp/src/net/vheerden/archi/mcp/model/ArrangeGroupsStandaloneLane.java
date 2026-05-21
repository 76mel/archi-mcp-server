package net.vheerden.archi.mcp.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.ICommunicationNetwork;
import com.archimatetool.model.IConnectable;
import com.archimatetool.model.IDevice;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelConnection;
import com.archimatetool.model.IDiagramModelGroup;
import com.archimatetool.model.IDiagramModelObject;
import com.archimatetool.model.INode;
import com.archimatetool.model.IPath;

/**
 * Story backlog-arrange-groups-standalone-element-lane (Option A — reserve centre lane).
 *
 * <p>Classifies a view's top-level non-group children as qualifying lane targets for
 * {@code arrange-groups}, assigns each qualifier to an inter-group gap, and computes
 * lane widths/heights along the layout axis. The lane reserves a corridor between
 * groups in topology order so the qualifier sits where the recipe text already
 * promises it would: {@code technology-deployment.md:35} "place it between the zones"
 * and {@code application-integration.md:37} "the hub sits between them".</p>
 *
 * <p>Wired only when {@code arrangement="topology"} is in effect at {@code arrangeGroups}
 * (and the resulting layout axis is row or column, not grid). Direct row/column/grid
 * calls and zero-qualifier views preserve byte-identical behaviour (Gate 2 / AC-3).</p>
 *
 * <p>A qualifier is a top-level non-group, non-Note {@code IDiagramModelArchimateObject}
 * whose {@code IArchimateConcept} is {@link INode}, {@link IDevice}, {@link IPath}, or
 * {@link ICommunicationNetwork}, AND that has direct view connections (source or target)
 * to elements inside ≥ 2 of the arranged target groups.</p>
 *
 * <p>{@code IDevice} is a sibling of {@code INode} in the Archi metamodel
 * ({@code IDevice extends ITechnologyElement} directly, NOT {@code INode}). It is listed
 * alongside {@code Node} in the technology-deployment recipe element-subset
 * ({@code technology-deployment.md:11}), so a top-level Device wired to ≥ 2 zone groups
 * qualifies for the lane placement.</p>
 */
final class ArrangeGroupsStandaloneLane {

    private ArrangeGroupsStandaloneLane() {}

    /** A qualifying standalone element + the target-group IDs it connects to (in encounter order). */
    record QualifyingStandaloneElement(
            IDiagramModelObject element,
            LinkedHashSet<String> connectedTargetGroupIds) {}

    /**
     * Classifies top-level non-group children of {@code viewChildren} as qualifying lane targets.
     *
     * @param viewChildren the view's top-level children (the iteration source used by
     *     {@code arrangeGroups} at {@code ArchiModelAccessorImpl.java:10530})
     * @param targetGroups groups currently scheduled for arrangement (order does not matter here;
     *     gap-assignment in {@link #assignToGaps} uses the post-orderer order)
     * @param elementToGroup element-id → top-level-group-id map produced by
     *     {@code ArchiModelAccessorImpl.mapElementsToGroup} (single source of truth shared with
     *     the topology weight pass at {@code :10580–10590})
     */
    static List<QualifyingStandaloneElement> classify(
            List<IDiagramModelObject> viewChildren,
            List<IDiagramModelGroup> targetGroups,
            Map<String, String> elementToGroup) {
        Set<String> targetGroupIds = new HashSet<>();
        for (IDiagramModelGroup g : targetGroups) {
            targetGroupIds.add(g.getId());
        }

        List<QualifyingStandaloneElement> qualifiers = new ArrayList<>();
        for (IDiagramModelObject child : viewChildren) {
            if (child instanceof IDiagramModelGroup) continue;
            if (!isLaneEligibleType(child)) continue;
            LinkedHashSet<String> connected =
                    collectConnectedTargetGroupIds(child, elementToGroup, targetGroupIds);
            if (connected.size() < 2) continue;
            qualifiers.add(new QualifyingStandaloneElement(child, connected));
        }
        return qualifiers;
    }

    private static boolean isLaneEligibleType(IDiagramModelObject obj) {
        if (!(obj instanceof IDiagramModelArchimateObject archiObj)) return false;
        IArchimateConcept concept = archiObj.getArchimateConcept();
        if (concept == null) return false;
        // IDevice is a sibling of INode in the Archi metamodel (both extend
        // ITechnologyElement directly); the recipe element-subset lists them together,
        // so a top-level Device wired to ≥ 2 groups qualifies for lane placement.
        return (concept instanceof INode)
                || (concept instanceof IDevice)
                || (concept instanceof IPath)
                || (concept instanceof ICommunicationNetwork);
    }

    private static LinkedHashSet<String> collectConnectedTargetGroupIds(
            IDiagramModelObject obj,
            Map<String, String> elementToGroup,
            Set<String> targetGroupIds) {
        LinkedHashSet<String> connected = new LinkedHashSet<>();
        if (obj instanceof IConnectable connectable) {
            for (IDiagramModelConnection conn : connectable.getSourceConnections()) {
                addEndpointGroup(conn.getTarget(), elementToGroup, targetGroupIds, connected);
            }
            for (IDiagramModelConnection conn : connectable.getTargetConnections()) {
                addEndpointGroup(conn.getSource(), elementToGroup, targetGroupIds, connected);
            }
        }
        return connected;
    }

    private static void addEndpointGroup(
            IConnectable endpoint,
            Map<String, String> elementToGroup,
            Set<String> targetGroupIds,
            LinkedHashSet<String> sink) {
        if (endpoint == null) return;
        String groupId = elementToGroup.get(endpoint.getId());
        if (groupId != null && targetGroupIds.contains(groupId)) {
            sink.add(groupId);
        }
    }

    /**
     * Assigns each qualifier to an inter-group gap in the topology-ordered
     * {@code orderedTargetGroups} list. Gap-i is the gap between
     * {@code orderedTargetGroups[i]} and {@code orderedTargetGroups[i+1]}
     * (0 ≤ i &lt; size − 1).
     *
     * <p>Assignment rule: the qualifier's connected groups have indices in
     * {@code [minIdx, maxIdx]} along the ordered list; the qualifier is placed in
     * gap {@code floor((minIdx + maxIdx − 1) / 2)} — the lower-middle gap inside that
     * range. For two-group connectivity this is unambiguous; for three or more groups
     * spanning a wider range, the rule prefers the lower-middle gap so the qualifier
     * still sits visually between the connected clusters.</p>
     */
    static Map<Integer, List<QualifyingStandaloneElement>> assignToGaps(
            List<QualifyingStandaloneElement> qualifiers,
            List<IDiagramModelGroup> orderedTargetGroups) {
        Map<String, Integer> groupIdToIndex = new LinkedHashMap<>();
        for (int i = 0; i < orderedTargetGroups.size(); i++) {
            groupIdToIndex.put(orderedTargetGroups.get(i).getId(), i);
        }

        Map<Integer, List<QualifyingStandaloneElement>> gapMap = new LinkedHashMap<>();
        for (QualifyingStandaloneElement q : qualifiers) {
            int minIdx = Integer.MAX_VALUE;
            int maxIdx = Integer.MIN_VALUE;
            for (String gid : q.connectedTargetGroupIds()) {
                Integer idx = groupIdToIndex.get(gid);
                if (idx == null) continue;
                if (idx < minIdx) minIdx = idx;
                if (idx > maxIdx) maxIdx = idx;
            }
            if (minIdx == Integer.MAX_VALUE || maxIdx == minIdx) continue;
            int gapIdx = (minIdx + maxIdx - 1) / 2;
            gapMap.computeIfAbsent(gapIdx, k -> new ArrayList<>()).add(q);
        }
        return gapMap;
    }

    /**
     * Computes the per-gap lane size along the layout axis. Returns a list of length
     * {@code max(0, numGroups − 1)}; non-qualifying gaps return 0 (caller uses
     * {@code resolvedSpacing} for those, preserving back-compat).
     *
     * <p>A qualifying gap reserves {@code qualifierAxisSize + 2*resolvedSpacing} so the
     * qualifier has {@code resolvedSpacing} clearance from each neighbouring group.
     * Multiple qualifiers in the same gap are stacked along the layout axis with
     * {@code resolvedSpacing} between them, so their axis sizes sum.</p>
     *
     * <p><strong>Invariant with {@link #placeQualifiers}:</strong> for n qualifiers in a
     * gap with axis-sizes {@code w_1..w_n}, lane size =
     * {@code (w_1 + .. + w_n) + (n-1)*resolvedSpacing + 2*resolvedSpacing}, i.e.
     * {@code sum(w_i) + (n+1)*resolvedSpacing}. {@code placeQualifiers} positions the
     * first qualifier at {@code laneLeft = groupLeftEnd + resolvedSpacing} and advances by
     * {@code w_i + resolvedSpacing} after each placement, so the last qualifier's right
     * edge sits at {@code laneLeft + sum(w_i) + (n-1)*resolvedSpacing}, leaving exactly
     * {@code resolvedSpacing} margin between it and the next group. Verified
     * algebraically for n=1 and n=2; generalises by induction. If you change either
     * method, re-verify the invariant.</p>
     *
     * @param horizontalAxis {@code true} for row layout (lane width along X);
     *     {@code false} for column layout (lane height along Y)
     */
    static List<Integer> computeLaneSizes(
            Map<Integer, List<QualifyingStandaloneElement>> gapAssignments,
            int numGroups,
            int resolvedSpacing,
            boolean horizontalAxis) {
        List<Integer> laneSizes = new ArrayList<>();
        for (int gapIdx = 0; gapIdx < Math.max(0, numGroups - 1); gapIdx++) {
            List<QualifyingStandaloneElement> qsInGap = gapAssignments.get(gapIdx);
            if (qsInGap == null || qsInGap.isEmpty()) {
                laneSizes.add(0);
                continue;
            }
            int sizeAlongAxis = 0;
            for (int i = 0; i < qsInGap.size(); i++) {
                IDiagramModelObject el = qsInGap.get(i).element();
                int dim = horizontalAxis
                        ? el.getBounds().getWidth()
                        : el.getBounds().getHeight();
                sizeAlongAxis += dim;
                if (i > 0) sizeAlongAxis += resolvedSpacing;
            }
            laneSizes.add(sizeAlongAxis + 2 * resolvedSpacing);
        }
        return laneSizes;
    }

    /** Final per-qualifier placement (element + bounds) emitted by {@link #placeQualifiers}. */
    record QualifierPlacement(IDiagramModelObject element, int x, int y, int width, int height) {}

    /**
     * Computes the final placement for each qualifier given its assigned gap and the
     * surrounding groups' positions and per-arrangement dimensions. Returns placements
     * in source order across all gaps. Empty list when there are no qualifying gaps.
     *
     * <p>Horizontal axis (row layout): qualifier x = lane.left + cumulative; y = vertical
     * midpoint of the union of the two adjacent groups' bounds, minus qualifier.height/2.
     * Vertical axis (column layout): transposed.</p>
     *
     * <p>{@code groupDims.get(i)} = {@code [width, height]} of group i AS LAID OUT BY
     * THE CALLER — for {@code arrangeGroups} primary site this is {@code group.getBounds()};
     * for {@code computeGroupedLayoutPass} this is the virtual post-resize dimensions.</p>
     *
     * @param gapAssignments output of {@link #assignToGaps}
     * @param numGroups number of arranged groups
     * @param positions per-group {@code [x, y]} from the position calculator (size = numGroups)
     * @param groupDims per-group {@code [width, height]} for the arrangement (size = numGroups)
     * @param resolvedSpacing layout spacing (used for the lane-internal clearance)
     * @param horizontalAxis true for row layout, false for column layout
     */
    static List<QualifierPlacement> placeQualifiers(
            Map<Integer, List<QualifyingStandaloneElement>> gapAssignments,
            int numGroups,
            List<int[]> positions,
            List<int[]> groupDims,
            int resolvedSpacing,
            boolean horizontalAxis) {
        List<QualifierPlacement> placements = new ArrayList<>();
        for (int gapIdx = 0; gapIdx < Math.max(0, numGroups - 1); gapIdx++) {
            List<QualifyingStandaloneElement> qs = gapAssignments.get(gapIdx);
            if (qs == null || qs.isEmpty()) continue;
            int[] leftPos = positions.get(gapIdx);
            int[] leftDim = groupDims.get(gapIdx);
            int[] rightPos = positions.get(gapIdx + 1);
            int[] rightDim = groupDims.get(gapIdx + 1);
            if (horizontalAxis) {
                int laneLeft = leftPos[0] + leftDim[0] + resolvedSpacing;
                int unionTop = Math.min(leftPos[1], rightPos[1]);
                int unionBottom = Math.max(
                        leftPos[1] + leftDim[1],
                        rightPos[1] + rightDim[1]);
                int unionMidY = (unionTop + unionBottom) / 2;
                int cursorX = laneLeft;
                for (QualifyingStandaloneElement q : qs) {
                    int w = q.element().getBounds().getWidth();
                    int h = q.element().getBounds().getHeight();
                    placements.add(new QualifierPlacement(q.element(),
                            cursorX, unionMidY - h / 2, w, h));
                    cursorX += w + resolvedSpacing;
                }
            } else {
                int laneTop = leftPos[1] + leftDim[1] + resolvedSpacing;
                int unionLeft = Math.min(leftPos[0], rightPos[0]);
                int unionRight = Math.max(
                        leftPos[0] + leftDim[0],
                        rightPos[0] + rightDim[0]);
                int unionMidX = (unionLeft + unionRight) / 2;
                int cursorY = laneTop;
                for (QualifyingStandaloneElement q : qs) {
                    int w = q.element().getBounds().getWidth();
                    int h = q.element().getBounds().getHeight();
                    placements.add(new QualifierPlacement(q.element(),
                            unionMidX - w / 2, cursorY, w, h));
                    cursorY += h + resolvedSpacing;
                }
            }
        }
        return placements;
    }
}
