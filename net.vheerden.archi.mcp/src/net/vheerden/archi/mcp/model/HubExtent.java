package net.vheerden.archi.mcp.model;

/**
 * Pure-EMF-free immutable descriptor of the view's dominant hub element,
 * captured ONCE pre-loop by the accessor closure from the EXISTING
 * {@code detectHubElements(viewId)} read — derived from
 * existing hub-extent reads ONLY; NOT a new {@code LayoutQualityAssessor}
 * metric.
 *
 * <p>Two consumers in {@link SpacingControlLoop}:
 * <ol>
 *   <li><strong>The spacing-regime-position axis (hub sub-signal):</strong>
 *       a hub with a high fan-out but small bounds is the
 *       infeasible-input-geometry the reframe identifies (the ST clone's
 *       214×68 hub absorbing 7+ connections vs an HH-like ≥300×250 hub) —
 *       see {@link SpacingControlLoop#hubUnderSizedForFanOut(HubExtent)}.</li>
 *   <li><strong>The PASS-honest actionable diagnosis:</strong> the
 *       diagnosis names the violated precondition as "hub WxH vs its
 *       connection count" — these three fields are exactly that payload.</li>
 * </ol></p>
 *
 * <p>{@code null} (no {@code HubExtent} supplied) means the hub sub-signal is
 * ABSENT — combined with a {@link Double#NaN} {@code avgSpacingPx} the
 * density-aware discriminator is inert and the loop behaves byte-identically
 * to the 2-state back-off (pin preservation).</p>
 *
 * @param maxHubConnectionCount the connection count of the most-connected
 *                              ("hub") element on the view
 * @param hubWidthPx            that hub element's current width in pixels
 * @param hubHeightPx           that hub element's current height in pixels
 */
public record HubExtent(
        int maxHubConnectionCount,
        int hubWidthPx,
        int hubHeightPx) {
}
