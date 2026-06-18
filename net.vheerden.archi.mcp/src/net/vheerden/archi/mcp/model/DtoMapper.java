package net.vheerden.archi.mcp.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IProfile;
import com.archimatetool.model.IProperty;

import net.vheerden.archi.mcp.response.dto.ElementDto;
import net.vheerden.archi.mcp.response.dto.RelationshipDto;
import net.vheerden.archi.mcp.response.dto.ViewDto;

/**
 * Pure {@code EObject -> DTO} mappers for elements, views, relationships, and
 * properties.
 *
 * <p>Extracted from ArchiModelAccessorImpl to improve cohesion. Every method is a
 * pure function over the EMF object(s) it is handed — no model spine, no shared
 * mutable state, no instance back-reference to the accessor. Values that require
 * cross-cluster helpers retained by the accessor (the resolved layer string and
 * the relationship semantic attributes) are passed in by the caller.</p>
 *
 * <p>Package-visible — only ArchiModelAccessorImpl should use this class.</p>
 */
final class DtoMapper {

    private static final Logger logger = LoggerFactory.getLogger(DtoMapper.class);

    private DtoMapper() {}

    /**
     * Converts a relationship to a search-enriched DTO with documentation, properties,
     * and resolved source/target element names. The semantic-attribute values
     * (accessType / associationDirected / influenceStrength) are computed by the
     * caller and passed in.
     */
    static RelationshipDto convertToSearchRelationshipDto(IArchimateRelationship relationship,
            String accessType, Boolean associationDirected, String influenceStrength) {
        String documentation = relationship.getDocumentation();
        if (documentation != null && documentation.isEmpty()) {
            documentation = null; // normalize empty to null for @JsonInclude(NON_NULL)
        }

        List<Map<String, String>> properties = null;
        if (relationship.getProperties() != null && !relationship.getProperties().isEmpty()) {
            properties = new ArrayList<>();
            for (IProperty prop : relationship.getProperties()) {
                Map<String, String> propMap = new LinkedHashMap<>();
                propMap.put("key", prop.getKey());
                propMap.put("value", prop.getValue());
                properties.add(propMap);
            }
        }

        String sourceName = relationship.getSource() != null ? relationship.getSource().getName() : null;
        String targetName = relationship.getTarget() != null ? relationship.getTarget().getName() : null;

        IProfile searchRelProfile = relationship.getPrimaryProfile();
        String searchRelSpec = (searchRelProfile != null) ? searchRelProfile.getName() : null;

        return new RelationshipDto(
                relationship.getId(),
                relationship.getName(),
                relationship.eClass().getName(),
                searchRelSpec,
                relationship.getSource() != null ? relationship.getSource().getId() : null,
                relationship.getTarget() != null ? relationship.getTarget().getId() : null,
                false,
                documentation,
                properties,
                sourceName,
                targetName,
                // surface semantic attributes through search read-side too
                accessType,
                associationDirected,
                influenceStrength);
    }

    /**
     * Builds an ElementDto with an explicit specialization override, for use when the
     * profile-assignment command has not yet executed. The resolved layer string is
     * passed in by the caller.
     */
    static ElementDto buildElementDtoWithSpecialization(IArchimateElement element,
            String specialization, String layer) {
        String type = element.eClass().getName();
        List<Map<String, String>> properties = convertProperties(element.getProperties());
        String documentation = element.getDocumentation();
        if (documentation != null && documentation.isEmpty()) {
            documentation = null;
        }
        return ElementDto.standard(
                element.getId(),
                element.getName(),
                type,
                specialization,
                layer,
                documentation,
                properties.isEmpty() ? null : properties);
    }

    /**
     * Builds a ViewDto from an IArchimateDiagramModel, resolving the folder path
     * from the view's container.
     */
    static ViewDto buildViewDto(IArchimateDiagramModel view) {
        String folderPath = null;
        if (view.eContainer() instanceof IFolder parentFolder) {
            folderPath = FolderOperations.buildFolderPath(parentFolder);
        }
        return buildViewDto(view, folderPath);
    }

    /**
     * Builds a ViewDto from an IArchimateDiagramModel with an explicit folder path.
     * Shared by {@link #buildViewDto(IArchimateDiagramModel)} and the view-collection
     * read path to avoid duplicating viewpoint/documentation normalization and
     * property extraction logic.
     */
    static ViewDto buildViewDto(IArchimateDiagramModel view, String folderPath) {
        String vp = view.getViewpoint();
        if (vp != null && vp.isEmpty()) {
            vp = null;
        }
        String doc = view.getDocumentation();
        if (doc != null && doc.isEmpty()) {
            doc = null;
        }
        Map<String, String> props = null;
        if (view.getProperties() != null && !view.getProperties().isEmpty()) {
            props = new LinkedHashMap<>();
            for (IProperty p : view.getProperties()) {
                props.put(p.getKey(), p.getValue());
            }
        }
        String routerType = mapConnectionRouterType(view.getConnectionRouterType());
        return new ViewDto(view.getId(), view.getName(), vp, routerType, folderPath, doc, props);
    }

    static List<Map<String, String>> convertProperties(
            org.eclipse.emf.common.util.EList<IProperty> properties) {
        if (properties == null || properties.isEmpty()) {
            return List.of();
        }
        List<Map<String, String>> result = new ArrayList<>();
        for (IProperty prop : properties) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("key", prop.getKey());
            entry.put("value", prop.getValue());
            result.add(entry);
        }
        return result;
    }

    /**
     * Maps an Archi EMF router type int to an MCP string.
     * Returns null for the default (manual/bendpoint) to keep responses compact.
     */
    static String mapConnectionRouterType(int routerType) {
        if (routerType == IDiagramModel.CONNECTION_ROUTER_MANHATTAN) {
            return "manhattan";
        }
        if (routerType != IDiagramModel.CONNECTION_ROUTER_BENDPOINT) {
            logger.warn("Unknown connection router type value: {}. "
                    + "Treating as default (manual).", routerType);
        }
        return null;
    }
}
