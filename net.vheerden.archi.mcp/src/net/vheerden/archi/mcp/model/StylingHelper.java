package net.vheerden.archi.mcp.model;

import java.util.regex.Pattern;

import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelGroup;
import com.archimatetool.model.IDiagramModelObject;
import com.archimatetool.model.IFontAttribute;
import com.archimatetool.model.IGrouping;
import com.archimatetool.model.ILineObject;
import com.archimatetool.model.ITextAlignment;
import com.archimatetool.model.ITextPosition;

import net.vheerden.archi.mcp.response.ErrorCode;

/**
 * Stateless styling validation, read, and post-computation helpers.
 *
 * <p>Extracted from ArchiModelAccessorImpl (Story 12-4) to improve cohesion.
 * Package-visible — only ArchiModelAccessorImpl should use this class.</p>
 */
final class StylingHelper {

    private StylingHelper() {}

    private static final Pattern HEX_COLOR_PATTERN =
            Pattern.compile("#[0-9A-Fa-f]{6}");

    static void validateStylingParams(StylingParams styling) {
        validateHexColor(styling.fillColor(), "fillColor");
        validateHexColor(styling.lineColor(), "lineColor");
        validateHexColor(styling.fontColor(), "fontColor");
        if (styling.opacity() != null && (styling.opacity() < 0 || styling.opacity() > 255)) {
            throw new ModelAccessException(
                    "opacity must be between 0 and 255, got: " + styling.opacity(),
                    ErrorCode.INVALID_PARAMETER,
                    null,
                    "Provide an opacity value between 0 (fully transparent) and 255 (fully opaque).",
                    null);
        }
        if (styling.lineWidth() != null && (styling.lineWidth() < 1 || styling.lineWidth() > 3)) {
            throw new ModelAccessException(
                    "lineWidth must be between 1 and 3, got: " + styling.lineWidth(),
                    ErrorCode.INVALID_PARAMETER,
                    null,
                    "Provide a lineWidth of 1 (normal), 2 (medium), or 3 (heavy).",
                    null);
        }
        validateFigureType(styling.figureType());
        validateTextAlignment(styling.textAlignment());
        validateVerticalTextAlignment(styling.verticalTextAlignment());
    }

    static void validateFigureType(String value) {
        if (value == null || value.isEmpty()) return;
        String normalized = value.toLowerCase();
        if (!normalized.equals("rectangular") && !normalized.equals("tabbed")) {
            throw new ModelAccessException(
                    "Invalid figureType value: '" + value + "'",
                    ErrorCode.INVALID_PARAMETER,
                    null,
                    "Provide 'rectangular' or 'tabbed'.",
                    null);
        }
    }

    static void validateTextAlignment(String value) {
        if (value == null || value.isEmpty()) return;
        String normalized = value.toLowerCase();
        if (!normalized.equals("left") && !normalized.equals("centre")
                && !normalized.equals("center") && !normalized.equals("right")) {
            throw new ModelAccessException(
                    "Invalid textAlignment value: '" + value + "'",
                    ErrorCode.INVALID_PARAMETER,
                    null,
                    "Provide 'left', 'centre' (or 'center'), or 'right'.",
                    null);
        }
    }

    static void validateVerticalTextAlignment(String value) {
        if (value == null || value.isEmpty()) return;
        String normalized = value.toLowerCase();
        if (!normalized.equals("top") && !normalized.equals("centre")
                && !normalized.equals("center") && !normalized.equals("bottom")) {
            throw new ModelAccessException(
                    "Invalid verticalTextAlignment value: '" + value + "'",
                    ErrorCode.INVALID_PARAMETER,
                    null,
                    "Provide 'top', 'centre' (or 'center'), or 'bottom'.",
                    null);
        }
    }

    static int mapFigureTypeToInt(String value) {
        String normalized = value.toLowerCase();
        return switch (normalized) {
            case "rectangular" -> 1;
            case "tabbed" -> 0;
            default -> throw new IllegalArgumentException("Unmapped figureType: " + value);
        };
    }

    static int mapTextAlignmentToInt(String value) {
        String normalized = value.toLowerCase();
        return switch (normalized) {
            case "left" -> ITextAlignment.TEXT_ALIGNMENT_LEFT;
            case "centre", "center" -> ITextAlignment.TEXT_ALIGNMENT_CENTER;
            case "right" -> ITextAlignment.TEXT_ALIGNMENT_RIGHT;
            default -> throw new IllegalArgumentException("Unmapped textAlignment: " + value);
        };
    }

    static int mapVerticalTextAlignmentToInt(String value) {
        String normalized = value.toLowerCase();
        return switch (normalized) {
            case "top" -> ITextPosition.TEXT_POSITION_TOP;
            case "centre", "center" -> ITextPosition.TEXT_POSITION_CENTRE;
            case "bottom" -> ITextPosition.TEXT_POSITION_BOTTOM;
            default -> throw new IllegalArgumentException("Unmapped verticalTextAlignment: " + value);
        };
    }

