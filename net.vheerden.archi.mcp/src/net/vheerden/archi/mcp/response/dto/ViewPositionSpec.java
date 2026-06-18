package net.vheerden.archi.mcp.response.dto;

/**
 * Input specification for a single view object position/size update
 * within an apply-positions operation.
 *
 * <p>At least one of x, y, width, height must be non-null.</p>
 */
public record ViewPositionSpec(
    String viewObjectId,
    Integer x,
    Integer y,
    Integer width,
    Integer height) {

    public ViewPositionSpec {
        if (viewObjectId == null || viewObjectId.isBlank()) {
            throw new IllegalArgumentException("viewObjectId must not be null or blank");
        }
    }
}
