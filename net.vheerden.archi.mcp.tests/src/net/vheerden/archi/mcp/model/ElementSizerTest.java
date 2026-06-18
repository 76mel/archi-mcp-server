package net.vheerden.archi.mcp.model;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests for {@link ElementSizer} — pure-geometry dimension computation.
 * Tests the {@code computeDimensions} and {@code simulateWordWrap} methods
 * using synthetic {@link ElementSizer.FontMetrics} (no SWT runtime required).
 */
public class ElementSizerTest {

    // ---- Helper to create FontMetrics from word widths ----

    /**
     * Creates FontMetrics with realistic values: 14px line height, 4px space width.
     * textWidth is the sum of word widths + spaces (single-line extent).
     */
    private static ElementSizer.FontMetrics metricsFor(String label, int... wordWidths) {
        int spaceWidth = 4;
        int lineHeight = 14;
        int textWidth = 0;
        for (int i = 0; i < wordWidths.length; i++) {
            textWidth += wordWidths[i];
            if (i < wordWidths.length - 1) {
                textWidth += spaceWidth;
            }
        }
        return new ElementSizer.FontMetrics(textWidth, lineHeight, spaceWidth, wordWidths);
    }

    // ---- Short name / null / empty → default ----

    @Test
    public void computeAutoSize_nullLabel_returnsDefault() {
        int[] size = ElementSizer.computeAutoSize(null);
        assertEquals(ElementSizer.DEFAULT_WIDTH, size[0]);
        assertEquals(ElementSizer.DEFAULT_HEIGHT, size[1]);
    }

    @Test
    public void computeAutoSize_emptyLabel_returnsDefault() {
        int[] size = ElementSizer.computeAutoSize("");
        assertEquals(ElementSizer.DEFAULT_WIDTH, size[0]);
        assertEquals(ElementSizer.DEFAULT_HEIGHT, size[1]);
    }

    @Test
    public void computeDimensions_shortName_returnsDefault() {
        // "Server" = 6 chars, below threshold
        ElementSizer.FontMetrics metrics = metricsFor("Server", 42);
        int[] size = ElementSizer.computeDimensions("Server", metrics);
        assertEquals(ElementSizer.DEFAULT_WIDTH, size[0]);
        assertEquals(ElementSizer.DEFAULT_HEIGHT, size[1]);
    }

    @Test
    public void computeDimensions_exactly15Chars_returnsDefault() {
        // Exactly 15 chars = at threshold, returns default
        String label = "Exactly15Chars!"; // 15 chars
        assertEquals(15, label.length());
        ElementSizer.FontMetrics metrics = metricsFor(label, 100);
        int[] size = ElementSizer.computeDimensions(label, metrics);
        assertEquals(ElementSizer.DEFAULT_WIDTH, size[0]);
        assertEquals(ElementSizer.DEFAULT_HEIGHT, size[1]);
    }

    // ---- Long names grow both dimensions ----

    @Test
    public void computeDimensions_longName_growsBothDimensions() {
        // "Transaction Monitoring System" = 29 chars, 3 words
        String label = "Transaction Monitoring System";
        // Simulate: Transaction=77, Monitoring=70, System=42
        ElementSizer.FontMetrics metrics = metricsFor(label, 77, 70, 42);
        int[] size = ElementSizer.computeDimensions(label, metrics);
        assertTrue("Width should exceed default: " + size[0], size[0] > ElementSizer.DEFAULT_WIDTH);
        assertTrue("Height should exceed default: " + size[1], size[1] > ElementSizer.DEFAULT_HEIGHT);
    }

    // ---- Aspect ratio bounds ----

    @Test
    public void computeDimensions_aspectRatioWithinBounds_moderate() {
        // "Card Management System" = 22 chars
        String label = "Card Management System";
        ElementSizer.FontMetrics metrics = metricsFor(label, 28, 84, 42);
        int[] size = ElementSizer.computeDimensions(label, metrics);
        double ratio = (double) size[0] / size[1];
        assertTrue("Ratio should be >= 1.2: " + ratio, ratio >= ElementSizer.MIN_ASPECT_RATIO);
        assertTrue("Ratio should be <= 2.5: " + ratio, ratio <= ElementSizer.MAX_ASPECT_RATIO);
    }

