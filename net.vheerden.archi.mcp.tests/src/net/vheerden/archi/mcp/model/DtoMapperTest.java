package net.vheerden.archi.mcp.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.archimatetool.model.IApplicationComponent;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IFlowRelationship;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IProperty;

import net.vheerden.archi.mcp.response.dto.ElementDto;
import net.vheerden.archi.mcp.response.dto.RelationshipDto;
import net.vheerden.archi.mcp.response.dto.ViewDto;

/**
 * Characterization pin for the pure {@code EObject -> DTO} mappers in {@link DtoMapper}.
 *
 * <p>Pure functions over a bare EMF model built with {@code IArchimateFactory.eINSTANCE};
 * runs without OSGi / a loaded model. These assertions lock the current golden output and
 * must stay green, unchanged, across the extraction move.</p>
 */
public class DtoMapperTest {

    private static final IArchimateFactory F = IArchimateFactory.eINSTANCE;

    private static IProperty prop(String key, String value) {
        IProperty p = F.createProperty();
        p.setKey(key);
        p.setValue(value);
        return p;
    }

    private static Map<String, String> propMap(String key, String value) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("value", value);
        return m;
    }

    // ---- mapConnectionRouterType -------------------------------------------------

    @Test
    public void mapConnectionRouterType_shouldReturnManhattan_whenManhattanConstant() {
        assertEquals("manhattan",
                DtoMapper.mapConnectionRouterType(IDiagramModel.CONNECTION_ROUTER_MANHATTAN));
    }

    @Test
    public void mapConnectionRouterType_shouldReturnNull_whenBendpointConstant() {
        assertNull(DtoMapper.mapConnectionRouterType(IDiagramModel.CONNECTION_ROUTER_BENDPOINT));
    }

    @Test
    public void mapConnectionRouterType_shouldReturnNull_whenUnknownValue() {
        // An unrecognised value also emits a WARN log (observable in test output); this pin
        // covers the return value only — the warn is an intentional, behaviour-preserving side-effect.
        assertNull(DtoMapper.mapConnectionRouterType(999));
    }

    // ---- convertProperties -------------------------------------------------------

    @Test
    public void convertProperties_shouldReturnEmptyList_whenNull() {
        List<Map<String, String>> result = DtoMapper.convertProperties(null);
        assertTrue(result.isEmpty());
    }

    @Test
    public void convertProperties_shouldReturnEmptyList_whenEmpty() {
        IApplicationComponent el = F.createApplicationComponent();
        List<Map<String, String>> result = DtoMapper.convertProperties(el.getProperties());
        assertTrue(result.isEmpty());
    }

    @Test
    public void convertProperties_shouldPreserveOrderAndKeyValueShape_whenNonEmpty() {
        IApplicationComponent el = F.createApplicationComponent();
        el.getProperties().add(prop("env", "prod"));
        el.getProperties().add(prop("tier", "1"));
        List<Map<String, String>> result = DtoMapper.convertProperties(el.getProperties());
        assertEquals(List.of(propMap("env", "prod"), propMap("tier", "1")), result);
    }

    // ---- buildElementDtoWithSpecialization --------------------------------------

    @Test
    public void buildElementDtoWithSpecialization_shouldBuildFullDto_whenDocAndPropsPresent() {
        IApplicationComponent el = F.createApplicationComponent();
        el.setId("el-1");
        el.setName("Billing");
        el.setDocumentation("Handles invoices");
        el.getProperties().add(prop("env", "prod"));

        ElementDto dto = DtoMapper.buildElementDtoWithSpecialization(el, "Microservice", "Application");

        assertEquals(ElementDto.standard("el-1", "Billing", "ApplicationComponent",
                "Microservice", "Application", "Handles invoices",
                List.of(propMap("env", "prod"))), dto);
    }

    @Test
    public void buildElementDtoWithSpecialization_shouldNormalizeEmptyDocAndOmitEmptyProps() {
        IApplicationComponent el = F.createApplicationComponent();
        el.setId("el-2");
        el.setName("Ledger");
        el.setDocumentation(""); // normalized to null

        ElementDto dto = DtoMapper.buildElementDtoWithSpecialization(el, null, "Application");

        assertEquals(ElementDto.standard("el-2", "Ledger", "ApplicationComponent",
                null, "Application", null, null), dto);
    }

    @Test
    public void buildElementDtoWithSpecialization_shouldForwardLayerVerbatim() {
        IApplicationComponent el = F.createApplicationComponent();
        el.setId("el-3");
        el.setName("X");
        ElementDto dto = DtoMapper.buildElementDtoWithSpecialization(el, null, "Technology");
        assertEquals("Technology", dto.layer());
    }

    // ---- buildViewDto (2-arg) ----------------------------------------------------

    @Test
    public void buildViewDto_shouldBuildFullDto_whenAllFieldsPresent() {
        IArchimateDiagramModel view = F.createArchimateDiagramModel();
        view.setId("v1");
        view.setName("Context");
        view.setViewpoint("Layered");
        view.setDocumentation("desc");
        view.setConnectionRouterType(IDiagramModel.CONNECTION_ROUTER_MANHATTAN);
        view.getProperties().add(prop("k", "v"));

        ViewDto dto = DtoMapper.buildViewDto(view, "Views");

        Map<String, String> expectedProps = new LinkedHashMap<>();
        expectedProps.put("k", "v");
        assertEquals(new ViewDto("v1", "Context", "Layered", "manhattan", "Views", "desc",
                expectedProps), dto);
    }

    @Test
    public void buildViewDto_shouldNormalizeEmptyViewpointDocAndOmitProps_andDefaultRouter() {
        IArchimateDiagramModel view = F.createArchimateDiagramModel();
        view.setId("v2");
        view.setName("Empty");
        view.setViewpoint("");      // normalized to null
        view.setDocumentation("");  // normalized to null
        view.setConnectionRouterType(IDiagramModel.CONNECTION_ROUTER_BENDPOINT);

        ViewDto dto = DtoMapper.buildViewDto(view, "P");

        assertEquals(new ViewDto("v2", "Empty", null, null, "P", null, null), dto);
    }

    // ---- buildViewDto (1-arg) ----------------------------------------------------

    @Test
    public void buildViewDto_shouldResolveNullFolderPath_whenNoContainer() {
        IArchimateDiagramModel view = F.createArchimateDiagramModel();
        view.setId("v3");
        view.setName("Loose");

        ViewDto dto = DtoMapper.buildViewDto(view);

        assertEquals(new ViewDto("v3", "Loose", null, null, null, null, null), dto);
    }

    @Test
    public void buildViewDto_shouldResolveFolderPath_whenContainedInFolder() {
        IFolder folder = F.createFolder();
        folder.setName("Views");
        IArchimateDiagramModel view = F.createArchimateDiagramModel();
        view.setId("v4");
        view.setName("Nested");
        folder.getElements().add(view);

        ViewDto dto = DtoMapper.buildViewDto(view);

        assertEquals(new ViewDto("v4", "Nested", null, null, "Views", null, null), dto);
    }

    // ---- convertToSearchRelationshipDto -----------------------------------------

    @Test
    public void convertToSearchRelationshipDto_shouldBuildFullDto_andForwardSemanticAttrs() {
        IApplicationComponent src = F.createApplicationComponent();
        src.setId("s1");
        src.setName("SourceEl");
        IApplicationComponent tgt = F.createApplicationComponent();
        tgt.setId("t1");
        tgt.setName("TargetEl");

        IFlowRelationship rel = F.createFlowRelationship();
        rel.setId("r1");
        rel.setName("feeds");
        rel.setDocumentation("flowdoc");
        rel.setSource(src);
        rel.setTarget(tgt);
        rel.getProperties().add(prop("p", "1"));

        RelationshipDto dto = DtoMapper.convertToSearchRelationshipDto(rel, "write", Boolean.TRUE, "Medium");

        assertEquals(new RelationshipDto("r1", "feeds", "FlowRelationship", null,
                "s1", "t1", false, "flowdoc", List.of(propMap("p", "1")),
                "SourceEl", "TargetEl", "write", Boolean.TRUE, "Medium"), dto);
    }

    @Test
    public void convertToSearchRelationshipDto_shouldNormalizeEmptyDocPropsAndNullEndpoints() {
        IFlowRelationship rel = F.createFlowRelationship();
        rel.setId("r2");
        rel.setName("orphan");
        rel.setDocumentation(""); // normalized to null
        // no source/target set -> null

        RelationshipDto dto = DtoMapper.convertToSearchRelationshipDto(rel, null, null, null);

        assertEquals(new RelationshipDto("r2", "orphan", "FlowRelationship", null,
                null, null, false, null, null, null, null, null, null, null), dto);
    }
}
