package net.vheerden.archi.mcp.model;

/**
 * Immutable snapshot of a property key-value pair, used by mutation commands
 * to capture old property state for undo support.
 *
 * <p>Shared by {@link UpdateElementCommand}, {@link UpdateFolderCommand},
 * {@link UpdateRelationshipCommand}, {@link UpdateViewCommand},
 * and {@link UpdateModelCommand} to avoid duplicating this structure.</p>
 */
record PropertySnapshot(String key, String value) {}
