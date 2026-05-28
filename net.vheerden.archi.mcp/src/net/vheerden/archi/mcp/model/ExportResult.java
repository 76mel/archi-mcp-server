package net.vheerden.archi.mcp.model;

import net.vheerden.archi.mcp.response.dto.ExportViewResultDto;

/**
 * Internal transport record for view export results (accessor to handler).
 *
 * <p>Not serialized to JSON directly. The handler uses {@code metadata}
 * for the JSON envelope and {@code imageBytes}/{@code svgContent} for
 * MCP content types.</p>
 *
 * <ul>
 *   <li>{@code imageBytes} — non-null for any inline single-binary format
 *       (PNG, JPG, or PDF raw bytes). The {@code metadata.format} field
 *       distinguishes which binary format the bytes carry.</li>
 *   <li>{@code svgContent} — non-null for SVG inline mode (raw SVG XML text)</li>
 *   <li>Both null for file output mode (path in metadata)</li>
 * </ul>
 *
 * <p><strong>Ownership contract:</strong> The {@code imageBytes} array is
 * owned exclusively by the handler that receives this result. The accessor
 * creates the array and transfers ownership — no defensive copy is made.
 * Callers MUST NOT retain references to the array after processing.</p>
 */
public record ExportResult(
    ExportViewResultDto metadata,
    byte[] imageBytes,
    String svgContent
) {}