    @Test
    public void computeDimensions_aspectRatioWithinBounds_long() {
        // "API Routing & Throttling & Load Balancing" = 42 chars
        String label = "API Routing & Throttling & Load Balancing";
        ElementSizer.FontMetrics metrics = metricsFor(label, 21, 49, 7, 70, 7, 28, 56);
        int[] size = ElementSizer.computeDimensions(label, metrics);
        double ratio = (double) size[0] / size[1];
        assertTrue("Ratio should be >= 1.2: " + ratio, ratio >= ElementSizer.MIN_ASPECT_RATIO);
        assertTrue("Ratio should be <= 2.5: " + ratio, ratio <= ElementSizer.MAX_ASPECT_RATIO);
    }

    @Test
    public void computeDimensions_veryLongName_respectsMaxAspectRatio() {
        // Very long single-word name forces wide layout
        String label = "VeryLongElementNameThatShouldNotBeExcessivelyWide";
        ElementSizer.FontMetrics metrics = metricsFor(label, 340);
        int[] size = ElementSizer.computeDimensions(label, metrics);
        double ratio = (double) size[0] / size[1];
        assertTrue("Ratio should be <= 2.5: " + ratio, ratio <= ElementSizer.MAX_ASPECT_RATIO);
    }

    // ---- Minimum size enforcement ----

    @Test
    public void computeDimensions_neverBelowMinimumWidth() {
        // 16-char name just above threshold but short words
        String label = "A Tiny Element X"; // 16 chars
        ElementSizer.FontMetrics metrics = metricsFor(label, 7, 28, 49, 7);
        int[] size = ElementSizer.computeDimensions(label, metrics);
        assertTrue("Width should be >= 120: " + size[0], size[0] >= ElementSizer.DEFAULT_WIDTH);
    }

    @Test
    public void computeDimensions_neverBelowMinimumHeight() {
        String label = "A Tiny Element X"; // 16 chars
        ElementSizer.FontMetrics metrics = metricsFor(label, 7, 28, 49, 7);
        int[] size = ElementSizer.computeDimensions(label, metrics);
        assertTrue("Height should be >= 55: " + size[1], size[1] >= ElementSizer.DEFAULT_HEIGHT);
    }

    // ---- Target aspect ratio ----

    @Test
    public void computeDimensions_moderateName_targetsAspectRatio() {
        // "Customer Profiling" = 18 chars, moderate length
        String label = "Customer Profiling";
        ElementSizer.FontMetrics metrics = metricsFor(label, 56, 56);
        int[] size = ElementSizer.computeDimensions(label, metrics);
        double ratio = (double) size[0] / size[1];
        // Should be reasonably close to 1.5 (within the valid range)
        assertTrue("Ratio should be >= 1.2: " + ratio, ratio >= ElementSizer.MIN_ASPECT_RATIO);
        assertTrue("Ratio should be <= 2.5: " + ratio, ratio <= ElementSizer.MAX_ASPECT_RATIO);
    }

    // ---- simulateWordWrap ----

    @Test
    public void simulateWordWrap_allFitsSingleLine() {
        String label = "Short Label";
        int[] wordWidths = { 35, 35 };
        int spaceWidth = 4;
        // Target width 200 — both words fit: 35 + 4 + 35 = 74
        int lines = ElementSizer.simulateWordWrap(label, wordWidths, spaceWidth, 200);
        assertEquals(1, lines);
    }

    @Test
    public void simulateWordWrap_wrapAtSecondWord() {
        String label = "Word1 Word2";
        int[] wordWidths = { 50, 50 };
        int spaceWidth = 4;
        // Target width 80 — Word1 (50) fits, Word2 won't fit (50+4+50=104 > 80)
        int lines = ElementSizer.simulateWordWrap(label, wordWidths, spaceWidth, 80);
        assertEquals(2, lines);
    }

