package net.vheerden.archi.mcp.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Headless unit tests for the pure relationship effect-string builder
 * {@link ArchiModelAccessorImpl#formatRelationshipEffect}.
 *
 * <p>The endpoint <em>resolution</em> (id → name) needs the EMF model and is exercised live;
 * this covers the pure <em>assembly</em> on a seam that takes already-resolved display
 * names — including the id-degrade case (the caller substitutes the id for a nameless endpoint, and
 * the formatter renders it verbatim).</p>
 */
public class RelationshipEffectFormatTest {

    @Test
    public void shouldNameBothEndpoints_forCreate() {
        assertEquals(
                "Create ServingRelationship: 'Payment Gateway' → 'Fraud Engine'",
                ArchiModelAccessorImpl.formatRelationshipEffect(
                        "Create", "ServingRelationship", "Payment Gateway", "Fraud Engine", null));
    }

    @Test
    public void shouldAppendCascadeSuffix_forDelete() {
        assertEquals(
                "Delete AssignmentRelationship: 'Actor' → 'Role' (cascade: 2 view connections)",
                ArchiModelAccessorImpl.formatRelationshipEffect(
                        "Delete", "AssignmentRelationship", "Actor", "Role",
                        "(cascade: 2 view connections)"));
    }

    @Test
    public void shouldDegradeToId_whenAnEndpointHasNoName() {
        // The call-site degrades a nameless endpoint to its id; the formatter renders it verbatim.
        String text = ArchiModelAccessorImpl.formatRelationshipEffect(
                "Create", "ServingRelationship", "id-1234", "Fraud Engine", null);
        assertEquals("Create ServingRelationship: 'id-1234' → 'Fraud Engine'", text);
    }

    @Test
    public void shouldNotAddTrailingSpace_whenSuffixBlank() {
        String text = ArchiModelAccessorImpl.formatRelationshipEffect(
                "Create", "ServingRelationship", "A", "B", "   ");
        assertEquals("Create ServingRelationship: 'A' → 'B'", text);
        assertFalse(text.endsWith(" "));
    }

    // ---- visual-connection effect text reuses the same assembly ----

    @Test
    public void shouldNameBothEndpoints_forAddConnectionToView() {
        // add-connection-to-view: the same builder, verb "Add connection", for cross-card
        // consistency with the create-/delete-relationship cards (recommended default).
        assertEquals(
                "Add connection ServingRelationship: 'Mobile Banking App' → 'Open Banking API Gateway'",
                ArchiModelAccessorImpl.formatRelationshipEffect(
                        "Add connection", "ServingRelationship",
                        "Mobile Banking App", "Open Banking API Gateway", null));
    }

    @Test
    public void shouldNameBothEndpoints_forUpdateViewConnection() {
        assertEquals(
                "Update connection FlowRelationship: 'A' → 'B'",
                ArchiModelAccessorImpl.formatRelationshipEffect(
                        "Update connection", "FlowRelationship", "A", "B", null));
    }

    @Test
    public void shouldDegradeToId_forNamelessVisualConnectionEndpoint() {
        // The call-site degrades a nameless endpoint to the relationship id; rendered verbatim.
        String text = ArchiModelAccessorImpl.formatRelationshipEffect(
                "Add connection", "ServingRelationship", "rel-42", "Fraud Engine", null);
        assertEquals("Add connection ServingRelationship: 'rel-42' → 'Fraud Engine'", text);
    }

    // ---- view-name clause builders (the id → view-name resolution is EMF and is exercised
    // OSGi-gated in ArchiModelAccessorImplTest; this pins the pure string assembly + degradation). ----

    @Test
    public void viewNameClause_shouldSpliceQuotedNameAfterDanglingViewToken() {
        // Production splices this directly after a "... to view" / "... from view" token.
        assertEquals(" 'Main View'", ArchiModelAccessorImpl.viewNameClause("Main View"));
        assertEquals("Add group 'X' to view 'Main View'",
                "Add group 'X' to view" + ArchiModelAccessorImpl.viewNameClause("Main View"));
    }

    @Test
    public void viewNameClause_shouldDegradeToEmpty_whenNameNullOrBlank() {
        // never "view ''" — the base text stays exactly as it reads today.
        assertEquals("", ArchiModelAccessorImpl.viewNameClause(null));
        assertEquals("", ArchiModelAccessorImpl.viewNameClause(""));
        assertEquals("", ArchiModelAccessorImpl.viewNameClause("   "));
        assertEquals("Add group 'X' to view",
                "Add group 'X' to view" + ArchiModelAccessorImpl.viewNameClause(null));
    }

    @Test
    public void viewPhrase_shouldBuildPrepositionalClause_whenNameUsable() {
        assertEquals("to view 'Main View'", ArchiModelAccessorImpl.viewPhrase("Main View", "to"));
        assertEquals("in view 'Main View'", ArchiModelAccessorImpl.viewPhrase("Main View", "in"));
    }

    @Test
    public void viewPhrase_shouldDegradeToNull_whenNameNullOrBlank() {
        // null suffix → formatRelationshipEffect adds no clause (for the connection effects).
        assertNull(ArchiModelAccessorImpl.viewPhrase(null, "to"));
        assertNull(ArchiModelAccessorImpl.viewPhrase("", "in"));
        assertNull(ArchiModelAccessorImpl.viewPhrase("  ", "to"));
    }

    @Test
    public void connectionEffect_shouldCarryEndpointNamesAndViewClause_viaSuffix() {
        // the connection effectDescription carries BOTH endpoint names and the view name.
        assertEquals(
                "Add connection ServingRelationship: 'A' → 'B' to view 'Main View'",
                ArchiModelAccessorImpl.formatRelationshipEffect(
                        "Add connection", "ServingRelationship", "A", "B",
                        ArchiModelAccessorImpl.viewPhrase("Main View", "to")));
        assertEquals(
                "Update connection ServingRelationship: 'A' → 'B' in view 'Main View'",
                ArchiModelAccessorImpl.formatRelationshipEffect(
                        "Update connection", "ServingRelationship", "A", "B",
                        ArchiModelAccessorImpl.viewPhrase("Main View", "in")));
    }

    @Test
    public void connectionEffect_shouldOmitViewClause_whenViewUnresolved() {
        // unresolved view → no trailing clause (reads exactly as the original text did).
        assertEquals(
                "Add connection ServingRelationship: 'A' → 'B'",
                ArchiModelAccessorImpl.formatRelationshipEffect(
                        "Add connection", "ServingRelationship", "A", "B",
                        ArchiModelAccessorImpl.viewPhrase(null, "to")));
    }
}
