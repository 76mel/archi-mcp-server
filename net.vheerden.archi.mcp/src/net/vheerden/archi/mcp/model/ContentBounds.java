package net.vheerden.archi.mcp.model;

/**
 * Axis-aligned bounding box of all visual content on a view.
 * Coordinates are absolute canvas coordinates (same as {@link AssessmentNode}).
 */
public record ContentBounds(double x, double y, double width, double height) {}
