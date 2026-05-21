package net.vheerden.archi.mcp.model.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.vheerden.archi.mcp.model.RoutingRect;
import net.vheerden.archi.mcp.model.routing.EdgeAttachmentCalculator.Face;
import net.vheerden.archi.mcp.response.dto.AbsoluteBendpointDto;

/**
 * Perimeter-terminal immutability carrier for B71.
 *
 * <p>See {@code _bmad-output/implementation-artifacts/b71-q1-carrier-analysis.md §9.5}
 * for the design derivation. The record carries exactly one field — the {@link Face}
 * on which the terminal bendpoint is anchored — because every other piece of
 * information the predicate needs (axis, face-line coordinate, source center) can be
 * derived on demand from the connection's live source rect, keeping the predicate
 * axis-agnostic and free of coordinate bookkeeping bugs.
 *
 * <p>The predicate {@link #preservesTerminalAnchoring} is enforced
 * <strong>exclusively</strong> at {@code path[0]} / {@code path[last]} mutator sites
 * within {@link PathStraightener} and {@link CoincidentSegmentDetector#applyOffsets}.
 * Stages that legitimately shape terminal bendpoints by contract
 * (4.7h/k/m/o — {@code alignTerminalsWithCenter} family and
 * {@link ChannelNudgingPass}) are outside this predicate's domain by construction.
 */
public record TerminalAnchoring(Face face) {

    public enum Axis { X, Y }

    public Axis parallelAxis() {
        return (face == Face.LEFT || face == Face.RIGHT) ? Axis.Y : Axis.X;
    }

    public Axis orthogonalAxis() {
        return (face == Face.LEFT || face == Face.RIGHT) ? Axis.X : Axis.Y;
    }

    /**
     * Absolute-canvas coordinate of the face line, one pixel outside the source rect
     * (e.g., LEFT &rarr; {@code source.x() - 1}, RIGHT &rarr; {@code source.x() + source.width() + 1}).
     */
    public int lineCoordinate(RoutingRect source) {
        return switch (face) {
            case LEFT   -> source.x() - 1;
            case RIGHT  -> source.x() + source.width() + 1;
            case TOP    -> source.y() - 1;
            case BOTTOM -> source.y() + source.height() + 1;
        };
    }

    /**
     * Winston's axis-agnostic form (Q1 §9.5, compose §12).
     *
     * <p>Rejects paths whose {@code path[0]} is collinear with the source center on the
     * parallel axis <strong>and</strong> NOT on the face line — the ChopboxAnchor
     * degeneracy signature. Preserves legitimate B9-distributed slots (off-center on the
     * face) and classic centre-face exits (on-center on the face). Empty paths return
     * {@code true} (fast-path).
     */
    public static boolean preservesTerminalAnchoring(
            TerminalAnchoring before,
            RoutingRect source,
            int[] sourceCenter,
            List<AbsoluteBendpointDto> afterPath) {
        if (afterPath.isEmpty()) {
            return true;
        }
        AbsoluteBendpointDto bp0 = afterPath.get(0);
        Axis orthogonal = before.orthogonalAxis();
        int bp0Orthogonal = (orthogonal == Axis.X) ? bp0.x() : bp0.y();
        int faceLine      = before.lineCoordinate(source);
        // B72-c: bp[0] must remain on the face line. The original B71
        // degeneracy-only check allowed lateral displacement when bp0's
        // parallel coordinate was nudged off centerY by B72-c's
        // slot-at-center fix. No wrap site should ever drag bp[0] off
        // the perimeter.
        return bp0Orthogonal == faceLine;
    }

    /**
     * Convenience wrap-site helper: checks {@link #preservesTerminalAnchoring} on
     * both terminal ends of {@code afterPath}. The source-side check runs against
     * {@code afterPath.get(0)}; the target-side check runs against
     * {@code afterPath.get(last)} by reusing the source-side predicate on a
     * reversed view.
     *
     * <p>Either anchoring may be {@code null}, in which case that end is
     * unchecked — this is the test-compat path used by legacy overloads of the
     * five wrap sites in {@link PathStraightener} and
     * {@link CoincidentSegmentDetector#applyOffsets} so that pre-B71 callers
     * remain green when no anchoring is supplied.
     */
    public static boolean preservesEndpoints(
            TerminalAnchoring sourceAnchoring, RoutingRect source, int[] sourceCenter,
            TerminalAnchoring targetAnchoring, RoutingRect target, int[] targetCenter,
            List<AbsoluteBendpointDto> afterPath) {
        if (sourceAnchoring != null && source != null && sourceCenter != null) {
            if (!preservesTerminalAnchoring(sourceAnchoring, source, sourceCenter, afterPath)) {
                return false;
            }
        }
        if (targetAnchoring != null && target != null && targetCenter != null
                && !afterPath.isEmpty()) {
            List<AbsoluteBendpointDto> reversed = new ArrayList<>(afterPath);
            Collections.reverse(reversed);
            if (!preservesTerminalAnchoring(targetAnchoring, target, targetCenter, reversed)) {
                return false;
            }
        }
        return true;
    }
}
