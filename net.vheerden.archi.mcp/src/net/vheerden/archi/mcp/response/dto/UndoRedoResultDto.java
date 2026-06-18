package net.vheerden.archi.mcp.response.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Result DTO for undo/redo operations.
 *
 * <p>{@code blockedReason} is a nullable marker set when the scoped agent
 * undo/redo refused with <strong>zero</strong> progress because the top-of-stack entry is the
 * human's (the "betrayal" guard). It lets {@link net.vheerden.archi.mcp.handlers.CommandStackHandler}
 * distinguish a human-blocked refusal ({@code operationsPerformed == 0 && blockedReason != null})
 * from a genuinely empty stack ({@code operationsPerformed == 0 && blockedReason == null}) and emit
 * the right diagnostic. {@code null} on the additive majority of operations and omitted on the wire
 * via the class-level {@link JsonInclude}(NON_NULL) — so it never leaks as {@code "blockedReason": null}
 * even through a bare {@code ObjectMapper} that lacks the global NON_NULL inclusion config.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UndoRedoResultDto(
		int operationsRequested,
		int operationsPerformed,
		List<String> labels,
		boolean canUndoAfter,
		boolean canRedoAfter,
		String blockedReason) {

	/**
	 * Back-compat convenience constructor at the prior canonical arity: no block reason.
	 * Retained so every existing call-site (incl. tests and stubs) compiles unchanged
	 * (record-arity discipline).
	 */
	public UndoRedoResultDto(
			int operationsRequested,
			int operationsPerformed,
			List<String> labels,
			boolean canUndoAfter,
			boolean canRedoAfter) {
		this(operationsRequested, operationsPerformed, labels, canUndoAfter, canRedoAfter, null);
	}
}