    @Test
    public void simulateWordWrap_multipleWraps() {
        String label = "One Two Three Four";
        int[] wordWidths = { 30, 30, 40, 30 };
        int spaceWidth = 4;
        // Target width 60:
        // Line 1: "One Two" = 30 + 4 + 30 = 64 > 60 → wrap after "One" (30)
        // Actually: "One" fits (30), "Two" doesn't fit (30+4+30=64>60) → new line
        // Line 2: "Two" (30), "Three" doesn't fit (30+4+40=74>60) → new line
        // Line 3: "Three" (40), "Four" doesn't fit (40+4+30=74>60) → new line
        // Line 4: "Four" (30)
        int lines = ElementSizer.simulateWordWrap(label, wordWidths, spaceWidth, 60);
        assertEquals(4, lines);
    }

    @Test
    public void simulateWordWrap_emptyWordsArray() {
        int lines = ElementSizer.simulateWordWrap("", new int[0], 4, 100);
        assertEquals(1, lines);
    }

    // ---- computeLabelHeightFromMetrics ----

    @Test
    public void computeLabelHeight_singleLine_returnsApprox25() {
        // "Server" at wide element (200px) — fits single line
        String label = "Application Server";
        // Words: Application=70, Server=42 → single-line width = 70+4+42 = 116
        ElementSizer.FontMetrics metrics = metricsFor(label, 70, 42);
        // Element width 200, content width = 200 - 30 (HORIZONTAL_PADDING) = 170 — fits in one line
        int height = ElementSizer.computeLabelHeightFromMetrics(label, metrics, 200);
        // Expected: 1 * 14 + 6 + 5 = 25
        assertEquals(25, height);
    }

    @Test
    public void computeLabelHeight_multiLine_exceedsDefault() {
        // "Payment Processing Engine" at 142px width — wraps to 2+ lines
        String label = "Payment Processing Engine";
        // Words: Payment=49, Processing=70, Engine=42
        ElementSizer.FontMetrics metrics = metricsFor(label, 49, 70, 42);
        // Element width 142, content width = 142 - 30 = 112
        // Line 1: "Payment" (49), "Processing" fits? 49+4+70=123 > 112 → wrap
        // Line 2: "Processing" (70), "Engine" fits? 70+4+42=116 > 112 → wrap
        // Line 3: "Engine" (42)
        // → 3 lines
        int height = ElementSizer.computeLabelHeightFromMetrics(label, metrics, 142);
        // Expected: 3 * 14 + 6 + 5 = 53
        assertTrue("Multi-line label height should exceed 25px: " + height, height > 25);
        assertEquals(53, height);
    }

    @Test
    public void computeLabelHeight_nullLabel_returnsDefault25() {
        int height = ElementSizer.computeLabelHeightFromMetrics(null, null, 200);
        assertEquals(ElementSizer.DEFAULT_LABEL_HEIGHT, height);
    }

    @Test
    public void computeLabelHeight_emptyLabel_returnsDefault25() {
        int height = ElementSizer.computeLabelHeightFromMetrics("", null, 200);
        assertEquals(ElementSizer.DEFAULT_LABEL_HEIGHT, height);
    }

    @Test
    public void computeLabelHeight_veryLongName_threeOrMoreLines() {
        // "Enterprise Application Integration Platform" at narrow width
        String label = "Enterprise Application Integration Platform";
        // Words: Enterprise=63, Application=70, Integration=70, Platform=49
        ElementSizer.FontMetrics metrics = metricsFor(label, 63, 70, 70, 49);
        // Element width 120, content width = 120 - 30 = 90
        // Line 1: "Enterprise" (63), "Application" 63+4+70=137>90 → wrap
        // Line 2: "Application" (70), "Integration" 70+4+70=144>90 → wrap
        // Line 3: "Integration" (70), "Platform" 70+4+49=123>90 → wrap
        // Line 4: "Platform" (49)
        // → 4 lines
        int height = ElementSizer.computeLabelHeightFromMetrics(label, metrics, 120);
        // Expected: 4 * 14 + 6 + 5 = 67
        assertEquals(67, height);
        assertTrue("Should be proportionally taller than 2-line", height > 53);
    }

