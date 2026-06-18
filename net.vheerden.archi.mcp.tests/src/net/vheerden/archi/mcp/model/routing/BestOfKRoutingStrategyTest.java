package net.vheerden.archi.mcp.model.routing;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import net.vheerden.archi.mcp.model.RoutingRect;
import net.vheerden.archi.mcp.model.routing.BestOfKRoutingStrategy.CandidateScorer;
import net.vheerden.archi.mcp.model.routing.BestOfKRoutingStrategy.RouteRunner;
import net.vheerden.archi.mcp.model.routing.RoutingPipeline.ConnectionEndpoints;

/**
 * Unit tests for {@link BestOfKRoutingStrategy}.
 *
 * <p><b>No rollback is tested because best-of-K needs none.</b> The never-worse
 * guarantee is monotone-by-selection: run&nbsp;0 ({@code null} override &equiv;
 * current {@code main}) is always in the candidate set and a strictly-greater
 * replacement rule means run&nbsp;0 wins every tie &mdash; so the emitted result's
 * objective is {@code &ge;} current {@code main} for every input, BY CONSTRUCTION.
 * These tests assert that construction directly with a deterministic fake pipeline
 * runner + a controlled scorer (the real pipeline + assessor are exercised
 * end-to-end on the V4 oracle fixture).
 *
 * <p>Pure-geometry tests &mdash; no OSGi runtime required. Same package as the
 * class under test, so the package-visible {@code effectiveK} / {@code shuffledOrder}
 * / {@code rngFor} seam is directly assertable.
 */
public class BestOfKRoutingStrategyTest {

    /** Sentinel run-0 result identity for {@code ==} never-worse assertions. */
    private static final RoutingResult RUN0 =
            new RoutingResult(Map.of(), List.of(), List.of());

    /**
     * Deterministic fake pipeline: {@code route(null)} returns the fixed
     * {@link #RUN0} sentinel (the current-{@code main} anchor); a non-null order
     * returns a fresh result whose {@code straightLineCrossings} encodes
     * {@code Arrays.hashCode(order)} so a scorer can map it back deterministically.
     * Records every order it is asked to run (a {@code null} marker for run&nbsp;0).
     */
    private static final class RecordingRunner implements RouteRunner {
        final List<Integer[]> ordersSeen = new ArrayList<>();
        final long sleepMillisPerShuffle;

        RecordingRunner() {
            this(0L);
        }

        RecordingRunner(long sleepMillisPerShuffle) {
            this.sleepMillisPerShuffle = sleepMillisPerShuffle;
        }

        @Override
        public RoutingResult route(Integer[] processingOrderOverride) {
            if (processingOrderOverride == null) {
                ordersSeen.add(null);
                return RUN0;
            }
            ordersSeen.add(processingOrderOverride.clone());
            if (sleepMillisPerShuffle > 0) {
                try {
                    Thread.sleep(sleepMillisPerShuffle);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return new RoutingResult(Map.of(), List.of(), List.of(), Map.of(), 0,
                    Map.of(), Arrays.hashCode(processingOrderOverride));
        }

        int shuffleRunCount() {
            int c = 0;
            for (Integer[] o : ordersSeen) {
                if (o != null) {
                    c++;
                }
            }
            return c;
        }
    }

    /** Scorer giving RUN0 a fixed score and every shuffled candidate another. */
    private static CandidateScorer scorer(double run0Score, double shuffleScore) {
        return r -> (r == RUN0) ? run0Score : shuffleScore;
    }

    private static List<ConnectionEndpoints> connections(int n) {
        List<ConnectionEndpoints> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            RoutingRect s = new RoutingRect(i * 10, 0, 40, 20, "s" + i);
            RoutingRect t = new RoutingRect(i * 10, 200 + i * 7, 40, 20, "t" + i);
            list.add(new ConnectionEndpoints("c" + i, s, t,
                    Collections.emptyList(), "", 0));
        }
        return list;
    }

    // --- never-worse-by-construction --------------------------------

    @Test
    public void shouldReturnExactRun0Result_whenK1() {
        RecordingRunner runner = new RecordingRunner();
        BestOfKRoutingStrategy s = new BestOfKRoutingStrategy(
                runner, scorer(0.5, 0.9), 1, 42L, 1000, 0L);

        RoutingResult result = s.selectBest(connections(5));

        assertSame("K=1 must return exactly the current-main (run-0) result", RUN0, result);
        assertEquals("K=1 must invoke the pipeline exactly once", 1, runner.ordersSeen.size());
        assertNull("the single K=1 run must be the null (unshuffled) override",
                runner.ordersSeen.get(0));
        assertEquals(0, runner.shuffleRunCount());
    }

