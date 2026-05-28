package net.vheerden.archi.mcp.response.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Data Transfer Object for model summary information.
 *
 * <p>Returned by the get-model-info and update-model commands. Provides a
 * high-level overview of the loaded ArchiMate model including element counts,
 * type distributions, layer distribution, and (Story 14-3 G6) the model's
 * own metadata — purpose and custom properties.</p>
 *
 * <p>Story 14-3 (G6): {@code purpose} and {@code properties} fields added for
 * read-write parity with the new {@code update-model} tool. The {@code documentation}
 * field deliberately does NOT appear here — {@code IArchimateModel} does not extend
 * {@code IDocumentable} in Archi 5.7/5.8; the model's free-text field IS {@code purpose}.</p>
 *
 * <p>{@link JsonInclude.Include#NON_NULL} omits the new fields when at Archi
 * defaults (purpose = null on a freshly-created model, properties = null when
 * the EList is empty), preserving byte-identical legacy {@code get-model-info}
 * responses for callers that never write to those fields.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ModelInfoDto(
    String name,
    String purpose,
    Map<String, String> properties,
    int elementCount,
    int relationshipCount,
    int viewCount,
    int specializationCount,
    Map<String, Integer> elementTypeDistribution,
    Map<String, Integer> relationshipTypeDistribution,
    Map<String, Integer> layerDistribution
) {
    /**
     * Backward-compatible constructor for legacy 8-field call sites that pre-date
     * Story 14-3 (G6). Delegates to the canonical 10-field constructor with
     * {@code purpose} and {@code properties} set to null (Jackson omits them per
     * {@link JsonInclude.Include#NON_NULL}).
     */
    public ModelInfoDto(String name,
            int elementCount, int relationshipCount, int viewCount, int specializationCount,
            Map<String, Integer> elementTypeDistribution,
            Map<String, Integer> relationshipTypeDistribution,
            Map<String, Integer> layerDistribution) {
        this(name, null, null, elementCount, relationshipCount, viewCount,
                specializationCount, elementTypeDistribution,
                relationshipTypeDistribution, layerDistribution);
    }
}
