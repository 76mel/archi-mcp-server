package net.vheerden.archi.mcp.model;

/**
 * Value object bundling optional visual styling parameters (Story 11-2;
 * extended for story {@code backlog-group-element-styling-surface} with
 * {@code figureType} + {@code textAlignment} + {@code verticalTextAlignment}).
 *
 * <p>Used to pass styling parameters through accessor method signatures
 * without bloating individual parameter lists. All fields are nullable:
 * null means "not specified" (leave unchanged), empty string for colours
 * means "clear to default".</p>
 *
 * @param fillColor             fill colour hex (#RRGGBB), empty string to clear, null = unchanged
 * @param lineColor             line/border colour hex (#RRGGBB), empty string to clear, null = unchanged
 * @param fontColor             font/text colour hex (#RRGGBB), empty string to clear, null = unchanged
 * @param opacity               opacity 0-255 (255 = fully opaque), null = unchanged
 * @param lineWidth             line width 1-3 (1=normal, 2=medium, 3=heavy), null = unchanged
 * @param figureType            figure type — "rectangular" or "tabbed", null = unchanged. Maps to
 *                              {@code IBorderType.setBorderType(int)} for {@code IDiagramModelGroup}
 *                              and {@code IDiagramModelArchimateObject.setType(int)} for ArchiMate elements
 * @param textAlignment         horizontal label alignment — "left", "centre" (UK) / "center" (US),
 *                              or "right", null = unchanged. Maps to {@code ITextAlignment.setTextAlignment(int)}
 * @param verticalTextAlignment vertical label position within the figure — "top", "centre" / "center",
 *                              or "bottom", null = unchanged. Maps to {@code ITextPosition.setTextPosition(int)}
 */
public record StylingParams(
    String fillColor,
    String lineColor,
    String fontColor,
    Integer opacity,
    Integer lineWidth,
    String figureType,
    String textAlignment,
    String verticalTextAlignment
) {

    /** An empty StylingParams indicating no styling changes. */
    public static final StylingParams NONE =
            new StylingParams(null, null, null, null, null, null, null, null);

    /**
     * Back-compatibility convenience constructor matching the original 5-field record
     * shape (Story 11-2). Delegates to the 8-field canonical constructor with
     * {@code figureType}, {@code textAlignment}, and {@code verticalTextAlignment}
     * all null. Preserves existing {@code new StylingParams(fill, line, font, opacity, lineWidth)}
     * call sites byte-identically.
     */
    public StylingParams(String fillColor, String lineColor, String fontColor,
                         Integer opacity, Integer lineWidth) {
        this(fillColor, lineColor, fontColor, opacity, lineWidth, null, null, null);
    }

    /**
     * Returns true if at least one styling parameter is specified (non-null).
     */
    public boolean hasAnyValue() {
        return fillColor != null || lineColor != null || fontColor != null
            || opacity != null || lineWidth != null
            || figureType != null || textAlignment != null || verticalTextAlignment != null;
    }
}