    @Test
    public void computeLabelHeight_wideElement_singleLine() {
        // Same long name but at very wide element — fits single line
        String label = "Payment Processing Engine";
        ElementSizer.FontMetrics metrics = metricsFor(label, 49, 70, 42);
        // Element width 300, content width = 300 - 30 = 270
        // Single line: 49+4+70+4+42 = 169 < 270 → 1 line
        int height = ElementSizer.computeLabelHeightFromMetrics(label, metrics, 300);
        assertEquals(25, height);
    }

    @Test
    public void computeLabelHeight_zeroOrNegativeContentWidth_returnsDefault() {
        String label = "Test Label Here!";
        ElementSizer.FontMetrics metrics = metricsFor(label, 28, 35, 28, 7);
        // Element width = HORIZONTAL_PADDING exactly → content width = 0
        int height = ElementSizer.computeLabelHeightFromMetrics(label, metrics, ElementSizer.HORIZONTAL_PADDING);
        assertEquals(ElementSizer.DEFAULT_LABEL_HEIGHT, height);
    }

    @Test
    public void computeLabelHeight_shortName_returnsDefault() {
        // Names <= SHORT_NAME_THRESHOLD (15 chars) should fast-path to DEFAULT_LABEL_HEIGHT
        // "DB" is 2 chars — well under threshold
        // computeLabelHeightFromMetrics still needs to handle short names for direct calls
        String label = "DB Server";  // 9 chars, under threshold
        ElementSizer.FontMetrics metrics = metricsFor(label, 14, 42);
        int height = ElementSizer.computeLabelHeightFromMetrics(label, metrics, 200);
        // Single line: 1 * 14 + 6 + 5 = 25
        assertEquals(25, height);
    }

    // --- fitTextBoxHeightToContent (notes + group label-band) -----------------------------
    //
    // View title-note autosize — pure-unit coverage.
    // Pattern mirrors computeLabelHeight tests above: fixed FontMetrics records (no SWT).
    //
    // Constants used by callers:
    //   notes:  minHeight = DEFAULT_NOTE_HEIGHT (80),   maxHeight = MAX_NOTE_HEIGHT (600)
    //   groups: minHeight = DEFAULT_GROUP_HEIGHT (200), maxHeight = MAX_GROUP_LABEL_BAND (800)
    //   padding = LABEL_VERTICAL_PADDING (6)
    //   lineHeight in test metrics = 14 (matches the production SWT system font reasonably)

    private static final int NOTE_MIN = 80;    // mirrors DEFAULT_NOTE_HEIGHT
    private static final int NOTE_MAX = 600;   // mirrors MAX_NOTE_HEIGHT
    private static final int GROUP_MIN = 200;  // mirrors DEFAULT_GROUP_HEIGHT
    private static final int GROUP_MAX = 800;  // mirrors MAX_GROUP_LABEL_BAND
    private static final int PADDING = 6;      // mirrors LABEL_VERTICAL_PADDING

    @Test
    public void fitTextBoxHeight_emptyText_returnsMinHeight_note() {
        // empty content → note height ≈ DEFAULT_NOTE_HEIGHT
        int h = ElementSizer.fitTextBoxHeightToContentFromMetrics(
                "", 185, metricsFor(""), PADDING, NOTE_MIN, NOTE_MAX);
        assertEquals(NOTE_MIN, h);
    }

    @Test
    public void fitTextBoxHeight_nullText_returnsMinHeight() {
        int h = ElementSizer.fitTextBoxHeightToContentFromMetrics(
                null, 185, metricsFor("ignored", 0), PADDING, NOTE_MIN, NOTE_MAX);
        assertEquals(NOTE_MIN, h);
    }

    @Test
    public void fitTextBoxHeight_nullMetrics_returnsMinHeight() {
        // Defensive: pure-geometry overload must not NPE on null metrics
        int h = ElementSizer.fitTextBoxHeightToContentFromMetrics(
                "Some text", 185, null, PADDING, NOTE_MIN, NOTE_MAX);
        assertEquals(NOTE_MIN, h);
    }