    static void validateConnectionStylingParams(StylingParams styling) {
        validateHexColor(styling.lineColor(), "lineColor");
        validateHexColor(styling.fontColor(), "fontColor");
        if (styling.lineWidth() != null && (styling.lineWidth() < 1 || styling.lineWidth() > 3)) {
            throw new ModelAccessException(
                    "lineWidth must be between 1 and 3, got: " + styling.lineWidth(),
                    ErrorCode.INVALID_PARAMETER,
                    null,
                    "Provide a lineWidth of 1 (normal), 2 (medium), or 3 (heavy).",
                    null);
        }
    }

    static void validateHexColor(String value, String fieldName) {
        if (value == null || value.isEmpty()) return;
        if (!HEX_COLOR_PATTERN.matcher(value).matches()) {
            throw new ModelAccessException(
                    "Invalid " + fieldName + " format: '" + value + "'",
                    ErrorCode.INVALID_PARAMETER,
                    null,
                    "Provide a hex colour in #RRGGBB format (e.g., '#FF6600') or empty string to clear.",
                    null);
        }
    }

    static void applyStylingToNewObject(IDiagramModelObject diagramObj, StylingParams styling) {
        if (styling == null || !styling.hasAnyValue()) return;

        validateStylingParams(styling);

        if (styling.fillColor() != null) {
            diagramObj.setFillColor(styling.fillColor().isEmpty() ? null : styling.fillColor());
        }
        if (styling.opacity() != null) {
            diagramObj.setAlpha(styling.opacity());
        }
        if (styling.lineColor() != null && diagramObj instanceof ILineObject lo) {
            lo.setLineColor(styling.lineColor().isEmpty() ? null : styling.lineColor());
        }
        if (styling.lineWidth() != null && diagramObj instanceof ILineObject lo) {
            lo.setLineWidth(styling.lineWidth());
        }
        if (styling.fontColor() != null && diagramObj instanceof IFontAttribute fa) {
            fa.setFontColor(styling.fontColor().isEmpty() ? null : styling.fontColor());
        }
        // figureType — restricted to the targets where "tabbed/rectangular" vocabulary is honest:
        // native groups (IBorderType.BORDER_TABBED/BORDER_RECTANGLE) and ArchiMate Grouping
        // elements (IDiagramModelArchimateObject.setType for the Grouping subtype only). Per
        // owner decision 2026-05-20: other ArchiMate element classes (Actor, Component, Node,
        // ...) also have setType(int) alternates, but their "alternate" figures are NOT tabbed
        // vs rectangular (stick-vs-box, 3D-vs-flat, etc.) — applying figureType to them under
        // "tabbed/rectangular" naming would be misleading. Silently ignored on those targets;
        // per-class figure switching deferred to a future story with proper vocabulary.
        if (styling.figureType() != null && !styling.figureType().isEmpty()) {
            int figureInt = mapFigureTypeToInt(styling.figureType());
            if (diagramObj instanceof IDiagramModelGroup group) {
                group.setBorderType(figureInt);
            } else if (diagramObj instanceof IDiagramModelArchimateObject archi
                    && archi.getArchimateElement() instanceof IGrouping) {
                archi.setType(figureInt);
            }
            // Notes implement IBorderType too but with different semantics (dogear/rectangle/none)
            // — out of scope for this story; figureType is silently ignored on notes.
        }
        if (styling.textAlignment() != null && !styling.textAlignment().isEmpty()
                && diagramObj instanceof ITextAlignment ta) {
            ta.setTextAlignment(mapTextAlignmentToInt(styling.textAlignment()));
        }
        if (styling.verticalTextAlignment() != null && !styling.verticalTextAlignment().isEmpty()
                && diagramObj instanceof ITextPosition tp) {
            tp.setTextPosition(mapVerticalTextAlignmentToInt(styling.verticalTextAlignment()));
        }
    }

    // ---- Read styling from view objects ----

    static String readFillColor(IDiagramModelObject obj) {
        return obj.getFillColor();
    }

    static String readLineColor(IDiagramModelObject obj) {
        if (obj instanceof ILineObject lo) {
            return lo.getLineColor();
        }
        return null;
    }

    static String readFontColor(IDiagramModelObject obj) {
        if (obj instanceof IFontAttribute fa) {
            return fa.getFontColor();
        }
        return null;
    }

    static Integer readOpacity(IDiagramModelObject obj) {
        int alpha = obj.getAlpha();
        return (alpha != 255) ? alpha : null;
    }

    static Integer readLineWidth(IDiagramModelObject obj) {
        if (obj instanceof ILineObject lo) {
            int lw = lo.getLineWidth();
            return (lw != 1) ? lw : null;
        }
        return null;
    }

    /**
     * Reads figureType as a user-facing string. Returns null when at Archi default
     * (matches readOpacity / readLineWidth pattern — DTO field omitted for the common case).
     * Restricted to the targets where "tabbed/rectangular" vocabulary is honest: native
     * groups (IBorderType) and ArchiMate Grouping elements (IDiagramModelArchimateObject
     * wrapping an IGrouping). Convention: 0 = tabbed (default), 1 = rectangular. For
     * other ArchiMate elements (Actor, Component, Node, ...), notes, and connections,
     * returns null — those have setType but with element-specific alternate-figure semantics
     * that this surface doesn't expose.
     */
    static String readFigureType(IDiagramModelObject obj) {
        if (obj instanceof IDiagramModelGroup group) {
            int bt = group.getBorderType();
            return (bt == IDiagramModelGroup.BORDER_RECTANGLE) ? "rectangular" : null;
        }
        if (obj instanceof IDiagramModelArchimateObject archi
                && archi.getArchimateElement() instanceof IGrouping) {
            int t = archi.getType();
            return (t == 1) ? "rectangular" : null;
        }
        return null;
    }

