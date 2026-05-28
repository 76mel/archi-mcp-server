package net.vheerden.archi.mcp.response.dto;

import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;

/**
 * Tests for {@link ViewObjectDto} record.
 */
public class ViewObjectDtoTest {

    @Test
    public void shouldCreateWithAllFields() {
        ViewObjectDto dto = new ViewObjectDto(
                "vo-1", "elem-1", "My Component", "ApplicationComponent",
                100, 200, 120, 55);

        assertEquals("vo-1", dto.viewObjectId());
        assertEquals("elem-1", dto.elementId());
        assertEquals("My Component", dto.elementName());
        assertEquals("ApplicationComponent", dto.elementType());
        assertEquals(100, dto.x());
        assertEquals(200, dto.y());
        assertEquals(120, dto.width());
        assertEquals(55, dto.height());
    }

    @Test
    public void shouldSupportEquality() {
        ViewObjectDto dto1 = new ViewObjectDto(
                "vo-1", "e-1", "Name", "Type", 10, 20, 120, 55);
        ViewObjectDto dto2 = new ViewObjectDto(
                "vo-1", "e-1", "Name", "Type", 10, 20, 120, 55);

        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    // ---- Story 14-1 (G4): labelExpression field tests ----

    @Test
    public void shouldExposeLabelExpression_whenCanonicalConstructorPopulatesIt() {
        ViewObjectDto dto = new ViewObjectDto(
                "vo-1", "e-1", "Name", "Type", 0, 0, 120, 55,
                null, null, null, null, null,
                null, null, null, null, null,
                null, null, null,
                "${name}");

        assertEquals("${name}", dto.labelExpression());
    }

    @Test
    public void shouldDefaultLabelExpressionToNull_whenBackCompatConstructorUsed() {
        // The 21-field back-compat constructor delegates to the canonical 22-field with
        // a trailing null — labelExpression must default to null so add-to-view paths,
        // which do not surface labelExpression, behave identically to before the story.
        ViewObjectDto dto = new ViewObjectDto(
                "vo-1", "e-1", "Name", "Type", 0, 0, 120, 55,
                null, null, null, null, null,
                null, null, null, null, null,
                "rectangular", "left", "centre");

        assertNull(dto.labelExpression());
    }

    @Test
    public void shouldOmitLabelExpressionFromJson_whenNull_AC6() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ViewObjectDto dto = new ViewObjectDto(
                "vo-1", "e-1", "Name", "Type", 0, 0, 120, 55);

        String json = mapper.writeValueAsString(dto);

        assertFalse("Null labelExpression must be omitted via @JsonInclude(NON_NULL)",
                json.contains("labelExpression"));
    }

    @Test
    public void shouldIncludeLabelExpressionInJson_whenPopulated_AC6() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ViewObjectDto dto = new ViewObjectDto(
                "vo-1", "e-1", "Name", "Type", 0, 0, 120, 55,
                null, null, null, null, null,
                null, null, null, null, null,
                null, null, null,
                "${property:Owner}");

        String json = mapper.writeValueAsString(dto);

        assertTrue("Populated labelExpression must round-trip through Jackson",
                json.contains("\"labelExpression\":\"${property:Owner}\""));
    }
}
