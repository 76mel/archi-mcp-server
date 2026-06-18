package net.vheerden.archi.mcp.response.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Bundle of optional ArchiMate-relationship semantic attributes shared by
 * {@code create-relationship} and {@code update-relationship}.
 *
 * <p>All three fields are independently optional and type-conditional —
 * each applies only to the matching relationship subtype:
 * {@code accessType} to {@code AccessRelationship},
 * {@code associationDirected} to {@code AssociationRelationship},
 * {@code influenceStrength} to {@code InfluenceRelationship}.
 * The MCP boundary rejects mismatched attribute-type pairings with
 * a clear {@code INVALID_PARAMETER} error.</p>
 *
 * <p>For {@code update-relationship}, {@code null} on any field means
 * "leave unchanged"; a non-null value sets. Empty string is rejected
 * for {@code accessType} (use {@code "access"} for unspecified). For
 * {@code influenceStrength}, empty string CLEARS the underlying EMF value.</p>
 *
 * <p>Mirrors the {@code StylingParams} record-bundle precedent
 * for clusters of conceptually-related additive fields.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RelationshipSemanticAttributes(
        String accessType,            // "access" | "read" | "write" | "readwrite"
        Boolean associationDirected,  // null | true | false
        String influenceStrength      // free text, max 255 chars, "" clears
) {
    /** Sentinel for "no semantic attributes supplied". */
    public static final RelationshipSemanticAttributes NONE =
            new RelationshipSemanticAttributes(null, null, null);

    /** True if any field is non-null. */
    public boolean hasAny() {
        return accessType != null || associationDirected != null || influenceStrength != null;
    }
}
