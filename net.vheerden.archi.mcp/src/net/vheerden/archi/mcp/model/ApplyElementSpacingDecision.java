package net.vheerden.archi.mcp.model;

/**
 * Pure-unit-testable decision for the {@code apply-element-spacing-
 * recommendations} convenience tool: given the inputs the tool can compute
 * upstream, decides whether to short-circuit (and which envelope to return)
 * or to call {@code adjustViewSpacing}.
 *
 * <p>Extracted from {@link ArchiModelAccessorImpl#applyElementSpacingRecommendations}
 * specifically so the short-circuit guards (no groups / no groups with 2+
 * children / no connections / delta=0 / dryRun) can be exercised by JUnit
 * without an OSGi context. The production method calls
 * {@link #decide(int, int, int, boolean, boolean, boolean, boolean)} once and
 * dispatches on {@link #shouldCallAdjustViewSpacing()}.</p>
 *
 * <p>This pattern is the test-burden response to Sonnet 4.6 cross-model code
 * review action item [HIGH] 2026-05-04 — "Add accessor-level behavioral
 * assertions" — and aligns with {@code feedback_first_principles_routing.md}
 * rigor (architectural separation between the EMF-bound tool method and the
 * pure decision logic).</p>
 *
 * @param interElementDelta  the delta the tool would pass to
 *                           {@code adjustViewSpacing}; 0 on every short-circuit
 *                           branch
 * @param shouldCallAdjustViewSpacing true on the apply path, false on every
 *                           short-circuit (no-groups, no-groups-with-2+-children,
 *                           no-connections, delta=0, dryRun)
 * @param noChangeReason     populated on every short-circuit branch except
 *                           dryRun (dryRun is a deliberate preview, not a
 *                           "no change recommended" — it has a recommendation
 *                           but does not apply it); null on dryRun + apply paths
 */
public record ApplyElementSpacingDecision(
        int interElementDelta,
        boolean shouldCallAdjustViewSpacing,
        String noChangeReason) {

    /**
     * Computes the decision from the upstream-collected inputs.
     *
     * @param connectionCount             total view connection count
     *                                    (sourced from assess-layout)
     * @param currentSpacingPx            min per-group spacing across top-
     *                                    level groups (most-tight wins);
     *                                    irrelevant when
     *                                    {@code !hasGroupWithMultipleChildren}
     * @param targetSpacingPx             heuristic-or-override target spacing
     * @param dryRun                      preview-without-mutation flag
     * @param hasNonEmptyGroups           true when at least one top-level
     *                                    group has at least one child
     * @param hasGroupWithMultipleChildren true when at least one top-level
     *                                    group has at least 2 non-note
     *                                    children (spacing is only defined
     *                                    when there are at least 2 elements
     *                                    to space between)
     * @param hasTargetSpacingOverride    true when the caller provided an
     *                                    explicit {@code targetSpacing}
     *                                    parameter (changes the wording of
     *                                    the delta=0 short-circuit reason)
     * @return decision describing the chosen branch + computed delta
     */
    public static ApplyElementSpacingDecision decide(
            int connectionCount,
            int currentSpacingPx,
            int targetSpacingPx,
            boolean dryRun,
            boolean hasNonEmptyGroups,
            boolean hasGroupWithMultipleChildren,
            boolean hasTargetSpacingOverride) {

        if (!hasNonEmptyGroups) {
            return new ApplyElementSpacingDecision(0, false,
                    "view has no groups; spacing recommendation not "
                    + "applicable on flat views — use update-view-object "
                    + "for surgical edits or arrange-groups to introduce "
                    + "groups first");
        }

        if (!hasGroupWithMultipleChildren) {
            return new ApplyElementSpacingDecision(0, false,
                    "no groups with 2+ elements detected; inter-element "
                    + "spacing not adjustable (a group needs at least two "
                    + "children before there is a between-element gap to "
                    + "inflate)");
        }

        if (connectionCount == 0) {
            return new ApplyElementSpacingDecision(0, false,
                    "view has no connections; spacing recommendation not "
                    + "applicable");
        }

        int delta = Math.max(0, targetSpacingPx - currentSpacingPx);
        if (delta == 0) {
            String reason = hasTargetSpacingOverride
                    ? "current spacing " + currentSpacingPx
                            + "px already meets or exceeds explicit target "
                            + targetSpacingPx + "px"
                    : "current spacing " + currentSpacingPx
                            + "px already meets or exceeds heuristic target "
                            + targetSpacingPx + "px for connection count "
                            + connectionCount;
            return new ApplyElementSpacingDecision(0, false, reason);
        }

        if (dryRun) {
            // Recommendation present, no mutation — noChangeReason intentionally null
            return new ApplyElementSpacingDecision(delta, false, null);
        }

        return new ApplyElementSpacingDecision(delta, true, null);
    }
}