    /**
     * Reads textAlignment as a user-facing string. Returns null when at Archi default
     * ({@link ITextAlignment#TEXT_ALIGNMENT_CENTER}). Returns "left" or "right" when
     * explicitly set; "centre" is the default and therefore omitted from the DTO.
     */
    static String readTextAlignment(IDiagramModelObject obj) {
        if (obj instanceof ITextAlignment ta) {
            int v = ta.getTextAlignment();
            if (v == ITextAlignment.TEXT_ALIGNMENT_LEFT) return "left";
            if (v == ITextAlignment.TEXT_ALIGNMENT_RIGHT) return "right";
            return null; // CENTER (default) — omit from DTO
        }
        return null;
    }

    /**
     * Reads verticalTextAlignment as a user-facing string. Returns null when at Archi default
     * ({@link ITextPosition#TEXT_POSITION_TOP} — confirmed empirically: a freshly-created
     * IDiagramModelGroup / IDiagramModelArchimateObject / IDiagramModelNote all return
     * {@code getTextPosition() == 0 == TEXT_POSITION_TOP}; the visible Archi UI convention
     * is that labels render in a top "header band" of the figure). Returns "centre" or
     * "bottom" when explicitly set.
     */
    static String readVerticalTextAlignment(IDiagramModelObject obj) {
        if (obj instanceof ITextPosition tp) {
            int v = tp.getTextPosition();
            if (v == ITextPosition.TEXT_POSITION_CENTRE) return "centre";
            if (v == ITextPosition.TEXT_POSITION_BOTTOM) return "bottom";
            return null; // TOP (default) — omit from DTO
        }
        return null;
    }

    // ---- Post-styling computation ----

    static String computePostStylingColor(String currentValue, String stylingValue) {
        if (stylingValue == null) return currentValue;
        return stylingValue.isEmpty() ? null : stylingValue;
    }

    static Integer computePostStylingOpacity(Integer currentValue, Integer stylingValue) {
        if (stylingValue == null) return currentValue;
        return (stylingValue == 255) ? null : stylingValue;
    }

    static Integer computePostStylingLineWidth(Integer currentValue, Integer stylingValue) {
        if (stylingValue == null) return currentValue;
        return (stylingValue == 1) ? null : stylingValue;
    }

    /**
     * Post-styling figureType DTO value. Empty styling input is treated as "unchanged"
     * (AC-11 — figureType has no symmetric "clear" semantics). Returns null when the
     * resolved figure type is Archi's default (tabbed) so the DTO field is omitted.
     */
    static String computePostStylingFigureType(String currentValue, String stylingValue) {
        if (stylingValue == null || stylingValue.isEmpty()) return currentValue;
        return stylingValue.toLowerCase().equals("rectangular") ? "rectangular" : null;
    }

    /**
     * Post-styling textAlignment DTO value. Returns null when the resolved alignment
     * is Archi's default ({@code TEXT_ALIGNMENT_CENTER}).
     */
    static String computePostStylingTextAlignment(String currentValue, String stylingValue) {
        if (stylingValue == null || stylingValue.isEmpty()) return currentValue;
        String n = stylingValue.toLowerCase();
        if (n.equals("left")) return "left";
        if (n.equals("right")) return "right";
        return null;
    }

    /**
     * Post-styling verticalTextAlignment DTO value. Returns null when the resolved
     * vertical alignment is Archi's default ({@code TEXT_POSITION_TOP}).
     */
    static String computePostStylingVerticalTextAlignment(String currentValue, String stylingValue) {
        if (stylingValue == null || stylingValue.isEmpty()) return currentValue;
        String n = stylingValue.toLowerCase();
        if (n.equals("centre") || n.equals("center")) return "centre";
        if (n.equals("bottom")) return "bottom";
        return null;
    }

    // ---- Connection styling ----

    static String readConnectionLineColor(IDiagramModelArchimateConnection conn) {
        return conn.getLineColor();
    }

    static String readConnectionFontColor(IDiagramModelArchimateConnection conn) {
        return conn.getFontColor();
    }

    static Integer readConnectionLineWidth(IDiagramModelArchimateConnection conn) {
        int lw = conn.getLineWidth();
        return (lw != 1) ? lw : null;
    }

    /**
     * Reads the label visibility state of a connection (Story 13-1).
     * Returns null when the label is visible (default), Boolean.FALSE when hidden.
     * This keeps the DTO field omitted from JSON for the common case.
     */
    static Boolean readConnectionNameVisible(IDiagramModelArchimateConnection conn) {
        return conn.isNameVisible() ? null : Boolean.FALSE;
    }
}
