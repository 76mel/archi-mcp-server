package net.vheerden.archi.mcp.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vheerden.archi.mcp.model.exceptions.MutationException;

/**
 * The shared "stored request → fresh {@code Command} + preconditions" seam used by the approve path
 * (Design §4 D5, §6). A pending proposal no longer holds a pre-built GEF {@code Command}
 * closed over propose-time EObjects; it holds a <em>deferred rebuild handle</em> — a
 * {@code Supplier<PreparedMutation<?>>} that re-invokes the very same per-tool {@code prepareXxx(...)}
 * logic the immediate-dispatch path runs, against the <em>current</em> model. This builder turns that
 * handle back into a fresh {@link PreparedMutation} at approve-time.
 *
 * <p><strong>No drift between paths.</strong> Both the immediate path and the approve path build through
 * the same {@code prepareXxx} family — the immediate path calls it directly; the approve path's stored
 * handle re-invokes the identical method. So preconditions and command construction cannot diverge: it is
 * the same code, run twice (the propose-time call produces the card's
 * {@code entity}/{@code effectDescription}; this approve-time call produces the command actually
 * dispatched, re-resolved against current state).</p>
 *
 * <p><strong>Stale/precondition signal, never an NPE.</strong> If a target was deleted or
 * retyped so re-resolution fails, the re-invoked {@code prepareXxx} throws (a {@link MutationException} or
 * a model-access exception). This builder lets a {@link MutationException} through unchanged and translates
 * any other runtime failure into a clean, plain-language {@link MutationException} — so the approve seam
 * surfaces "stale", not a raw exception or null-pointer. The {@link ProposalStalenessGuard} runs
 * <em>first</em> and names the touched/removed object for the common cases; this is the last-line safety
 * net for targets the coarse guard did not track.</p>
 *
 * <p>Package-private, {@code model/}-only. Stateless — one instance per dispatcher.</p>
 */
final class ProposalBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ProposalBuilder.class);

    /**
     * Re-resolves and rebuilds the proposal's command fresh against the current model by invoking its
     * stored deferred handle.
     *
     * @param proposal the approved proposal carrying the deferred rebuild handle
     * @return a fresh {@link PreparedMutation} (command + re-resolved entity)
     * @throws MutationException if a targeted object can no longer be resolved or fails preconditions
     *                           (reported as stale, not as a raw exception)
     */
    PreparedMutation<?> rebuild(PendingProposal proposal) throws MutationException {
        try {
            PreparedMutation<?> fresh = proposal.rebuild().get();
            if (fresh == null || fresh.command() == null) {
                throw new MutationException(staleMessage());
            }
            return fresh;
        } catch (MutationException e) {
            throw e;
        } catch (RuntimeException e) {
            logger.info("Proposal '{}' could not be rebuilt against the current model: {}",
                    proposal.proposalId(), e.getMessage());
            throw new MutationException(staleMessage(), e);
        }
    }

    private static String staleMessage() {
        return "This proposal can no longer be applied because a targeted object was changed or removed "
                + "since the agent proposed it. Reject it and ask the agent to retry.";
    }
}
