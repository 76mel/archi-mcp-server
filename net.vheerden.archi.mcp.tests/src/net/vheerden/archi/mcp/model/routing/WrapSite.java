package net.vheerden.archi.mcp.model.routing;

/**
 * The five terminal-anchoring wrap sites enumerated for parameterised dispatch in
 * {@link ChopboxAnchorDegeneracyTest}. Each constant names exactly one
 * mutator that wraps a {@code path[0]} / {@code path[last]} mutation under
 * {@link TerminalAnchoring#preservesEndpoints}.
 *
 * <p>This enum is the cartesian dimension that lifts the 81-row generator matrix
 * to 5 × 81 = 405 parameterised assertions.
 */
public enum WrapSite {
    ELIMINATE_REVERSALS,
    COLLAPSE_BENDS,
    SNAP_TO_STRAIGHT,
    COLLAPSE_STAIRCASE_JOGS,
    APPLY_OFFSETS
}
