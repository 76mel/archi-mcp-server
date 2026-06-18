package net.vheerden.archi.mcp.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.vheerden.archi.mcp.model.geometry.GeometryUtils;
import net.vheerden.archi.mcp.model.routing.CoincidentSegmentDetector;
import net.vheerden.archi.mcp.model.routing.CoincidentSegmentDiagnostic;

/**
 * Stateless pure-geometry computation for layout quality assessment.
 * No EMF imports — operates on {@link AssessmentNode} and {@link AssessmentConnection} records.
 *
 * <p>All coordinates are expected to be in absolute canvas space. The accessor is
 * responsible for converting nested element coordinates by accumulating parent offsets.</p>
 *
 * <p>Ancestor-descendant containment relationships (groups containing elements,
 * including nested groups) are handled specially: overlap, spacing, and alignment
 * metrics exclude containment pairs to avoid false positives from intentional nesting.</p>
 */
class LayoutQualityAssessor {

    private static final int MAX_DESCRIPTIONS = 10;
    private static final double ALIGNMENT_TOLERANCE = 5.0;
    /** Angular threshold (degrees) for non-orthogonal terminal detection. */
    private static final double NON_ORTH_ANGLE_THRESHOLD = 5.0;
    /**
     * Visible-segment-length guard (2026-04-27):
     * when a candidate non-orthogonal terminal's visible (post-clip) segment length is
     * below this threshold, the diagonal is sub-perceptible at typical Archi zoom levels
     * and is silently skipped. Calibrated against the V4 manual oracle view
     * (id-3b2665e3ff6840708dbed2b3d1415613): 20 of 21 violators were 1.0–1.3px visible,
     * produced by Archi storing manually-routed BPs 1px off the perimeter face line.
     */
    static final double VISIBLE_DIAGONAL_MIN_PX = 3.0;
    private static final double OFF_CANVAS_THRESHOLD = 10000.0;

    // Suggestion thresholds (Finding #11: named constants with documented rationale)
    /** Edge crossings above this count trigger a suggestion to use hierarchical layout. */
    static final int CROSSING_SUGGESTION_THRESHOLD = 10;
    /** Average spacing below this (px) triggers a "too tight" suggestion. */
    static final double SPACING_SUGGESTION_THRESHOLD = 15.0;
    /** Alignment score below this triggers a "poor alignment" suggestion. */
    static final int ALIGNMENT_SUGGESTION_THRESHOLD = 30;

    // Overall rating thresholds
    static final int EXCELLENT_MAX_CROSSINGS = 5;
    static final double EXCELLENT_MIN_SPACING = 30.0;
    static final int EXCELLENT_MIN_ALIGNMENT = 60;
    static final int GOOD_MAX_CROSSINGS = 20;
    static final double GOOD_MIN_SPACING = 15.0;
    static final int GOOD_MIN_ALIGNMENT = 30;
    static final int FAIR_MAX_CROSSINGS = 30;
    static final int FAIR_MAX_PASS_THROUGHS = 3;

    // Density-aware crossing thresholds
    /** Crossings per connection ratio: moderate impact threshold. */
    static final double CROSSING_RATIO_MODERATE = 4.0;
    /** Crossings/connection ratio below this is "good" quality. */
    static final double CROSSING_RATIO_GOOD = 1.5;

    /**
     * Inset (px) applied to obstacle rectangles before pass-through intersection tests.
     * Accounts for OrthogonalAnchor corner-arc imprecision in diagonal exit zones
     * where the simplified ChopboxAnchor fallback deviates from Archi's actual
     * corner arc calculation (using COSPI4). Typical deviation: 10-15px.
     */
    static final double PASS_THROUGH_INSET = 10.0;

    /**
     * Inset for self-element pass-through detection.
     * Smaller than PASS_THROUGH_INSET to match the router's 5px tolerance,
     * ensuring the assessor safety net catches everything the router detects.
     */
    static final double SELF_ELEMENT_INSET = 5.0;

    // Coincident segment thresholds
    static final int GOOD_MAX_COINCIDENT = 3;
    static final int FAIR_MAX_COINCIDENT = 8;

    // Non-orthogonal terminal density thresholds
    /** Non-orth terminals per connection ratio: at or below this is "good" quality. */
    static final double NON_ORTH_RATIO_GOOD = 0.10;
    /** Non-orth terminals per connection ratio: at or below this is "fair" quality. */
    static final double NON_ORTH_RATIO_FAIR = 0.30;

    /** Above this element count, add a performance warning to suggestions. */
    static final int LARGE_VIEW_WARNING_THRESHOLD = 500;

    // ---- Assessor.Redesign metric constants (2026-04-26) ----

    /**
     * Tolerance (px) for testing whether a bendpoint lies on an element's perimeter line (M1).
     * Bendpoints stored by Archi are int-snapped, so 0.5px tolerance is sufficient and
     * avoids float-equality fragility.
     */
    static final double PERIMETER_TOLERANCE_PX = 0.5;

    /**
     * Tolerance (px) for testing whether two bendpoints share an axis (M3 zigzag detection).
     * Matches Archi's int-snapped storage.
     */
    static final double ZIGZAG_AXIS_TOLERANCE_PX = 1.0;

    /**
     * Minimum delta (px) magnitude for the two reversal arms of a zigzag triple (M3).
     * Below this, the bendpoint sequence is treated as colinear (no reversal).
     */
    static final double ZIGZAG_MIN_DELTA_PX = 1.0;

    /**
     * Distance (px) within which a connection segment is considered coincident with a
     * foreign element's edge line (M4). Per spec: 3px hugs the perimeter visibly.
     */
    static final double EDGE_COINCIDENCE_TOLERANCE_PX = 3.0;

    /**
     * Minimum overlap (px) of a connection segment's projected extent against a foreign
     * element's edge extent for an M4 edge-coincidence flag.
     */
    static final double EDGE_COINCIDENCE_MIN_OVERLAP_PX = 10.0;

    /**
     * Public canonical hub-detection threshold (2026-05-04).
     * Elements with at or above this number of connections are CANDIDATE HUBS for the
     * agent's pre-routing analysis. This is the single public number cited in
     * {@code CLAUDE.md}, {@code README.md}, {@code archimate-view-patterns.md}, the
     * {@code detect-hub-elements} tool description, and {@code docs/layout-engine.md}.
     *
     * <p>Distinct from two internal thresholds with different roles:
     * <ul>
     *   <li>{@link #M5_FACE_GUARD_MIN_CONNECTIONS} (4) — face-count guard for the M5
     *       hub-port-quality metric (rating-internal, not public).
     *   <li>{@code EdgeAttachmentCalculator.HUB_FACE_REDISTRIBUTION_THRESHOLD} (6) —
     *       Phase 1.1 face-redistribution gate (router-internal, behavioural).
     * </ul>
     * The {@code detect-hub-elements} tool emits sizing suggestions when
     * {@code connectionCount > HUB_DETECTION_THRESHOLD + 1} (i.e., {@code > 6}); the
     * {@code +1} aligns with the dimension formula's growth term
     * {@code 15 × (count − 6)} which is non-positive at exactly 5.
     */
    public static final int HUB_DETECTION_THRESHOLD = 5;

    /**
     * Internal threshold for the M5 hub-port-quality metric — minimum face-count to
     * participate in M5 scoring. Below this the metric is vacuous (a single face is
     * trivially balanced). Distinct from {@link #HUB_DETECTION_THRESHOLD} (public, 5).
     * Renamed from {@code HUB_FACE_MIN_CONNECTIONS}, 2026-05-04.
     */
    static final int M5_FACE_GUARD_MIN_CONNECTIONS = 4;

    /**
     * Slot-equality tolerance (px) when computing distinct slot counts on a hub face (M5).
     * Two terminal endpoints whose along-face coordinate differs by less than this are
     * considered to share a slot.
     */
    static final double HUB_PORT_SLOT_TOLERANCE_PX = 1.0;

    /**
     * Hub-port quality threshold (M5). View-aggregate quality below this contributes to
     * routing Tier 2R; at or above, the metric contributes "good" (no rating impact).
     */
    static final double HUB_PORT_QUALITY_FAIR_THRESHOLD = 0.5;

    /** Hub-port quality at or above this is "good" (no rating impact under M5). */
    static final double HUB_PORT_QUALITY_GOOD_THRESHOLD = 0.75;

    /** Hub-port quality at or above this is treated as a clean signal (no defect). */
    static final double HUB_PORT_QUALITY_PASS_THRESHOLD = 0.95;

    /** M4 edge-coincidence count thresholds for breakdown rating. */
    static final int EDGE_COINCIDENCE_GOOD_MAX = 2;
    static final int EDGE_COINCIDENCE_FAIR_MAX = 5;

    /**
     * A-gated escalation threshold (2026-05-21).
     * M4 {@code connectionEdgeCoincidence} is normally Tier-2R (cap-fair). When the count reaches or
     * exceeds this value the edge-hug is "egregious" and escalates to Tier-1R, so {@code overall}
     * reads "poor" instead of being masked at "fair". Anchored at 7 = the Retail Bank View G count
     * flagged by eye (2026-05-19); the common 1-5 forced-hug case stays cap-fair. MUST be
     * &gt; {@link #EDGE_COINCIDENCE_FAIR_MAX} for the escalation to be meaningful (below FAIR_MAX the
     * breakdown rating is not yet "poor", so escalating it would not change overall). Validated
     * 2026-05-21 as the regression guardrail beside the Lever-B router fix; live
     * geometry proved an egregious count is router-eliminable, not a topology floor.
     */
    static final int EDGE_COINCIDENCE_EGREGIOUS_MAX = 7;

    // ---- Assessor.Redesign Successor D — parallelConnectionGap metric constants ----
    // (2026-05-12)

    /**
     * Tolerance (px) for testing whether a bendpoint pair forms an axis-aligned segment
     * (parallelConnectionGap V/H classification). Distinct from
     * {@link #ZIGZAG_AXIS_TOLERANCE_PX} (1.0): that constant tests zigzag-triple axis
     * membership; this constant tests parallel-gap segment classification.
     *
     * <p>Calibration-anchor value: the 4-view calibration workspace
     * (compute_parallel_gap.py:70) used AXIS_TOL = 2 px and produced V4 manual gold
     * V_p10 = 13.30 (perception-aligned). Tightening to 1.0 would drop borderline
     * near-axis segments and shift V_p10 away from the gold anchor, breaking the
     * JUnit pin.</p>
     */
    static final double PARALLEL_GAP_AXIS_TOLERANCE_PX = 2.0;

    /**
     * Narrow-gap count threshold (px) — T1 in the parallelConnectionGap metric family.
     * Segments with {@code nearestParallelGap < T1} are counted in {@code narrowGapCount15}.
     * See workspace {@code results.md} &sect; "Primary Metric Selection".
     */
    static final int PARALLEL_GAP_NARROW_T1_PX = 15;

    /**
     * Narrow-gap count threshold (px) — T2 in the parallelConnectionGap metric family.
     * Primary calibration-validated narrow-count threshold (per workspace
     * {@code results.md} &sect; "Primary Metric Selection"). Segments with
     * {@code nearestParallelGap < T2} are counted in {@code narrowGapCount25} and
     * contribute to the V-axis {@code violatorIds} set.
     */
    static final int PARALLEL_GAP_NARROW_T2_PX = 25;

    /**
     * Narrow-gap count threshold (px) — T3 in the parallelConnectionGap metric family.
     * Segments with {@code nearestParallelGap < T3} are counted in {@code narrowGapCount40}.
     */
    static final int PARALLEL_GAP_NARROW_T3_PX = 40;

    private final CoincidentSegmentDetector coincidentDetector;

    LayoutQualityAssessor() {
        this.coincidentDetector = new CoincidentSegmentDetector();
    }

    /**
     * Runs full layout quality assessment on the given nodes and connections.
     *
     * @param includeViolatorIds if true, collects per-metric violator IDs
     */
    LayoutAssessmentResult assess(List<AssessmentNode> nodes,
                                   List<AssessmentConnection> connections,
                                   boolean includeViolatorIds) {
        // Separate notes from layout nodes.
        // Notes are excluded from all scoring metrics but used for informational overlap detection.
        List<AssessmentNode> layoutNodes = new ArrayList<>();
        List<AssessmentNode> noteNodes = new ArrayList<>();
        for (AssessmentNode node : nodes) {
            if (node.isNote()) {
                noteNodes.add(node);
            } else {
                layoutNodes.add(node);
            }
        }

        // Build transitive containment set for exclusions (transitive closure)
        Set<String> containmentPairs = buildContainmentPairs(layoutNodes);

        // Single-pass overlap detection: sibling + containment counts (notes excluded)
        OverlapResult overlapResult = computeOverlaps(layoutNodes, containmentPairs, includeViolatorIds);
        int crossingCount = countEdgeCrossings(connections);
        double avgSpacing = computeAverageSpacing(layoutNodes, containmentPairs);
        int alignment = computeAlignmentScore(layoutNodes);
        // Label overlap detection — must precede rating/suggestions
        LabelOverlapResult labelResult = countLabelOverlaps(connections, layoutNodes);
        BoundaryViolationResult boundaryResult = detectBoundaryViolations(layoutNodes, includeViolatorIds);
        PassThroughResult passThroughResult = detectPassThroughs(connections, layoutNodes, includeViolatorIds);
        // Count groups for group-aware suggestions
        boolean hasGroups = false;
        for (AssessmentNode node : layoutNodes) {
            if (node.isGroup()) {
                hasGroups = true;
                break;
            }
        }
        // Coincident segment detection (optional violator IDs)
        CoincidentSegmentDetector.CoincidentSegmentResult coincidentResult =
                coincidentDetector.detectCoincidentSegments(connections, includeViolatorIds);
        int coincidentSegmentCount = coincidentResult.count();
        // Optional categorization of coincident segments (gated by system property).
        // Emits per-pair log with TERMINAL_APPROACH / GAP_CROSSING / WITHIN_GROUP tags.
        // Zero cost when property unset.
        if (Boolean.getBoolean("archi.mcp.diag.coincident") && !connections.isEmpty()) {
            emitCoincidentDiagnostic(connections, layoutNodes);
        }
        // Non-orthogonal terminal detection — post-clip visible segment.
        // Correction: bendpoints on or inside source/target element bounds are
        // not counted (Archi clips the rendered line at the perimeter).
        NonOrthogonalTerminalResult nonOrthResult =
                countNonOrthogonalTerminals(connections, layoutNodes, includeViolatorIds);
        int nonOrthogonalTerminalCount = nonOrthResult.count();

        // Assessor.Redesign M2-M5: new perception-aligned metrics.
        InteriorTerminationResult interiorResult =
                countInteriorTerminations(connections, layoutNodes, includeViolatorIds);
        ZigzagResult zigzagResult = countZigzags(connections, passThroughResult.violatorIds(), includeViolatorIds);
        EdgeCoincidenceResult edgeCoincidenceResult =
                countConnectionEdgeCoincidence(connections, layoutNodes, includeViolatorIds);
        HubPortQualityResult hubPortResult =
                computeHubPortQuality(connections, layoutNodes, includeViolatorIds);
        // R8 Corridor Utilisation (2026-05-03).
        R8CorridorUtilisationResult corridorUtilisationResult =
                computeR8CorridorUtilisation(connections, layoutNodes, includeViolatorIds);
        // Successor D parallelConnectionGap (2026-05-12).
        // Informational only — does NOT contribute to rating/suggestions.
        ParallelConnectionGapResult parallelGapResult =
                computeParallelConnectionGap(connections, includeViolatorIds);

        // Informational detection (label truncation, parent label obscured, image sibling overlap).
        // Hoisted above the rating call — these contribute to layoutRating (parentLabelObscured
        // promoted Tier 1L) and routingRating (labelTruncation promoted Tier 2R).
        LabelTruncationResult labelTruncResult = detectLabelTruncation(layoutNodes);
        ParentLabelObscuredResult parentLabelResult = detectParentLabelObscuredByChild(layoutNodes);
        ImageSiblingOverlapResult imageSiblingResult = detectImageSiblingOverlap(layoutNodes);

        // Rating and suggestions use sibling overlaps only
        // Two-dimensional rating (layout-tier × routing-tier × min combiner).
        // Rating uses cross-element PT count only (self-element PTs don't penalise)
        List<String> offCanvas = detectOffCanvas(layoutNodes);
        RatingResult ratingResult = computeRatingWithBreakdown(
                overlapResult.siblingCount(), crossingCount, avgSpacing, alignment,
                labelResult.count(), passThroughResult.crossElementCount(), coincidentSegmentCount,
                nonOrthogonalTerminalCount, connections.size(), hasGroups,
                boundaryResult.descriptions().size(), parentLabelResult.count(),
                offCanvas.size(), labelTruncResult.count(),
                interiorResult.count(), zigzagResult.count(),
                edgeCoincidenceResult.count(), hubPortResult.viewAggregate());
        String rating = ratingResult.rating();
        Map<String, String> ratingBreakdown = ratingResult.breakdown();
        List<String> suggestions = generateSuggestions(
                overlapResult.siblingCount(), crossingCount, avgSpacing, alignment,
                boundaryResult.descriptions().size(), offCanvas.size(), layoutNodes.size(),
                labelResult.count(), hasGroups, connections.size(), coincidentSegmentCount,
                nonOrthogonalTerminalCount, labelResult.shortSegmentCount(),
                overlapResult.containmentCount(), nonOrthResult.zeroBendpointCount(),
                interiorResult.count(), zigzagResult.count(),
                edgeCoincidenceResult.count(), hubPortResult.viewAggregate());

        // Density-aware crossing metric
        double crossingsPerConnection = connections.size() > 0
                ? (double) crossingCount / connections.size() : 0.0;

        // Informational note-overlap detection (notes vs layout nodes)
        NoteOverlapResult noteOverlapResult = countNoteOverlaps(noteNodes, layoutNodes);

        // Compute bounding box of ALL visual content (elements + groups + notes)
        ContentBounds contentBounds = computeContentBounds(nodes);

        // Build violator IDs map (only when requested, omit empty metrics)
        Map<String, Set<String>> violatorIds = null;
        if (includeViolatorIds) {
            violatorIds = new LinkedHashMap<>();
            if (!overlapResult.violatorIds().isEmpty()) {
                violatorIds.put("overlaps", overlapResult.violatorIds());
            }
            if (!passThroughResult.violatorIds().isEmpty()) {
                violatorIds.put("passThroughs", passThroughResult.violatorIds());
            }
            // Map coincident connection indices back to IDs
            if (!coincidentResult.violatorConnectionIndices().isEmpty()) {
                Set<String> coincidentIds = new HashSet<>();
                for (int idx : coincidentResult.violatorConnectionIndices()) {
                    if (idx >= 0 && idx < connections.size()) {
                        coincidentIds.add(connections.get(idx).id());
                    }
                }
                if (!coincidentIds.isEmpty()) {
                    violatorIds.put("coincidentSegments", coincidentIds);
                }
            }
            if (!nonOrthResult.violatorIds().isEmpty()) {
                violatorIds.put("nonOrthogonalTerminals", nonOrthResult.violatorIds());
            }
            if (!boundaryResult.violatorIds().isEmpty()) {
                violatorIds.put("boundaryViolations", boundaryResult.violatorIds());
            }
            // Assessor.Redesign M2-M5: violator IDs for new metrics.
            if (!interiorResult.violatorIds().isEmpty()) {
                violatorIds.put("interiorTerminations", interiorResult.violatorIds());
            }
            if (!zigzagResult.violatorIds().isEmpty()) {
                violatorIds.put("zigzags", zigzagResult.violatorIds());
            }
            if (!edgeCoincidenceResult.violatorIds().isEmpty()) {
                violatorIds.put("edgeCoincidence", edgeCoincidenceResult.violatorIds());
            }
            if (!hubPortResult.lowQualityElementIds().isEmpty()) {
                violatorIds.put("hubPortLowQuality", hubPortResult.lowQualityElementIds());
            }
            // Successor D parallelConnectionGap (per-axis V/H violator surfaces).
            if (!parallelGapResult.vAxis().violatorIds().isEmpty()) {
                violatorIds.put("parallelConnectionGapV", parallelGapResult.vAxis().violatorIds());
            }
            if (!parallelGapResult.hAxis().violatorIds().isEmpty()) {
                violatorIds.put("parallelConnectionGapH", parallelGapResult.hAxis().violatorIds());
            }
            if (violatorIds.isEmpty()) {
                violatorIds = null;
            }
        }

        // Orphan detection is done at EMF level in ArchiModelAccessorImpl, not here.
        // Pass 0/empty — the accessor merges orphan data into the DTO directly.
        return new LayoutAssessmentResult(
                overlapResult.siblingCount(), overlapResult.containmentCount(),
                crossingCount, avgSpacing, alignment, rating, ratingBreakdown,
                overlapResult.siblingDescriptions(), boundaryResult.descriptions(),
                passThroughResult.descriptions(),
                offCanvas, labelResult.count(), labelResult.descriptions(),
                0, List.of(), connections.size(), crossingsPerConnection,
                noteOverlapResult.count(), noteOverlapResult.descriptions(),
                hasGroups, coincidentSegmentCount, nonOrthogonalTerminalCount,
                contentBounds,
                labelTruncResult.count(), labelTruncResult.descriptions(),
                parentLabelResult.count(), parentLabelResult.descriptions(),
                imageSiblingResult.count(), imageSiblingResult.descriptions(),
                violatorIds,
                suggestions,
                // Assessor.Redesign M2-M6 (appended)
                interiorResult.count(), interiorResult.descriptions(),
                zigzagResult.count(), zigzagResult.descriptions(),
                edgeCoincidenceResult.count(), edgeCoincidenceResult.descriptions(),
                hubPortResult.viewAggregate(),
                includeViolatorIds ? hubPortResult.perFaceDetails() : List.of(),
                ratingResult.layoutRating(), ratingResult.routingRating(),
                // R8 Corridor Utilisation (2026-05-03)
                corridorUtilisationResult.viewAggregate(),
                corridorUtilisationResult.perChannelDetails(),
                // Successor D parallelConnectionGap (2026-05-12)
                parallelGapResult.vAxis().p10(),
                parallelGapResult.vAxis().narrowGapCount25(),
                includeViolatorIds ? buildParallelGapDetail(parallelGapResult) : null);
    }