    @Test
    public void fitTextBoxHeight_zeroWidth_returnsMinHeight() {
        int h = ElementSizer.fitTextBoxHeightToContentFromMetrics(
                "Some text", 0, metricsFor("Some text", 50, 28), PADDING, NOTE_MIN, NOTE_MAX);
        assertEquals(NOTE_MIN, h);
    }

    @Test
    public void fitTextBoxHeight_negativeWidth_returnsMinHeight() {
        int h = ElementSizer.fitTextBoxHeightToContentFromMetrics(
                "Some text", -10, metricsFor("Some text", 50, 28), PADDING, NOTE_MIN, NOTE_MAX);
        assertEquals(NOTE_MIN, h);
    }

    @Test
    public void fitTextBoxHeight_oneLine_clampsUpToMinHeight() {
        // 1-line content → returns minHeight (floor preserved, no shrink below default)
        // lineCount=1, lineHeight=14, padding=6 → raw height = 20 → clamp up to NOTE_MIN (80)
        String text = "Title";
        ElementSizer.FontMetrics metrics = metricsFor(text, 35);
        int h = ElementSizer.fitTextBoxHeightToContentFromMetrics(
                text, 185, metrics, PADDING, NOTE_MIN, NOTE_MAX);
        assertEquals(NOTE_MIN, h);
    }

    @Test
    public void fitTextBoxHeight_sixLineWrap_growsBeyondMinHeight_retailBankTitle() {
        // The Retail Bank prompt's title note ("A — Business Architecture: …banking products")
        // when rendered at DEFAULT_NOTE_WIDTH (185) wraps to ~6 lines and should grow past
        // DEFAULT_NOTE_HEIGHT (80).
        //
        // We synthesize 6 wraps at width=185 by giving 6 wordWidths of 170 each:
        //   line 1: word(170) fits in 185 → next word 170+4+170=344>185 → wrap
        //   ...repeated 6 times → 6 lines
        String text = "Word1 Word2 Word3 Word4 Word5 Word6";
        ElementSizer.FontMetrics metrics = metricsFor(text, 170, 170, 170, 170, 170, 170);

        int h = ElementSizer.fitTextBoxHeightToContentFromMetrics(
                text, 185, metrics, PADDING, NOTE_MIN, NOTE_MAX);
        // Expected: 6 lines × 14 + 6 padding = 90  (> NOTE_MIN=80)
        assertEquals(90, h);
        assertTrue("6-line wrapped title height should exceed NOTE_MIN: " + h, h > NOTE_MIN);
    }

    @Test
    public void fitTextBoxHeight_pathologicalLong_clampsToMaxHeight() {
        // Pathological wall-of-text degrades to maxHeight (not the canvas blowing up).
        // 100 wordWidths of 170 each → 100 lines → raw 100*14 + 6 = 1406, clamp to NOTE_MAX (600)
        StringBuilder text = new StringBuilder();
        int[] wordWidths = new int[100];
        for (int i = 0; i < 100; i++) {
            text.append("Word").append(i).append(' ');
            wordWidths[i] = 170;
        }
        ElementSizer.FontMetrics metrics = metricsFor(text.toString().trim(), wordWidths);

        int h = ElementSizer.fitTextBoxHeightToContentFromMetrics(
                text.toString().trim(), 185, metrics, PADDING, NOTE_MIN, NOTE_MAX);
        assertEquals(NOTE_MAX, h);
    }

    @Test
    public void fitTextBoxHeight_groupShortLabel_returnsGroupMinHeight() {
        // Helper-side analog: short group label at width=300 should clamp up to
        // DEFAULT_GROUP_HEIGHT (200), not return 200+ε. The accessor caller then
        // short-circuits to DEFAULT_GROUP_HEIGHT literally — this test pins the floor.
        String label = "Banking Products";
        ElementSizer.FontMetrics metrics = metricsFor(label, 56, 56);  // fits in 1 line at 300

        int h = ElementSizer.fitTextBoxHeightToContentFromMetrics(
                label, 300, metrics, PADDING, GROUP_MIN, GROUP_MAX);
        // Raw height: 1*14 + 6 = 20, clamps up to GROUP_MIN (200). Must be exactly 200.
        assertEquals(GROUP_MIN, h);
    }

