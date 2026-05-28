package net.vheerden.archi.mcp.response.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object for ArchiMate relationships.
 *
 * <p>Represents a relationship between two ArchiMate concepts.
 * Used in view contents, relationship queries, and search results.</p>
 *
 * <p>Fields {@code documentation}, {@code properties}, {@code sourceName},
 * and {@code targetName} are optional — populated only for search results
 * with the {@code full} field preset. They are omitted from JSON when null.</p>
 *
 * <p>Fields {@code accessType}, {@code associationDirected}, and
 * {@code influenceStrength} (Story 14-7 / G1) are populated only when the
 * relationship is the matching ArchiMate subtype: {@code accessType} for
 * {@code AccessRelationship} (one of {@code "access" / "read" / "write" / "readwrite"});
 * {@code associationDirected} for {@code AssociationRelationship} (boxed boolean);
 * {@code influenceStrength} for {@code InfluenceRelationship} (free text, omitted
 * when empty). Omitted from JSON for relationships of other subtypes.</p>
 */
public record RelationshipDto(
    String id,
    String name,
    String type,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String specialization,
    String sourceId,
    String targetId,
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    boolean alreadyExisted,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String documentation,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<Map<String, String>> properties,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String sourceName,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String targetName,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String accessType,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    Boolean associationDirected,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String influenceStrength
) {
    /**
     * Convenience constructor without optional fields (defaults to null/false).
     */
    public RelationshipDto(String id, String name, String type, String sourceId, String targetId) {
        this(id, name, type, null, sourceId, targetId, false, null, null, null, null,
                null, null, null);
    }

    /**
     * Constructor with alreadyExisted flag but no optional search fields.
     */
    public RelationshipDto(String id, String name, String type, String sourceId, String targetId,
                           boolean alreadyExisted) {
        this(id, name, type, null, sourceId, targetId, alreadyExisted, null, null, null, null,
                null, null, null);
    }

    /**
     * Back-compat constructor matching the pre-G1 (11-field) canonical shape.
     * Delegates to the 14-field canonical with {@code null} for the 3 G1 fields.
     */
    public RelationshipDto(String id, String name, String type, String specialization,
                           String sourceId, String targetId, boolean alreadyExisted,
                           String documentation, List<Map<String, String>> properties,
                           String sourceName, String targetName) {
        this(id, name, type, specialization, sourceId, targetId, alreadyExisted,
                documentation, properties, sourceName, targetName,
                null, null, null);
    }
}
