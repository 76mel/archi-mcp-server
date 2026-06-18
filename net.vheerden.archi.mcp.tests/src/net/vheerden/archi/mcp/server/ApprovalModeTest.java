package net.vheerden.archi.mcp.server;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import net.vheerden.archi.mcp.McpPlugin;

/**
 * Tests for {@link ApprovalMode}, the human-owned approval bit.
 *
 * <p>Exercises the headlessly-testable decisions delegated to the plain holder: the start-up
 * seed (the bit reflects the stored preference so the human's choice survives a restart) and the
 * read/write contract. The SWT friction-asymmetry (ON = quiet, OFF = confirm) and the live pref
 * store are display/PDE-tier — here the seed and persister are explicit/capturing seams.</p>
 */
public class ApprovalModeTest {

    @Test
    public void shouldSeedFromInitialValue() {
        // The effective bit reflects the value it was seeded with (production: the stored pref).
        assertTrue(new ApprovalMode(true, value -> { }).isOn());
        assertFalse(new ApprovalMode(false, value -> { }).isOn());
    }

    @Test
    public void shouldDefaultToGated_onFreshInstall() {
        // When the preference has never been set, the registered default is GATED (fail safe).
        assertTrue("approval mode must default ON (gated) when unconfigured",
                McpPlugin.DEFAULT_APPROVAL_MODE);
    }

    @Test
    public void shouldReflectWrites() {
        ApprovalMode mode = new ApprovalMode(true, value -> { });

        mode.setOn(false);
        assertFalse(mode.isOn());

        mode.setOn(true);
        assertTrue(mode.isOn());
    }

    @Test
    public void shouldPersistIntent_onEverySet_soItSurvivesRestart() {
        AtomicReference<Boolean> persisted = new AtomicReference<>();
        ApprovalMode mode = new ApprovalMode(true, persisted::set);

        mode.setOn(false);
        assertEquals(Boolean.FALSE, persisted.get());

        mode.setOn(true);
        assertEquals(Boolean.TRUE, persisted.get());
    }

    // ---- Change listener (the Pending Approvals view repaints on a mode toggle) ----

    @Test
    public void shouldNotifyChangeListener_whenBitChanges() {
        ApprovalMode mode = new ApprovalMode(true, value -> { });
        int[] fired = {0};
        mode.addChangeListener(() -> fired[0]++);

        mode.setOn(false);

        assertEquals(1, fired[0]);
    }

    @Test
    public void shouldNotNotifyChangeListener_whenValueUnchanged() {
        ApprovalMode mode = new ApprovalMode(true, value -> { });
        int[] fired = {0};
        mode.addChangeListener(() -> fired[0]++);

        mode.setOn(true); // already ON — no change, no fire

        assertEquals(0, fired[0]);
    }

    @Test
    public void shouldStopNotifying_afterListenerRemoved() {
        ApprovalMode mode = new ApprovalMode(true, value -> { });
        int[] fired = {0};
        Runnable listener = () -> fired[0]++;
        mode.addChangeListener(listener);
        mode.removeChangeListener(listener);

        mode.setOn(false);

        assertEquals(0, fired[0]);
    }

    @Test
    public void shouldNotBreakSetOn_whenChangeListenerThrows() {
        ApprovalMode mode = new ApprovalMode(true, value -> { });
        mode.addChangeListener(() -> {
            throw new IllegalStateException("listener boom");
        });
        int[] good = {0};
        mode.addChangeListener(() -> good[0]++);

        mode.setOn(false); // must not propagate the throw

        assertFalse(mode.isOn());
        assertEquals("well-behaved listener still fires after a throwing one", 1, good[0]);
    }
}
