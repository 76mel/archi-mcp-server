package net.vheerden.archi.mcp.response.dto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests for {@link StructuredWarningDto} (Story
 * RoutingPreconditions.AutoRouteStructuredWarning, Row E).
 */
public class StructuredWarningDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void record_serialization_fullPayload_shouldRoundtrip() throws Exception {
        StructuredWarningDto dto = new StructuredWarningDto(
                StructuredWarningCodes.AUTO_NUDGE_SKIPPED_SIBLING_OVERLAP,
                "autoNudge skipped because sibling elements have overlapping bounding boxes.",
                "layout-within-group",
                List.of("id-element-A", "id-element-B"));

        String json = objectMapper.writeValueAsString(dto);
        assertTrue("code should be serialized",
                json.contains("\"code\":\"AUTO_NUDGE_SKIPPED_SIBLING_OVERLAP\""));
        assertTrue("message should be serialized",
                json.contains("\"message\":\"autoNudge skipped because sibling elements"));
        assertTrue("remediationTool should be serialized",
                json.contains("\"remediationTool\":\"layout-within-group\""));
        assertTrue("remediationViolatorIds should be serialized as an array",
                json.contains("\"remediationViolatorIds\":[\"id-element-A\",\"id-element-B\"]"));

        StructuredWarningDto roundtrip = objectMapper.readValue(json, StructuredWarningDto.class);
        assertEquals(dto.code(), roundtrip.code());
        assertEquals(dto.message(), roundtrip.message());
        assertEquals(dto.remediationTool(), roundtrip.remediationTool());
        assertEquals(dto.remediationViolatorIds(), roundtrip.remediationViolatorIds());
    }

    @Test
    public void record_serialization_emptyRemediationViolatorIds_shouldOmitField() throws Exception {
        StructuredWarningDto dto = new StructuredWarningDto(
                StructuredWarningCodes.AUTO_NUDGE_SKIPPED_SIBLING_OVERLAP,
                "msg",
                "layout-within-group",
                List.of());

        String json = objectMapper.writeValueAsString(dto);
        assertFalse("remediationViolatorIds should be omitted when empty per @JsonInclude(NON_EMPTY)",
                json.contains("remediationViolatorIds"));
        assertTrue("remediationTool should be present",
                json.contains("\"remediationTool\":\"layout-within-group\""));
    }

    @Test
    public void record_serialization_nullRemediationTool_shouldOmitField() throws Exception {
        StructuredWarningDto dto = new StructuredWarningDto(
                "SOME_CODE",
                "msg",
                "",
                List.of("id-A"));

        String json = objectMapper.writeValueAsString(dto);
        assertFalse("remediationTool should be omitted when empty per @JsonInclude(NON_EMPTY)",
                json.contains("remediationTool"));
        assertTrue("remediationViolatorIds should be present",
                json.contains("\"remediationViolatorIds\":[\"id-A\"]"));
    }

    @Test
    public void record_compactConstructor_nullViolatorIds_shouldDefaultToEmptyList() {
        StructuredWarningDto dto = new StructuredWarningDto(
                StructuredWarningCodes.AUTO_NUDGE_SKIPPED_SIBLING_OVERLAP,
                "msg",
                "layout-within-group",
                null);

        assertNotNull("compact constructor should null-guard remediationViolatorIds",
                dto.remediationViolatorIds());
        assertTrue("null violator-IDs should be replaced with an empty list",
                dto.remediationViolatorIds().isEmpty());
    }

    @Test
    public void codes_constant_value_shouldBeStable() {
        // The canonical AUTO_NUDGE_SKIPPED_SIBLING_OVERLAP code value is part of the
        // MCP response contract and must NEVER drift — once published, codes are
        // never renamed (only deprecated and superseded by a new code). Pin the
        // literal value verbatim per Row E spec.
        assertEquals("AUTO_NUDGE_SKIPPED_SIBLING_OVERLAP",
                StructuredWarningCodes.AUTO_NUDGE_SKIPPED_SIBLING_OVERLAP);
    }
}
