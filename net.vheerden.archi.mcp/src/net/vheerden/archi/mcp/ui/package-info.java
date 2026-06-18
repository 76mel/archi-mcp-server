/**
 * SWT/Eclipse UI components for the MCP Server plugin.
 *
 * <p><strong>CRITICAL BOUNDARY:</strong> Only this package should import
 * SWT and Eclipse UI types. All UI updates MUST run on the SWT display
 * thread via {@code Display.getDefault().asyncExec()} or {@code syncExec()}.</p>
 *
 * <p>UI components:</p>
 * <ul>
 *   <li>McpPreferencePage - Server settings preferences</li>
 *   <li>McpMenuContribution - Start/Stop menu actions</li>
 *   <li>McpStatusIndicator - Server status display</li>
 * </ul>
 */
package net.vheerden.archi.mcp.ui;