    @Test
    public void fitTextBoxHeight_groupLongLabel_growsBeyondGroupMinHeight() {
        // Long group label at default width forces label band > DEFAULT_GROUP_HEIGHT.
        // Need raw height > 200 → lineCount * 14 + 6 > 200 → lineCount >= 14 lines.
        StringBuilder label = new StringBuilder();
        int[] wordWidths = new int[14];
        for (int i = 0; i < 14; i++) {
            label.append("VeryLongDescriptiveWord ");
            wordWidths[i] = 290;  // each fills the 300-wide group on its own
        }
        ElementSizer.FontMetrics metrics = metricsFor(label.toString().trim(), wordWidths);

        int h = ElementSizer.fitTextBoxHeightToContentFromMetrics(
                label.toString().trim(), 300, metrics, PADDING, GROUP_MIN, GROUP_MAX);
        // Expected: 14 lines × 14 + 6 = 202 > GROUP_MIN (200)
        assertEquals(202, h);
        assertTrue("Long group label must grow beyond GROUP_MIN: " + h, h > GROUP_MIN);
    }

    @Test
    public void fitTextBoxHeight_widerBox_fewerLines() {
        // Pinning behaviour: same text at wider width wraps fewer times → smaller height.
        String text = "Word1 Word2 Word3";
        ElementSizer.FontMetrics metrics = metricsFor(text, 100, 100, 100);

        // Width=110: each word forces wrap (100+4+100=204>110) → 3 lines → 3*14+6=48 → clamp to 80
        int hNarrow = ElementSizer.fitTextBoxHeightToContentFromMetrics(
                text, 110, metrics, PADDING, NOTE_MIN, NOTE_MAX);
        // Width=400: all three fit (100+4+100+4+100=308<400) → 1 line → 14+6=20 → clamp to 80
        int hWide = ElementSizer.fitTextBoxHeightToContentFromMetrics(
                text, 400, metrics, PADDING, NOTE_MIN, NOTE_MAX);

        // Both clamp to NOTE_MIN — the pin verifies the formula direction (wider → fewer lines)
        // before clamp. Verify with a config that breaches the floor:
        ElementSizer.FontMetrics tallMetrics = metricsFor(text, 100, 100, 100);
        int hRawNarrow = ElementSizer.fitTextBoxHeightToContentFromMetrics(
                text, 110, tallMetrics, PADDING, 0, NOTE_MAX);  // minHeight=0 disables floor
        int hRawWide = ElementSizer.fitTextBoxHeightToContentFromMetrics(
                text, 400, tallMetrics, PADDING, 0, NOTE_MAX);
        assertEquals("narrow → 3 lines pre-floor", 3 * 14 + 6, hRawNarrow);
        assertEquals("wide → 1 line pre-floor", 1 * 14 + 6, hRawWide);
        assertTrue("hNarrow == hWide once both hit NOTE_MIN floor", hNarrow == NOTE_MIN && hWide == NOTE_MIN);
    }

    @Test
    public void fitTextBoxHeight_minHeightAlone_clampsBelowMaxHeight() {
        // Sanity: if minHeight > maxHeight (invalid but defensive), function returns minHeight
        // (the Math.max wins over Math.min). Documents the implementation choice — minHeight
        // semantically takes precedence when the band is invalid.
        int h = ElementSizer.fitTextBoxHeightToContentFromMetrics(
                "Anything", 100, metricsFor("Anything", 50), PADDING, 500, 200);
        // raw = 14+6=20, Math.min(200, 20)=20, Math.max(500, 20)=500
        assertEquals(500, h);
    }

    // --- computeWrapFitDimensions (view-embedded function box-sizing for wrap) ---------
    //
    // Compact wrap-fit: keep the box WIDTH, grow HEIGHT so the label wraps to a 2nd line and fits.
    // Formula: contentWidth = fixedWidth − HORIZONTAL_PADDING(30);
    //          lineCount = simulateWordWrap(…, contentWidth);
    //          height = lineCount × lineHeight + VERTICAL_PADDING(20); width = fixedWidth (verbatim).
    // RED-ON-REVERT: if this is ever swapped back to computeDimensions (single-line widening),
    // the width assertions break (computeDimensions returns a different, wider width).