    @Test
    public void shouldKeepRun0_whenEveryShuffleStrictlyWorse() {
        RecordingRunner runner = new RecordingRunner();
        BestOfKRoutingStrategy s = new BestOfKRoutingStrategy(
                runner, scorer(/*run0*/ 1.0, /*shuffle*/ 0.0), 10, 42L, 1000, 0L);

        RoutingResult result = s.selectBest(connections(5));

        assertSame("never-worse: if no shuffle beats run-0, emit exactly run-0", RUN0, result);
        assertEquals("K=10 explores run-0 + 9 shuffles", 10, runner.ordersSeen.size());
        assertEquals(9, runner.shuffleRunCount());
    }

    @Test
    public void shouldKeepRun0_whenAllScoresTie() {
        RecordingRunner runner = new RecordingRunner();
        BestOfKRoutingStrategy s = new BestOfKRoutingStrategy(
                runner, scorer(0.5, 0.5), 8, 42L, 1000, 0L);

        RoutingResult result = s.selectBest(connections(5));

        assertSame("tie-break: run-0 (lowest index) wins all exact ties", RUN0, result);
    }

    @Test
    public void shouldSelectBetterCandidate_whenAShuffleStrictlyBeatsRun0() {
        RecordingRunner runner = new RecordingRunner();
        CandidateScorer s0 = scorer(/*run0*/ 0.10, /*shuffle*/ 0.90);
        BestOfKRoutingStrategy s = new BestOfKRoutingStrategy(
                runner, s0, 6, 42L, 1000, 0L);

        RoutingResult result = s.selectBest(connections(5));

        assertNotSame("a strictly-better shuffle must be selected over run-0", RUN0, result);
        assertTrue("emitted objective must be >= run-0 objective (never-worse)",
                s0.score(result) >= s0.score(RUN0));
    }

    // --- determinism / reproducibility -----------------------------

    @Test
    public void shouldProduceIdenticalOrderSequence_forSameSeed() {
        RecordingRunner a = new RecordingRunner();
        RecordingRunner b = new RecordingRunner();
        List<ConnectionEndpoints> conns = connections(7);

        new BestOfKRoutingStrategy(a, scorer(1, 0), 10, 12345L, 1000, 0L).selectBest(conns);
        new BestOfKRoutingStrategy(b, scorer(1, 0), 10, 12345L, 1000, 0L).selectBest(conns);

        assertEquals(a.ordersSeen.size(), b.ordersSeen.size());
        for (int i = 0; i < a.ordersSeen.size(); i++) {
            assertArrayEquals("same seed must yield byte-identical processing orders @run " + i,
                    a.ordersSeen.get(i), b.ordersSeen.get(i));
        }
    }

    @Test
    public void shouldExploreDistinctOrderings_forDistinctSeeds() {
        RecordingRunner a = new RecordingRunner();
        RecordingRunner b = new RecordingRunner();
        List<ConnectionEndpoints> conns = connections(7);

        new BestOfKRoutingStrategy(a, scorer(1, 0), 10, 1L, 1000, 0L).selectBest(conns);
        new BestOfKRoutingStrategy(b, scorer(1, 0), 10, 999_983L, 1000, 0L).selectBest(conns);

        boolean anyDiffer = false;
        for (int i = 1; i < a.ordersSeen.size() && !anyDiffer; i++) {
            if (!Arrays.equals(a.ordersSeen.get(i), b.ordersSeen.get(i))) {
                anyDiffer = true;
            }
        }
        assertTrue("distinct seeds must explore distinct orderings (the search is real)", anyDiffer);
    }

    @Test
    public void shuffledOrder_shouldBePureFunctionOfSeedAndRun() {
        BestOfKRoutingStrategy s1 = new BestOfKRoutingStrategy(
                new RecordingRunner(), scorer(1, 0), 10, 777L, 1000, 0L);
        BestOfKRoutingStrategy s2 = new BestOfKRoutingStrategy(
                new RecordingRunner(), scorer(1, 0), 10, 777L, 1000, 0L);
        Integer[] base = {0, 1, 2, 3, 4, 5, 6, 7};

        for (int run = 1; run < 10; run++) {
            assertArrayEquals("shuffledOrder must be deterministic in (seed,run) @run " + run,
                    s1.shuffledOrder(base, run), s2.shuffledOrder(base, run));
        }
        assertArrayEquals("shuffledOrder must not mutate the base array",
                new Integer[]{0, 1, 2, 3, 4, 5, 6, 7}, base);
    }

