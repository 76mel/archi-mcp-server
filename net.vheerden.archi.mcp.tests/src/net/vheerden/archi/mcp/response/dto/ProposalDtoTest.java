package net.vheerden.archi.mcp.response.dto;

import static org.junit.Assert.*;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests for {@link ProposalDto} (effectDescription + intent).
 */
public class ProposalDtoTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void shouldConstructWithAllFields() {
        Map<String, Object> currentState = new LinkedHashMap<>();
        currentState.put("name", "Old Name");
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("name", "New Name");

        ProposalDto dto = new ProposalDto(
                "p-1", "update-element", "pending",
                "Update element: abc123", currentState, changes,
                "Element exists. All changes valid.", "2026-02-24T10:00:00Z");

        assertEquals("p-1", dto.proposalId());
        assertEquals("update-element", dto.tool());
        assertEquals("pending", dto.status());
        assertEquals("Update element: abc123", dto.description());
        assertNotNull(dto.currentState());
        assertEquals("Old Name", dto.currentState().get("name"));
        assertNotNull(dto.proposedChanges());
        assertEquals("New Name", dto.proposedChanges().get("name"));
        assertEquals("Element exists. All changes valid.", dto.validationSummary());
        assertEquals("2026-02-24T10:00:00Z", dto.createdAt());
    }

    @Test
    public void shouldAllowNullCurrentState_forCreateOperations() {
        ProposalDto dto = new ProposalDto(
                "p-2", "create-element", "pending",
                "Create BusinessActor: Node", null,
                Map.of("type", "BusinessActor", "name", "Node"),
                "Type valid.", "2026-02-24T10:01:00Z");

        assertNull(dto.currentState());
        assertNotNull(dto.proposedChanges());
    }

    @Test
    public void shouldAllowRejectedStatus() {
        ProposalDto dto = new ProposalDto(
                "p-3", "create-element", "rejected",
                "Create Node", null, Map.of("name", "Node"),
                "Type valid.", "2026-02-24T10:02:00Z");

        assertEquals("rejected", dto.status());
    }

    // ---- effectDescription + intent ----

    @Test
    public void shouldDefaultNewFieldsToNull_withBackCompatConstructor() {
        ProposalDto dto = new ProposalDto(
                "p-1", "create-element", "pending", "Create Node", null,
                Map.of("name", "Node"), "Type valid.", "2026-02-24T10:00:00Z");
        assertNull(dto.effectDescription());
        assertNull(dto.intent());
    }

    @Test
    public void shouldCarryEffectDescriptionAndIntent_withCanonicalConstructor() {
        ProposalDto dto = new ProposalDto(
                "p-1", "bulk-mutate", "pending", "Bulk", null,
                Map.of("operationCount", 1), "Valid.", "2026-02-24T10:00:00Z",
                "Create ServingRelationship: 'A' → 'B'", "Wire the path");
        assertEquals("Create ServingRelationship: 'A' → 'B'", dto.effectDescription());
        assertEquals("Wire the path", dto.intent());
    }

    @Test
    public void shouldOmitNewFieldsFromJson_whenNull() throws Exception {
        ProposalDto dto = new ProposalDto(
                "p-1", "create-element", "pending", "Create Node", null,
                Map.of("name", "Node"), "Type valid.", "2026-02-24T10:00:00Z");
        String json = MAPPER.writeValueAsString(dto);
        assertFalse("NON_NULL omits a null effectDescription", json.contains("effectDescription"));
        assertFalse("NON_NULL omits a null intent", json.contains("intent"));
    }

    @Test
    public void shouldIncludeNewFieldsInJson_andRoundTrip_whenPresent() throws Exception {
        ProposalDto dto = new ProposalDto(
                "p-1", "bulk-mutate", "pending", "Bulk", null,
                Map.of("operationCount", 1), "Valid.", "2026-02-24T10:00:00Z",
                "Create ServingRelationship: 'A' → 'B'", "Wire the path");
        String json = MAPPER.writeValueAsString(dto);
        assertTrue(json.contains("effectDescription"));
        assertTrue(json.contains("intent"));

        ProposalDto back = MAPPER.readValue(json, ProposalDto.class);
        assertEquals(dto.effectDescription(), back.effectDescription());
        assertEquals(dto.intent(), back.intent());
        assertEquals(dto.tool(), back.tool());
    }
}
