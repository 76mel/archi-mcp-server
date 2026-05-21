package net.vheerden.archi.mcp.response.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Structured warning emission for tool responses (Story
 * RoutingPreconditions.AutoRouteStructuredWarning, Row E).
 *
 * <p>Emitted in parallel with the existing free-text {@code warnings: List<String>}
 * field for back-compat — new consumers parse {@code structuredWarnings}; legacy
 * consumers continue to parse {@code warnings}.</p>
 *
 * <p>Canonical {@code code} values are defined as constants in
 * {@link StructuredWarningCodes}. Codes are stable; once published, never
 * renamed (only deprecated and superseded by a new code). This is part of the
 * MCP response contract.</p>
 *
 * @param code                   stable machine-parseable code (e.g.
 *                               {@code AUTO_NUDGE_SKIPPED_SIBLING_OVERLAP})
 * @param message                human-readable detail (also present verbatim in
 *                               the legacy {@code warnings: List<String>})
 * @param remediationTool        MCP tool name the caller should invoke to address
 *                               the warning (e.g. {@code layout-within-group});
 *                               omitted when empty
 * @param remediationViolatorIds element IDs that triggered the warning; omitted
 *                               when empty
 */
public record StructuredWarningDto(
        String code,
        String message,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        String remediationTool,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<String> remediationViolatorIds) {

    /** Compact constructor: null-guard the violator-IDs list. */
    public StructuredWarningDto {
        remediationViolatorIds = remediationViolatorIds != null
                ? remediationViolatorIds : List.of();
    }
}
