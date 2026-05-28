package net.vheerden.archi.mcp.response.dto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests for {@link AutoRouteResultDto} (Story 13-7, backlog-b14, RoutingPreconditions.AutoRouteStructuredWarning).
 */
public class AutoRouteResultDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void shouldStoreNudgedElements_whenFullConstructor() {
        List<NudgedElementDto> nudged = List.of(
                new NudgedElementDto("vo-1", "El A", 50, 0),
                new NudgedElementDto("vo-2", "El B", 0, -40));

        AutoRouteResultDto dto = new AutoRouteResultDto(
                "v-1", 8, 0, "orthogonal", false, 2,
                List.of(), List.of(), List.of(), List.of(), nudged);

        assertEquals("v-1", dto.viewId());
        assertEquals(8, dto.connectionsRouted());
        assertEquals(0, dto.connectionsFailed());
        assertEquals("orthogonal", dto.strategy());
        assertFalse(dto.routerTypeSwitched());
        assertEquals(2, dto.labelsOptimized());
        assertEquals(2, dto.nudgedElements().size());
        assertEquals("vo-1", dto.nudgedElements().get(0).viewObjectId());
        assertEquals(50, dto.nudgedElements().get(0).deltaX());
    }

    @Test
    public void shouldDefaultNudgedElementsToEmptyList_whenNullPassed() {
        AutoRouteResultDto dto = new AutoRouteResultDto(
                "v-1", 5, 0, "orthogonal", false, 0,
                null, null, null, null, null);

        assertTrue(dto.nudgedElements().isEmpty());
        assertTrue(dto.warnings().isEmpty());
        assertTrue(dto.failed().isEmpty());
        assertTrue(dto.recommendations().isEmpty());
        assertTrue(dto.violations().isEmpty());
    }

    @Test
    public void shouldDefaultNudgedElementsToEmpty_whenConvenienceConstructor() {
        AutoRouteResultDto dto = new AutoRouteResultDto(
                "v-1", 5, "orthogonal", false);

        assertTrue(dto.nudgedElements().isEmpty());
        assertEquals(0, dto.connectionsFailed());
    }

    @Test
    public void shouldDefaultNudgedElementsToEmpty_whenTenParamConstructor() {
        AutoRouteResultDto dto = new AutoRouteResultDto(
                "v-1", 5, 2, "orthogonal", false, 1,
                List.of(), List.of(), List.of(), List.of());

        assertTrue(dto.nudgedElements().isEmpty());
        assertEquals(2, dto.connectionsFailed());
    }

    // ---- Crossing delta tests (backlog-b14) ----

    @Test
    public void shouldStoreCrossingFields_whenCanonicalConstructor() {
        AutoRouteResultDto dto = new AutoRouteResultDto(
                "v-1", 5, 0, "orthogonal", false, 1,
                3, 1,
                List.of(), List.of(), List.of(), List.of(), List.of());

        assertEquals(3, dto.crossingsBefore());
        assertEquals(1, dto.crossingsAfter());
    }

    @Test
    public void shouldDefaultCrossingsToZero_whenConvenienceConstructorWithoutCrossings() {
        // 11-param convenience constructor (without crossings, with nudgedElements)
        AutoRouteResultDto dto = new AutoRouteResultDto(
                "v-1", 5, 0, "orthogonal", false, 1,
                List.of(), List.of(), List.of(), List.of(), List.of());

        assertEquals(0, dto.crossingsBefore());
        assertEquals(0, dto.crossingsAfter());
    }

    @Test
    public void shouldDefaultCrossingsToZero_whenMinimalConstructor() {
        AutoRouteResultDto dto = new AutoRouteResultDto(
                "v-1", 5, "orthogonal", false);

        assertEquals(0, dto.crossingsBefore());
        assertEquals(0, dto.crossingsAfter());
    }

    @Test
    public void shouldDefaultCrossingsToZero_whenTenParamConstructor() {
        AutoRouteResultDto dto = new AutoRouteResultDto(
                "v-1", 5, 2, "orthogonal", false, 1,
                List.of(), List.of(), List.of(), List.of());

        assertEquals(0, dto.crossingsBefore());
        assertEquals(0, dto.crossingsAfter());
    }

    @Test
    public void shouldDefaultCrossingsToZero_whenWarningsOnlyConstructor() {
        AutoRouteResultDto dto = new AutoRouteResultDto(
                "v-1", 3, "clear", false, List.of("warning"));

        assertEquals(0, dto.crossingsBefore());
        assertEquals(0, dto.crossingsAfter());
    }

    @Test
    public void shouldDefaultCrossingsToZero_whenNineParamConstructor() {
        // 9-param: without labelsOptimized
        AutoRouteResultDto dto = new AutoRouteResultDto(
                "v-1", 5, 1, "orthogonal", false,
                List.of(), List.of(), List.of(), List.of());

        assertEquals(0, dto.crossingsBefore());
        assertEquals(0, dto.crossingsAfter());
    }

    @Test
    public void shouldDefaultCrossingsToZero_whenEightParamConstructor() {
        // 8-param: without violations
        AutoRouteResultDto dto = new AutoRouteResultDto(
                "v-1", 5, 1, "orthogonal", false,
                List.of(), List.of(), List.of());

        assertEquals(0, dto.crossingsBefore());
        assertEquals(0, dto.crossingsAfter());
    }

    @Test
    public void shouldDefaultCrossingsToZero_whenSevenParamConstructor() {
        // 7-param: without recommendations
        AutoRouteResultDto dto = new AutoRouteResultDto(
                "v-1", 5, 1, "orthogonal", false,
                List.of(), List.of());

        assertEquals(0, dto.crossingsBefore());
        assertEquals(0, dto.crossingsAfter());
    }

    // ---- Resized groups tests (backlog-b15) ----

    @Test
    public void shouldStoreResizedGroups_whenCanonicalConstructor() {
        List<ResizedGroupDto> groups = List.of(
                new ResizedGroupDto("g-1", "Group A", 100, 100, 450, 300));

        AutoRouteResultDto dto = new AutoRouteResultDto(
                "v-1", 5, 0, "orthogonal", false, 1,
                3, 1,
                List.of(), List.of(), List.of(), List.of(), List.of(), groups);

        assertEquals(1, dto.resizedGroups().size());
        assertEquals("g-1", dto.resizedGroups().get(0).viewObjectId());
        assertEquals("Group A", dto.resizedGroups().get(0).groupName());
        assertEquals(450, dto.resizedGroups().get(0).newWidth());
    }

    @Test
    public void shouldDefaultResizedGroupsToEmpty_whenNullPassed() {
        AutoRouteResultDto dto = new AutoRouteResultDto(
                "v-1", 5, 0, "orthogonal", false, 0,
                0, 0, null, null, null, null, null, null);

        assertTrue(dto.resizedGroups().isEmpty());
    }

    @Test
    public void shouldDefaultResizedGroupsToEmpty_when13ParamConstructor() {
        // 13-param constructor (backward compat, no resizedGroups)
        AutoRouteResultDto dto = new AutoRouteResultDto(
                "v-1", 5, 0, "orthogonal", false, 1,
                3, 1,
                List.of(), List.of(), List.of(), List.of(), List.of());

        assertTrue(dto.resizedGroups().isEmpty());
    }

    @Test
    public void shouldDefaultResizedGroupsToEmpty_whenConvenienceConstructor() {
        AutoRouteResultDto dto = new AutoRouteResultDto(
                "v-1", 5, "orthogonal", false);

        assertTrue(dto.resizedGroups().isEmpty());
    }

    // ---- Straight-line crossings tests (backlog-b22) ----

    @Test
    public void shouldStoreStraightLineCrossings_whenCanonicalConstructor() {
        AutoRouteResultDto dto = new AutoRouteResultDto(
                "v-1", 5, 0, "orthogonal", false, 1,
                3, 1, 8,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        assertEquals(3, dto.crossingsBefore());
        assertEquals(1, dto.crossingsAfter());
        assertEquals(8, dto.straightLineCrossings());
    }

    @Test
    public void shouldDefaultStraightLineCrossingsToZero_when14ParamConstructor() {
        // 14-param backward-compat constructor (without straightLineCrossings)
        AutoRouteResultDto dto = new AutoRouteResultDto(
                "v-1", 5, 0, "orthogonal", false, 1,
                3, 1,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        assertEquals(0, dto.straightLineCrossings());
        assertEquals(3, dto.crossingsBefore());
        assertEquals(1, dto.crossingsAfter());
    }

    @Test
    public void shouldDefaultStraightLineCrossingsToZero_whenMinimalConstructor() {
        AutoRouteResultDto dto = new AutoRouteResultDto(
                "v-1", 5, "orthogonal", false);

        assertEquals(0, dto.straightLineCrossings());
    }

    @Test
    public void shouldDefaultStraightLineCrossingsToZero_whenNoCrossingsConstructor() {
        // 11-param: without crossings, with nudgedElements
        AutoRouteResultDto dto = new AutoRouteResultDto(
                "v-1", 5, 0, "orthogonal", false, 1,
                List.of(), List.of(), List.of(), List.of(), List.of());

        assertEquals(0, dto.straightLineCrossings());
    }

    // ---- Structured warnings tests (RoutingPreconditions.AutoRouteStructuredWarning, Row E) ----

    @Test
    public void serialization_emptyStructuredWarnings_shouldOmitField() throws Exception {
        AutoRouteResultDto dto = new AutoRouteResultDto(
                "v-1", 5, "orthogonal", false);

        String json = objectMapper.writeValueAsString(dto);
        assertFalse("structuredWarnings should be omitted when empty per @JsonInclude(NON_EMPTY)",
                json.contains("structuredWarnings"));
        assertTrue("viewId should still be present",
                json.contains("\"viewId\":\"v-1\""));
    }

    @Test
    public void serialization_populatedStructuredWarnings_shouldIncludeField() throws Exception {
        StructuredWarningDto warning = new StructuredWarningDto(
                StructuredWarningCodes.AUTO_NUDGE_SKIPPED_SIBLING_OVERLAP,
                "autoNudge skipped because sibling elements have overlapping bounding boxes.",
                "layout-within-group",
                List.of("id-sibling-A", "id-sibling-B"));

        AutoRouteResultDto dto = new AutoRouteResultDto(
                "v-1", 5, 0, "orthogonal", false, 1,
                3, 1, 8,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(warning));

        String json = objectMapper.writeValueAsString(dto);
        assertTrue("structuredWarnings should be present in JSON",
                json.contains("\"structuredWarnings\""));
        assertTrue("structured warning code should be present",
                json.contains("\"code\":\"AUTO_NUDGE_SKIPPED_SIBLING_OVERLAP\""));
        assertTrue("structured warning remediationTool should be present",
                json.contains("\"remediationTool\":\"layout-within-group\""));
        assertTrue("structured warning remediationViolatorIds should be present (2 elements)",
                json.contains("\"remediationViolatorIds\":[\"id-sibling-A\",\"id-sibling-B\"]"));
    }

    @Test
    public void shouldDefaultStructuredWarningsToEmpty_whenConvenienceConstructor() {
        AutoRouteResultDto dto = new AutoRouteResultDto(
                "v-1", 5, "orthogonal", false);

        assertTrue("convenience constructors must default structuredWarnings to empty",
                dto.structuredWarnings().isEmpty());
    }

    @Test
    public void shouldDefaultStructuredWarningsToEmpty_whenNullPassed() {
        AutoRouteResultDto dto = new AutoRouteResultDto(
                "v-1", 5, 0, "orthogonal", false, 1,
                3, 1, 8,
                null, null, null, null, null, null,
                null);

        assertTrue("compact constructor must null-guard structuredWarnings to empty",
                dto.structuredWarnings().isEmpty());
    }

    // ---- Interior-termination veto sub-count
    //      (backlog-auto-route-terminals-only-interior-termination-veto) ----

    @Test
    public void serialization_zeroVetoedByInterior_shouldOmitField() throws Exception {
        // Canonical constructor, vetoedByInterior = 0 (13th scalar, after vetoedByCrossing).
        AutoRouteResultDto dto = new AutoRouteResultDto(
                "v-1", 5, 0, "orthogonal", false, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of());

        String json = objectMapper.writeValueAsString(dto);
        assertFalse("vetoedByInterior should be omitted when 0 per @JsonInclude(NON_DEFAULT)",
                json.contains("vetoedByInterior"));
        assertFalse("vetoedByZigzag should be omitted when 0 per @JsonInclude(NON_DEFAULT)",
                json.contains("vetoedByZigzag"));
    }

    @Test
    public void serialization_populatedVetoedByInterior_shouldIncludeField() throws Exception {
        // 9 skipped = 0 already-orthogonal + 2 obstacle + 1 crossing + 3 interior + 3 zigzag.
        AutoRouteResultDto dto = new AutoRouteResultDto(
                "v-1", 2, 0, "orthogonal", false, 0, 0, 0, 0, 9, 2, 1, 3, 3,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of());

        assertEquals(3, dto.vetoedByInterior());
        assertEquals(3, dto.vetoedByZigzag());
        String json = objectMapper.writeValueAsString(dto);
        assertTrue("vetoedByInterior should be present in JSON when > 0",
                json.contains("\"vetoedByInterior\":3"));
        assertTrue("vetoedByZigzag should be present in JSON when > 0",
                json.contains("\"vetoedByZigzag\":3"));
    }

    // ---- Blocked recommendations / nudgeBlockedReason tests (Story 14-12) ----

    @Test
    public void shouldOmitBlockedRecommendations_whenEmpty() throws Exception {
        // Canonical pre-14-12 21-arg convenience constructor (Story 14-12).
        // Defaults blockedRecommendations to List.of() and nudgeBlockedReason to null;
        // both must be omitted from JSON per @JsonInclude(NON_EMPTY / NON_NULL).
        AutoRouteResultDto dto = new AutoRouteResultDto(
                "v-1", 5, 0, "orthogonal", false, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of());

        assertTrue("blockedRecommendations must default to empty",
                dto.blockedRecommendations().isEmpty());
        assertEquals("nudgeBlockedReason must default to null", null, dto.nudgeBlockedReason());
        String json = objectMapper.writeValueAsString(dto);
        assertFalse("blockedRecommendations should be omitted when empty per @JsonInclude(NON_EMPTY)",
                json.contains("blockedRecommendations"));
        assertFalse("nudgeBlockedReason should be omitted when null per @JsonInclude(NON_NULL)",
                json.contains("nudgeBlockedReason"));
    }

    @Test
    public void shouldSerializeBlockedRecommendations_whenPopulated() throws Exception {
        // Construct via the new 23-arg canonical constructor with one recommendation
        // surfaced under blockedRecommendations and nudgeBlockedReason populated to
        // the canonical sibling_overlap value (Story 14-12).
        MoveRecommendationDto rec = new MoveRecommendationDto(
                "el-1", "AppServer Cluster", 230, 0,
                "Move 230px east to clear sibling overlap blocking 1 connection",
                1);

        AutoRouteResultDto dto = new AutoRouteResultDto(
                "v-1", 4, 1, "orthogonal", true, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of(), List.of(),
                List.of(),                                   // recommendations (advisory) — empty
                List.of(), List.of(), List.of(),
                List.of(),                                   // structuredWarnings — covered separately
                List.of(rec),                                // blockedRecommendations — populated
                AutoRouteBlockedReasons.SIBLING_OVERLAP);    // nudgeBlockedReason

        assertEquals(1, dto.blockedRecommendations().size());
        assertEquals("el-1", dto.blockedRecommendations().get(0).elementId());
        assertEquals(230, dto.blockedRecommendations().get(0).dx());
        assertEquals("sibling_overlap", dto.nudgeBlockedReason());
        String json = objectMapper.writeValueAsString(dto);
        assertTrue("blockedRecommendations should be present in JSON when populated",
                json.contains("\"blockedRecommendations\""));
        assertTrue("blockedRecommendation elementId should be present",
                json.contains("\"elementId\":\"el-1\""));
        assertTrue("nudgeBlockedReason should be present in JSON when populated",
                json.contains("\"nudgeBlockedReason\":\"sibling_overlap\""));
        assertFalse("recommendations should be omitted (empty advisory path)",
                json.contains("\"recommendations\""));
    }
}