    /**
     * Converts the internal {@link ParallelConnectionGapResult} to the public
     * {@link LayoutAssessmentResult.ParallelConnectionGapDetail} record (per-axis,
     * without the violator-id sets — those are surfaced in the result's top-level
     * {@code violatorIds} map).
     */
    private LayoutAssessmentResult.ParallelConnectionGapDetail buildParallelGapDetail(
            ParallelConnectionGapResult r) {
        return new LayoutAssessmentResult.ParallelConnectionGapDetail(
                toAxisDetail(r.vAxis()), toAxisDetail(r.hAxis()));
    }

    private LayoutAssessmentResult.ParallelConnectionGapAxisDetail toAxisDetail(
            ParallelConnectionGapAxis a) {
        return new LayoutAssessmentResult.ParallelConnectionGapAxisDetail(
                a.qualifyingSegmentCount(), a.mean(), a.min(), a.p10(),
                a.narrowGapCount15(), a.narrowGapCount25(), a.narrowGapCount40());
    }

    // ---- Containment relationship helpers (transitive closure) ----

    /**
     * Builds a set of ALL ancestor-descendant pairs (transitive closure) for
     * fast containment lookup. For each node, walks up the parentId chain and
     * adds pairs for EVERY ancestor, not just the direct parent.
     *
     * <p>Example: TopGroup → SubGroup → Element produces pairs:
     * "TopGroup:SubGroup", "SubGroup:Element", AND "TopGroup:Element".</p>
     */
    private Set<String> buildContainmentPairs(List<AssessmentNode> nodes) {
        Map<String, AssessmentNode> nodeMap = new HashMap<>();
        for (AssessmentNode node : nodes) {
            nodeMap.put(node.id(), node);
        }

        Set<String> pairs = new HashSet<>();
        for (AssessmentNode node : nodes) {
            if (node.parentId() != null) {
                // Walk up the ancestor chain and add ALL ancestor:descendant pairs
                String descendantId = node.id();
                AssessmentNode current = nodeMap.get(node.parentId());
                while (current != null) {
                    pairs.add(current.id() + ":" + descendantId);
                    if (current.parentId() == null) break;
                    current = nodeMap.get(current.parentId());
                }
            }
        }
        return pairs;
    }

    private boolean isContainmentPair(AssessmentNode a, AssessmentNode b,
                                       Set<String> containmentPairs) {
        return containmentPairs.contains(a.id() + ":" + b.id())
                || containmentPairs.contains(b.id() + ":" + a.id());
    }

