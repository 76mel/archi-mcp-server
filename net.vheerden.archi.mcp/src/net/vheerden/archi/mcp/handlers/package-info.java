/**
 * MCP tool handlers grouped by domain.
 *
 * <p><strong>CRITICAL BOUNDARY:</strong> Handlers in this package MUST NOT
 * import any EMF or ArchimateTool model types. All model access goes through
 * {@link net.vheerden.archi.mcp.model.ArchiModelAccessor}.</p>
 *
 * <p>Handler classes:</p>
 * <ul>
 *   <li>ModelQueryHandler - get-element, get-model-info</li>
 *   <li>ViewHandler - get-views, get-view-contents</li>
 *   <li>SearchHandler - search-elements</li>
 *   <li>TraversalHandler - get-relationships</li>
 * </ul>
 */
package net.vheerden.archi.mcp.handlers;
