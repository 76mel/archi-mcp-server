package net.vheerden.archi.mcp.response.dto;

/**
 * Canonical {@link AutoRouteResultDto#nudgeBlockedReason} values (Story 14-12).
 *
 * <p>Codes are stable; once published, never renamed (only deprecated and
 * superseded by a new code). This is part of the MCP response contract.
 *
 * <p>Currently has one value; the class exists to host future blocking
 * reasons without forcing each addition to be a magic string at the
 * accessor call site.
 */
public final class AutoRouteBlockedReasons {

    private AutoRouteBlockedReasons() {}

    /**
     * autoNudge was blocked because the view has overlapping sibling
     * elements — degenerate geometry that the nudge re-routing pipeline
     * cannot resolve. Sibling-symmetric with
     * {@link StructuredWarningCodes#AUTO_NUDGE_SKIPPED_SIBLING_OVERLAP}.
     */
    public static final String SIBLING_OVERLAP = "sibling_overlap";
}
