package net.vheerden.archi.mcp.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/**
 * JUnit pin for {@link SpacingIterationStep} (AC-7.2). Pure-unit tests
 * (no OSGi). Sibling-symmetric with {@link ApplyElementSpacingDecision} +
 * {@link ApplyGroupSpacingDecision} + {@link ApplySpacingDecision} record
 * test patterns (record equality + null-safety).
 *
 * <p>Story:
 * `backlog-convenience-tool-control-loop-architectural-redesign` AC-7.2 —
 * minimum 4 @Test methods covering record equality + null-safety + ordering.
 * This class delivers 7 @Test methods (3 over minimum).</p>
 */
public class SpacingIterationStepTest {

    private static final LayoutMetrics METRICS_A =
            new LayoutMetrics(1, 0.50, 8, 4, 0, 5.0, 100);
    private static final LayoutMetrics METRICS_B =
            new LayoutMetrics(2, 0.75, 4, 2, 0, 9.0, 80);

    // ---- Record equality (record auto-generates equals/hashCode) ----

    @Test
    public void recordEquality_sameValues_areEqual() {
        SpacingIterationStep a = new SpacingIterationStep(
                0, 30, 70, METRICS_A, METRICS_B, 1, 2, 1, false, null);
        SpacingIterationStep b = new SpacingIterationStep(
                0, 30, 70, METRICS_A, METRICS_B, 1, 2, 1, false, null);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void recordEquality_differentDelta_areNotEqual() {
        SpacingIterationStep a = new SpacingIterationStep(
                0, 30, 70, METRICS_A, METRICS_B, 1, 2, 1, false, null);
        SpacingIterationStep b = new SpacingIterationStep(
                0, 40, 80, METRICS_A, METRICS_B, 1, 2, 1, false, null);
        assertNotEquals(a, b);
    }

    // ---- Null-safety contract ----

    @Test
    public void nullPreStateMetrics_throwsNpe() {
        try {
            new SpacingIterationStep(
                    0, 30, 70, /*preState=*/ null, METRICS_B,
                    1, 2, 1, false, null);
            fail("Expected NullPointerException for null preStateMetrics");
        } catch (NullPointerException expected) {
            assertTrue("NPE message should mention the field",
                    expected.getMessage() != null
                            && expected.getMessage().contains("preStateMetrics"));
        }
    }

    @Test
    public void nullPostStateMetrics_throwsNpe() {
        try {
            new SpacingIterationStep(
                    0, 30, 70, METRICS_A, /*postState=*/ null,
                    1, 2, 1, false, null);
            fail("Expected NullPointerException for null postStateMetrics");
        } catch (NullPointerException expected) {
            assertTrue("NPE message should mention the field",
                    expected.getMessage() != null
                            && expected.getMessage().contains("postStateMetrics"));
        }
    }

    @Test
    public void nullBackOffReason_allowed_whenNotBackedOff() {
        // When backedOff=false, backOffReason is conventionally null.
        // The record permits this construction.
        SpacingIterationStep step = new SpacingIterationStep(
                0, 30, 70, METRICS_A, METRICS_B, 1, 2, 1, false, null);
        assertNull(step.backOffReason());
    }

    // ---- Ordering in a list (forensic trace) ----

    @Test
    public void ordering_byStepIndex_inList_preservesMonotonicSequence() {
        SpacingIterationStep s0 = new SpacingIterationStep(
                0, 30, 70, METRICS_A, METRICS_B, 1, 2, 1, false, null);
        SpacingIterationStep s1 = new SpacingIterationStep(
                1, 40, 110, METRICS_B, METRICS_A, 2, 1, -1, false, null);
        SpacingIterationStep s2 = new SpacingIterationStep(
                2, 50, 160, METRICS_A, METRICS_B, 1, 2, 1, true,
                "aggregate_threshold_regressed_at_iteration_2"
                        + "_reverted_to_iteration_1");

        List<SpacingIterationStep> trace = new ArrayList<>();
        Collections.addAll(trace, s0, s1, s2);

        // Monotonic stepIndex by list position
        for (int i = 0; i < trace.size(); i++) {
            assertEquals("trace[" + i + "].stepIndex",
                    i, trace.get(i).stepIndex());
        }

        // Backed-off step is the LAST element of this list when back-off
        // fired (loop semantics — see SpacingControlLoop class javadoc).
        assertTrue("last step in this trace should be backed-off",
                trace.get(trace.size() - 1).backedOff());
    }

    @Test
    public void thresholdsMetDelta_signedDifference_acceptedStep_isPositive() {
        // Per record javadoc: thresholdsMetDelta is signed (post - pre).
        // The record itself does NOT enforce this; the loop constructs the
        // value. We pin that the field is straightforward + observable.
        SpacingIterationStep acceptedStep = new SpacingIterationStep(
                0, 30, 70, METRICS_A, METRICS_B,
                METRICS_A.thresholdsMet(), METRICS_B.thresholdsMet(),
                METRICS_B.thresholdsMet() - METRICS_A.thresholdsMet(),
                false, null);
        assertEquals(1, acceptedStep.thresholdsMetBefore());
        assertEquals(2, acceptedStep.thresholdsMetAfter());
        assertEquals(1, acceptedStep.thresholdsMetDelta());
    }
}