    /**
     * Collects all descendant IDs for a given node (children, grandchildren, etc.).
     */
    private Set<String> getDescendantIds(String nodeId, List<AssessmentNode> nodes) {
        Set<String> descendants = new HashSet<>();
        // Seed with direct children, then iteratively expand
        Set<String> frontier = new HashSet<>();
        frontier.add(nodeId);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (AssessmentNode n : nodes) {
                if (n.parentId() != null && frontier.contains(n.parentId())
                        && descendants.add(n.id())) {
                    frontier.add(n.id());
                    changed = true;
                }
            }
        }
        return descendants;
    }

    /**
     * Collects all ancestor IDs for a given node by walking the parentId chain.
     */
    private Set<String> getAncestorIds(String nodeId,
                                        Map<String, AssessmentNode> nodeMap) {
        Set<String> ancestors = new HashSet<>();
        AssessmentNode current = nodeMap.get(nodeId);
        while (current != null && current.parentId() != null) {
            ancestors.add(current.parentId());
            current = nodeMap.get(current.parentId());
        }
        return ancestors;
    }

    // ---- Overlap Detection (Finding #2: exclude containment, #10: single pass, transitive) ----

    /** Combined sibling + containment counts and descriptions from a single pass. */
    record OverlapResult(int siblingCount, int containmentCount,
                         List<String> siblingDescriptions, Set<String> violatorIds) {}

    OverlapResult computeOverlaps(List<AssessmentNode> nodes,
                                   Set<String> containmentPairs,
                                   boolean collectViolatorIds) {
        int siblingCount = 0;
        int containmentCount = 0;
        List<String> siblingDescriptions = new ArrayList<>();
        Set<String> violatorIds = collectViolatorIds ? new HashSet<>() : Set.of();
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                AssessmentNode a = nodes.get(i);
                AssessmentNode b = nodes.get(j);
                if (!rectanglesOverlap(a, b)) {
                    continue;
                }
                // Containment pairs overlap by design — count separately
                if (isContainmentPair(a, b, containmentPairs)) {
                    containmentCount++;
                } else if (Objects.equals(a.parentId(), b.parentId())) {
                    // Only count overlaps between siblings (same parent).
                    // Elements in different groups near a shared boundary are NOT
                    // sibling overlaps — they are cross-group boundary proximity.
                    siblingCount++;
                    if (siblingDescriptions.size() < MAX_DESCRIPTIONS) {
                        siblingDescriptions.add("Element '" + a.id()
                                + "' overlaps with element '" + b.id() + "'");
                    }
                    if (collectViolatorIds) {
                        violatorIds.add(a.id());
                        violatorIds.add(b.id());
                    }
                }
            }
        }
        return new OverlapResult(siblingCount, containmentCount, siblingDescriptions, violatorIds);
    }

    private boolean rectanglesOverlap(AssessmentNode a, AssessmentNode b) {
        return a.x() < b.x() + b.width()
                && a.x() + a.width() > b.x()
                && a.y() < b.y() + b.height()
                && a.y() + a.height() > b.y();
    }

    // ---- Edge Crossing Detection (Finding #8: remove sharesEndpoint skip) ----

    int countEdgeCrossings(List<AssessmentConnection> connections) {
        List<List<double[]>> paths = new ArrayList<>(connections.size());
        for (AssessmentConnection conn : connections) {
            paths.add(conn.pathPoints());
        }
        return countPathCrossings(paths);
    }

    /**
     * Counts edge crossings among a list of raw path point lists.
     * Each path is a list of [x, y] points (source center → bendpoints → target center).
     * Package-visible for use by autoRouteConnections crossing delta.
     */
    static int countPathCrossings(List<List<double[]>> paths) {
        int count = 0;
        for (int i = 0; i < paths.size(); i++) {
            for (int j = i + 1; j < paths.size(); j++) {
                count += countSegmentCrossings(paths.get(i), paths.get(j));
            }
        }
        return count;
    }

    static int countSegmentCrossings(List<double[]> path1, List<double[]> path2) {
        int count = 0;
        for (int i = 0; i < path1.size() - 1; i++) {
            for (int j = 0; j < path2.size() - 1; j++) {
                if (segmentsIntersect(
                        path1.get(i)[0], path1.get(i)[1],
                        path1.get(i + 1)[0], path1.get(i + 1)[1],
                        path2.get(j)[0], path2.get(j)[1],
                        path2.get(j + 1)[0], path2.get(j + 1)[1])) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Line segment intersection test using cross-product orientation.
     * Returns true if segment P1-P2 properly intersects segment P3-P4.
     * Segments sharing an endpoint (P1=P3, etc.) produce a zero cross-product
     * and correctly return false.
     */
    static boolean segmentsIntersect(double p1x, double p1y, double p2x, double p2y,
                                      double p3x, double p3y, double p4x, double p4y) {
        double d1 = crossProduct(p3x, p3y, p4x, p4y, p1x, p1y);
        double d2 = crossProduct(p3x, p3y, p4x, p4y, p2x, p2y);
        double d3 = crossProduct(p1x, p1y, p2x, p2y, p3x, p3y);
        double d4 = crossProduct(p1x, p1y, p2x, p2y, p4x, p4y);

        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0))
                && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
            return true;
        }

        // Collinear cases — not counted as crossings
        return false;
    }

    /**
     * Cross product of vectors (bx-ax, by-ay) and (cx-ax, cy-ay).
     */
    private static double crossProduct(double ax, double ay, double bx, double by,
                                        double cx, double cy) {
        return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    }

    // ---- Average Spacing (Finding #4: exclude containment pairs) ----

    double computeAverageSpacing(List<AssessmentNode> nodes,
                                  Set<String> containmentPairs) {
        if (nodes.size() < 2) {
            return 0.0;
        }

        double totalMinGap = 0.0;
        int counted = 0;
        for (int i = 0; i < nodes.size(); i++) {
            double minGap = Double.MAX_VALUE;
            boolean hasNeighbor = false;
            for (int j = 0; j < nodes.size(); j++) {
                if (i == j) continue;
                // Skip containment pairs for spacing computation
                if (isContainmentPair(nodes.get(i), nodes.get(j), containmentPairs)) {
                    continue;
                }
                double gap = edgeToEdgeDistance(nodes.get(i), nodes.get(j));
                if (gap < minGap) {
                    minGap = gap;
                    hasNeighbor = true;
                }
            }
            if (hasNeighbor) {
                totalMinGap += minGap;
                counted++;
            }
        }
        return counted > 0 ? totalMinGap / counted : 0.0;
    }

    /**
     * Computes the minimum edge-to-edge distance between two axis-aligned rectangles.
     * Returns 0 if they overlap.
     */
    private double edgeToEdgeDistance(AssessmentNode a, AssessmentNode b) {
        double dx = Math.max(0, Math.max(b.x() - (a.x() + a.width()),
                a.x() - (b.x() + b.width())));
        double dy = Math.max(0, Math.max(b.y() - (a.y() + a.height()),
                a.y() - (b.y() + b.height())));

        if (dx == 0 && dy == 0) {
            return 0; // overlapping
        }
        return Math.sqrt(dx * dx + dy * dy);
    }

    // ---- Alignment Score (Finding #9: exclude groups, #12: 0 for empty) ----

    int computeAlignmentScore(List<AssessmentNode> nodes) {
        // Filter to non-group (leaf) elements only for alignment scoring
        List<AssessmentNode> leafNodes = new ArrayList<>();
        for (AssessmentNode node : nodes) {
            if (!node.isGroup()) {
                leafNodes.add(node);
            }
        }

        // Finding #12: return 0 for empty/single — no alignment data, not "perfect"
        if (leafNodes.size() < 2) {
            return 0;
        }

        Set<String> alignedElements = new HashSet<>();

        // Check left-edge alignment
        findAlignedGroups(leafNodes, n -> n.x(), alignedElements);
        // Check center-x alignment
        findAlignedGroups(leafNodes, n -> n.x() + n.width() / 2, alignedElements);
        // Check top-edge alignment
        findAlignedGroups(leafNodes, n -> n.y(), alignedElements);
        // Check center-y alignment
        findAlignedGroups(leafNodes, n -> n.y() + n.height() / 2, alignedElements);

        return Math.min(100, (int) ((alignedElements.size() * 100.0) / leafNodes.size()));
    }

    @FunctionalInterface
    interface CoordinateExtractor {
        double extract(AssessmentNode node);
    }

    private void findAlignedGroups(List<AssessmentNode> nodes,
                                    CoordinateExtractor extractor,
                                    Set<String> alignedElements) {
        for (int i = 0; i < nodes.size(); i++) {
            double coord = extractor.extract(nodes.get(i));
            for (int j = i + 1; j < nodes.size(); j++) {
                double otherCoord = extractor.extract(nodes.get(j));
                if (Math.abs(coord - otherCoord) <= ALIGNMENT_TOLERANCE) {
                    alignedElements.add(nodes.get(i).id());
                    alignedElements.add(nodes.get(j).id());
                }
            }
        }
    }

    // ---- Rating Comparison Utilities ----

    /**
     * Returns the ordinal value of a rating for comparison purposes.
     * Higher is better: excellent=4, good=3, fair=2, poor=1, not-applicable=0.
     */
    static int ratingOrdinal(String rating) {
        return switch (rating) {
            case "excellent" -> 4;
            case "good" -> 3;
            case "fair" -> 2;
            case "poor" -> 1;
            default -> 0; // "not-applicable" or unknown
        };
    }

    /**
     * Returns true if the achieved rating meets or exceeds the target rating.
     */
    static boolean meetsTarget(String achieved, String target) {
        return ratingOrdinal(achieved) >= ratingOrdinal(target);
    }

    // ---- Overall Rating (Finding #11: named constants; M6 two-dimensional rating) ----

    /**
     * Result of rating computation including per-metric breakdown and
     * the two-dimensional layout/routing decomposition (Assessor.Redesign M6).
     *
     * <p>Under M6, {@code rating} is the worse of {@code layoutRating} and {@code routingRating}
     * ("min" in human terms — `excellent < good < fair < poor` — i.e. worse-dimension dominates).</p>
     */
    record RatingResult(String rating, Map<String, String> breakdown,
                         String layoutRating, String routingRating) {}

    /**
     * Computes the overall quality rating with per-metric breakdown.
     * Delegates to the breakdown-aware overload with {@code hasGroups=false} and
     * zero values for the M2-M5 + L1-L3 inputs.
     *
     * @deprecated Use {@link #computeRatingWithBreakdown} to get both the rating
     *             and per-metric breakdown, and to enable grouped-view leniency.
     */
    @Deprecated
    String computeOverallRating(int overlaps, int crossings,
                                 double avgSpacing, int alignmentScore,
                                 int labelOverlapCount, int passThroughCount,
                                 int connectionCount) {
        return computeRatingWithBreakdown(overlaps, crossings, avgSpacing,
                alignmentScore, labelOverlapCount, passThroughCount,
                0, 0, connectionCount, false).rating();
    }

    /**
     * Computes the overall quality rating with per-metric breakdown — M6 two-dimensional model.
     *
     * <p>Backwards-compatible delegating overload (10-arg). Existing callers pass zeros for the
     * M2-M5 + L1-L3 inputs; M6 promotions for parentLabelObscured and labelTruncation are then
     * inactive (count = 0). Use the 18-arg expanded overload to exercise the full M6 model.</p>
     */
    RatingResult computeRatingWithBreakdown(int overlaps, int crossings,
                                             double avgSpacing, int alignmentScore,
                                             int labelOverlapCount, int passThroughCount,
                                             int coincidentSegments, int nonOrthogonalTerminals,
                                             int connectionCount, boolean hasGroups) {
        return computeRatingWithBreakdown(overlaps, crossings, avgSpacing, alignmentScore,
                labelOverlapCount, passThroughCount, coincidentSegments, nonOrthogonalTerminals,
                connectionCount, hasGroups,
                0, 0, 0, 0,           // boundaryViolation, parentLabelObscured, offCanvas, labelTruncation
                0, 0, 0, 1.0);        // interior, zigzag, edgeCoincidence, hubPortQuality (1.0 = perfect)
    }

    /**
     * Computes the overall quality rating with per-metric breakdown (M6).
     *
     * <p><b>M6 model (Assessor.Redesign 2026-04-26):</b> Each metric contributes an individual
     * rating ("pass"/"excellent"/"good"/"fair"/"poor"). The overall rating uses a two-dimensional
     * decomposition: a layout-tier rating (L1: overlaps, boundary, parentLabelObscured-promoted;
     * L2 cap-fair: spacing, off-canvas; L3 cap-good: alignment) AND a routing-tier rating
     * (R1: passThroughs, M2 interior, M3 zigzag, conn-vs-conn coincident; R2 cap-fair: M1 nonOrth,
     * M4 edge-coincidence, M5 low hub-port quality, labelOverlap-promoted, labelTruncation-promoted;
     * R3 cap-good: edge crossings). Combined {@code overall = worse(layoutRating, routingRating)}.
     * Per spec, layout is the prerequisite — a view with sibling overlaps is broken regardless
     * of routing quality.</p>
     *
     * @param hasGroups when true, crossing leniency applies if passThroughCount (cross-element only) &lt;= FAIR_MAX_PASS_THROUGHS
     */
    RatingResult computeRatingWithBreakdown(int overlaps, int crossings,
                                             double avgSpacing, int alignmentScore,
                                             int labelOverlapCount, int passThroughCount,
                                             int coincidentSegments, int nonOrthogonalTerminals,
                                             int connectionCount, boolean hasGroups,
                                             int boundaryViolationCount, int parentLabelObscuredCount,
                                             int offCanvasCount, int labelTruncationCount,
                                             int interiorTerminationCount, int zigzagCount,
                                             int connectionEdgeCoincidenceCount,
                                             double hubPortQualityScore) {
        Map<String, String> breakdown = new LinkedHashMap<>();

        // 1. Overlaps rating (L1) — binary >0 → poor (sibling overlaps are tier-1L layout-severity)
        if (overlaps == 0) {
            breakdown.put("overlaps", "pass");
        } else {
            breakdown.put("overlaps", "poor");
        }

        // 2. Edge crossings rating (R3 cap-good — density-aware, Stories 11-12 / 11-22)
        double crossingRatio = connectionCount > 0
                ? (double) crossings / connectionCount : crossings;
        String crossingRating;
        if (crossings < EXCELLENT_MAX_CROSSINGS) {
            crossingRating = "pass";
        } else if (crossings < GOOD_MAX_CROSSINGS) {
            crossingRating = "good";
        } else if (connectionCount > 0 && crossingRatio <= CROSSING_RATIO_GOOD) {
            // Views with low density (≤1.5 crossings/conn) rate "good"
            // even when absolute count exceeds GOOD_MAX_CROSSINGS
            crossingRating = "good";
        } else if (connectionCount > 0 && crossingRatio <= CROSSING_RATIO_MODERATE) {
            crossingRating = "fair";
        } else if (crossings < FAIR_MAX_CROSSINGS) {
            crossingRating = "fair";
        } else {
            crossingRating = "poor";
        }
        // Grouped-view leniency — one-tier boost (not unconditional floor).
        // Under M6, crossings already cap at "good" (Tier 3R), but the leniency still applies
        // to the breakdown rating for diagnostic clarity (a "fair"-rated breakdown with
        // grouped-view conditions becomes "good").
        if (hasGroups && overlaps == 0 && passThroughCount <= FAIR_MAX_PASS_THROUGHS
                && labelOverlapCount == 0 && alignmentScore > GOOD_MIN_ALIGNMENT
                && avgSpacing > GOOD_MIN_SPACING
                && ("fair".equals(crossingRating) || "poor".equals(crossingRating))) {
            crossingRating = "poor".equals(crossingRating) ? "fair" : "good";
        }
        breakdown.put("edgeCrossings", crossingRating);

        // 3. Spacing rating (L2 cap-fair)
        if (avgSpacing > EXCELLENT_MIN_SPACING) {
            breakdown.put("spacing", "pass");
        } else if (avgSpacing > GOOD_MIN_SPACING) {
            breakdown.put("spacing", "good");
        } else {
            breakdown.put("spacing", "fair");
        }

        // 4. Alignment rating (L3 cap-good)
        if (alignmentScore > EXCELLENT_MIN_ALIGNMENT) {
            breakdown.put("alignment", "pass");
        } else if (alignmentScore > GOOD_MIN_ALIGNMENT) {
            breakdown.put("alignment", "good");
        } else {
            breakdown.put("alignment", "fair");
        }

        // 5. Label overlaps rating (R2 cap-fair — promoted from R3 under M6)
        if (labelOverlapCount == 0) {
            breakdown.put("labelOverlaps", "pass");
        } else if (labelOverlapCount <= 2) {
            breakdown.put("labelOverlaps", "good");
        } else {
            breakdown.put("labelOverlaps", "fair");
        }

        // 6. Pass-throughs rating (R1)
        if (passThroughCount == 0) {
            breakdown.put("passThroughs", "pass");
        } else if (passThroughCount <= FAIR_MAX_PASS_THROUGHS) {
            breakdown.put("passThroughs", "fair");
        } else {
            breakdown.put("passThroughs", "poor");
        }

        // 7. Coincident segments rating (R1 conn-vs-conn)
        if (coincidentSegments == 0) {
            breakdown.put("coincidentSegments", "pass");
        } else if (coincidentSegments <= GOOD_MAX_COINCIDENT) {
            breakdown.put("coincidentSegments", "good");
        } else if (coincidentSegments <= FAIR_MAX_COINCIDENT) {
            breakdown.put("coincidentSegments", "fair");
        } else {
            breakdown.put("coincidentSegments", "poor");
        }

        // 8. Non-orthogonal terminals rating (R2 cap-fair — promoted from R3 under M6, density-aware).
        // M1 corrected definition (visible post-clip segment) flows through `nonOrthogonalTerminals`.
        if (nonOrthogonalTerminals == 0) {
            breakdown.put("nonOrthogonalTerminals", "pass");
        } else if (connectionCount > 0) {
            double nonOrthRatio = (double) nonOrthogonalTerminals / connectionCount;
            if (nonOrthRatio <= NON_ORTH_RATIO_GOOD) {
                breakdown.put("nonOrthogonalTerminals", "good");
            } else if (nonOrthRatio <= NON_ORTH_RATIO_FAIR) {
                breakdown.put("nonOrthogonalTerminals", "fair");
            } else {
                breakdown.put("nonOrthogonalTerminals", "poor");
            }
        } else {
            // Zero connections but non-zero non-orth (edge case) — rate as fair
            breakdown.put("nonOrthogonalTerminals", "fair");
        }

        // 9. Boundary violations (L1 — Assessor.Redesign promotion: any violation is layout-Tier-1L)
        breakdown.put("boundaryViolations", boundaryViolationCount == 0 ? "pass" : "poor");

        // 10. Parent label obscured (L1 — promoted from info per M6)
        breakdown.put("parentLabelObscured", parentLabelObscuredCount == 0 ? "pass" : "poor");

        // 11. Off-canvas (L2 cap-fair — was partial; explicit under M6)
        breakdown.put("offCanvas", offCanvasCount == 0 ? "pass" : "fair");

        // 12. Label truncation (R2 cap-fair — promoted from info per M6)
        breakdown.put("labelTruncations", labelTruncationCount == 0 ? "pass" : "fair");

        // 13. Interior terminations (R1 — M2)
        breakdown.put("interiorTerminations", interiorTerminationCount == 0 ? "pass" : "poor");

        // 14. Zigzags (R1 — M3)
        breakdown.put("zigzags", zigzagCount == 0 ? "pass" : "poor");

        // 15. Edge-coincidence (R2 cap-fair — M4; A-gated: Tier-1R escalation at
        //     count >= EDGE_COINCIDENCE_EGREGIOUS_MAX, see computeRoutingTierLevel)
        if (connectionEdgeCoincidenceCount == 0) {
            breakdown.put("connectionEdgeCoincidence", "pass");
        } else if (connectionEdgeCoincidenceCount <= EDGE_COINCIDENCE_GOOD_MAX) {
            breakdown.put("connectionEdgeCoincidence", "good");
        } else if (connectionEdgeCoincidenceCount <= EDGE_COINCIDENCE_FAIR_MAX) {
            breakdown.put("connectionEdgeCoincidence", "fair");
        } else {
            breakdown.put("connectionEdgeCoincidence", "poor");
        }

        // 16. Hub-port quality (R2 cap-fair — M5; threshold quality < 0.5 contributes)
        if (hubPortQualityScore >= HUB_PORT_QUALITY_PASS_THRESHOLD) {
            breakdown.put("hubPortQuality", "pass");
        } else if (hubPortQualityScore >= HUB_PORT_QUALITY_GOOD_THRESHOLD) {
            breakdown.put("hubPortQuality", "good");
        } else if (hubPortQualityScore >= HUB_PORT_QUALITY_FAIR_THRESHOLD) {
            breakdown.put("hubPortQuality", "fair");
        } else {
            breakdown.put("hubPortQuality", "poor");
        }

        // M6: two-dimensional rating (layout-tier × routing-tier × worse combiner).
        int layoutLevel = computeLayoutTierLevel(breakdown);
        int routingLevel = computeRoutingTierLevel(breakdown, connectionEdgeCoincidenceCount);
        int overallLevel = Math.max(layoutLevel, routingLevel);
        String layoutRating = levelToRating(layoutLevel);
        String routingRating = levelToRating(routingLevel);
        String overall = levelToRating(overallLevel);
        breakdown.put("overall", overall);

        return new RatingResult(overall, breakdown, layoutRating, routingRating);
    }

    /**
     * Layout-tier level under M6 (worse contribution wins, with per-tier caps).
     * <ul>
     *   <li><b>Tier 1L</b> (critical, no cap): overlaps, boundaryViolations, parentLabelObscured (promoted)</li>
     *   <li><b>Tier 2L</b> (cap fair=2): spacing, offCanvas</li>
     *   <li><b>Tier 3L</b> (cap good=1): alignment</li>
     * </ul>
     */
    private int computeLayoutTierLevel(Map<String, String> breakdown) {
        int tier1 = Math.max(Math.max(
                ratingLevel(breakdown.getOrDefault("overlaps", "pass")),
                ratingLevel(breakdown.getOrDefault("boundaryViolations", "pass"))),
                ratingLevel(breakdown.getOrDefault("parentLabelObscured", "pass")));
        int tier2 = Math.max(
                ratingLevel(breakdown.getOrDefault("spacing", "pass")),
                ratingLevel(breakdown.getOrDefault("offCanvas", "pass")));
        int tier3 = ratingLevel(breakdown.getOrDefault("alignment", "pass"));

        int level = tier1;
        level = Math.max(level, Math.min(tier2, 2));
        level = Math.max(level, Math.min(tier3, 1));
        return level;
    }

    /**
     * Routing-tier level under M6 (worse contribution wins, with per-tier caps).
     * <ul>
     *   <li><b>Tier 1R</b> (critical, no cap): passThroughs, M2 interior, M3 zigzag, conn-vs-conn coincident;
     *       <b>plus M4 edge-coincidence when the count is egregious</b>
     *       (&ge; {@link #EDGE_COINCIDENCE_EGREGIOUS_MAX} — A-gated escalation)</li>
     *   <li><b>Tier 2R</b> (cap fair=2): M1 nonOrth, M4 edge-coincidence (count &lt; EGREGIOUS), M5 low
     *       hub-port quality, labelOverlaps (promoted), labelTruncations (promoted)</li>
     *   <li><b>Tier 3R</b> (cap good=1): edge crossings</li>
     * </ul>
     */
    private int computeRoutingTierLevel(Map<String, String> breakdown, int edgeCoincidenceCount) {
        int tier1 = Math.max(Math.max(Math.max(
                ratingLevel(breakdown.getOrDefault("passThroughs", "pass")),
                ratingLevel(breakdown.getOrDefault("interiorTerminations", "pass"))),
                ratingLevel(breakdown.getOrDefault("zigzags", "pass"))),
                ratingLevel(breakdown.getOrDefault("coincidentSegments", "pass")));
        // A-gated escalation (2026-05-21):
        // M4 connectionEdgeCoincidence is normally Tier-2R (cap-fair, see tier2 below). An EGREGIOUS
        // count escalates it to Tier-1R so an eye-obvious hug-storm drives overall="poor" instead of
        // being masked at "fair". Spares the common 1-5 forced-hug case. M4 is intentionally also left
        // in tier2 (harmless: tier2 caps at 2; the tier1 contribution dominates when this fires).
        if (edgeCoincidenceCount >= EDGE_COINCIDENCE_EGREGIOUS_MAX) {
            tier1 = Math.max(tier1,
                    ratingLevel(breakdown.getOrDefault("connectionEdgeCoincidence", "pass")));
        }
        int tier2 = Math.max(Math.max(Math.max(Math.max(
                ratingLevel(breakdown.getOrDefault("nonOrthogonalTerminals", "pass")),
                ratingLevel(breakdown.getOrDefault("connectionEdgeCoincidence", "pass"))),
                ratingLevel(breakdown.getOrDefault("hubPortQuality", "pass"))),
                ratingLevel(breakdown.getOrDefault("labelOverlaps", "pass"))),
                ratingLevel(breakdown.getOrDefault("labelTruncations", "pass")));
        int tier3 = ratingLevel(breakdown.getOrDefault("edgeCrossings", "pass"));

        int level = tier1;
        level = Math.max(level, Math.min(tier2, 2));
        level = Math.max(level, Math.min(tier3, 1));
        return level;
    }

    /** Maps level (0..3) to rating string. 0 = excellent, 3 = poor. */
    private static String levelToRating(int level) {
        return switch (level) {
            case 0 -> "excellent";
            case 1 -> "good";
            case 2 -> "fair";
            default -> "poor";
        };
    }

    private int ratingLevel(String rating) {
        return switch (rating) {
            case "pass", "excellent" -> 0;
            case "good" -> 1;
            case "fair" -> 2;
            case "poor" -> 3;
            default -> 2; // unknown ratings treated conservatively as "fair"
        };
    }

    // ---- Non-Orthogonal Terminal Detection (M1 corrected post-clip) ----

    /** Result of non-orthogonal terminal detection (adds violator IDs and zero-bendpoint count). */
    record NonOrthogonalTerminalResult(int count, Set<String> violatorIds, int zeroBendpointCount) {}

    /**
     * Backwards-compatible delegating overload (no node lookup — falls back to geometric semantics).
     * Tests that don't pass synthetic nodes get pre-M1 behaviour for their unchanged paths.
     *
     * @deprecated Prefer the 3-arg overload for M1 post-clip correctness.
     */
    @Deprecated
    NonOrthogonalTerminalResult countNonOrthogonalTerminals(
            List<AssessmentConnection> connections, boolean collectViolatorIds) {
        return countNonOrthogonalTerminals(connections, List.of(), collectViolatorIds);
    }

    /**
     * Counts connections with at least one non-orthogonal terminal segment.
     *
     * <p><b>M1 corrected definition (Assessor.Redesign 2026-04-26):</b> the terminal segment is
     * the portion of {@code [sourceAnchor → BP1]} (or {@code [BP_last → targetAnchor]}) that lies
     * <i>outside</i> the source/target element bounds. Archi clips connection rendering at the
     * perimeter; the geometric diagonal between an element-center sourceAnchor and an on-perimeter
     * BP1 has zero visible length and is therefore <b>not</b> counted as non-orthogonal.</p>
     *
     * <p>Implementation: when {@code path[1]} (or {@code path[size-2]}) lies on the perimeter or
     * strictly inside the source (or target) element rect, the visible segment is zero or
     * invisible — skip the non-orth flag. Otherwise apply the existing geometric test on
     * {@code [path[0], path[1]]}: the post-clip segment lies on the same line as the geometric
     * one, so its orthogonality angle is invariant.</p>
     *
     * <p><b>M1 minimum-visible-length guard (2026-04-27):</b>
     * when the visible (post-clip) segment length is &lt; {@link #VISIBLE_DIAGONAL_MIN_PX}, the
     * diagonal is sub-perceptible at typical Archi zoom levels and is silently skipped. This
     * calibrates the metric against hand-routed views (manual oracle
     * {@code id-3b2665e3ff6840708dbed2b3d1415613}) where Archi commonly stores manually-routed
     * BPs 1px off the perimeter face line, producing a 1.0–1.3px visible diagonal that the
     * geometric angle test would otherwise flag. The guard is purely additive — connections
     * with longer visible diagonals (e.g. the V4 manual oracle's APIM→CorpBank case at 320px
     * visible length) remain flagged because the diagonal IS visible to the user.</p>
     *
     * <p>When {@code layoutNodes} is empty or the source/target node cannot be resolved by ID,
     * BOTH the M1 perimeter check AND the visible-length guard collapse to no-ops:
     * {@link #isOnOrInsideElement} returns {@code false} for null elem (so the perimeter-skip
     * never fires) and {@link #visibleSegmentLength} returns {@link Double#POSITIVE_INFINITY}
     * for null elem (so the {@code >= VISIBLE_DIAGONAL_MIN_PX} guard always passes). The
     * conjunction then collapses to the legacy geometric test path. This preserves
     * backwards-compatibility for the @Deprecated 2-arg overload — note that the {@code +∞}
     * return for {@code visibleSegmentLength(null elem)} is load-bearing: returning {@code 0}
     * would make every legacy diagonal flag silently suppressed (round-1 regression on 9
     * pre-existing tests, fixed in round-2).</p>
     *
     * @param connections      connection paths to evaluate
     * @param layoutNodes      lookup for source/target rectangles (may be empty for legacy callers)
     * @param collectViolatorIds when true, populates violator set
     */
    NonOrthogonalTerminalResult countNonOrthogonalTerminals(
            List<AssessmentConnection> connections, List<AssessmentNode> layoutNodes,
            boolean collectViolatorIds) {
        Map<String, AssessmentNode> nodeById = new HashMap<>();
        for (AssessmentNode n : layoutNodes) {
            nodeById.put(n.id(), n);
        }
        int count = 0;
        int zeroBpCount = 0;
        Set<String> violatorIds = collectViolatorIds ? new HashSet<>() : Set.of();
        for (AssessmentConnection conn : connections) {
            List<double[]> path = conn.pathPoints();
            if (path.size() < 2) continue;
            AssessmentNode source = nodeById.get(conn.sourceNodeId());
            AssessmentNode target = nodeById.get(conn.targetNodeId());

            // Source terminal — M1: skip when path[1] is on or inside source rect,
            // OR when the visible post-clip segment
            // is below the perceptibility threshold.
            boolean sourceVisibleNonOrth = false;
            if (!isOnOrInsideElement(path.get(1), source)
                    && isNonOrthogonal(path.get(0), path.get(1))
                    && visibleSegmentLength(path.get(0), path.get(1), source)
                            >= VISIBLE_DIAGONAL_MIN_PX) {
                sourceVisibleNonOrth = true;
            }
            if (sourceVisibleNonOrth) {
                count++;
                if (collectViolatorIds) {
                    violatorIds.add(conn.id());
                }
                // zero-bendpoint = 2-point path (source center + target center, no intermediate BPs)
                if (path.size() == 2) {
                    zeroBpCount++;
                }
                continue;
            }
            // Target terminal — M1: skip when path[size-2] is on or inside target rect,
            // OR when the visible post-clip segment
            // is below the perceptibility threshold. Note the helper's anchor argument
            // is the target-side element-center (path[last]), bp is the outside BP
            // (path[last - 1]) — the convention is anchor=inside, bp=outside.
            int last = path.size() - 1;
            if (!isOnOrInsideElement(path.get(last - 1), target)
                    && isNonOrthogonal(path.get(last - 1), path.get(last))
                    && visibleSegmentLength(path.get(last), path.get(last - 1), target)
                            >= VISIBLE_DIAGONAL_MIN_PX) {
                count++;
                if (collectViolatorIds) {
                    violatorIds.add(conn.id());
                }
                // 2-point paths are zero-bendpoint, but for 2-point paths source and target
                // terminals are the same segment — already handled above via continue
            }
        }
        return new NonOrthogonalTerminalResult(count, violatorIds, zeroBpCount);
    }

    /**
     * M1 helper: returns true if the bendpoint lies on the perimeter line of the element
     * (within {@link #PERIMETER_TOLERANCE_PX}px) or strictly inside the element's bounding rect.
     * When {@code elem} is null, returns false (caller falls back to legacy geometric test).
     *
     * <p>The "perimeter line" is the literal element edge (LEFT: x=elem.x; RIGHT: x=elem.x+w;
     * TOP: y=elem.y; BOTTOM: y=elem.y+h) — this matches the spec example where Archi stores
     * a bendpoint at (641, 259) on an element whose LEFT face is x=641. {@code RoutingPipeline}
     * uses a different 1px-offset convention internally (Layer 3 sub-package) — those are
     * deliberately distinct under Task 2.2 design (inline duplicate, no Layer-3 cross-coupling).</p>
     */
    static boolean isOnOrInsideElement(double[] bp, AssessmentNode elem) {
        if (elem == null) return false;
        double x = bp[0];
        double y = bp[1];
        double left = elem.x();
        double right = elem.x() + elem.width();
        double top = elem.y();
        double bottom = elem.y() + elem.height();
        double tol = PERIMETER_TOLERANCE_PX;
        return x >= left - tol && x <= right + tol
                && y >= top - tol && y <= bottom + tol;
    }

    /**
     * M1 helper (2026-04-27): returns the Euclidean
     * length of the visible (post-clip) portion of segment {@code [anchor, bp]} against
     * {@code elem}. The visible portion runs from the perimeter clip-point to {@code bp}.
     *
     * <p>Convention: {@code anchor} is the element-center side of the terminal segment
     * (always strictly inside {@code elem} for ChopboxAnchor source/target anchors);
     * {@code bp} is the bendpoint outside the element. Caller already guards via
     * {@link #isOnOrInsideElement} on {@code bp} — this helper short-circuits to 0
     * when the guard's invariant ever fails (defense-in-depth).</p>
     *
     * <p><b>Null-elem semantics:</b> returns {@link Double#POSITIVE_INFINITY} when
     * {@code elem} is null. This is the legacy 2-arg overload path (no node lookup
     * available) — the +∞ return makes the {@code >= VISIBLE_DIAGONAL_MIN_PX} guard
     * in {@code countNonOrthogonalTerminals} a no-op, correctly collapsing to the
     * legacy geometric-only test. Returning 0 here would incorrectly suppress every
     * legacy non-orth flag.</p>
     *
     * @param anchor inside-the-element endpoint of the segment (typically the element center)
     * @param bp outside-the-element endpoint (typically the first/last bendpoint)
     * @param elem the source/target element rect (may be null for legacy callers)
     * @return visible segment length in pixels; {@code +∞} if elem null (legacy no-op);
     *         0 if bp on/inside elem (defense-in-depth — caller already short-circuits)
     */
    static double visibleSegmentLength(double[] anchor, double[] bp, AssessmentNode elem) {
        if (elem == null) return Double.POSITIVE_INFINITY;
        if (isOnOrInsideElement(bp, elem)) return 0.0;
        double[] clip = lineRectIntersection(anchor, bp, elem);
        if (clip == null) {
            // Degenerate fallback: anchor outside rect AND line misses rect entirely.
            // Return full segment length so M1 still flags long diagonals — the visible
            // segment, having no clip, IS the full segment.
            return Math.hypot(bp[0] - anchor[0], bp[1] - anchor[1]);
        }
        return Math.hypot(bp[0] - clip[0], bp[1] - clip[1]);
    }

    /**
     * M1 helper (2026-04-27): returns the perimeter
     * intersection point of segment {@code [a, b]} against rect {@code r}, or null if
     * the segment does not cross the perimeter at any {@code t} in {@code (0, 1]}.
     *
     * <p>For the standard M1 use case ({@code a} = element center, strictly inside;
     * {@code b} = outside), the segment crosses the perimeter exactly once and the
     * smallest valid {@code t} identifies the clip point. Algorithm: parametrize as
     * {@code P(t) = a + t * (b - a)}, intersect with each of the 4 face lines (skipping
     * any line whose normal is parallel to the segment), accept only intersections that
     * lie within the face's bounded extent, and return the smallest-{@code t} hit.</p>
     */
    static double[] lineRectIntersection(double[] a, double[] b, AssessmentNode r) {
        if (r == null) return null;
        double left = r.x();
        double right = r.x() + r.width();
        double top = r.y();
        double bottom = r.y() + r.height();
        double dx = b[0] - a[0];
        double dy = b[1] - a[1];

        double bestT = Double.POSITIVE_INFINITY;
        double[] bestPoint = null;

        // LEFT face: x = left
        if (Math.abs(dx) > 1e-9) {
            double t = (left - a[0]) / dx;
            if (t > 1e-9 && t <= 1.0) {
                double y = a[1] + t * dy;
                if (y >= top && y <= bottom && t < bestT) {
                    bestT = t;
                    bestPoint = new double[]{left, y};
                }
            }
        }
        // RIGHT face: x = right
        if (Math.abs(dx) > 1e-9) {
            double t = (right - a[0]) / dx;
            if (t > 1e-9 && t <= 1.0) {
                double y = a[1] + t * dy;
                if (y >= top && y <= bottom && t < bestT) {
                    bestT = t;
                    bestPoint = new double[]{right, y};
                }
            }
        }
        // TOP face: y = top
        if (Math.abs(dy) > 1e-9) {
            double t = (top - a[1]) / dy;
            if (t > 1e-9 && t <= 1.0) {
                double x = a[0] + t * dx;
                if (x >= left && x <= right && t < bestT) {
                    bestT = t;
                    bestPoint = new double[]{x, top};
                }
            }
        }
        // BOTTOM face: y = bottom
        if (Math.abs(dy) > 1e-9) {
            double t = (bottom - a[1]) / dy;
            if (t > 1e-9 && t <= 1.0) {
                double x = a[0] + t * dx;
                if (x >= left && x <= right && t < bestT) {
                    bestT = t;
                    bestPoint = new double[]{x, bottom};
                }
            }
        }

        return bestPoint;
    }

    /**
     * M2 helper: returns true if the bendpoint lies <b>strictly inside</b> the element bounds —
     * NOT on the perimeter line and NOT outside. Strict inequalities (with a small tolerance to
     * exclude on-perimeter cases). When {@code elem} is null, returns false.
     */
    static boolean isStrictlyInside(double[] bp, AssessmentNode elem) {
        if (elem == null) return false;
        double tol = PERIMETER_TOLERANCE_PX;
        return bp[0] > elem.x() + tol
                && bp[0] < elem.x() + elem.width() - tol
                && bp[1] > elem.y() + tol
                && bp[1] < elem.y() + elem.height() - tol;
    }

    private boolean isNonOrthogonal(double[] p1, double[] p2) {
        double dx = Math.abs(p1[0] - p2[0]);
        double dy = Math.abs(p1[1] - p2[1]);
        if (dx < 1e-9 && dy < 1e-9) return false; // zero-length or near-zero segment
        // Angular detection — angle in [0°, 90°] quadrant
        double angleDeg = Math.toDegrees(Math.atan2(dy, dx));
        // Deviation from nearest cardinal axis (0° or 90°)
        double deviation = Math.min(angleDeg, 90.0 - angleDeg);
        return deviation > NON_ORTH_ANGLE_THRESHOLD;
    }

    // ---- Assessor.Redesign M2: Interior-Termination Detection ----

    /** Result of M2 interior-termination detection. */
    record InteriorTerminationResult(int count, List<String> descriptions, Set<String> violatorIds) {}

    /**
     * Counts connections whose first or last bendpoint lies <b>strictly inside</b> the
     * source/target element bounds (M2). Strict inequalities — bendpoints on the perimeter
     * line do NOT count (those are handled by M1's post-clip definition). Connections with
     * fewer than 2 path points are skipped.
     *
     * <p>Spec live example: a connection whose {@code BP_last} coordinates fall inside the
     * target element rectangle indicates a routing failure where Archi's ChopboxAnchor face
     * selection couldn't resolve the terminal correctly. Tier 1R severity (peer with
     * passThroughs).</p>
     */
    InteriorTerminationResult countInteriorTerminations(
            List<AssessmentConnection> connections, List<AssessmentNode> layoutNodes,
            boolean collectViolatorIds) {
        Map<String, AssessmentNode> nodeById = new HashMap<>();
        for (AssessmentNode n : layoutNodes) {
            nodeById.put(n.id(), n);
        }
        int count = 0;
        List<String> descriptions = new ArrayList<>();
        Set<String> violatorIds = collectViolatorIds ? new HashSet<>() : Set.of();
        for (AssessmentConnection conn : connections) {
            List<double[]> path = conn.pathPoints();
            // Need at least source-center + one BP + target-center to assess a terminal BP.
            if (path.size() < 3) continue;
            AssessmentNode source = nodeById.get(conn.sourceNodeId());
            AssessmentNode target = nodeById.get(conn.targetNodeId());
            // path.get(0) = sourceAnchor (element center), path.get(size-1) = targetAnchor (element center).
            // The interior-termination signal is the FIRST BP after the source — path.get(1),
            // and the LAST BP before the target — path.get(size-2).
            boolean sourceInterior = isStrictlyInside(path.get(1), source);
            boolean targetInterior = isStrictlyInside(path.get(path.size() - 2), target);
            if (sourceInterior || targetInterior) {
                count++;
                String side = sourceInterior && targetInterior ? "source and target"
                        : sourceInterior ? "source" : "target";
                if (descriptions.size() < MAX_DESCRIPTIONS) {
                    descriptions.add("Connection '" + conn.id() + "' " + conn.sourceNodeId()
                            + " → " + conn.targetNodeId()
                            + ": interior termination on " + side);
                }
                if (collectViolatorIds) {
                    violatorIds.add(conn.id());
                }
            }
        }
        return new InteriorTerminationResult(count, descriptions, violatorIds);
    }

    // ---- Assessor.Redesign M3: Zigzag / Reversal Detection ----

    /** Result of M3 zigzag detection. */
    record ZigzagResult(int count, List<String> descriptions, Set<String> violatorIds) {}

    /**
     * Counts connections containing at least one zigzag triple (M3). A zigzag is three
     * consecutive bendpoints {@code (bp_i, bp_{i+1}, bp_{i+2})} where either:
     * <ul>
     *   <li>all three share the same X within {@link #ZIGZAG_AXIS_TOLERANCE_PX} AND the Y-deltas
     *       between consecutive pairs have opposite signs both > {@link #ZIGZAG_MIN_DELTA_PX} in
     *       magnitude, OR</li>
     *   <li>symmetric: all three share the same Y AND X-deltas have opposite signs.</li>
     * </ul>
     * Counted as a binary defect per connection — one or more zigzag triples → +1 to count.
     *
     * <p>Spec live example: connection {@code id-3795e46e72a049e596b618b1ce948441} (API Mgmt →
     * Corporate Banking, Oracle C1) triple {@code (403,259) → (403,219) → (403,261)} flags
     * (x=403 shared, Δy = -40 then +42 — opposite signs both > 1px). Tier 1R severity.</p>
     *
     * <p>Classification precedence: connections whose IDs appear in {@code passThroughViolatorIds}
     * are skipped (passthrough takes precedence over zigzag) — the failed-detour-around-element
     * pattern produces a small reversal because the detour failed and passed through, and the
     * visually-correct label is passthrough-only.</p>
     *
     * @see #detectPassThroughs(List, List, boolean)
     */
    ZigzagResult countZigzags(List<AssessmentConnection> connections,
                              Set<String> passThroughViolatorIds,
                              boolean collectViolatorIds) {
        Objects.requireNonNull(passThroughViolatorIds, "passThroughViolatorIds must not be null");
        int count = 0;
        List<String> descriptions = new ArrayList<>();
        Set<String> violatorIds = collectViolatorIds ? new HashSet<>() : Set.of();
        for (AssessmentConnection conn : connections) {
            if (passThroughViolatorIds.contains(conn.id())) continue;
            List<double[]> path = conn.pathPoints();
            if (path.size() < 3) continue;
            for (int i = 0; i < path.size() - 2; i++) {
                double[] a = path.get(i);
                double[] b = path.get(i + 1);
                double[] c = path.get(i + 2);
                if (isZigzagTriple(a, b, c)) {
                    count++;
                    if (descriptions.size() < MAX_DESCRIPTIONS) {
                        descriptions.add("Connection '" + conn.id() + "' " + conn.sourceNodeId()
                                + " → " + conn.targetNodeId() + ": zigzag/reversal at index " + i
                                + " (" + formatPoint(a) + " → " + formatPoint(b)
                                + " → " + formatPoint(c) + ")");
                    }
                    if (collectViolatorIds) {
                        violatorIds.add(conn.id());
                    }
                    break; // binary defect per connection — one triple is enough
                }
            }
        }
        return new ZigzagResult(count, descriptions, violatorIds);
    }

    private static boolean isZigzagTriple(double[] a, double[] b, double[] c) {
        double tol = ZIGZAG_AXIS_TOLERANCE_PX;
        double minDelta = ZIGZAG_MIN_DELTA_PX;
        // Shared-X variant: all three points within tol of the same X, opposite-sign Y deltas.
        boolean sharedX = Math.abs(a[0] - b[0]) <= tol
                && Math.abs(b[0] - c[0]) <= tol;
        if (sharedX) {
            double dy1 = b[1] - a[1];
            double dy2 = c[1] - b[1];
            if (Math.abs(dy1) > minDelta && Math.abs(dy2) > minDelta
                    && Math.signum(dy1) != Math.signum(dy2)) {
                return true;
            }
        }
        // Shared-Y variant: symmetric.
        boolean sharedY = Math.abs(a[1] - b[1]) <= tol
                && Math.abs(b[1] - c[1]) <= tol;
        if (sharedY) {
            double dx1 = b[0] - a[0];
            double dx2 = c[0] - b[0];
            if (Math.abs(dx1) > minDelta && Math.abs(dx2) > minDelta
                    && Math.signum(dx1) != Math.signum(dx2)) {
                return true;
            }
        }
        return false;
    }

    private static String formatPoint(double[] p) {
        return "(" + Math.round(p[0]) + "," + Math.round(p[1]) + ")";
    }

    // ---- Assessor.Redesign M4: Connection-vs-Element-Edge Coincidence ----

    /** Result of M4 edge-coincidence detection. */
    record EdgeCoincidenceResult(int count, List<String> descriptions, Set<String> violatorIds) {}

    /**
     * Counts connection segments that "hug" an element's edge within
     * {@link #EDGE_COINCIDENCE_TOLERANCE_PX}px (M4). For each segment, classified as
     * horizontal or vertical (within 1px tolerance):
     * <ul>
     *   <li>Horizontal at {@code y_seg}: any element (including the connection's own
     *       source/target) with TOP or BOTTOM edge within {@link #EDGE_COINCIDENCE_TOLERANCE_PX}
     *       AND segment x-range overlapping element x-range by at least
     *       {@link #EDGE_COINCIDENCE_MIN_OVERLAP_PX}.</li>
     *   <li>Vertical: symmetric (any element with LEFT/RIGHT edges).</li>
     * </ul>
     * Includes the connection's own source/target faces — perpendicular terminal segments
     * are silent by construction (orientation mismatch in classification at lines above), so
     * any flag against source/target is a real parallel-coincident defect. Distinct from
     * {@code coincidentSegmentCount} (R6) which is connection-vs-connection.
     *
     * <p>Spec live example: connection {@code id-74e3ee1e02a84721a3db682cb1b6fb24} (API Mgmt →
     * Internet Banking) horizontal segment at y=150 vs Internet Banking BOTTOM at y=148 (gap
     * 2px). Internet Banking IS the connection's target — under the post-removal rule
     * (2026-04-27), this is in scope. Tier 2R severity (cap fair).</p>
     */
    EdgeCoincidenceResult countConnectionEdgeCoincidence(
            List<AssessmentConnection> connections, List<AssessmentNode> layoutNodes,
            boolean collectViolatorIds) {
        int count = 0;
        List<String> descriptions = new ArrayList<>();
        Set<String> violatorIds = collectViolatorIds ? new HashSet<>() : Set.of();
        for (AssessmentConnection conn : connections) {
            List<double[]> path = conn.pathPoints();
            if (path.size() < 2) continue;
            boolean flagged = false;
            for (int i = 0; i < path.size() - 1; i++) {
                double[] s = path.get(i);
                double[] e = path.get(i + 1);
                boolean horizontal = Math.abs(s[1] - e[1]) <= ZIGZAG_AXIS_TOLERANCE_PX
                        && Math.abs(s[0] - e[0]) > ZIGZAG_AXIS_TOLERANCE_PX;
                boolean vertical = Math.abs(s[0] - e[0]) <= ZIGZAG_AXIS_TOLERANCE_PX
                        && Math.abs(s[1] - e[1]) > ZIGZAG_AXIS_TOLERANCE_PX;
                if (!horizontal && !vertical) continue;
                for (AssessmentNode elem : layoutNodes) {
                    if (horizontal && segmentHugsHorizontalEdge(s, e, elem)) {
                        count++;
                        if (descriptions.size() < MAX_DESCRIPTIONS) {
                            descriptions.add("Connection '" + conn.id() + "' segment "
                                    + formatPoint(s) + "→" + formatPoint(e)
                                    + " hugs element '" + elem.id() + "' edge");
                        }
                        if (collectViolatorIds) {
                            violatorIds.add(conn.id());
                        }
                        flagged = true;
                        break;
                    }
                    if (vertical && segmentHugsVerticalEdge(s, e, elem)) {
                        count++;
                        if (descriptions.size() < MAX_DESCRIPTIONS) {
                            descriptions.add("Connection '" + conn.id() + "' segment "
                                    + formatPoint(s) + "→" + formatPoint(e)
                                    + " hugs element '" + elem.id() + "' edge");
                        }
                        if (collectViolatorIds) {
                            violatorIds.add(conn.id());
                        }
                        flagged = true;
                        break;
                    }
                }
                if (flagged) break;
            }
        }
        return new EdgeCoincidenceResult(count, descriptions, violatorIds);
    }

    private static boolean segmentHugsHorizontalEdge(double[] s, double[] e, AssessmentNode elem) {
        double y = (s[1] + e[1]) / 2.0;
        double topEdge = elem.y();
        double bottomEdge = elem.y() + elem.height();
        boolean nearEdge = Math.abs(y - topEdge) <= EDGE_COINCIDENCE_TOLERANCE_PX
                || Math.abs(y - bottomEdge) <= EDGE_COINCIDENCE_TOLERANCE_PX;
        if (!nearEdge) return false;
        double segLeft = Math.min(s[0], e[0]);
        double segRight = Math.max(s[0], e[0]);
        double overlap = Math.min(segRight, elem.x() + elem.width()) - Math.max(segLeft, elem.x());
        return overlap >= EDGE_COINCIDENCE_MIN_OVERLAP_PX;
    }

    private static boolean segmentHugsVerticalEdge(double[] s, double[] e, AssessmentNode elem) {
        double x = (s[0] + e[0]) / 2.0;
        double leftEdge = elem.x();
        double rightEdge = elem.x() + elem.width();
        boolean nearEdge = Math.abs(x - leftEdge) <= EDGE_COINCIDENCE_TOLERANCE_PX
                || Math.abs(x - rightEdge) <= EDGE_COINCIDENCE_TOLERANCE_PX;
        if (!nearEdge) return false;
        double segTop = Math.min(s[1], e[1]);
        double segBottom = Math.max(s[1], e[1]);
        double overlap = Math.min(segBottom, elem.y() + elem.height()) - Math.max(segTop, elem.y());
        return overlap >= EDGE_COINCIDENCE_MIN_OVERLAP_PX;
    }

    // ---- Assessor.Redesign M5: Hub-Port Allocation Quality ----

    /** Result of M5 hub-port quality computation. {@code viewAggregate} = min (worst) hub-face quality. */
    record HubPortQualityResult(double viewAggregate,
                                List<LayoutAssessmentResult.HubFaceDetail> perFaceDetails,
                                Set<String> lowQualityElementIds) {}

    private enum Face {LEFT, RIGHT, TOP, BOTTOM}

    /**
     * Computes per-element-face hub-port allocation quality (M5). For each element, groups its
     * incoming + outgoing connection terminal endpoints by face (LEFT/RIGHT/TOP/BOTTOM). Any
     * face with at least {@link #M5_FACE_GUARD_MIN_CONNECTIONS} connections is a "hub face"; its
     * quality is {@code distinctSlots / connectionsOnFace} (slot = Y for LEFT/RIGHT, X for
     * TOP/BOTTOM, equality within {@link #HUB_PORT_SLOT_TOLERANCE_PX}px).
     *
     * <p>View aggregate is the minimum (worst) of per-hub-face qualities — NOT the mean — so a
     * single degraded face on an otherwise-healthy hub is surfaced honestly rather than averaged
     * away (2026-06-14). When no hub face exists, returns
     * 1.0 (no defect signal). Per-face details are populated only when {@code includeViolatorIds}
     * is true; otherwise the list is empty (avoids unnecessary allocation in the common path).</p>
     *
     * <p>Spec live example: API Mgmt BOTTOM face on Oracle view — 4 connections all at slot
     * X=792 → 1 distinct slot / 4 connections = quality 0.25. Tier 2R severity (threshold 0.5).</p>
     */
    HubPortQualityResult computeHubPortQuality(
            List<AssessmentConnection> connections, List<AssessmentNode> layoutNodes,
            boolean includeViolatorIds) {
        Map<String, AssessmentNode> nodeById = new HashMap<>();
        for (AssessmentNode n : layoutNodes) {
            nodeById.put(n.id(), n);
        }
        // Per-element-face: list of along-face slot coordinates.
        Map<String, Map<Face, List<Double>>> facesByElement = new HashMap<>();
        for (AssessmentConnection conn : connections) {
            List<double[]> path = conn.pathPoints();
            if (path.size() < 2) continue;
            recordTerminal(facesByElement, nodeById.get(conn.sourceNodeId()),
                    path.get(path.size() == 2 ? 0 : 1));
            recordTerminal(facesByElement, nodeById.get(conn.targetNodeId()),
                    path.get(path.size() == 2 ? path.size() - 1 : path.size() - 2));
        }
        List<Double> hubFaceQualities = new ArrayList<>();
        List<LayoutAssessmentResult.HubFaceDetail> details = new ArrayList<>();
        Set<String> lowQualityIds = new HashSet<>();
        for (Map.Entry<String, Map<Face, List<Double>>> e : facesByElement.entrySet()) {
            String elemId = e.getKey();
            for (Map.Entry<Face, List<Double>> faceEntry : e.getValue().entrySet()) {
                List<Double> slots = faceEntry.getValue();
                if (slots.size() < M5_FACE_GUARD_MIN_CONNECTIONS) continue;
                int distinct = countDistinctSlots(slots);
                double quality = (double) distinct / slots.size();
                hubFaceQualities.add(quality);
                if (includeViolatorIds) {
                    details.add(new LayoutAssessmentResult.HubFaceDetail(
                            elemId, faceEntry.getKey().name(), slots.size(), distinct, quality));
                }
                if (quality < HUB_PORT_QUALITY_FAIR_THRESHOLD) {
                    lowQualityIds.add(elemId);
                }
            }
        }
        // (2026-06-14): the view aggregate is the
        // WORST hub face (min), NOT the mean across faces. The mean averaged a degraded face (e.g. a
        // one-sided egress fan-out at q≈0.71) together with healthy faces (q1.0), so a real one-sided
        // hub never reached the rating — "good" masked "fair" (live View-G IAM probe: RIGHT 7/5 q0.71
        // buoyed by LEFT 4/4 q1.0 → mean 0.86 "good"). min surfaces the degraded face honestly. A
        // legitimately-busy SYMMETRIC hub is unaffected (every face at the same quality → min == mean),
        // so this does not over-flag balanced hubs. M5 stays Tier-2R (capped at "fair"); see :1068.
        double aggregate = hubFaceQualities.isEmpty() ? 1.0
                : hubFaceQualities.stream().mapToDouble(Double::doubleValue).min().orElse(1.0);
        return new HubPortQualityResult(aggregate,
                includeViolatorIds ? details : List.of(),
                lowQualityIds);
    }

    /** Face + along-face slot coordinate for a terminal contribution to hub-port quality (M5). */
    private record TerminalSlot(Face face, double slot) {}

    private static void recordTerminal(Map<String, Map<Face, List<Double>>> facesByElement,
                                       AssessmentNode elem, double[] terminalBp) {
        if (elem == null || terminalBp == null) return;
        TerminalSlot ts = inferTerminalSlot(terminalBp, elem);
        if (ts == null) return;
        Map<Face, List<Double>> faces = facesByElement.computeIfAbsent(elem.id(), k -> new HashMap<>());
        List<Double> slots = faces.computeIfAbsent(ts.face(), k -> new ArrayList<>());
        slots.add(ts.slot());
    }

    /**
     * Determines the face + along-face slot for a terminal bendpoint (M5).
     *
     * <p>Three cases (Assessor.Redesign code-review H3+M2 fix, 2026-04-27):
     * <ul>
     *   <li>BP on the element's perimeter line → use the BP coordinate directly as the slot.</li>
     *   <li>BP strictly inside the element → return null (interior termination is an M2 defect;
     *       the visible face is ambiguous so it should not contribute to face counts).</li>
     *   <li>BP outside the element → compute the clip-point of the segment from element-center
     *       to BP with the element's rect; the face that segment exits identifies the visible
     *       face, and the clip-point's along-face coordinate is the slot. Without this, M5
     *       would silently skip non-orthogonal terminals (M1-flagged), under-reporting hub
     *       congestion on real models.</li>
     * </ul>
     * Returns null when the slot cannot be determined.</p>
     */
    static TerminalSlot inferTerminalSlot(double[] bp, AssessmentNode elem) {
        Face face = inferFace(bp, elem);
        if (face != null) {
            double slot = (face == Face.LEFT || face == Face.RIGHT) ? bp[1] : bp[0];
            return new TerminalSlot(face, slot);
        }
        if (isStrictlyInside(bp, elem)) {
            return null; // Interior — face ambiguous; caller treats as non-contributor.
        }
        return clipSegmentToFace(elem, bp);
    }

    /**
     * Determines which face a terminal bendpoint sits on, given the element rect. Returns
     * null when the bendpoint is not on or within the element's perimeter band.
     */
    private static Face inferFace(double[] bp, AssessmentNode elem) {
        double tol = PERIMETER_TOLERANCE_PX;
        double left = elem.x();
        double right = elem.x() + elem.width();
        double top = elem.y();
        double bottom = elem.y() + elem.height();
        boolean onLeft = Math.abs(bp[0] - left) <= tol;
        boolean onRight = Math.abs(bp[0] - right) <= tol;
        boolean onTop = Math.abs(bp[1] - top) <= tol;
        boolean onBottom = Math.abs(bp[1] - bottom) <= tol;
        boolean withinHorizontal = bp[0] >= left - tol && bp[0] <= right + tol;
        boolean withinVertical = bp[1] >= top - tol && bp[1] <= bottom + tol;
        if (onLeft && withinVertical) return Face.LEFT;
        if (onRight && withinVertical) return Face.RIGHT;
        if (onTop && withinHorizontal) return Face.TOP;
        if (onBottom && withinHorizontal) return Face.BOTTOM;
        return null;
    }

    /**
     * Computes the clip-point of the segment from the element's center to the (exterior) bendpoint,
     * matching Archi's perimeter clipping behaviour. Returns the visible-face slot the segment
     * exits through, or null if no valid intersection (degenerate case — center coincides with BP).
     */
    private static TerminalSlot clipSegmentToFace(AssessmentNode elem, double[] bp) {
        double cx = elem.x() + elem.width() / 2.0;
        double cy = elem.y() + elem.height() / 2.0;
        double dx = bp[0] - cx;
        double dy = bp[1] - cy;
        if (Math.abs(dx) < 1e-9 && Math.abs(dy) < 1e-9) return null;
        double left = elem.x();
        double right = elem.x() + elem.width();
        double top = elem.y();
        double bottom = elem.y() + elem.height();
        Face bestFace = null;
        double bestT = Double.POSITIVE_INFINITY;
        double bestSlot = 0.0;
        if (dx < -1e-9) {
            double t = (left - cx) / dx;
            double y = cy + t * dy;
            if (t > 0 && t < bestT && y >= top - PERIMETER_TOLERANCE_PX
                    && y <= bottom + PERIMETER_TOLERANCE_PX) {
                bestT = t; bestFace = Face.LEFT; bestSlot = y;
            }
        }
        if (dx > 1e-9) {
            double t = (right - cx) / dx;
            double y = cy + t * dy;
            if (t > 0 && t < bestT && y >= top - PERIMETER_TOLERANCE_PX
                    && y <= bottom + PERIMETER_TOLERANCE_PX) {
                bestT = t; bestFace = Face.RIGHT; bestSlot = y;
            }
        }
        if (dy < -1e-9) {
            double t = (top - cy) / dy;
            double x = cx + t * dx;
            if (t > 0 && t < bestT && x >= left - PERIMETER_TOLERANCE_PX
                    && x <= right + PERIMETER_TOLERANCE_PX) {
                bestT = t; bestFace = Face.TOP; bestSlot = x;
            }
        }
        if (dy > 1e-9) {
            double t = (bottom - cy) / dy;
            double x = cx + t * dx;
            if (t > 0 && t < bestT && x >= left - PERIMETER_TOLERANCE_PX
                    && x <= right + PERIMETER_TOLERANCE_PX) {
                bestT = t; bestFace = Face.BOTTOM; bestSlot = x;
            }
        }
        return bestFace == null ? null : new TerminalSlot(bestFace, bestSlot);
    }

    private static int countDistinctSlots(List<Double> slots) {
        List<Double> sorted = new ArrayList<>(slots);
        sorted.sort(Double::compare);
        int distinct = 0;
        Double last = null;
        for (Double s : sorted) {
            if (last == null || Math.abs(s - last) > HUB_PORT_SLOT_TOLERANCE_PX) {
                distinct++;
                last = s;
            }
        }
        return distinct;
    }

    // ---- Assessor.Redesign R8: Corridor-Utilisation (2026-05-03) ----

    /** R8: minimum parallel-segment length (px) to count as a corridor-traversal segment. */
    static final double R8_MIN_PARALLEL_SEGMENT_LENGTH_PX = 30.0;

    /** R8: clearance band (px) per side; mirrors {@code ChannelNudgingPass.MIN_CLEARANCE_PX}. */
    static final double R8_MIN_CLEARANCE_PX = 10.0;

    /** R8: axis-parallel tolerance (px); Archi int-snaps bendpoints so 1.0 is sufficient. */
    static final double R8_AXIS_PARALLEL_TOLERANCE_PX = 1.0;

    /** Result of R8 corridor-utilisation computation. */
    record R8CorridorUtilisationResult(double viewAggregate,
                                        List<LayoutAssessmentResult.CorridorUtilisationDetail> perChannelDetails) {}

    /** Internal: one long parallel segment extracted from one connection's pathPoints. */
    private record R8Segment(int axis, double sharedCoord, double parStart, double parEnd,
                              String connectionId) {}

    /** Internal: a wall pair bracketing one R8 segment in the perpendicular axis. */
    private record R8WallPair(String lowId, String highId, double lowEdge, double highEdge) {}

    /**
     * Computes R8 corridor-utilisation score. Per-corridor
     * {@code spread_ratio = span / available} where occupants are long parallel segments
     * sharing the same wall pair; view aggregate is occupant-count-weighted mean. Returns
     * {@code 1.0} vacuously when no multi-occupant channel exists (mirrors
     * {@link #computeHubPortQuality}). Single-occupant channels and channels without
     * obstacle-bounded walls are skipped.
     */
    R8CorridorUtilisationResult computeR8CorridorUtilisation(
            List<AssessmentConnection> connections, List<AssessmentNode> layoutNodes,
            boolean includeViolatorIds) {
        // 1. Extract long parallel segments per connection (deterministic insertion order).
        List<R8Segment> segments = new ArrayList<>();
        for (AssessmentConnection conn : connections) {
            extractLongParallelSegments(conn, segments);
        }

        // 2. Group segments by corridor identity (wall pair). LinkedHashMap preserves
        // first-encounter order so per-channel detail emission and aggregate computation
        // are deterministic across runs (pinned-test
        // calibration requires deterministic algorithm output).
        Map<String, List<R8Segment>> occupantsByCorridor = new LinkedHashMap<>();
        Map<String, R8WallPair> wallsByCorridor = new HashMap<>();
        for (R8Segment seg : segments) {
            R8WallPair walls = findChannelWalls(seg, layoutNodes);
            if (walls == null) continue;
            String key = seg.axis() + "|" + walls.lowId() + "|" + walls.highId();
            occupantsByCorridor.computeIfAbsent(key, k -> new ArrayList<>()).add(seg);
            wallsByCorridor.putIfAbsent(key, walls);
        }

        // 3. Per-corridor spread_ratio + occupant-count-weighted view aggregate.
        List<LayoutAssessmentResult.CorridorUtilisationDetail> details = new ArrayList<>();
        double weightedSum = 0.0;
        int totalOccupants = 0;
        for (Map.Entry<String, List<R8Segment>> e : occupantsByCorridor.entrySet()) {
            List<R8Segment> occupants = e.getValue();
            if (occupants.size() < 2) continue;
            R8WallPair walls = wallsByCorridor.get(e.getKey());
            double available = walls.highEdge() - walls.lowEdge() - 2 * R8_MIN_CLEARANCE_PX;
            if (available <= 0) continue; // Degenerate corridor — walls within clearance band.

            double minCoord = Double.POSITIVE_INFINITY;
            double maxCoord = Double.NEGATIVE_INFINITY;
            for (R8Segment occ : occupants) {
                minCoord = Math.min(minCoord, occ.sharedCoord());
                maxCoord = Math.max(maxCoord, occ.sharedCoord());
            }
            double span = maxCoord - minCoord;
            // Clamp to [0.0, 1.0]: occupants spread wider than the post-clearance band
            // (span > available) indicates wall-hugging — already an M4 edge-coincidence
            // signal; R8 caps at 1.0 to keep the metric within its documented range.
            double spreadRatio = Math.min(span / available, 1.0);

            weightedSum += spreadRatio * occupants.size();
            totalOccupants += occupants.size();

            if (includeViolatorIds) {
                double occupantMidCoord = (minCoord + maxCoord) / 2.0;
                details.add(new LayoutAssessmentResult.CorridorUtilisationDetail(
                        occupants.get(0).axis(), occupantMidCoord,
                        walls.lowId(), walls.highId(),
                        occupants.size(), span, available, spreadRatio));
            }
        }
        double aggregate = totalOccupants > 0 ? weightedSum / totalOccupants : 1.0;
        return new R8CorridorUtilisationResult(aggregate, details);
    }

    /** axis = 0 for vertical (occupants share x), 1 for horizontal. */
    private void extractLongParallelSegments(AssessmentConnection conn, List<R8Segment> into) {
        List<double[]> path = conn.pathPoints();
        if (path == null || path.size() < 2) return;
        for (int i = 0; i < path.size() - 1; i++) {
            double[] p = path.get(i);
            double[] q = path.get(i + 1);
            double dx = Math.abs(q[0] - p[0]);
            double dy = Math.abs(q[1] - p[1]);
            if (dx < R8_AXIS_PARALLEL_TOLERANCE_PX && dy >= R8_MIN_PARALLEL_SEGMENT_LENGTH_PX) {
                into.add(new R8Segment(0, (p[0] + q[0]) / 2.0,
                        Math.min(p[1], q[1]), Math.max(p[1], q[1]), conn.id()));
            } else if (dy < R8_AXIS_PARALLEL_TOLERANCE_PX
                    && dx >= R8_MIN_PARALLEL_SEGMENT_LENGTH_PX) {
                into.add(new R8Segment(1, (p[1] + q[1]) / 2.0,
                        Math.min(p[0], q[0]), Math.max(p[0], q[0]), conn.id()));
            }
        }
    }

    /** Two-pass: prefer group walls (corridors are gaps between groups); fall back to any rect. */
    private R8WallPair findChannelWalls(R8Segment seg, List<AssessmentNode> layoutNodes) {
        R8WallPair groupWalls = scanWalls(seg, layoutNodes, true);
        if (groupWalls != null) return groupWalls;
        return scanWalls(seg, layoutNodes, false);
    }

    /** Returns null when either wall is missing (segment perimeter-bound on one side). */
    private R8WallPair scanWalls(R8Segment seg, List<AssessmentNode> layoutNodes,
                                  boolean groupsOnly) {
        String lowId = null, highId = null;
        double lowEdge = Double.NEGATIVE_INFINITY, highEdge = Double.POSITIVE_INFINITY;
        for (AssessmentNode n : layoutNodes) {
            if (n.isNote()) continue;
            if (groupsOnly && !n.isGroup()) continue;
            double left = n.x(), right = n.x() + n.width();
            double top = n.y(), bottom = n.y() + n.height();
            if (seg.axis() == 0) {
                // Vertical channel — walls are LEFT/RIGHT in x; perpendicular range is y.
                double overlap = Math.min(seg.parEnd(), bottom) - Math.max(seg.parStart(), top);
                if (overlap < 1.0) continue;
                if (right < seg.sharedCoord() && right > lowEdge) {
                    lowEdge = right;
                    lowId = n.id();
                }
                if (left > seg.sharedCoord() && left < highEdge) {
                    highEdge = left;
                    highId = n.id();
                }
            } else {
                // Horizontal channel — walls are TOP/BOTTOM in y; perpendicular range is x.
                double overlap = Math.min(seg.parEnd(), right) - Math.max(seg.parStart(), left);
                if (overlap < 1.0) continue;
                if (bottom < seg.sharedCoord() && bottom > lowEdge) {
                    lowEdge = bottom;
                    lowId = n.id();
                }
                if (top > seg.sharedCoord() && top < highEdge) {
                    highEdge = top;
                    highId = n.id();
                }
            }
        }
        if (lowId != null && highId != null) {
            return new R8WallPair(lowId, highId, lowEdge, highEdge);
        }
        return null;
    }

    // ---- Assessor.Redesign Successor D: parallelConnectionGap ----
    // (2026-05-12.)

    /**
     * Per-axis aggregate of nearest-parallel-overlapping-neighbour gaps for the
     * parallelConnectionGap metric. {@code mean / min / p10} are boxed because they
     * are {@code null} when {@code qualifyingSegmentCount == 0} (no segment had any
     * overlapping parallel neighbour in this axis). {@code violatorIds} is populated
     * only when {@code includeViolatorIds = true}; empty {@code Set.of()} otherwise.
     * A connection's id is added to {@code violatorIds} when at least one of its
     * segments produced a qualifying gap less than {@link #PARALLEL_GAP_NARROW_T2_PX}.
     */
    record ParallelConnectionGapAxis(int qualifyingSegmentCount, Double mean, Double min, Double p10,
                                      int narrowGapCount15, int narrowGapCount25, int narrowGapCount40,
                                      Set<String> violatorIds) {}

    /** Combined V (axis 0) + H (axis 1) result for {@link #computeParallelConnectionGap}. */
    record ParallelConnectionGapResult(ParallelConnectionGapAxis vAxis, ParallelConnectionGapAxis hAxis) {}

    /** Internal: one axis-aligned segment extracted from a connection's pathPoints. */
    private record ParallelGapSegment(int axis, double fixedCoord, double spanLow, double spanHigh,
                                       String connectionId) {}

    /**
     * Computes the parallelConnectionGap metric family (Successor D, 2026-05-12).
     *
     * <p>For each axis (V primary, H secondary): classify each bendpoint-pair segment
     * as V (Δx &lt; {@link #PARALLEL_GAP_AXIS_TOLERANCE_PX}, Δy ≥ tolerance) or H
     * (Δy &lt; tolerance, Δx ≥ tolerance), drop zero-length and diagonal pairs.
     * For each segment, find the nearest co-axial parallel segment whose span overlaps
     * (strict overlap &gt; 0); the gap is the perpendicular distance between fixed
     * coordinates (or 0 when the two fixed coordinates are identical and spans overlap).
     * Aggregate the per-segment gap list: arithmetic mean, min, 10th-percentile (linear
     * interpolation per Python {@code numpy.percentile}-equivalent), and narrow-gap
     * counts at thresholds 15/25/40 px.</p>
     *
     * <p>Calibration anchor: V4 manual gold view {@code id-3b2665e3ff6840708dbed2b3d1415613}
     * produced {@code V_p10 = 13.30} under {@link #PARALLEL_GAP_AXIS_TOLERANCE_PX} = 2.0;
     * monotonic owner-perception ordering on 4 reference views (V4 gold &gt; HH source
     * &gt; ST source) validates the metric as perception-aligned.</p>
     *
     * <p><b>INFORMATIONAL ONLY</b> — this metric does NOT contribute to
     * {@code computeRatingWithBreakdown} or {@code generateSuggestions}
     * (matches {@code corridorUtilisationScore} (R8) precedent). The narrow-corridor
     * defect class (5th unmeasured class) is
     * a structural-floor problem in the routing pipeline; rating-tying would mark all
     * views below the floor as poor regardless of agent improvements. Rating-tying is
     * deferred until Successor C (routing-pipeline narrow-corridor floor closure) ships.</p>
     *
     * @see #computeR8CorridorUtilisation closest sibling metric
     */
    ParallelConnectionGapResult computeParallelConnectionGap(
            List<AssessmentConnection> connections, boolean includeViolatorIds) {
        List<ParallelGapSegment> vSegs = new ArrayList<>();
        List<ParallelGapSegment> hSegs = new ArrayList<>();
        for (AssessmentConnection conn : connections) {
            extractParallelGapSegments(conn, vSegs, hSegs);
        }
        ParallelConnectionGapAxis vAxis = computeAxisAggregates(vSegs, includeViolatorIds);
        ParallelConnectionGapAxis hAxis = computeAxisAggregates(hSegs, includeViolatorIds);
        return new ParallelConnectionGapResult(vAxis, hAxis);
    }

    /**
     * Translates the Python reference {@code extract_segments} (compute_parallel_gap.py
     * lines 75-102): classify each consecutive bendpoint pair as V or H per the axis
     * tolerance and append to the appropriate per-axis list. Diagonal and zero-length
     * pairs are dropped.
     */
    private void extractParallelGapSegments(AssessmentConnection conn,
                                             List<ParallelGapSegment> vSegs,
                                             List<ParallelGapSegment> hSegs) {
        List<double[]> path = conn.pathPoints();
        if (path == null || path.size() < 2) return;
        double tol = PARALLEL_GAP_AXIS_TOLERANCE_PX;
        for (int i = 0; i < path.size() - 1; i++) {
            double[] p = path.get(i);
            double[] q = path.get(i + 1);
            double dx = Math.abs(q[0] - p[0]);
            double dy = Math.abs(q[1] - p[1]);
            if (dx < tol && dy < tol) continue;
            if (dy < tol && dx >= tol) {
                double fixedY = (p[1] + q[1]) / 2.0;
                hSegs.add(new ParallelGapSegment(1, fixedY,
                        Math.min(p[0], q[0]), Math.max(p[0], q[0]), conn.id()));
            } else if (dx < tol && dy >= tol) {
                double fixedX = (p[0] + q[0]) / 2.0;
                vSegs.add(new ParallelGapSegment(0, fixedX,
                        Math.min(p[1], q[1]), Math.max(p[1], q[1]), conn.id()));
            }
        }
    }

    /**
     * Translates Python {@code nearest_parallel_gaps} (lines 105-134) +
     * {@code percentile} (lines 137-149). For each segment, scan all other co-axial
     * segments; if their spans overlap strictly, gap = |Δfixed| (or 0 when fixed
     * coordinates coincide). Take the minimum across overlapping neighbours; segments
     * with no overlapping neighbour are non-qualifying and contribute nothing.
     */
    private ParallelConnectionGapAxis computeAxisAggregates(List<ParallelGapSegment> segs,
                                                              boolean includeViolatorIds) {
        Set<String> violatorIds = includeViolatorIds ? new HashSet<>() : Set.of();
        if (segs.isEmpty()) {
            return new ParallelConnectionGapAxis(0, null, null, null, 0, 0, 0, violatorIds);
        }
        List<Double> gaps = new ArrayList<>();
        // Parallel list: gaps.get(k) was produced by gapOwners.get(k); used for violator-id
        // attribution (avoids re-deriving owner ids from minimum-gap during aggregation).
        List<String> gapOwners = new ArrayList<>();
        int n = segs.size();
        for (int i = 0; i < n; i++) {
            ParallelGapSegment s = segs.get(i);
            Double bestGap = null;
            for (int j = 0; j < n; j++) {
                if (j == i) continue;
                ParallelGapSegment s2 = segs.get(j);
                double overlap = Math.min(s.spanHigh(), s2.spanHigh())
                        - Math.max(s.spanLow(), s2.spanLow());
                if (overlap <= 0.0) continue;
                double gap = (s.fixedCoord() == s2.fixedCoord())
                        ? 0.0
                        : Math.abs(s.fixedCoord() - s2.fixedCoord());
                if (bestGap == null || gap < bestGap) bestGap = gap;
            }
            if (bestGap == null) continue;
            gaps.add(bestGap);
            gapOwners.add(s.connectionId());
        }
        int m = gaps.size();
        if (m == 0) {
            return new ParallelConnectionGapAxis(0, null, null, null, 0, 0, 0, violatorIds);
        }
        double sum = 0.0;
        double minV = Double.POSITIVE_INFINITY;
        int n15 = 0, n25 = 0, n40 = 0;
        for (int k = 0; k < m; k++) {
            double g = gaps.get(k);
            sum += g;
            if (g < minV) minV = g;
            if (g < PARALLEL_GAP_NARROW_T1_PX) n15++;
            if (g < PARALLEL_GAP_NARROW_T2_PX) {
                n25++;
                if (includeViolatorIds) {
                    violatorIds.add(gapOwners.get(k));
                }
            }
            if (g < PARALLEL_GAP_NARROW_T3_PX) n40++;
        }
        double mean = sum / m;
        double p10 = percentileP10(gaps);
        return new ParallelConnectionGapAxis(m, mean, minV, p10, n15, n25, n40, violatorIds);
    }

    /**
     * Linear-interpolation 10th-percentile matching Python {@code numpy.percentile}
     * default ({@code linear}). For a single element returns that element. For n &ge; 2:
     * {@code k = (n-1) × 0.10}; {@code f = floor(k)}; {@code c = min(f+1, n-1)};
     * {@code result = xs[f] + (xs[c] − xs[f]) × (k − f)}.
     */
    private static double percentileP10(List<Double> values) {
        List<Double> xs = new ArrayList<>(values);
        Collections.sort(xs);
        int n = xs.size();
        if (n == 1) return xs.get(0);
        double k = (n - 1) * 0.10;
        int f = (int) Math.floor(k);
        int c = Math.min(f + 1, n - 1);
        return xs.get(f) + (xs.get(c) - xs.get(f)) * (k - f);
    }

    // ---- Boundary Violation Detection ----

    /** Result of boundary violation detection (adds violator IDs). */
    record BoundaryViolationResult(List<String> descriptions, Set<String> violatorIds) {}

    BoundaryViolationResult detectBoundaryViolations(List<AssessmentNode> nodes,
                                                      boolean collectViolatorIds) {
        Map<String, AssessmentNode> nodeMap = new HashMap<>();
        for (AssessmentNode node : nodes) {
            nodeMap.put(node.id(), node);
        }

        List<String> violations = new ArrayList<>();
        Set<String> violatorIds = collectViolatorIds ? new HashSet<>() : Set.of();
        for (AssessmentNode child : nodes) {
            if (child.parentId() == null) continue;

            AssessmentNode parent = nodeMap.get(child.parentId());
            if (parent == null) continue;

            // Both child and parent are in absolute coordinates,
            // so direct comparison is valid
            if (child.x() < parent.x()
                    || child.y() < parent.y()
                    || child.x() + child.width() > parent.x() + parent.width()
                    || child.y() + child.height() > parent.y() + parent.height()) {
                if (violations.size() < MAX_DESCRIPTIONS) {
                    violations.add("Element '" + child.id()
                            + "' extends outside parent group '" + parent.id() + "'");
                }
                if (collectViolatorIds) {
                    violatorIds.add(child.id());
                }
            }
        }
        return new BoundaryViolationResult(violations, violatorIds);
    }

    // ---- Connection Pass-Through Detection (Finding #3: exclude ancestor groups) ----

    /**
     * Result of pass-through detection separating cross-element and self-element counts.
     * The descriptions list contains both types for informational reporting.
     * The crossElementCount is used for rating penalty calculation.
     */
    record PassThroughResult(List<String> descriptions, int crossElementCount,
                             Set<String> violatorIds) {
        int totalCount() { return descriptions.size(); }
    }

    /**
     * Detects cross-element and self-element pass-throughs for all connections.
     *
     * <p><strong>Note:</strong> Cross-element violator IDs are always collected
     * unconditionally — {@code collectViolatorIds} is kept only for API compatibility
     * and is ignored inside this method. Unconditional collection is required so the
     * classification-precedence guard in {@link #countZigzags(List, Set, boolean)}
     * works in rating-only paths ({@code includeViolatorIds == false}).</p>
     *
     * @param connections        connections to inspect
     * @param nodes              all layout nodes (used to build ancestor/descendant exclusion sets)
     * @param collectViolatorIds ignored — kept for API stability; cross-element violator IDs
     *                           are always populated regardless of this flag
     * @return pass-through result; {@link PassThroughResult#crossElementCount()} used for
     *         rating; {@link PassThroughResult#violatorIds()} fed to
     *         {@link #countZigzags(List, Set, boolean)} as the precedence-guard skip-set
     */
    PassThroughResult detectPassThroughs(List<AssessmentConnection> connections,
                                     List<AssessmentNode> nodes,
                                     boolean collectViolatorIds) {
        // Build node map for ancestor lookups
        Map<String, AssessmentNode> nodeMap = new HashMap<>();
        for (AssessmentNode node : nodes) {
            nodeMap.put(node.id(), node);
        }

        List<String> descriptions = new ArrayList<>();
        int crossElementCount = 0;
        // Cross-element violator IDs are collected unconditionally so the classification
        // precedence guard in countZigzags() works regardless of collectViolatorIds (which
        // gates only the outer assess() enrichment block).
        Set<String> violatorIds = new HashSet<>();

        for (AssessmentConnection conn : connections) {
            boolean descriptionsCapped = descriptions.size() >= MAX_DESCRIPTIONS;

            // Collect IDs to exclude: source, target, ancestors, and ALL descendants of source/target
            Set<String> excludeIds = new HashSet<>();
            excludeIds.add(conn.sourceNodeId());
            excludeIds.add(conn.targetNodeId());
            excludeIds.addAll(getAncestorIds(conn.sourceNodeId(), nodeMap));
            excludeIds.addAll(getAncestorIds(conn.targetNodeId(), nodeMap));
            // Exclude all descendants of source/target — connections from a parent element
            // naturally pass through contained children/grandchildren; not a real pass-through
            excludeIds.addAll(getDescendantIds(conn.sourceNodeId(), nodes));
            excludeIds.addAll(getDescendantIds(conn.targetNodeId(), nodes));

            // Clip path from element centers to element edges (visual fidelity)
            List<double[]> clippedPath = clipPathToVisualEdges(
                    conn.pathPoints(),
                    nodeMap.get(conn.sourceNodeId()),
                    nodeMap.get(conn.targetNodeId()));

            for (AssessmentNode node : nodes) {
                // Skip source, target, ancestors, descendants, and groups (transparent containers)
                if (excludeIds.contains(node.id()) || node.isGroup()) {
                    continue;
                }

                if (pathPassesThroughNode(clippedPath, node)) {
                    if (!descriptionsCapped) {
                        descriptions.add("Connection '" + conn.id()
                                + "' passes through element '" + node.id() + "'");
                    }
                    crossElementCount++;
                    violatorIds.add(conn.id());
                    break; // Only report each connection once per element
                }
            }

            // Self-element pass-through detection: track in descriptions but NOT count
            // as cross-element. Two complementary checks:
            //   nonTerminalPassesThroughNode — clipped path, intermediate segments only
            //     (terminal segments naturally touch the endpoints by design).
            //   terminalSegmentOverPenetrates — unclipped path, terminal point only
            //     (stored final/first bendpoints past element center are pathological
            //      and missed by nonTerminalPassesThroughNode, which excludes the
            //      terminal segment from its loop window).
            if (!descriptionsCapped && clippedPath.size() >= 3) {
                AssessmentNode tgtNode = nodeMap.get(conn.targetNodeId());
                if (tgtNode != null && !tgtNode.isGroup()) {
                    boolean tgtNonTermHit = nonTerminalPassesThroughNode(clippedPath, tgtNode, true);
                    boolean tgtOverPenetrate = terminalSegmentOverPenetrates(conn.pathPoints(), tgtNode, true);
                    if (tgtNonTermHit || tgtOverPenetrate) {
                        descriptions.add("Connection '" + conn.id()
                                + "' routes through its own target element '" + tgtNode.id() + "'");
                    }
                }

                AssessmentNode srcNode = nodeMap.get(conn.sourceNodeId());
                if (srcNode != null && !srcNode.isGroup()
                        && descriptions.size() < MAX_DESCRIPTIONS) {
                    boolean srcNonTermHit = nonTerminalPassesThroughNode(clippedPath, srcNode, false);
                    boolean srcOverPenetrate = terminalSegmentOverPenetrates(conn.pathPoints(), srcNode, false);
                    if (srcNonTermHit || srcOverPenetrate) {
                        descriptions.add("Connection '" + conn.id()
                                + "' routes through its own source element '" + srcNode.id() + "'");
                    }
                }
            }
        }
        return new PassThroughResult(descriptions, crossElementCount, violatorIds);
    }

    /**
     * Checks if non-terminal segments of a path pass through a node.
     * For target elements, skips the last segment (which naturally enters the target).
     * For source elements, skips the first segment (which naturally exits the source).
     *
     * @param path     clipped path points
     * @param node     the source or target element to check
     * @param isTarget true if checking target element (skip last segment),
     *                 false if checking source element (skip first segment)
     * @return true if a non-terminal segment passes through the node
     */
    boolean nonTerminalPassesThroughNode(List<double[]> path, AssessmentNode node,
                                          boolean isTarget) {
        double insetX = node.x() + SELF_ELEMENT_INSET;
        double insetY = node.y() + SELF_ELEMENT_INSET;
        double insetW = node.width() - 2 * SELF_ELEMENT_INSET;
        double insetH = node.height() - 2 * SELF_ELEMENT_INSET;
        if (insetW <= 0 || insetH <= 0) return false;

        // For target: check segments 0..n-3 (skip last segment n-2..n-1)
        // For source: check segments 1..n-2 (skip first segment 0..1)
        int start = isTarget ? 0 : 1;
        int end = isTarget ? path.size() - 2 : path.size() - 1;

        for (int i = start; i < end; i++) {
            if (lineSegmentIntersectsRect(
                    path.get(i)[0], path.get(i)[1],
                    path.get(i + 1)[0], path.get(i + 1)[1],
                    insetX, insetY, insetW, insetH)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the connection's stored (pre-clip) path penetrates its own
     * source or target element body STRICTLY past the element's center along the
     * dominant entry axis. This catches terminal-segment over-penetration that
     * {@link #nonTerminalPassesThroughNode} misses because that method excludes
     * the terminal segment from its loop window (a natural-approach optimisation
     * that incorrectly suppresses detection when the stored terminal point sits
     * deep inside the element body).
     *
     * <p>For target-side checks ({@code isTarget=true}), the predicate fires when
     * the stored final point is inside the target body and lies STRICTLY past target
     * center along the segment's dominant entry axis. For source-side checks
     * ({@code isTarget=false}), it fires when the stored first point is inside the
     * source body and lies STRICTLY past source center along the segment's
     * dominant exit axis.
     *
     * <p>Strictness ({@code >} / {@code <}, not {@code >=} / {@code <=}) is required
     * because the universal Archi connection-anchor convention places the stored
     * first/last bendpoint AT the element center ({@link #clipPathToVisualEdges}
     * later transforms these to element edges for visual rendering). A non-strict
     * comparison would over-trigger on every center-anchored 3+ point path. The
     * detected pattern is therefore "terminal point past center" — i.e. the stored
     * bendpoint sits in the element's far half relative to the entry/exit direction,
     * which is pathological (no natural routing produces a stored bendpoint past
     * element center on the wrong side of the connection's natural anchor).
     *
     * @param unclippedPath stored path points (pre-clip — clipPathToVisualEdges NOT applied)
     * @param node          the source or target element to check
     * @param isTarget      true if checking target element (final point), false if source (first point)
     * @return true if the terminal point over-penetrates the element body past center
     */
    boolean terminalSegmentOverPenetrates(List<double[]> unclippedPath, AssessmentNode node,
                                          boolean isTarget) {
        if (unclippedPath.size() < 2) return false;
        double[] terminalPoint = isTarget
                ? unclippedPath.get(unclippedPath.size() - 1)
                : unclippedPath.get(0);
        double[] otherPoint = isTarget
                ? unclippedPath.get(unclippedPath.size() - 2)
                : unclippedPath.get(1);
        double tx = terminalPoint[0];
        double ty = terminalPoint[1];
        double minX = node.x();
        double maxX = node.x() + node.width();
        double minY = node.y();
        double maxY = node.y() + node.height();
        // Terminal must be inside element body (boundary inclusive)
        if (tx < minX || tx > maxX || ty < minY || ty > maxY) return false;
        double centerX = node.x() + node.width() / 2.0;
        double centerY = node.y() + node.height() / 2.0;
        double dx = Math.abs(tx - otherPoint[0]);
        double dy = Math.abs(ty - otherPoint[1]);
        // Over-penetration: terminal lies STRICTLY past element center along
        // the dominant entry axis. Strict inequalities preserve the
        // center-anchor convention (terminal AT center → not detected).
        if (dx >= dy) {
            // Horizontal-dominant. If approaching from west of terminal
            // (otherPoint.x < tx), over-penetration iff tx > centerX.
            // If approaching from east (otherPoint.x > tx), iff tx < centerX.
            return otherPoint[0] < tx ? tx > centerX : tx < centerX;
        } else {
            // Vertical-dominant.
            return otherPoint[1] < ty ? ty > centerY : ty < centerY;
        }
    }

    private boolean pathPassesThroughNode(List<double[]> path, AssessmentNode node) {
        // Shrink obstacle rect by PASS_THROUGH_INSET to absorb corner-arc imprecision
        double insetX = node.x() + PASS_THROUGH_INSET;
        double insetY = node.y() + PASS_THROUGH_INSET;
        double insetW = node.width() - 2 * PASS_THROUGH_INSET;
        double insetH = node.height() - 2 * PASS_THROUGH_INSET;
        if (insetW <= 0 || insetH <= 0) return false; // Element too small after inset

        for (int i = 0; i < path.size() - 1; i++) {
            if (lineSegmentIntersectsRect(
                    path.get(i)[0], path.get(i)[1],
                    path.get(i + 1)[0], path.get(i + 1)[1],
                    insetX, insetY, insetW, insetH)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Clips path endpoints from element centers to element perimeters.
     *
     * <p>Archi uses OrthogonalAnchor (default) which projects the reference point's
     * coordinate onto the nearest edge — fundamentally different from ChopboxAnchor's
     * ray-intersection approach. BendpointConnectionRouter uses the first bendpoint
     * as reference for the source anchor and the last bendpoint for the target anchor;
     * without bendpoints it falls back to the opposite endpoint's center.</p>
     */
    List<double[]> clipPathToVisualEdges(List<double[]> path,
                                          AssessmentNode srcNode,
                                          AssessmentNode tgtNode) {
        if (path.size() < 2) return path;

        List<double[]> clipped = new ArrayList<>(path);
        int last = path.size() - 1;

        // Reference for source: first bendpoint if exists, else target center
        double[] srcRef = path.size() > 2 ? path.get(1) : path.get(last);
        // Reference for target: last bendpoint if exists, else source center
        double[] tgtRef = path.size() > 2 ? path.get(last - 1) : path.get(0);

        if (srcNode != null) {
            double[] exit = orthogonalExitPoint(
                    srcNode.x(), srcNode.y(), srcNode.width(), srcNode.height(),
                    srcRef[0], srcRef[1]);
            if (exit != null) {
                clipped.set(0, exit);
            }
        }

        if (tgtNode != null) {
            double[] entry = orthogonalExitPoint(
                    tgtNode.x(), tgtNode.y(), tgtNode.width(), tgtNode.height(),
                    tgtRef[0], tgtRef[1]);
            if (entry != null) {
                clipped.set(last, entry);
            }
        }

        return clipped;
    }

    /**
     * Computes the perimeter exit point using Archi's OrthogonalAnchor model.
     *
     * <p>If the reference point's x or y falls within the element bounds,
     * the exit projects that coordinate onto the nearest edge (orthogonal exit).
     * For diagonal references (both x and y outside bounds), falls back to
     * ChopboxAnchor-style ray intersection since both anchors produce similar
     * results in corner zones.</p>
     */
    double[] orthogonalExitPoint(double rx, double ry, double rw, double rh,
                                  double refX, double refY) {
        double left = rx, right = rx + rw, top = ry, bottom = ry + rh;
        double cx = rx + rw / 2, cy = ry + rh / 2;

        boolean xInside = refX >= left && refX <= right;
        boolean yInside = refY >= top && refY <= bottom;

        if (xInside && !yInside) {
            // Reference directly above or below — exit from top/bottom edge at ref.x
            return new double[]{refX, refY < top ? top : bottom};
        } else if (!xInside && yInside) {
            // Reference directly left or right — exit from left/right edge at ref.y
            return new double[]{refX < left ? left : right, refY};
        } else if (!xInside) {
            // Diagonal — use ray intersection from center toward reference (ChopboxAnchor fallback)
            return rectExitPoint(cx, cy, refX, refY, rx, ry, rw, rh);
        }
        // Reference inside element — return center (degenerate case)
        return new double[]{cx, cy};
    }

    /**
     * Finds where a ray from (x1,y1) toward (x2,y2) exits the given rectangle.
     * Assumes (x1,y1) is inside the rectangle. Returns the exit point,
     * or null if the ray is degenerate (zero length).
     * Used as fallback for diagonal OrthogonalAnchor zones.
     */
    double[] rectExitPoint(double x1, double y1, double x2, double y2,
                            double rx, double ry, double rw, double rh) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        if (Math.abs(dx) < 1e-10 && Math.abs(dy) < 1e-10) return null;

        double tExit = Double.MAX_VALUE;

        if (Math.abs(dx) > 1e-10) {
            // Right edge
            double t = (rx + rw - x1) / dx;
            if (t > 1e-10) {
                double yAt = y1 + t * dy;
                if (yAt >= ry && yAt <= ry + rh && t < tExit) tExit = t;
            }
            // Left edge
            t = (rx - x1) / dx;
            if (t > 1e-10) {
                double yAt = y1 + t * dy;
                if (yAt >= ry && yAt <= ry + rh && t < tExit) tExit = t;
            }
        }
        if (Math.abs(dy) > 1e-10) {
            // Bottom edge
            double t = (ry + rh - y1) / dy;
            if (t > 1e-10) {
                double xAt = x1 + t * dx;
                if (xAt >= rx && xAt <= rx + rw && t < tExit) tExit = t;
            }
            // Top edge
            t = (ry - y1) / dy;
            if (t > 1e-10) {
                double xAt = x1 + t * dx;
                if (xAt >= rx && xAt <= rx + rw && t < tExit) tExit = t;
            }
        }

        if (tExit == Double.MAX_VALUE) return null;
        return new double[]{x1 + tExit * dx, y1 + tExit * dy};
    }

    /**
     * Tests if a line segment intersects an axis-aligned rectangle.
     * Delegates to {@link GeometryUtils#lineSegmentIntersectsRect(double, double, double, double, double, double, double, double)}.
     */
    static boolean lineSegmentIntersectsRect(double x1, double y1, double x2, double y2,
                                              double rx, double ry, double rw, double rh) {
        return GeometryUtils.lineSegmentIntersectsRect(x1, y1, x2, y2, rx, ry, rw, rh);
    }

    // ---- Off-Canvas Detection ----

    List<String> detectOffCanvas(List<AssessmentNode> nodes) {
        List<String> warnings = new ArrayList<>();
        for (AssessmentNode node : nodes) {
            if (warnings.size() >= MAX_DESCRIPTIONS) break;

            if (node.x() < 0 || node.y() < 0) {
                warnings.add("Element '" + node.id()
                        + "' is at negative coordinates (" + (int) node.x()
                        + ", " + (int) node.y() + ")");
            } else if (node.x() > OFF_CANVAS_THRESHOLD || node.y() > OFF_CANVAS_THRESHOLD
                    || node.x() + node.width() > OFF_CANVAS_THRESHOLD
                    || node.y() + node.height() > OFF_CANVAS_THRESHOLD) {
                warnings.add("Element '" + node.id()
                        + "' extends beyond canvas bounds at (" + (int) node.x()
                        + ", " + (int) node.y() + ")");
            }
        }
        return warnings;
    }

    // ---- Label Overlap Detection ----

    // Keep in sync with LabelClearance.CHAR_WIDTH etc.
    // (duplicated due to architecture boundary: model vs model.routing)
    /** Estimated character width in pixels (Archi's default ~11pt font). */
    static final double LABEL_CHAR_WIDTH = 8.0;
    /** Estimated character height in pixels. */
    static final double LABEL_CHAR_HEIGHT = 14.0;
    /** Horizontal padding around label text. */
    static final double LABEL_PADDING_X = 10.0;
    /** Vertical padding around label text. */
    static final double LABEL_PADDING_Y = 6.0;
    /** Inset margin applied to label bounds before overlap checks.
     *  Labels must overlap by at least this much on each side to count.
     *  Prevents false positives from estimated bounding boxes barely touching. */
    static final double LABEL_OVERLAP_INSET = 10.0;

    /** Proximity threshold in pixels for label-to-element and label-to-label near-miss detection.
     *  Labels within this distance of an element or another label (but not technically overlapping
     *  after inset) are flagged as proximity issues. */
    static final double LABEL_PROXIMITY_THRESHOLD = 5.0;

    record LabelBounds(double x, double y, double width, double height, String connectionId) {}

    record LabelOverlapResult(int count, List<String> descriptions, int shortSegmentCount) {
        /** Backward-compatible constructor without shortSegmentCount. */
        LabelOverlapResult(int count, List<String> descriptions) {
            this(count, descriptions, 0);
        }
    }

    /**
     * Estimates the bounding box of a connection label based on its text position
     * along the path. Position 0=source (15%), 1=middle (50%), 2=target (85%).
     * Returns null if labelText is empty or path has fewer than 2 points.
     */
    LabelBounds estimateLabelBounds(AssessmentConnection conn) {
        String label = conn.labelText();
        if (label == null || label.isEmpty()) {
            return null;
        }
        List<double[]> path = conn.pathPoints();
        if (path.size() < 2) {
            return null;
        }

        double labelWidth = label.length() * LABEL_CHAR_WIDTH + LABEL_PADDING_X;
        double labelHeight = LABEL_CHAR_HEIGHT + LABEL_PADDING_Y;

        // Compute total path length
        double totalLength = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            double dx = path.get(i + 1)[0] - path.get(i)[0];
            double dy = path.get(i + 1)[1] - path.get(i)[1];
            totalLength += Math.sqrt(dx * dx + dy * dy);
        }

        if (totalLength < 1.0) {
            return null;
        }

        // Determine position along path
        double fraction;
        switch (conn.textPosition()) {
            case 0:  fraction = 0.15; break; // source
            case 2:  fraction = 0.85; break; // target
            default: fraction = 0.50; break; // middle (default)
        }

        double targetDist = totalLength * fraction;

        // Walk path to find the point at targetDist
        double accumulated = 0;
        double cx = path.get(0)[0];
        double cy = path.get(0)[1];

        for (int i = 0; i < path.size() - 1; i++) {
            double dx = path.get(i + 1)[0] - path.get(i)[0];
            double dy = path.get(i + 1)[1] - path.get(i)[1];
            double segLen = Math.sqrt(dx * dx + dy * dy);
            if (accumulated + segLen >= targetDist) {
                double remaining = targetDist - accumulated;
                double t = (segLen > 0) ? remaining / segLen : 0;
                cx = path.get(i)[0] + dx * t;
                cy = path.get(i)[1] + dy * t;
                break;
            }
            accumulated += segLen;
        }

        // Center label at the computed point
        return new LabelBounds(
                cx - labelWidth / 2, cy - labelHeight / 2,
                labelWidth, labelHeight, conn.id());
    }

    /**
     * Counts label overlaps: labels overlapping nodes and labels overlapping other labels.
     */
    LabelOverlapResult countLabelOverlaps(List<AssessmentConnection> connections,
                                           List<AssessmentNode> nodes) {
        List<LabelBounds> allLabels = new ArrayList<>();
        for (AssessmentConnection conn : connections) {
            LabelBounds lb = estimateLabelBounds(conn);
            if (lb != null) {
                allLabels.add(lb);
            }
        }

        int count = 0;
        List<String> descriptions = new ArrayList<>();

        // Build node map for ancestor/descendant lookups
        Map<String, AssessmentNode> nodeMap = new HashMap<>();
        for (AssessmentNode node : nodes) {
            nodeMap.put(node.id(), node);
        }

        // Build per-connection exclusion sets: source, target, ancestors, descendants
        // (same logic as detectPassThroughs — labels naturally sit within ancestor groups)
        Map<String, Set<String>> connExcludeMap = new HashMap<>();
        for (AssessmentConnection conn : connections) {
            Set<String> excludeIds = new HashSet<>();
            excludeIds.add(conn.sourceNodeId());
            excludeIds.add(conn.targetNodeId());
            excludeIds.addAll(getAncestorIds(conn.sourceNodeId(), nodeMap));
            excludeIds.addAll(getAncestorIds(conn.targetNodeId(), nodeMap));
            excludeIds.addAll(getDescendantIds(conn.sourceNodeId(), nodes));
            excludeIds.addAll(getDescendantIds(conn.targetNodeId(), nodes));
            connExcludeMap.put(conn.id(), excludeIds);
        }

        // Check label-node overlaps and proximity (skip source, target, ancestors, descendants, and groups)
        // Apply inset margin to label bounds to avoid false positives from estimation error
        for (LabelBounds label : allLabels) {
            Set<String> excludeIds = connExcludeMap.getOrDefault(label.connectionId(), Set.of());
            for (AssessmentNode node : nodes) {
                // Skip source, target, ancestors, descendants, and groups (transparent containers)
                if (excludeIds.contains(node.id()) || node.isGroup()) {
                    continue;
                }
                if (insetRectOverlap(label, node.x(), node.y(), node.width(), node.height())) {
                    count++;
                    if (descriptions.size() < MAX_DESCRIPTIONS) {
                        descriptions.add("Label on connection '" + label.connectionId()
                                + "' overlaps element '" + node.id() + "'");
                    }
                } else if (isWithinProximity(label, node.x(), node.y(), node.width(), node.height())) {
                    // Label-to-element proximity detection
                    count++;
                    if (descriptions.size() < MAX_DESCRIPTIONS) {
                        descriptions.add("Label on connection '" + label.connectionId()
                                + "' is too close to element '" + node.id() + "'");
                    }
                }
            }
        }

        // Check label-label overlaps and near-misses (apply inset to both labels)
        for (int i = 0; i < allLabels.size(); i++) {
            for (int j = i + 1; j < allLabels.size(); j++) {
                LabelBounds a = allLabels.get(i);
                LabelBounds b = allLabels.get(j);
                if (insetRectOverlap(a, b.x(), b.y(), b.width(), b.height())) {
                    count++;
                    if (descriptions.size() < MAX_DESCRIPTIONS) {
                        descriptions.add("Label on connection '" + a.connectionId()
                                + "' overlaps label on connection '" + b.connectionId() + "'");
                    }
                } else if (isWithinProximity(a, b.x(), b.y(), b.width(), b.height())) {
                    // Label-to-label near-miss detection
                    count++;
                    if (descriptions.size() < MAX_DESCRIPTIONS) {
                        descriptions.add("Label on connection '" + a.connectionId()
                                + "' is too close to label on connection '" + b.connectionId() + "'");
                    }
                }
            }
        }

        // Short-segment detection
        // When a label's hosting segment is shorter than the label width,
        // the label cannot fit regardless of position. Report specific guidance.
        int shortSegmentCount = 0;
        Map<String, AssessmentConnection> connMap = new HashMap<>();
        for (AssessmentConnection conn : connections) {
            connMap.put(conn.id(), conn);
        }
        for (LabelBounds label : allLabels) {
            AssessmentConnection conn = connMap.get(label.connectionId());
            if (conn == null) continue;
            List<double[]> path = conn.pathPoints();
            if (path.size() < 2) continue;

            double labelWidth = label.width();

            // Find the hosting segment (the segment containing the label center point)
            double fraction;
            switch (conn.textPosition()) {
                case 0:  fraction = 0.15; break;
                case 2:  fraction = 0.85; break;
                default: fraction = 0.50; break;
            }

            double totalLength = 0;
            for (int i = 0; i < path.size() - 1; i++) {
                double dx = path.get(i + 1)[0] - path.get(i)[0];
                double dy = path.get(i + 1)[1] - path.get(i)[1];
                totalLength += Math.sqrt(dx * dx + dy * dy);
            }
            double targetDist = totalLength * fraction;

            // Walk path to find hosting segment
            double accumulated = 0;
            for (int i = 0; i < path.size() - 1; i++) {
                double dx = path.get(i + 1)[0] - path.get(i)[0];
                double dy = path.get(i + 1)[1] - path.get(i)[1];
                double segLen = Math.sqrt(dx * dx + dy * dy);
                if (accumulated + segLen >= targetDist || i == path.size() - 2) {
                    // This is the hosting segment
                    boolean isHorizontal = Math.abs(dy) < 2.0;
                    boolean isVertical = Math.abs(dx) < 2.0;

                    if (isHorizontal && segLen < labelWidth) {
                        // Horizontal segment too short for label
                        shortSegmentCount++;
                        if (descriptions.size() < MAX_DESCRIPTIONS) {
                            String srcName = conn.sourceNodeId();
                            String tgtName = conn.targetNodeId();
                            descriptions.add("Label on connection '" + label.connectionId()
                                    + "' exceeds segment length — increase spacing between "
                                    + srcName + " and " + tgtName);
                        }
                    } else if (isVertical) {
                        // Vertical segment: check if all 3 positions produce overlaps
                        // (label optimizer already ran — if we still have an overlap for this connection,
                        // it means all positions were exhausted)
                        Set<String> excludeIds = connExcludeMap.getOrDefault(label.connectionId(), Set.of());
                        boolean hasOverlap = false;
                        for (AssessmentNode node : nodes) {
                            if (excludeIds.contains(node.id()) || node.isGroup()) continue;
                            if (insetRectOverlap(label, node.x(), node.y(), node.width(), node.height())
                                    || isWithinProximity(label, node.x(), node.y(), node.width(), node.height())) {
                                hasOverlap = true;
                                break;
                            }
                        }
                        if (hasOverlap) {
                            if (descriptions.size() < MAX_DESCRIPTIONS) {
                                descriptions.add("Label on connection '" + label.connectionId()
                                        + "' has no clear label position — consider repositioning nearby elements");
                            }
                        }
                    }
                    break;
                }
                accumulated += segLen;
            }
        }

        return new LabelOverlapResult(count, descriptions, shortSegmentCount);
    }

    /**
     * Checks if a label's inset bounding box overlaps another rectangle.
     * The label bounds are shrunk by LABEL_OVERLAP_INSET on each side to
     * avoid false positives from estimated bounding boxes barely touching.
     * The inset is capped at 1/3 of each dimension to prevent the bounds
     * from collapsing to zero (label height is typically only 20px).
     */
    private boolean insetRectOverlap(LabelBounds label,
                                      double x2, double y2, double w2, double h2) {
        double xInset = Math.min(LABEL_OVERLAP_INSET, label.width() / 3);
        double yInset = Math.min(LABEL_OVERLAP_INSET, label.height() / 3);
        double lx = label.x() + xInset;
        double ly = label.y() + yInset;
        double lw = label.width() - 2 * xInset;
        double lh = label.height() - 2 * yInset;
        if (lw <= 0 || lh <= 0) return false;
        return lx < x2 + w2 && lx + lw > x2 && ly < y2 + h2 && ly + lh > y2;
    }

    /**
     * Checks if a label's bounding box is within LABEL_PROXIMITY_THRESHOLD of another rectangle
     * without actually overlapping (after inset). This detects "near-miss" situations where
     * labels are too close to elements or other labels for comfortable reading.
     * <p>
     * Expands the target rectangle by the proximity threshold on each side, then checks
     * if the raw (non-inset) label bounds overlap the expanded rectangle.
     */
    private boolean isWithinProximity(LabelBounds label,
                                       double x2, double y2, double w2, double h2) {
        // Expand the target rectangle by the proximity threshold
        double ex = x2 - LABEL_PROXIMITY_THRESHOLD;
        double ey = y2 - LABEL_PROXIMITY_THRESHOLD;
        double ew = w2 + 2 * LABEL_PROXIMITY_THRESHOLD;
        double eh = h2 + 2 * LABEL_PROXIMITY_THRESHOLD;

        // Check if raw label bounds overlap the expanded rectangle
        return label.x() < ex + ew && label.x() + label.width() > ex
                && label.y() < ey + eh && label.y() + label.height() > ey;
    }

    // ---- Note Overlap Detection (informational, not penalizing) ----

    /** Result of note-overlap detection. Informational only — does not affect rating. */
    record NoteOverlapResult(int count, List<String> descriptions) {}

    // ---- Content bounding box ----

    /**
     * Computes the axis-aligned bounding box of all visual content.
     * Includes elements, groups, and notes — everything the user sees on the canvas.
     * Returns {@code null} if there are no nodes.
     */
    private ContentBounds computeContentBounds(List<AssessmentNode> allNodes) {
        if (allNodes.isEmpty()) {
            return null;
        }
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (AssessmentNode node : allNodes) {
            if (node.x() < minX) minX = node.x();
            if (node.y() < minY) minY = node.y();
            double right = node.x() + node.width();
            double bottom = node.y() + node.height();
            if (right > maxX) maxX = right;
            if (bottom > maxY) maxY = bottom;
        }
        return new ContentBounds(minX, minY, maxX - minX, maxY - minY);
    }

    /**
     * Detects overlaps between notes and non-note layout nodes.
     * These are informational only — they do NOT affect the quality rating.
     */
    NoteOverlapResult countNoteOverlaps(List<AssessmentNode> noteNodes,
                                         List<AssessmentNode> layoutNodes) {
        if (noteNodes.isEmpty()) {
            return new NoteOverlapResult(0, List.of());
        }
        int count = 0;
        List<String> descriptions = new ArrayList<>();
        for (AssessmentNode note : noteNodes) {
            for (AssessmentNode element : layoutNodes) {
                // Skip if note is a child of this group (contained notes are expected)
                if (element.isGroup() && note.parentId() != null
                        && note.parentId().equals(element.id())) {
                    continue;
                }
                if (rectanglesOverlap(note, element)) {
                    count++;
                    if (descriptions.size() < MAX_DESCRIPTIONS) {
                        String targetType = element.isGroup() ? "group" : "element";
                        descriptions.add("Note '" + note.id()
                                + "' overlaps " + targetType + " '" + element.id() + "'");
                    }
                }
            }
        }
        return new NoteOverlapResult(count, descriptions);
    }

    // ---- Informational Detection (label truncation, parent label obscured, image sibling overlap) ----

    /** Estimated type icon width in pixels (right-aligned in Archi elements). */
    static final double TYPE_ICON_WIDTH = 16.0;
    /** Estimated label area height for single-line labels. */
    static final double ESTIMATED_LABEL_HEIGHT = LABEL_CHAR_HEIGHT + LABEL_PADDING_Y; // 20px
    /** Estimated image icon size (width and height) for non-fill positions. */
    static final double IMAGE_ICON_SIZE = 24.0;

    record LabelTruncationResult(int count, List<String> descriptions) {}
    record ParentLabelObscuredResult(int count, List<String> descriptions) {}
    record ImageSiblingOverlapResult(int count, List<String> descriptions) {}

    /**
     * Detects element labels that are truncated after word wrapping.
     * Archi word-wraps labels, so a label wider than the element can still fit
     * if it wraps to multiple lines within the available height.
     * Truncation is detected when the estimated wrapped height exceeds the element height.
     * Skips groups, notes, and elements with null/empty names.
     *
     * <p><b>Affects the rating.</b> Since M6 (2026-04-26) the resulting count is wired as a
     * Tier-2R cap-fair metric: a nonzero count caps {@code routingTier} (and, unless layoutTier is
     * already worse, the overall rating) at "fair" — see {@code computeRoutingTierLevel} and the
     * {@code labelTruncations} breakdown entry. It is NOT informational-only (the previous Javadoc
     * said so — that was true earlier but has been false since M6).
     *
     * <p><b>Calibration note</b> (spike 2026-06-14): the
     * {@code width - TYPE_ICON_WIDTH} horizontal budget and the vertical wrap model were validated
     * against live Archi rendering and found fail-safe and accurate. In the short-box
     * (forced-single-line) regime probed — the View-G shape, a 150x26 box — Archi rendered the label
     * roughly 1.35x wider than the ElementSizer-measured single-line width
     * ({@link AssessmentNode#labelTextWidth()}); a label measured at 151px truncated in boxes up to
     * 200px wide and fit single-line only near 210px. So this predicate is, if anything, mildly
     * under-conservative — not over-conservative — in that regime. Do NOT relax it toward "pass when
     * {@code width >= labelTextWidth}": that would under-flag boxes Archi actually truncates (verified:
     * a 141px label in a 150px box still truncates because the type icon consumes ~16px). The real
     * fix for over-tight boxes is taller/wider geometry (let the label wrap), not a looser predicate.
     */
    LabelTruncationResult detectLabelTruncation(List<AssessmentNode> nodes) {
        int count = 0;
        List<String> descriptions = new ArrayList<>();
        for (AssessmentNode node : nodes) {
            if (node.isGroup() || node.isNote() || node.name() == null || node.name().isEmpty()) {
                continue;
            }
            double availableWidth = node.width() - TYPE_ICON_WIDTH;
            if (availableWidth <= 0) {
                continue;
            }
            double textWidth = node.labelTextWidth();
            if (textWidth <= 0 || textWidth <= availableWidth) {
                continue; // fits on single line
            }
            // Archi word-wraps labels — estimate whether wrapped text overflows vertically
            int estimatedLines = (int) Math.ceil(textWidth / availableWidth);
            double neededHeight = estimatedLines * LABEL_CHAR_HEIGHT + LABEL_PADDING_Y;
            if (neededHeight > node.height()) {
                count++;
                if (descriptions.size() < MAX_DESCRIPTIONS) {
                    descriptions.add(String.format(
                            "Element '%s' label (~%d lines, %.0fpx wide) may be truncated in %dx%d element at (%.0f,%.0f)",
                            node.name(), estimatedLines, textWidth,
                            (int) node.width(), (int) node.height(),
                            node.x(), node.y()));
                }
            }
        }
        return new LabelTruncationResult(count, descriptions);
    }

    /**
     * Detects parents whose label text area is overlapped by their first (topmost) child.
     *
     * <p><b>Affects the rating.</b> Since M6 (2026-04-26) the resulting count is promoted to
     * layout <b>Tier-1L</b> (critical, no cap): {@code computeRatingWithBreakdown} records
     * {@code parentLabelObscured} as {@code "poor"} when the count is nonzero and folds it into the
     * Tier-1L level, so a single hit drives {@code layoutRating} to "poor" and vetoes the overall
     * rating; it is also weighted {@code ×6} in the tier-weighted quality score ({@code tierWeightedScore}
     * in {@code ArchiModelAccessorImpl}). This is harsher than the sibling {@code labelTruncation} metric,
     * which only caps routing at "fair" (Tier-2R). It is NOT informational-only (the previous Javadoc
     * said so — that was true earlier but has been false since M6).
     */
    ParentLabelObscuredResult detectParentLabelObscuredByChild(List<AssessmentNode> nodes) {
        // Build parent→children map from parentId back-references
        Map<String, List<AssessmentNode>> childrenByParent = new LinkedHashMap<>();
        Map<String, AssessmentNode> nodeById = new LinkedHashMap<>();
        for (AssessmentNode node : nodes) {
            nodeById.put(node.id(), node);
            if (node.parentId() != null) {
                childrenByParent.computeIfAbsent(node.parentId(), k -> new ArrayList<>()).add(node);
            }
        }

        int count = 0;
        List<String> descriptions = new ArrayList<>();
        for (Map.Entry<String, List<AssessmentNode>> entry : childrenByParent.entrySet()) {
            AssessmentNode parent = nodeById.get(entry.getKey());
            if (parent == null || parent.name() == null || parent.name().isEmpty()) {
                continue;
            }
            // Find child with smallest absolute y
            List<AssessmentNode> children = entry.getValue();
            double minChildY = Double.MAX_VALUE;
            for (AssessmentNode child : children) {
                if (child.y() < minChildY) {
                    minChildY = child.y();
                }
            }
            // Estimate label height — doubles for multi-line wrapping
            double labelHeight = ESTIMATED_LABEL_HEIGHT;
            double availableWidth = parent.width() - TYPE_ICON_WIDTH;
            if (availableWidth > 0 && parent.labelTextWidth() > availableWidth) {
                labelHeight *= 2; // multi-line wrap
            }
            double labelBottom = parent.y() + labelHeight;
            if (minChildY < labelBottom) {
                count++;
                if (descriptions.size() < MAX_DESCRIPTIONS) {
                    double relativeChildY = minChildY - parent.y();
                    descriptions.add(String.format(
                            "Parent '%s' label obscured by child at y=%.0f (label needs %.0fpx, child starts at %.0fpx relative)",
                            parent.name(), minChildY, labelHeight, relativeChildY));
                }
            }
        }
        return new ParentLabelObscuredResult(count, descriptions);
    }

    /**
     * Detects elements with images whose image bounding box overlaps a sibling element.
     * Informational only — does NOT affect rating.
     */
    ImageSiblingOverlapResult detectImageSiblingOverlap(List<AssessmentNode> nodes) {
        // Build sibling groups: nodes with same parentId (null = top-level)
        Map<String, List<AssessmentNode>> siblingGroups = new LinkedHashMap<>();
        for (AssessmentNode node : nodes) {
            String key = node.parentId() != null ? node.parentId() : "__top__";
            siblingGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(node);
        }

        int count = 0;
        List<String> descriptions = new ArrayList<>();
        for (List<AssessmentNode> siblings : siblingGroups.values()) {
            for (AssessmentNode node : siblings) {
                if (node.imagePath() == null) continue;
                double[] imgBounds = estimateImageBounds(node);
                if (imgBounds == null) continue;

                for (AssessmentNode sibling : siblings) {
                    if (sibling.id().equals(node.id())) continue;
                    if (rectanglesOverlap(imgBounds[0], imgBounds[1], imgBounds[2], imgBounds[3],
                            sibling.x(), sibling.y(), sibling.width(), sibling.height())) {
                        count++;
                        if (descriptions.size() < MAX_DESCRIPTIONS) {
                            descriptions.add(String.format(
                                    "Element '%s' image (%s) overlapped by sibling '%s' at (%.0f,%.0f)",
                                    node.name() != null ? node.name() : node.id(),
                                    node.imagePosition(), sibling.id(),
                                    sibling.x(), sibling.y()));
                        }
                        break; // one overlap per image element is enough
                    }
                }
            }
        }
        return new ImageSiblingOverlapResult(count, descriptions);
    }

    /**
     * Estimates the absolute image bounding box for an element based on imagePosition.
     * Returns {x, y, width, height} in absolute coordinates, or null if position unknown.
     */
    private double[] estimateImageBounds(AssessmentNode node) {
        String pos = node.imagePosition();
        if (pos == null) return null;
        double ex = node.x(), ey = node.y(), ew = node.width(), eh = node.height();
        double iw = IMAGE_ICON_SIZE, ih = IMAGE_ICON_SIZE;

        return switch (pos) {
            case "fill" -> new double[]{ex, ey, ew, eh};
            case "top-left" -> new double[]{ex, ey, iw, ih};
            case "top-centre" -> new double[]{ex + ew / 2 - iw / 2, ey, iw, ih};
            case "top-right" -> new double[]{ex + ew - iw, ey, iw, ih};
            case "middle-left" -> new double[]{ex, ey + eh / 2 - ih / 2, iw, ih};
            case "middle-centre" -> new double[]{ex + ew / 2 - iw / 2, ey + eh / 2 - ih / 2, iw, ih};
            case "middle-right" -> new double[]{ex + ew - iw, ey + eh / 2 - ih / 2, iw, ih};
            case "bottom-left" -> new double[]{ex, ey + eh - ih, iw, ih};
            case "bottom-centre" -> new double[]{ex + ew / 2 - iw / 2, ey + eh - ih, iw, ih};
            case "bottom-right" -> new double[]{ex + ew - iw, ey + eh - ih, iw, ih};
            default -> null;
        };
    }

    /**
     * Checks if two rectangles overlap (axis-aligned, specified by x, y, width, height).
     */
    private boolean rectanglesOverlap(double x1, double y1, double w1, double h1,
                                      double x2, double y2, double w2, double h2) {
        return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2;
    }

    // ---- Suggestion Generation (Finding #7: performance warning, #11: named constants) ----

    private List<String> generateSuggestions(int overlaps, int crossings,
                                              double avgSpacing, int alignmentScore,
                                              int boundaryViolationCount, int offCanvasCount,
                                              int nodeCount, int labelOverlapCount,
                                              boolean hasGroups, int connectionCount,
                                              int coincidentSegmentCount,
                                              int nonOrthogonalTerminalCount,
                                              int shortSegmentCount,
                                              int containmentOverlapCount,
                                              int zeroBendpointNonOrthCount,
                                              int interiorTerminationCount,
                                              int zigzagCount,
                                              int connectionEdgeCoincidenceCount,
                                              double hubPortQualityScore) {
        List<String> suggestions = new ArrayList<>();

        // Finding #7: performance warning for large views
        if (nodeCount > LARGE_VIEW_WARNING_THRESHOLD) {
            suggestions.add("View has " + nodeCount + " elements (>" + LARGE_VIEW_WARNING_THRESHOLD
                    + ") — assessment metrics may be slow for very large views.");
        }

        // Group-aware suggestions.
        // Groups: suggest layout-within-group + auto-route-connections.
        // Non-grouped (flat or containment): auto-route-connections / auto-layout-and-route.
        // compute-layout (formerly layout-view) removed from all suggestion paths.
        if (hasGroups) {
            if (overlaps > 0) {
                suggestions.add("Found " + overlaps
                        + " overlapping element pairs — use layout-within-group"
                        + " with increased spacing to spread elements apart,"
                        + " then re-run auto-route-connections");
            }
            if (crossings > CROSSING_SUGGESTION_THRESHOLD) {
                double ratio = connectionCount > 0
                        ? (double) crossings / connectionCount : crossings;
                suggestions.add("Found " + crossings
                        + " edge crossings (" + String.format("%.1f", ratio)
                        + " per connection) — increase element spacing within groups"
                        + " using layout-within-group and re-run auto-route-connections");
            }
            if (avgSpacing < SPACING_SUGGESTION_THRESHOLD && overlaps == 0) {
                suggestions.add("Average spacing is only " + Math.round(avgSpacing)
                        + "px — use adjust-view-spacing to increase gaps and improve"
                        + " routing quality, or manually increase spacing with"
                        + " layout-within-group then re-run auto-route-connections");
            }
        } else {
            // Flat or containment view: suggest layout-flat-view / auto-route / auto-layout-and-route
            if (overlaps > 0) {
                suggestions.add("Found " + overlaps
                        + " overlapping element pairs — use layout-flat-view to"
                        + " reposition elements with proper spacing, then"
                        + " auto-route-connections. Or use auto-layout-and-route"
                        + " for fully algorithmic positioning");
            }
            if (crossings > CROSSING_SUGGESTION_THRESHOLD) {
                suggestions.add("Found " + crossings
                        + " edge crossings — try auto-route-connections first"
                        + " (preserves positions). If crossings persist, use"
                        + " layout-flat-view with increased spacing to reposition"
                        + " elements, then re-route. Use auto-layout-and-route"
                        + " with targetRating as a last resort");
            }
            if (avgSpacing < SPACING_SUGGESTION_THRESHOLD && overlaps == 0) {
                suggestions.add("Average spacing is only " + Math.round(avgSpacing)
                        + "px — use layout-flat-view with increased spacing"
                        + " to reposition elements, then auto-route-connections");
            }
        }
        if (alignmentScore < ALIGNMENT_SUGGESTION_THRESHOLD) {
            if (hasGroups) {
                suggestions.add("Alignment score is " + alignmentScore
                        + "/100 — use layout-within-group to improve alignment within each group");
            } else {
                suggestions.add("Alignment score is " + alignmentScore
                        + "/100 — use auto-layout-and-route for uniform alignment");
            }
        }
        if (boundaryViolationCount > 0) {
            suggestions.add("Found " + boundaryViolationCount
                    + " elements extending outside their parent groups"
                    + " — resize groups or reposition elements");
        }
        if (offCanvasCount > 0) {
            suggestions.add("Found " + offCanvasCount
                    + " elements at negative or extreme coordinates"
                    + " — reposition to visible canvas area");
        }
        if (labelOverlapCount > 0) {
            if (hasGroups) {
                suggestions.add(labelOverlapCount + " connection labels overlap or are too close to elements or other labels"
                        + " — increase spacing within groups using layout-within-group"
                        + " and re-run auto-route-connections");
            } else {
                suggestions.add(labelOverlapCount + " connection labels overlap or are too close to elements or other labels"
                        + " — use auto-layout-and-route with increased spacing");
            }
        }

        // Short-segment label suggestion (separate from general label overlap)
        if (shortSegmentCount > 0) {
            suggestions.add(shortSegmentCount + " connection labels exceed available segment length"
                    + " — increase element spacing");
        }

        // Non-orthogonal terminal suggestion with ELK-aware text
        if (nonOrthogonalTerminalCount > 0) {
            String elkSuffix = " — these are straight-line connections typical of ELK layout;"
                    + " a full re-route would likely increase crossings."
                    + " Run auto-route-connections with mode='terminals-only' to rectify"
                    + " terminal segments without touching the routed body";
            if (zeroBendpointNonOrthCount == nonOrthogonalTerminalCount) {
                // All non-orth connections are zero-bendpoint (ELK straight-line signature)
                suggestions.add(nonOrthogonalTerminalCount + " connections have diagonal terminal segments"
                        + elkSuffix);
            } else if (zeroBendpointNonOrthCount > 0) {
                // Mixed: some zero-BP (ELK), some routed
                int routedCount = nonOrthogonalTerminalCount - zeroBendpointNonOrthCount;
                suggestions.add(zeroBendpointNonOrthCount + " connections have diagonal terminal segments"
                        + elkSuffix);
                suggestions.add(routedCount + " connections have diagonal terminal segments"
                        + " — re-run auto-route-connections (or use mode='terminals-only'"
                        + " to preserve the routed body) to improve orthogonality");
            } else {
                // No zero-BP: all are routed connections
                suggestions.add(nonOrthogonalTerminalCount + " connections have diagonal terminal segments"
                        + " — re-run auto-route-connections (or use mode='terminals-only'"
                        + " to preserve the routed body) to improve orthogonality");
            }
        }

        // Coincident segment suggestion
        if (coincidentSegmentCount > 0) {
            if (hasGroups) {
                suggestions.add(coincidentSegmentCount + " overlapping connection segments detected"
                        + " — use adjust-view-spacing to increase element spacing and re-route"
                        + " in a single call, or manually increase spacing with"
                        + " layout-within-group then re-run auto-route-connections");
            } else {
                suggestions.add(coincidentSegmentCount + " overlapping connection segments detected"
                        + " — increase element spacing or use auto-layout-and-route"
                        + " to separate coincident paths");
            }
        }

        // §10.4: Informational containment overlap note — clarifies that these are expected
        // ancestor-descendant overlaps (elements inside groups), not layout problems.
        if (containmentOverlapCount > 0) {
            suggestions.add(containmentOverlapCount
                    + " containment overlaps detected (expected — ancestor-descendant"
                    + " overlaps from elements inside groups, not layout problems). No action needed.");
        }

        // Assessor.Redesign M2: interior terminations.
        if (interiorTerminationCount > 0) {
            suggestions.add(interiorTerminationCount
                    + " connections terminate inside element bounds — check ChopboxAnchor"
                    + " face selection and re-run auto-route-connections");
        }

        // Assessor.Redesign M3: zigzag/reversal patterns.
        if (zigzagCount > 0) {
            suggestions.add(zigzagCount
                    + " connections have zigzag/reversal patterns — re-run"
                    + " auto-route-connections; PathStraightener.eliminateReversals or"
                    + " removeCollinearPoints may need investigation");
        }

        // Assessor.Redesign M4: connection-vs-element-edge coincidence.
        if (connectionEdgeCoincidenceCount > 0) {
            suggestions.add(connectionEdgeCoincidenceCount
                    + " connection segments hug element edges within "
                    + (int) EDGE_COINCIDENCE_TOLERANCE_PX
                    + "px — consider channel offset or increased element spacing");
        }

        // Assessor.Redesign M5: hub-port allocation quality.
        if (hubPortQualityScore < HUB_PORT_QUALITY_FAIR_THRESHOLD) {
            suggestions.add("Hub-port allocation quality is "
                    + String.format("%.2f", hubPortQualityScore)
                    + " (below " + HUB_PORT_QUALITY_FAIR_THRESHOLD
                    + ") — terminal allocator failing to distribute connections across face slots;"
                    + " inspect violatorIds.hubPortLowQuality for affected elements");
        }

        if (suggestions.isEmpty()) {
            suggestions.add("Layout quality is good — no immediate improvements needed.");
        }

        return suggestions;
    }

    /**
     * Builds element/group context from {@code layoutNodes} and
     * invokes {@link CoincidentSegmentDiagnostic#emit} to log a per-pair
     * categorization. Called only when {@code -Darchi.mcp.diag.coincident=true}.
     */
    private void emitCoincidentDiagnostic(List<AssessmentConnection> connections,
                                          List<AssessmentNode> layoutNodes) {
        // Lookup: node id → rect.
        Map<String, CoincidentSegmentDiagnostic.ElementRect> rectById = new HashMap<>();
        List<CoincidentSegmentDiagnostic.GroupRect> topLevelGroups = new ArrayList<>();
        for (AssessmentNode n : layoutNodes) {
            rectById.put(n.id(), new CoincidentSegmentDiagnostic.ElementRect(
                    n.id(), n.x(), n.y(), n.width(), n.height()));
            if (n.isGroup() && n.parentId() == null) {
                topLevelGroups.add(new CoincidentSegmentDiagnostic.GroupRect(
                        n.id(), n.x(), n.y(), n.width(), n.height()));
            }
        }

        Map<Integer, String> connIds = new HashMap<>();
        Map<Integer, CoincidentSegmentDiagnostic.ElementRect> sources = new HashMap<>();
        Map<Integer, CoincidentSegmentDiagnostic.ElementRect> targets = new HashMap<>();
        for (int i = 0; i < connections.size(); i++) {
            AssessmentConnection c = connections.get(i);
            connIds.put(i, c.id());
            sources.put(i, rectById.get(c.sourceNodeId()));
            targets.put(i, rectById.get(c.targetNodeId()));
        }

        CoincidentSegmentDiagnostic.emit(coincidentDetector, connections,
                connIds, sources, targets, topLevelGroups);
    }
}
