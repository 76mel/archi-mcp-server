package net.vheerden.archi.mcp.response.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Data Transfer Object for view export results.
 *
 * <p>Returned by the export-view command. Contains metadata about the
 * rendered image including dimensions, format, and render time.
 * {@code filePath} is non-null only for file output mode (inline=false).</p>
 *
 * <p>{@code width} and {@code height} are boxed {@code Integer} to allow
 * {@code @JsonInclude(NON_NULL)} exclusion on error paths and on vector
 * formats. Raster formats (PNG, JPG) populate width/height in pixels; vector
 * formats (SVG, PDF) leave them null because the output is resolution-
 * independent (page-sized rather than pixel-sized).
 * {@code renderTimeMs} is primitive {@code long} because it is always set
 * (defaults to 0 on the happy path before rendering starts).</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExportViewResultDto(
    String viewId,
    String viewName,
    String format,
    String mimeType,
    Integer width,
    Integer height,
    String filePath,
    long renderTimeMs
) {}
