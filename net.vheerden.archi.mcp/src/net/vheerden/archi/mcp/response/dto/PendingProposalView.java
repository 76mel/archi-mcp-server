package net.vheerden.archi.mcp.response.dto;

import java.util.Objects;

/**
 * A pending proposal paired with the session it lives in, for the cross-session aggregation the
 * Pending Approvals dock view consumes (Task 2).
 *
 * <p>The approval gate is <strong>global</strong> (one human, one gate), but proposals
 * are still <em>stored</em> per session in {@code MutationDispatcher.batchSessions}. The view must
 * show the union of all sessions' proposals, and when the human clicks Approve/Reject it must call
 * {@code ApprovalService.approve(sessionId, proposalId)} with the <em>right</em> session. This
 * record carries that routing {@code sessionId} alongside the wire {@link ProposalDto}.</p>
 *
 * <p><strong>Why not add {@code sessionId} to {@link ProposalDto}?</strong> {@code ProposalDto}
 * is the transport contract for the agent-facing {@code list-pending-approvals} tool; the session
 * key is a server-internal routing concern that does not belong on the agent's wire response. So the
 * routing key travels on this UI-side record, and {@code ProposalDto} stays unchanged.</p>
 *
 * <p>Plain record — no Eclipse/EMF imports — so it is shared cleanly across the {@code model/},
 * {@code server/}, and {@code ui/} layers.</p>
 *
 * @param sessionId the session the proposal is stored under (the Approve/Reject routing key)
 * @param proposal  the wire DTO the view renders
 */
public record PendingProposalView(String sessionId, ProposalDto proposal) {

    public PendingProposalView {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(proposal, "proposal must not be null");
    }
}