    @Test
    public void computeWrapFitDimensions_shouldKeepWidthAndGrowHeight_whenLabelWrapsTwoLines() {
        // Simplified 3-word analogue of the View-G fixture shape (the real label "Mobile Payments &
        // Transfers" is 4 tokens; this synthetic 3-word/[60,50,55] case is self-consistent and keeps
        // the wrap arithmetic readable). A 150-wide box, ~165px single-line text:
        // content = 150−30 = 120. Words 60/50/55: 60, 60+4+50=114 (fits), 114+4+55=173>120 → wrap → 2 lines.
        ElementSizer.FontMetrics metrics = metricsFor("Mobile Payments Transfers", 60, 50, 55);
        int[] size = ElementSizer.computeWrapFitDimensionsFromMetrics("Mobile Payments Transfers", metrics, 150);
        assertEquals("width must be preserved verbatim (the grid pitch)", 150, size[0]);
        assertEquals("height = 2 lines × 14 + 20", 2 * 14 + 20, size[1]); // 48
        assertTrue("grew well past the clipped 26px box, clears the live 2-line wrap threshold (>=40)",
                size[1] >= 40);
    }

    @Test
    public void computeWrapFitDimensions_shouldKeepWidthAndStaySingleLine_whenLabelFits() {
        // Wide box → label fits single line → 1 line; width still preserved verbatim.
        ElementSizer.FontMetrics metrics = metricsFor("Payment Processing Engine", 49, 70, 42);
        int[] size = ElementSizer.computeWrapFitDimensionsFromMetrics("Payment Processing Engine", metrics, 300);
        assertEquals("width preserved verbatim", 300, size[0]);
        assertEquals("height = 1 line × 14 + 20", 1 * 14 + 20, size[1]); // 34
    }

    @Test
    public void computeWrapFitDimensions_shouldReturnFixedWidthAndDefaultHeight_whenLabelNull() {
        int[] size = ElementSizer.computeWrapFitDimensionsFromMetrics(null, null, 150);
        assertEquals(150, size[0]);
        assertEquals(ElementSizer.DEFAULT_HEIGHT, size[1]);
    }

    @Test
    public void computeWrapFitDimensions_shouldReturnDefaultHeight_whenContentWidthNonPositive() {
        // fixedWidth == HORIZONTAL_PADDING → contentWidth = 0 → no useful wrap → default height,
        // width still returned verbatim.
        ElementSizer.FontMetrics metrics = metricsFor("Some Label Here", 28, 35, 28);
        int[] size = ElementSizer.computeWrapFitDimensionsFromMetrics(
                "Some Label Here", metrics, ElementSizer.HORIZONTAL_PADDING);
        assertEquals(ElementSizer.HORIZONTAL_PADDING, size[0]);
        assertEquals(ElementSizer.DEFAULT_HEIGHT, size[1]);
    }

    @Test
    public void computeWrapFitDimensions_publicEmptyLabel_returnsFixedWidthDefaultHeight() {
        // Public method short-circuits on empty BEFORE any SWT measurement — safe headlessly.
        int[] size = ElementSizer.computeWrapFitDimensions("", 150);
        assertEquals(150, size[0]);
        assertEquals(ElementSizer.DEFAULT_HEIGHT, size[1]);
    }

    @Test
    public void computeWrapFitDimensions_multiLineLabel_growsProportionally() {
        // Narrow box: content = 120−30 = 90; each 70px word forces its own line → 4 lines.
        ElementSizer.FontMetrics metrics = metricsFor("Segmentation And Personalisation Service", 70, 70, 70, 70);
        int[] size = ElementSizer.computeWrapFitDimensionsFromMetrics(
                "Segmentation And Personalisation Service", metrics, 120);
        assertEquals(120, size[0]);
        assertEquals("height = 4 lines × 14 + 20", 4 * 14 + 20, size[1]); // 76
        assertTrue("taller than the 2-line case", size[1] > (2 * 14 + 20));
    }
}