    @Test
    public void shuffledOrder_shouldBeAPermutationOfBase() {
        BestOfKRoutingStrategy s = new BestOfKRoutingStrategy(
                new RecordingRunner(), scorer(1, 0), 10, 5L, 1000, 0L);
        Integer[] base = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        Integer[] shuffled = s.shuffledOrder(base, 3);

        Integer[] sortedShuffled = shuffled.clone();
        Arrays.sort(sortedShuffled);
        assertArrayEquals("a shuffle must be a permutation of the base order (no loss/dup)",
                base, sortedShuffled);
    }

    @Test
    public void shouldDeriveBaseOrderFromUnchangedBuildConnectionRoutingOrder() {
        RecordingRunner runner = new RecordingRunner();
        List<ConnectionEndpoints> conns = connections(6);
        BestOfKRoutingStrategy s = new BestOfKRoutingStrategy(
                runner, scorer(1, 0), 4, 42L, 1000, 0L);

        s.selectBest(conns);

        Integer[] base = RoutingPipeline.buildConnectionRoutingOrder(conns);
        Integer[] sortedBase = base.clone();
        Arrays.sort(sortedBase);
        for (Integer[] order : runner.ordersSeen) {
            if (order != null) {
                Integer[] sorted = order.clone();
                Arrays.sort(sorted);
                assertArrayEquals("every shuffle must be a permutation of the "
                        + "unchanged buildConnectionRoutingOrder base", sortedBase, sorted);
            }
        }
    }

    // --- large-view guard + wall-clock budget ----------------------

    @Test
    public void shouldDegradeToK1_whenViewExceedsLargeViewThreshold() {
        RecordingRunner runner = new RecordingRunner();
        BestOfKRoutingStrategy s = new BestOfKRoutingStrategy(
                runner, scorer(1, 0), /*k*/ 12, 42L, /*largeViewThreshold*/ 4, 0L);

        RoutingResult result = s.selectBest(connections(5)); // 5 > 4 ⇒ degrade

        assertSame("large-view guard must degrade to K=1 (current-main, never-worse)",
                RUN0, result);
        assertEquals(1, runner.ordersSeen.size());
        assertEquals(0, runner.shuffleRunCount());
    }

    @Test
    public void effectiveK_shouldCollapseToOne_forTrivialOrLargeViews() {
        BestOfKRoutingStrategy s = new BestOfKRoutingStrategy(
                new RecordingRunner(), scorer(1, 0), 12, 42L, 100, 0L);

        assertEquals("baseLength < 2 ⇒ only one ordering ⇒ K=1", 1, s.effectiveK(1, 1));
        assertEquals("baseLength 0 ⇒ K=1", 1, s.effectiveK(0, 0));
        assertEquals("connections over threshold ⇒ K=1", 1, s.effectiveK(101, 50));
        assertEquals("normal view ⇒ full K", 12, s.effectiveK(30, 30));
    }

    @Test
    public void shouldStopEarlyButStayNeverWorse_whenWallClockBudgetExhausted() {
        RecordingRunner runner = new RecordingRunner(/*sleep ms per shuffle*/ 10L);
        BestOfKRoutingStrategy s = new BestOfKRoutingStrategy(
                runner, scorer(/*run0*/ 1.0, /*shuffle*/ 0.0),
                /*k*/ 50, 42L, 1000, /*budgetMillis*/ 1L);

        RoutingResult result = s.selectBest(connections(6));

        assertSame("budget cut must still return best-so-far including run-0 (never-worse)",
                RUN0, result);
        assertTrue("budget must stop the search well before K=50 shuffles",
                runner.shuffleRunCount() < 50);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectKBelowOne() {
        new BestOfKRoutingStrategy(new RecordingRunner(), scorer(1, 0), 0, 1L, 100, 0L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNullRunner() {
        new BestOfKRoutingStrategy(null, scorer(1, 0));
    }

    /** Directly pin the ctor null-scorer guard. */
    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNullScorer() {
        new BestOfKRoutingStrategy(new RecordingRunner(), null);
    }
}
