package net.vheerden.archi.mcp.response.dto;

/**
 * Result DTO for the apply-positions compound operation.
 */
public record ApplyViewLayoutResultDto(
    String viewId,
    int positionsUpdated,
    int connectionsUpdated,
    int totalOperations) {}
