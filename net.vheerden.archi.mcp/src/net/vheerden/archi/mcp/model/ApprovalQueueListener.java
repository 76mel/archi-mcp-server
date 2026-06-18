package net.vheerden.archi.mcp.model;

/**
 * Observer notified whenever the pending-approval queue changes (the missing wire).
 *
 * <p>Previously, a gated mutation arriving on a Jetty worker thread put a proposal in the
 * dispatcher's map and <em>nothing told the UI</em>. {@link MutationDispatcher} fires this
 * listener after every queue-mutating point (store / remove / approve / reject / clear) so the
 * Pending Approvals dock view can rebuild itself.</p>
 *
 * <p><strong>Layer boundary:</strong> this is a plain {@code model/} interface — it
 * imports <em>no</em> SWT/EMF/GEF types. The dispatcher fires it on whatever thread mutated the
 * queue (typically a Jetty thread); the listener implementation in {@code ui/} is responsible
 * for marshalling to the SWT thread via {@code Display.asyncExec}. Keeping the marshalling out
 * of {@code model/} is what keeps the model layer SWT-free.</p>
 *
 * <p><strong>Contract:</strong> implementations must be cheap and must not throw — the
 * dispatcher fires outside any lock and guards each callback, but a slow or throwing listener
 * still degrades the mutation path it fires from.</p>
 */
@FunctionalInterface
public interface ApprovalQueueListener {

    /**
     * Called after the pending-approval queue has changed (a proposal was stored, removed,
     * approved, rejected, or the whole queue was cleared). Carries no payload — the listener
     * re-reads the current aggregate via the aggregation seam.
     */
    void onQueueChanged();
}
