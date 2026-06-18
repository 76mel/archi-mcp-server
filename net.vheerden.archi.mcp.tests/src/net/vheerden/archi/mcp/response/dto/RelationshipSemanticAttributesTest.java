package net.vheerden.archi.mcp.response.dto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

/**
 * Unit tests for {@link RelationshipSemanticAttributes} (G1).
 *
 * <p>Pure JUnit (no OSGi/EMF) — exercises the record + Jackson serialisation.</p>
 */
public class RelationshipSemanticAttributesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void shouldDefaultAllFieldsToNull_AC5() {
        RelationshipSemanticAttributes none = RelationshipSemanticAttributes.NONE;
        assertNull(none.accessType());
        assertNull(none.associationDirected());
        assertNull(none.influenceStrength());
    }

    @Test
    public void shouldHasAnyReturnFalseForNone_AC5() {
        assertFalse(RelationshipSemanticAttributes.NONE.hasAny());
        assertFalse(new RelationshipSemanticAttributes(null, null, null).hasAny());
    }

    @Test
    public void shouldHasAnyReturnTrueWhenAnyFieldSet_AC5() {
        assertTrue(new RelationshipSemanticAttributes("read", null, null).hasAny());
        assertTrue(new RelationshipSemanticAttributes(null, Boolean.TRUE, null).hasAny());
        assertTrue(new RelationshipSemanticAttributes(null, null, "+").hasAny());
        assertTrue(new RelationshipSemanticAttributes("read", Boolean.FALSE, "-").hasAny());
    }

    @Test
    public void shouldSerialiseAccessTypeOnly_omittingOthers_AC5() throws Exception {
        RelationshipSemanticAttributes attrs =
                new RelationshipSemanticAttributes("read", null, null);
        String json = mapper.writeValueAsString(attrs);
        // NON_NULL discipline — only the populated field should appear.
        assertTrue("expected accessType key in JSON: " + json, json.contains("\"accessType\":\"read\""));
        assertFalse("associationDirected should be omitted: " + json,
                json.contains("associationDirected"));
        assertFalse("influenceStrength should be omitted: " + json,
                json.contains("influenceStrength"));
    }

    @Test
    public void shouldRoundTripJson_AC5() throws Exception {
        RelationshipSemanticAttributes original =
                new RelationshipSemanticAttributes("write", Boolean.TRUE, "+/-");
        String json = mapper.writeValueAsString(original);
        RelationshipSemanticAttributes parsed =
                mapper.readValue(json, RelationshipSemanticAttributes.class);
        assertEquals(original, parsed);
    }
}
