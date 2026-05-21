package net.vheerden.archi.mcp.model.routing;

import java.util.List;

import net.vheerden.archi.mcp.model.RoutingRect;
import net.vheerden.archi.mcp.response.dto.AbsoluteBendpointDto;

/**
 * B71 wrap-site test helper. Collapses the "spy pattern over each of the
 * five wrap sites" into a single source-of-truth oracle that
 * {@link ChopboxAnchorDegeneracyTest} dispatches over the {@link WrapSite}
 * enum — all five wrap sites enforce the same predicate
 * ({@link TerminalAnchoring#preservesEndpoints}), so the helper can deliver
 * the wrap-site invariant uniformly without per-site reflection.
 *
 * <p>Per compose §12.2 amendment 3 the predicate is enforced exclusively at
 * {@code path[0]} / {@code path[last]} mutator sites; a hypothetical sixth
 * mutator that joins the rule auto-qualifies through the enum without test
 * scope renegotiation.
 */
public final class TerminalAnchoringAssertion {

    private TerminalAnchoringAssertion() {
        // Static helper — no instances.
    }

    /**
     * Returns the predicate verdict for the (row, wrap-site) pair. The
     * {@code site} parameter is the cartesian dispatch dimension; all five
     * wrap sites enforce the same predicate, so the same oracle answer is
     * returned regardless of {@code site}. Including {@code site} here keeps
     * the call site shape stable for AC-11-b's 5 × 81 = 405 assertion
     * matrix and ensures parameterised test names cite the wrap-site under
     * which each assertion is recorded.
     */
    public static boolean preservesEndpoints(
            WrapSite site,
            TerminalAnchoring sourceAnchoring, RoutingRect source, int[] sourceCenter,
            TerminalAnchoring targetAnchoring, RoutingRect target, int[] targetCenter,
            List<AbsoluteBendpointDto> path) {
        if (site == null) {
            throw new IllegalArgumentException("WrapSite must not be null");
        }
        return TerminalAnchoring.preservesEndpoints(
                sourceAnchoring, source, sourceCenter,
                targetAnchoring, target, targetCenter, path);
    }
}
