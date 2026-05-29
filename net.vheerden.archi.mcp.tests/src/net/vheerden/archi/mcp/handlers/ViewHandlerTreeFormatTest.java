package net.vheerden.archi.mcp.handlers;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import net.vheerden.archi.mcp.model.BaseTestAccessor;
import net.vheerden.archi.mcp.model.NoModelLoadedException;
import net.vheerden.archi.mcp.registry.CommandRegistry;
import net.vheerden.archi.mcp.response.ResponseFormatter;
import net.vheerden.archi.mcp.response.dto.ElementDto;
import net.vheerden.archi.mcp.response.dto.ModelInfoDto;
import net.vheerden.archi.mcp.response.dto.RelationshipDto;
import net.vheerden.archi.mcp.response.dto.ViewConnectionDto;
import net.vheerden.archi.mcp.response.dto.ViewContentsDto;
import net.vheerden.archi.mcp.response.dto.ViewDto;
import net.vheerden.archi.mcp.response.dto.ViewGroupDto;
import net.vheerden.archi.mcp.response.dto.ViewNodeDto;
import net.vheerden.archi.mcp.response.dto.ViewNoteDto;

/**
 * Unit tests for get-view-contents format=tree (Story backlog-a1).
 *
 * <p>Tests the compact containment hierarchy format for group discovery.
 * Uses stub accessors — no EMF/OSGi runtime required.</p>
 */
public class ViewHandlerTreeFormatTest {

    private CommandRegistry registry;
    private ResponseFormatter formatter;
    private ObjectMapper objectMapper;

    @Before
    public void setUp() {
        registry = new CommandRegistry();
        formatter = new ResponseFormatter();
        objectMapper = new ObjectMapper();
    }

    // ---- AC-1: Groups containing elements in tree format ----

    @SuppressWarnings("unchecked")
    @Test
    public void shouldReturnNestedTree_whenViewHasGroupsWithElements() throws Exception {
        TreeStubAccessor accessor = new TreeStubAccessor("grouped");
        ViewHandler handler = new ViewHandler(accessor, formatter, registry, null);
        handler.registerTools();

        McpSchema.CallToolResult result = invokeWithFormat("view-grouped", "tree");
        assertFalse(result.isError());

        Map<String, Object> envelope = parseJson(result);
        Map<String, Object> resultMap = (Map<String, Object>) envelope.get("result");
        assertNotNull(resultMap);
        assertEquals("view-grouped", resultMap.get("viewId"));
        assertEquals("Grouped View", resultMap.get("viewName"));

        List<Map<String, Object>> tree = (List<Map<String, Object>>) resultMap.get("tree");
        assertNotNull(tree);

        // Find the group node in the tree
        Map<String, Object> groupNode = tree.stream()
                .filter(n -> "group".equals(n.get("type")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No group found in tree"));

        assertEquals("grp-1", groupNode.get("viewObjectId"));
        assertEquals("Infrastructure", groupNode.get("label"));
        assertEquals(3, groupNode.get("childCount"));

        List<Map<String, Object>> children = (List<Map<String, Object>>) groupNode.get("children");
        assertEquals(3, children.size());

        // Verify child elements
        Map<String, Object> child1 = children.get(0);
        assertEquals("element", child1.get("type"));
        assertEquals("vo-1", child1.get("viewObjectId"));
        assertEquals("Server", child1.get("name"));
        assertEquals("Node", child1.get("elementType"));
    }

    // ---- AC-2: Nested groups (group inside group) ----

    @SuppressWarnings("unchecked")
    @Test
    public void shouldReturnNestedGroups_whenGroupContainsGroup() throws Exception {
        TreeStubAccessor accessor = new TreeStubAccessor("nested");
        ViewHandler handler = new ViewHandler(accessor, formatter, registry, null);
        handler.registerTools();

        McpSchema.CallToolResult result = invokeWithFormat("view-nested", "tree");
        assertFalse(result.isError());

        Map<String, Object> envelope = parseJson(result);
        Map<String, Object> resultMap = (Map<String, Object>) envelope.get("result");
        List<Map<String, Object>> tree = (List<Map<String, Object>>) resultMap.get("tree");

        // Root should have 1 top-level group
        Map<String, Object> outerGroup = tree.stream()
                .filter(n -> "group".equals(n.get("type")))
                .findFirst()
                .orElseThrow();

        assertEquals("Outer Group", outerGroup.get("label"));
        List<Map<String, Object>> outerChildren = (List<Map<String, Object>>) outerGroup.get("children");

        // Outer group has: 1 element + 1 nested group
        assertEquals(2, outerChildren.size());

        Map<String, Object> innerGroup = outerChildren.stream()
                .filter(n -> "group".equals(n.get("type")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No nested group found"));

        assertEquals("Inner Group", innerGroup.get("label"));
        List<Map<String, Object>> innerChildren = (List<Map<String, Object>>) innerGroup.get("children");
        assertEquals(1, innerChildren.size());
        assertEquals("element", innerChildren.get(0).get("type"));
    }

    // ---- AC-3: Ungrouped elements at root level ----

    @SuppressWarnings("unchecked")
    @Test
    public void shouldPlaceUngroupedElementsAtRoot() throws Exception {
        TreeStubAccessor accessor = new TreeStubAccessor("grouped");
        ViewHandler handler = new ViewHandler(accessor, formatter, registry, null);
        handler.registerTools();

        McpSchema.CallToolResult result = invokeWithFormat("view-grouped", "tree");
        Map<String, Object> envelope = parseJson(result);
        Map<String, Object> resultMap = (Map<String, Object>) envelope.get("result");
        List<Map<String, Object>> tree = (List<Map<String, Object>>) resultMap.get("tree");

        // Should have root-level elements (ungrouped)
        long rootElements = tree.stream()
                .filter(n -> "element".equals(n.get("type")))
                .count();
        assertEquals("Should have 1 ungrouped element at root", 1, rootElements);

        Map<String, Object> ungrouped = tree.stream()
                .filter(n -> "element".equals(n.get("type")))
                .findFirst().orElseThrow();
        assertEquals("vo-3", ungrouped.get("viewObjectId"));
        assertEquals("Firewall", ungrouped.get("name"));
    }

    // ---- AC-4: Tree node shape (viewObjectId, type, name/label, childCount, children) ----

    @SuppressWarnings("unchecked")
    @Test
    public void shouldIncludeOnlyCompactFields_inTreeNodes() throws Exception {
        TreeStubAccessor accessor = new TreeStubAccessor("grouped");
        ViewHandler handler = new ViewHandler(accessor, formatter, registry, null);
        handler.registerTools();

        McpSchema.CallToolResult result = invokeWithFormat("view-grouped", "tree");
        Map<String, Object> envelope = parseJson(result);
        Map<String, Object> resultMap = (Map<String, Object>) envelope.get("result");
        List<Map<String, Object>> tree = (List<Map<String, Object>>) resultMap.get("tree");

        // Check group node has exactly the right fields
        Map<String, Object> groupNode = tree.stream()
                .filter(n -> "group".equals(n.get("type")))
                .findFirst().orElseThrow();
        assertTrue(groupNode.containsKey("viewObjectId"));
        assertTrue(groupNode.containsKey("type"));
        assertTrue(groupNode.containsKey("label"));
        assertTrue(groupNode.containsKey("childCount"));
        assertTrue(groupNode.containsKey("children"));
        // No position/styling fields
        assertFalse("No x coordinate in tree", groupNode.containsKey("x"));
        assertFalse("No fillColor in tree", groupNode.containsKey("fillColor"));

        // Check element node has correct fields
        Map<String, Object> elemNode = tree.stream()
                .filter(n -> "element".equals(n.get("type")))
                .findFirst().orElseThrow();
        assertTrue(elemNode.containsKey("viewObjectId"));
        assertTrue(elemNode.containsKey("type"));
        assertTrue(elemNode.containsKey("name"));
        assertTrue(elemNode.containsKey("elementType"));
        assertFalse("No x coordinate in tree", elemNode.containsKey("x"));
        assertFalse("No elementId in tree", elemNode.containsKey("elementId"));
    }

    // ---- AC-4: Notes in tree ----

    @SuppressWarnings("unchecked")
    @Test
    public void shouldIncludeNotesInTree_withLabelField() throws Exception {
        TreeStubAccessor accessor = new TreeStubAccessor("grouped");
        ViewHandler handler = new ViewHandler(accessor, formatter, registry, null);
        handler.registerTools();

        McpSchema.CallToolResult result = invokeWithFormat("view-grouped", "tree");
        Map<String, Object> envelope = parseJson(result);
        Map<String, Object> resultMap = (Map<String, Object>) envelope.get("result");
        List<Map<String, Object>> tree = (List<Map<String, Object>>) resultMap.get("tree");

        // Find the group and check for a note in its children
        Map<String, Object> groupNode = tree.stream()
                .filter(n -> "group".equals(n.get("type")))
                .findFirst().orElseThrow();

        // The root has a note
        Map<String, Object> noteNode = tree.stream()
                .filter(n -> "note".equals(n.get("type")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No note found in tree"));

        assertEquals("note", noteNode.get("type"));
        assertEquals("Design note", noteNode.get("label"));
        assertTrue(noteNode.containsKey("viewObjectId"));
    }

    // ---- AC-4: Note inside a group ----

    @SuppressWarnings("unchecked")
    @Test
    public void shouldNestNoteInsideGroup_whenNoteHasParent() throws Exception {
        TreeStubAccessor accessor = new TreeStubAccessor("grouped");
        ViewHandler handler = new ViewHandler(accessor, formatter, registry, null);
        handler.registerTools();

        McpSchema.CallToolResult result = invokeWithFormat("view-grouped", "tree");
        Map<String, Object> envelope = parseJson(result);
        Map<String, Object> resultMap = (Map<String, Object>) envelope.get("result");
        List<Map<String, Object>> tree = (List<Map<String, Object>>) resultMap.get("tree");

        // Find the group node
        Map<String, Object> groupNode = tree.stream()
                .filter(n -> "group".equals(n.get("type")))
                .findFirst().orElseThrow();

        List<Map<String, Object>> children = (List<Map<String, Object>>) groupNode.get("children");

        // Group should contain a note child
        Map<String, Object> groupedNote = children.stream()
                .filter(n -> "note".equals(n.get("type")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No note found inside group"));

        assertEquals("note", groupedNote.get("type"));
        assertEquals("Group note", groupedNote.get("label"));
        assertEquals("note-2", groupedNote.get("viewObjectId"));
    }

    // ---- Graceful degradation: element missing from elements list ----

    @SuppressWarnings("unchecked")
    @Test
    public void shouldOmitNameAndType_whenElementNotInElementsList() throws Exception {
        TreeStubAccessor accessor = new TreeStubAccessor("orphan-element");
        ViewHandler handler = new ViewHandler(accessor, formatter, registry, null);
        handler.registerTools();

        McpSchema.CallToolResult result = invokeWithFormat("view-orphan", "tree");
        assertFalse(result.isError());

        Map<String, Object> envelope = parseJson(result);
        Map<String, Object> resultMap = (Map<String, Object>) envelope.get("result");
        List<Map<String, Object>> tree = (List<Map<String, Object>>) resultMap.get("tree");

        assertEquals(1, tree.size());
        Map<String, Object> node = tree.get(0);
        assertEquals("vo-orphan", node.get("viewObjectId"));
        assertEquals("element", node.get("type"));
        // Name and elementType should be absent — element not in elements list
        assertFalse("name should be absent for orphan element", node.containsKey("name"));
        assertFalse("elementType should be absent for orphan element", node.containsKey("elementType"));
    }

    // ---- AC-5: Stats object ----

    @SuppressWarnings("unchecked")
    @Test
    public void shouldReturnCorrectStats_forGroupedView() throws Exception {
        TreeStubAccessor accessor = new TreeStubAccessor("grouped");
        ViewHandler handler = new ViewHandler(accessor, formatter, registry, null);
        handler.registerTools();

        McpSchema.CallToolResult result = invokeWithFormat("view-grouped", "tree");
        Map<String, Object> envelope = parseJson(result);
        Map<String, Object> resultMap = (Map<String, Object>) envelope.get("result");
        Map<String, Object> stats = (Map<String, Object>) resultMap.get("stats");

        assertNotNull(stats);
        assertEquals(1, stats.get("totalGroups"));
        assertEquals(1, stats.get("topLevelGroups"));
        assertEquals(0, stats.get("nestedGroups"));
        assertEquals(3, stats.get("totalElements"));
        assertEquals(2, stats.get("totalNotes"));
        assertEquals(1, stats.get("ungroupedElements"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldReturnCorrectStats_forNestedGroupView() throws Exception {
        TreeStubAccessor accessor = new TreeStubAccessor("nested");
        ViewHandler handler = new ViewHandler(accessor, formatter, registry, null);
        handler.registerTools();

        McpSchema.CallToolResult result = invokeWithFormat("view-nested", "tree");
        Map<String, Object> envelope = parseJson(result);
        Map<String, Object> resultMap = (Map<String, Object>) envelope.get("result");
        Map<String, Object> stats = (Map<String, Object>) resultMap.get("stats");

        assertEquals(2, stats.get("totalGroups"));
        assertEquals(1, stats.get("topLevelGroups"));
        assertEquals(1, stats.get("nestedGroups"));
        assertEquals(2, stats.get("totalElements"));
        assertEquals(0, stats.get("totalNotes"));
        assertEquals(0, stats.get("ungroupedElements"));
    }

    // ---- AC-6: Flat view (no groups) ----

    @SuppressWarnings("unchecked")
    @Test
    public void shouldReturnAllAtRootLevel_whenNoGroups() throws Exception {
        TreeStubAccessor accessor = new TreeStubAccessor("flat");
        ViewHandler handler = new ViewHandler(accessor, formatter, registry, null);
        handler.registerTools();

        McpSchema.CallToolResult result = invokeWithFormat("view-flat", "tree");
        Map<String, Object> envelope = parseJson(result);
        Map<String, Object> resultMap = (Map<String, Object>) envelope.get("result");
        List<Map<String, Object>> tree = (List<Map<String, Object>>) resultMap.get("tree");
        Map<String, Object> stats = (Map<String, Object>) resultMap.get("stats");

        // All elements at root, no groups
        assertEquals(2, tree.size());
        assertTrue(tree.stream().allMatch(n -> "element".equals(n.get("type"))));
        assertEquals(0, stats.get("totalGroups"));
        assertEquals(0, stats.get("topLevelGroups"));
        assertEquals(2, stats.get("ungroupedElements"));
    }

    // ---- AC-7: Empty view ----

    @SuppressWarnings("unchecked")
    @Test
    public void shouldReturnEmptyTree_whenViewIsEmpty() throws Exception {
        TreeStubAccessor accessor = new TreeStubAccessor("empty");
        ViewHandler handler = new ViewHandler(accessor, formatter, registry, null);
        handler.registerTools();

        McpSchema.CallToolResult result = invokeWithFormat("view-empty", "tree");
        Map<String, Object> envelope = parseJson(result);
        Map<String, Object> resultMap = (Map<String, Object>) envelope.get("result");
        List<Map<String, Object>> tree = (List<Map<String, Object>>) resultMap.get("tree");
        Map<String, Object> stats = (Map<String, Object>) resultMap.get("stats");

        assertNotNull(tree);
        assertTrue(tree.isEmpty());
        assertEquals(0, stats.get("totalGroups"));
        assertEquals(0, stats.get("topLevelGroups"));
        assertEquals(0, stats.get("nestedGroups"));
        assertEquals(0, stats.get("totalElements"));
        assertEquals(0, stats.get("totalNotes"));
        assertEquals(0, stats.get("ungroupedElements"));
    }

    // ---- AC-8: Existing formats unchanged ----

    @SuppressWarnings("unchecked")
    @Test
    public void shouldReturnJsonFormat_whenFormatIsJson() throws Exception {
        TreeStubAccessor accessor = new TreeStubAccessor("flat");
        ViewHandler handler = new ViewHandler(accessor, formatter, registry, null);
        handler.registerTools();

        McpSchema.CallToolResult result = invokeWithFormat("view-flat", "json");
        Map<String, Object> envelope = parseJson(result);

        // JSON format has "result" with elements/relationships
        Map<String, Object> resultMap = (Map<String, Object>) envelope.get("result");
        assertNotNull(resultMap);
        assertTrue(resultMap.containsKey("elements"));
        assertTrue(resultMap.containsKey("relationships"));
        // No tree or stats
        assertFalse(resultMap.containsKey("tree"));
        assertFalse(resultMap.containsKey("stats"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldReturnGraphFormat_whenFormatIsGraph() throws Exception {
        TreeStubAccessor accessor = new TreeStubAccessor("flat");
        ViewHandler handler = new ViewHandler(accessor, formatter, registry, null);
        handler.registerTools();

        McpSchema.CallToolResult result = invokeWithFormat("view-flat", "graph");
        Map<String, Object> envelope = parseJson(result);

        // Graph format has "graph" key with nodes/edges
        Map<String, Object> graph = (Map<String, Object>) envelope.get("graph");
        assertNotNull(graph);
        assertTrue(graph.containsKey("nodes"));
        assertTrue(graph.containsKey("edges"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldReturnSummaryFormat_whenFormatIsSummary() throws Exception {
        TreeStubAccessor accessor = new TreeStubAccessor("flat");
        ViewHandler handler = new ViewHandler(accessor, formatter, registry, null);
        handler.registerTools();

        McpSchema.CallToolResult result = invokeWithFormat("view-flat", "summary");
        Map<String, Object> envelope = parseJson(result);

        // Summary format has "summary" string key
        assertNotNull(envelope.get("summary"));
        assertTrue(envelope.get("summary") instanceof String);
    }

    // ---- Graph format regression: groups/notes present
    //      (Story backlog-get-view-contents-graph-format-internal-error) ----
    //
    // The prior graph-format test above uses the "flat" variant (no groups/notes),
    // which dodged the ClassCastException that crashed graph format whenever a view
    // contained visual Groupings or notes. These tests pin the fix.

    @SuppressWarnings("unchecked")
    @Test
    public void shouldReturnGraph_whenViewHasGroupsAndNotes() throws Exception {
        TreeStubAccessor accessor = new TreeStubAccessor("grouped");
        ViewHandler handler = new ViewHandler(accessor, formatter, registry, null);
        handler.registerTools();

        McpSchema.CallToolResult result = invokeWithFormat("view-grouped", "graph");
        assertFalse("graph format must not crash on a view with groups/notes", result.isError());

        Map<String, Object> envelope = parseJson(result);
        Map<String, Object> graph = (Map<String, Object>) envelope.get("graph");
        assertNotNull(graph);

        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");
        assertNotNull(nodes);

        // The single group appears as a graph node with _nodeType=group and its label
        Map<String, Object> groupNode = nodes.stream()
                .filter(n -> "group".equals(n.get("_nodeType")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No group node in graph"));
        assertEquals("grp-1", groupNode.get("viewObjectId"));
        assertEquals("Infrastructure", groupNode.get("label"));
        // AC-7: null fields are omitted (NON_NULL), exactly as JSON serialization would —
        // the group's styling fields are null and must not appear as keys.
        assertFalse("null styling fields must be omitted from the group node",
                groupNode.containsKey("fillColor"));

        // Both notes appear as graph nodes with _nodeType=note
        long noteNodes = nodes.stream()
                .filter(n -> "note".equals(n.get("_nodeType")))
                .count();
        assertEquals("both notes should appear as graph nodes", 2, noteNodes);

        // Element nodes are still produced (3 elements; identified by "id", no _nodeType)
        long elementNodes = nodes.stream()
                .filter(n -> n.get("_nodeType") == null && n.get("id") != null)
                .count();
        assertEquals(3, elementNodes);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldReturnGraph_whenTechViewWithZonesAndCommunicationNetwork() throws Exception {
        // Reproduces the reported "view I" shape: 3 zone Groupings, deep node nesting,
        // a CommunicationNetwork element participating in associations.
        TreeStubAccessor accessor = new TreeStubAccessor("tech-zones");
        ViewHandler handler = new ViewHandler(accessor, formatter, registry, null);
        handler.registerTools();

        McpSchema.CallToolResult result = invokeWithFormat("view-tech-zones", "graph");
        assertFalse("graph format must not crash on tech view with zones + CommunicationNetwork",
                result.isError());

        Map<String, Object> envelope = parseJson(result);
        Map<String, Object> graph = (Map<String, Object>) envelope.get("graph");
        assertNotNull(graph);

        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");
        List<Map<String, Object>> edges = (List<Map<String, Object>>) graph.get("edges");

        // All 3 zone groupings present as graph nodes
        long groupNodes = nodes.stream()
                .filter(n -> "group".equals(n.get("_nodeType")))
                .count();
        assertEquals("3 zone groupings should appear as graph nodes", 3, groupNodes);

        // The note present as a graph node (independent AC-2/AC-4 coverage on the view-I fixture)
        long noteNodes = nodes.stream()
                .filter(n -> "note".equals(n.get("_nodeType")))
                .count();
        assertEquals("the zone note should appear as a graph node", 1, noteNodes);

        // CommunicationNetwork element present as a node
        assertTrue("CommunicationNetwork element should be a graph node",
                nodes.stream().anyMatch(n -> "net-wan".equals(n.get("id"))));

        // The association edge whose endpoint is the CommunicationNetwork is present
        assertTrue("association edge to CommunicationNetwork should be present",
                edges.stream().anyMatch(e -> "rel-assoc".equals(e.get("id"))
                        && "net-wan".equals(e.get("targetId"))));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldPreserveGroupChildIdsInGraphNodes() throws Exception {
        // List-typed DTO fields (childViewObjectIds) must survive the DTO->map
        // conversion, identical to the JSON field set (AC-7/AC-8).
        TreeStubAccessor accessor = new TreeStubAccessor("nested");
        ViewHandler handler = new ViewHandler(accessor, formatter, registry, null);
        handler.registerTools();

        McpSchema.CallToolResult result = invokeWithFormat("view-nested", "graph");
        assertFalse(result.isError());

        Map<String, Object> envelope = parseJson(result);
        Map<String, Object> graph = (Map<String, Object>) envelope.get("graph");
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");

        Map<String, Object> outerGroup = nodes.stream()
                .filter(n -> "group".equals(n.get("_nodeType"))
                        && "grp-outer".equals(n.get("viewObjectId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No grp-outer node in graph"));

        List<String> childIds = (List<String>) outerGroup.get("childViewObjectIds");
        assertNotNull("childViewObjectIds list-field must survive DTO->map conversion", childIds);
        assertTrue("childViewObjectIds should contain the nested group id",
                childIds.contains("grp-inner"));
    }

    // ---- AC-9: fields/exclude/preset ignored for tree format ----

    @SuppressWarnings("unchecked")
    @Test
    public void shouldIgnoreFieldSelection_whenTreeFormat() throws Exception {
        TreeStubAccessor accessor = new TreeStubAccessor("grouped");
        ViewHandler handler = new ViewHandler(accessor, formatter, registry, null);
        handler.registerTools();

        // Pass fields/exclude/preset — should still return full tree
        McpServerFeatures.SyncToolSpecification spec = findToolSpec("get-view-contents");
        Map<String, Object> args = new HashMap<>();
        args.put("viewId", "view-grouped");
        args.put("format", "tree");
        args.put("fields", "minimal");
        args.put("exclude", List.of("groups", "notes"));
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get-view-contents", args);
        McpSchema.CallToolResult result = spec.callHandler().apply(null, request);

        assertFalse(result.isError());
        Map<String, Object> envelope = parseJson(result);
        Map<String, Object> resultMap = (Map<String, Object>) envelope.get("result");

        // Tree should still have groups and notes despite exclude
        List<Map<String, Object>> tree = (List<Map<String, Object>>) resultMap.get("tree");
        assertNotNull(tree);
        assertTrue("Tree should still contain groups despite exclude",
                tree.stream().anyMatch(n -> "group".equals(n.get("type"))));
    }

    // ---- AC-10: nextSteps guidance ----

    @SuppressWarnings("unchecked")
    @Test
    public void shouldSuggestGroupTools_whenGroupsExist() throws Exception {
        TreeStubAccessor accessor = new TreeStubAccessor("grouped");
        ViewHandler handler = new ViewHandler(accessor, formatter, registry, null);
        handler.registerTools();

        McpSchema.CallToolResult result = invokeWithFormat("view-grouped", "tree");
        Map<String, Object> envelope = parseJson(result);
        List<String> nextSteps = (List<String>) envelope.get("nextSteps");

        assertNotNull(nextSteps);
        assertTrue(nextSteps.stream().anyMatch(s -> s.contains("layout-within-group")));
        assertTrue(nextSteps.stream().anyMatch(s -> s.contains("arrange-groups")));
        assertTrue(nextSteps.stream().anyMatch(s -> s.contains("optimize-group-order")));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldSuggestFlatLayoutTools_whenNoGroups() throws Exception {
        TreeStubAccessor accessor = new TreeStubAccessor("flat");
        ViewHandler handler = new ViewHandler(accessor, formatter, registry, null);
        handler.registerTools();

        McpSchema.CallToolResult result = invokeWithFormat("view-flat", "tree");
        Map<String, Object> envelope = parseJson(result);
        List<String> nextSteps = (List<String>) envelope.get("nextSteps");

        assertNotNull(nextSteps);
        assertTrue(nextSteps.stream().anyMatch(s -> s.contains("layout-flat-view")));
    }

    // ---- Schema test: format enum includes "tree" ----

    @SuppressWarnings("unchecked")
    @Test
    public void shouldIncludeTreeInFormatEnum() {
        TreeStubAccessor accessor = new TreeStubAccessor("flat");
        ViewHandler handler = new ViewHandler(accessor, formatter, registry, null);
        handler.registerTools();

        McpSchema.Tool tool = findToolSpec("get-view-contents").tool();
        Map<String, Object> properties = tool.inputSchema().properties();
        Map<String, Object> formatProp = (Map<String, Object>) properties.get("format");
        List<String> enumValues = (List<String>) formatProp.get("enum");

        assertTrue(enumValues.contains("tree"));
        assertTrue(enumValues.contains("json"));
        assertTrue(enumValues.contains("graph"));
        assertTrue(enumValues.contains("summary"));
        assertEquals(4, enumValues.size());
    }

    // ---- _meta envelope test ----

    @SuppressWarnings("unchecked")
    @Test
    public void shouldIncludeMetaInTreeResponse() throws Exception {
        TreeStubAccessor accessor = new TreeStubAccessor("grouped");
        ViewHandler handler = new ViewHandler(accessor, formatter, registry, null);
        handler.registerTools();

        McpSchema.CallToolResult result = invokeWithFormat("view-grouped", "tree");
        Map<String, Object> envelope = parseJson(result);
        Map<String, Object> meta = (Map<String, Object>) envelope.get("_meta");

        assertNotNull(meta);
        assertNotNull(meta.get("modelVersion"));
    }

    // ---- Tree format element-container nesting (Story C1f) ----

    @SuppressWarnings("unchecked")
    @Test
    public void shouldNestElementChildren_underApplicationComponentContainer() throws Exception {
        // AC-1: ApplicationComponent containing 3 nested ApplicationFunctions
        // (the literal Probe 5 View F 1708 case).
        TreeStubAccessor accessor = new TreeStubAccessor("component-with-functions");
        ViewHandler handler = new ViewHandler(accessor, formatter, registry, null);
        handler.registerTools();

        McpSchema.CallToolResult result = invokeWithFormat("view-component-with-functions", "tree");
        assertFalse(result.isError());

        Map<String, Object> envelope = parseJson(result);
        Map<String, Object> resultMap = (Map<String, Object>) envelope.get("result");
        List<Map<String, Object>> tree = (List<Map<String, Object>>) resultMap.get("tree");

        // Single top-level element node: the ApplicationComponent container.
        assertEquals(1, tree.size());
        Map<String, Object> comp = tree.get(0);
        assertEquals("vo-comp-1", comp.get("viewObjectId"));
        assertEquals("element", comp.get("type"));
        assertEquals("Mobile Banking App", comp.get("name"));
        assertEquals("ApplicationComponent", comp.get("elementType"));
        assertEquals(3, comp.get("childCount"));

        List<Map<String, Object>> children = (List<Map<String, Object>>) comp.get("children");
        assertEquals(3, children.size());

        // Children order = visualMetadata insertion order (vo-fn-1, vo-fn-2, vo-fn-3).
        String[] expectedVoIds = {"vo-fn-1", "vo-fn-2", "vo-fn-3"};
        String[] expectedNames = {"Account Inquiry", "Payment Submission", "Statement Download"};
        for (int i = 0; i < 3; i++) {
            Map<String, Object> child = children.get(i);
            assertEquals(expectedVoIds[i], child.get("viewObjectId"));
            assertEquals("element", child.get("type"));
            assertEquals(expectedNames[i], child.get("name"));
            assertEquals("ApplicationFunction", child.get("elementType"));
            // Leaf elements emit NO children / childCount fields.
            assertFalse("leaf element must not emit children", child.containsKey("children"));
            assertFalse("leaf element must not emit childCount", child.containsKey("childCount"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldNestElementChildren_underNodeContainer_inTechZonesView() throws Exception {
        // AC-2: Node containing a nested SystemSoftware child, via the existing
        // tech-zones fixture (vo-sw parented under vo-app at fixture line ~820).
        TreeStubAccessor accessor = new TreeStubAccessor("tech-zones");
        ViewHandler handler = new ViewHandler(accessor, formatter, registry, null);
        handler.registerTools();

        McpSchema.CallToolResult result = invokeWithFormat("view-tech-zones", "tree");
        assertFalse(result.isError());

        Map<String, Object> envelope = parseJson(result);
        Map<String, Object> resultMap = (Map<String, Object>) envelope.get("result");
        List<Map<String, Object>> tree = (List<Map<String, Object>>) resultMap.get("tree");

        // Locate the Application Zone group, then the App Server Node inside it.
        Map<String, Object> appZone = tree.stream()
                .filter(n -> "group".equals(n.get("type")) && "grp-appz".equals(n.get("viewObjectId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No grp-appz group in tree"));

        List<Map<String, Object>> zoneChildren = (List<Map<String, Object>>) appZone.get("children");
        Map<String, Object> appNode = zoneChildren.stream()
                .filter(n -> "vo-app".equals(n.get("viewObjectId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("App Server Node not found inside grp-appz"));

        assertEquals("element", appNode.get("type"));
        assertEquals("Node", appNode.get("elementType"));
        assertEquals(1, appNode.get("childCount"));

        List<Map<String, Object>> nodeChildren = (List<Map<String, Object>>) appNode.get("children");
        assertEquals(1, nodeChildren.size());
        Map<String, Object> sw = nodeChildren.get(0);
        assertEquals("vo-sw", sw.get("viewObjectId"));
        assertEquals("element", sw.get("type"));
        assertEquals("App Runtime", sw.get("name"));
        assertEquals("SystemSoftware", sw.get("elementType"));
        assertFalse("nested SystemSoftware leaf must not emit children", sw.containsKey("children"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldNotEmitChildrenField_onLeafElement_inFlatView() throws Exception {
        // AC-3: regression pin — flat view with no element-container nesting.
        // Every element node renders byte-identically to v1.5 (no children/childCount).
        TreeStubAccessor accessor = new TreeStubAccessor("flat");
        ViewHandler handler = new ViewHandler(accessor, formatter, registry, null);
        handler.registerTools();

        McpSchema.CallToolResult result = invokeWithFormat("view-flat", "tree");
        Map<String, Object> envelope = parseJson(result);
        Map<String, Object> resultMap = (Map<String, Object>) envelope.get("result");
        List<Map<String, Object>> tree = (List<Map<String, Object>>) resultMap.get("tree");

        assertEquals(2, tree.size());
        for (Map<String, Object> node : tree) {
            assertEquals("element", node.get("type"));
            assertFalse("flat-view leaf must not emit children", node.containsKey("children"));
            assertFalse("flat-view leaf must not emit childCount", node.containsKey("childCount"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldKeepTotalElementsPolymorphicCorrect_acrossNestingLevels() throws Exception {
        // AC-4: stats.totalElements counts every visualMetadata entry regardless of parent
        // type; ungroupedElements counts only root-level (parentViewObjectId == null) elements.
        TreeStubAccessor accessor = new TreeStubAccessor("component-with-functions");
        ViewHandler handler = new ViewHandler(accessor, formatter, registry, null);
        handler.registerTools();

        McpSchema.CallToolResult result = invokeWithFormat("view-component-with-functions", "tree");
        Map<String, Object> envelope = parseJson(result);
        Map<String, Object> resultMap = (Map<String, Object>) envelope.get("result");
        Map<String, Object> stats = (Map<String, Object>) resultMap.get("stats");

        assertEquals(4, stats.get("totalElements"));   // 1 component + 3 functions
        assertEquals(1, stats.get("ungroupedElements")); // only the component is root
        assertEquals(0, stats.get("totalGroups"));
        assertEquals(0, stats.get("totalNotes"));
    }

    // ---- Helper Methods ----

    private McpSchema.CallToolResult invokeWithFormat(String viewId, String format) {
        McpServerFeatures.SyncToolSpecification spec = findToolSpec("get-view-contents");
        Map<String, Object> args = new HashMap<>();
        args.put("viewId", viewId);
        if (format != null) {
            args.put("format", format);
        }
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get-view-contents", args);
        return spec.callHandler().apply(null, request);
    }

    private McpServerFeatures.SyncToolSpecification findToolSpec(String toolName) {
        return registry.getToolSpecifications().stream()
                .filter(s -> s.tool().name().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool not found: " + toolName));
    }

    private Map<String, Object> parseJson(McpSchema.CallToolResult result) throws Exception {
        McpSchema.TextContent content = (McpSchema.TextContent) result.content().get(0);
        return objectMapper.readValue(content.text(),
                new TypeReference<Map<String, Object>>() {});
    }

    // ---- Stub Accessor ----

    /**
     * Stub accessor providing different view configurations for tree format testing.
     */
    private static class TreeStubAccessor extends BaseTestAccessor {
        private final String variant;

        TreeStubAccessor(String variant) {
            super(true);
            this.variant = variant;
        }

        @Override
        public List<ViewDto> getViews(String viewpointFilter) {
            return List.of(
                    new ViewDto("view-grouped", "Grouped View", null, "Views"),
                    new ViewDto("view-nested", "Nested View", null, "Views"),
                    new ViewDto("view-flat", "Flat View", null, "Views"),
                    new ViewDto("view-empty", "Empty View", null, "Views"),
                    new ViewDto("view-orphan", "Orphan Element View", null, "Views"),
                    new ViewDto("view-tech-zones", "Technology Zones View", null, "Views"),
                    new ViewDto("view-component-with-functions", "Component with Functions View",
                            null, "Views"));
        }

        @Override
        public ModelInfoDto getModelInfo() {
            return new ModelInfoDto("Test Model", 10, 5, 4, 0, Map.of(), Map.of(), Map.of());
        }

        @Override
        public Optional<ViewContentsDto> getViewContents(String viewId) {
            return switch (viewId) {
                case "view-grouped" -> Optional.of(createGroupedView());
                case "view-nested" -> Optional.of(createNestedView());
                case "view-flat" -> Optional.of(createFlatView());
                case "view-empty" -> Optional.of(createEmptyView());
                case "view-orphan" -> Optional.of(createOrphanElementView());
                case "view-tech-zones" -> Optional.of(createTechZonesView());
                case "view-component-with-functions" -> Optional.of(createComponentWithFunctionsView());
                default -> Optional.empty();
            };
        }

        /**
         * Grouped view: 1 group with 2 elements, 1 ungrouped element, 1 ungrouped note.
         */
        private ViewContentsDto createGroupedView() {
            List<ElementDto> elements = List.of(
                    ElementDto.standard("elem-1", "Server", "Node", null, "Technology", null, null),
                    ElementDto.standard("elem-2", "Database", "Node", null, "Technology", null, null),
                    ElementDto.standard("elem-3", "Firewall", "Node", null, "Technology", null, null));

            List<ViewNodeDto> visualMetadata = List.of(
                    new ViewNodeDto("vo-1", "elem-1", 10, 10, 120, 55, "grp-1"),
                    new ViewNodeDto("vo-2", "elem-2", 10, 80, 120, 55, "grp-1"),
                    new ViewNodeDto("vo-3", "elem-3", 300, 10, 120, 55));

            List<ViewGroupDto> groups = List.of(
                    new ViewGroupDto("grp-1", "Infrastructure", 0, 0, 250, 200, null,
                            List.of()));

            List<ViewNoteDto> notes = List.of(
                    new ViewNoteDto("note-1", "Design note", 300, 200, 150, 40, null),
                    new ViewNoteDto("note-2", "Group note", 10, 150, 150, 40, "grp-1"));

            return new ViewContentsDto("view-grouped", "Grouped View", null, null,
                    elements, List.of(), visualMetadata, List.of(), groups, notes);
        }

        /**
         * Nested view: outer group → (1 element + inner group → 1 element).
         */
        private ViewContentsDto createNestedView() {
            List<ElementDto> elements = List.of(
                    ElementDto.standard("elem-1", "App Server", "Node", null, "Technology", null, null),
                    ElementDto.standard("elem-2", "Container", "Node", null, "Technology", null, null));

            List<ViewNodeDto> visualMetadata = List.of(
                    new ViewNodeDto("vo-1", "elem-1", 10, 10, 120, 55, "grp-outer"),
                    new ViewNodeDto("vo-2", "elem-2", 20, 80, 100, 55, "grp-inner"));

            List<ViewGroupDto> groups = List.of(
                    new ViewGroupDto("grp-outer", "Outer Group", 0, 0, 300, 300, null,
                            List.of("grp-inner")),
                    new ViewGroupDto("grp-inner", "Inner Group", 10, 60, 200, 150, "grp-outer",
                            List.of()));

            return new ViewContentsDto("view-nested", "Nested View", null, null,
                    elements, List.of(), visualMetadata, List.of(), groups, List.of());
        }

        /**
         * Flat view: 2 elements, no groups, no notes.
         */
        private ViewContentsDto createFlatView() {
            List<ElementDto> elements = List.of(
                    ElementDto.standard("elem-1", "Web App", "ApplicationComponent", null, "Application", null, null),
                    ElementDto.standard("elem-2", "API", "ApplicationComponent", null, "Application", null, null));

            List<RelationshipDto> relationships = List.of(
                    new RelationshipDto("rel-1", "Serves", "ServingRelationship", "elem-1", "elem-2"));

            List<ViewNodeDto> visualMetadata = List.of(
                    new ViewNodeDto("vo-1", "elem-1", 100, 50, 120, 55),
                    new ViewNodeDto("vo-2", "elem-2", 300, 50, 120, 55));

            return new ViewContentsDto("view-flat", "Flat View", null, null,
                    elements, relationships, visualMetadata, List.of(), List.of(), List.of());
        }

        /**
         * Empty view: no elements, no groups, no notes.
         */
        private ViewContentsDto createEmptyView() {
            return new ViewContentsDto("view-empty", "Empty View", null, null,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }

        /**
         * Technology zones view reproducing the reported "view I" crash shape:
         * 3 zone Groupings, elements nested inside groups, an element nested inside
         * another element (deep node nesting), a CommunicationNetwork participating
         * in associations, and a note. Triggers the graph-format ClassCastException
         * because groups/notes are present.
         */
        private ViewContentsDto createTechZonesView() {
            List<ElementDto> elements = List.of(
                    ElementDto.standard("node-web", "Web Server", "Node", null, "Technology", null, null),
                    ElementDto.standard("node-app", "App Server", "Node", null, "Technology", null, null),
                    ElementDto.standard("node-db", "DB Server", "Node", null, "Technology", null, null),
                    ElementDto.standard("sw-app", "App Runtime", "SystemSoftware", null, "Technology", null, null),
                    ElementDto.standard("net-wan", "Corp WAN", "CommunicationNetwork", null, "Technology", null, null));

            List<RelationshipDto> relationships = List.of(
                    // CommunicationNetwork participating in associations (the reported shape)
                    new RelationshipDto("rel-assoc", "", "AssociationRelationship", "node-web", "net-wan"),
                    new RelationshipDto("rel-assoc2", "", "AssociationRelationship", "node-app", "net-wan"),
                    new RelationshipDto("rel-serve", "Serves", "ServingRelationship", "node-app", "node-db"));

            // Deep nesting: nodes inside zone groups, sw-app inside node-app's view object
            List<ViewNodeDto> visualMetadata = List.of(
                    new ViewNodeDto("vo-web", "node-web", 10, 10, 120, 55, "grp-dmz"),
                    new ViewNodeDto("vo-app", "node-app", 10, 10, 160, 110, "grp-appz"),
                    new ViewNodeDto("vo-sw", "sw-app", 20, 40, 120, 50, "vo-app"),
                    new ViewNodeDto("vo-db", "node-db", 10, 10, 120, 55, "grp-data"),
                    new ViewNodeDto("vo-net", "net-wan", 400, 300, 150, 40));

            List<ViewGroupDto> groups = List.of(
                    new ViewGroupDto("grp-dmz", "DMZ Zone", 0, 0, 200, 150, null, List.of()),
                    new ViewGroupDto("grp-appz", "Application Zone", 250, 0, 250, 200, null, List.of()),
                    new ViewGroupDto("grp-data", "Data Zone", 0, 200, 200, 150, null, List.of()));

            List<ViewNoteDto> notes = List.of(
                    new ViewNoteDto("note-z", "Zone segmentation per security policy",
                            400, 0, 220, 50, null));

            return new ViewContentsDto("view-tech-zones", "Technology Zones View", null, null,
                    elements, relationships, visualMetadata, List.of(), groups, notes);
        }

        /**
         * View with an element on the view (visualMetadata) whose elementId
         * does not match any entry in the elements list — tests graceful degradation.
         */
        private ViewContentsDto createOrphanElementView() {
            // No elements in the elements list — elementId "no-match" won't resolve
            List<ViewNodeDto> visualMetadata = List.of(
                    new ViewNodeDto("vo-orphan", "no-match", 100, 50, 120, 55));

            return new ViewContentsDto("view-orphan", "Orphan Element View", null, null,
                    List.of(), List.of(), visualMetadata, List.of(), List.of(), List.of());
        }

        /**
         * View F (1708 retail-bank) shape: 1 ApplicationComponent on canvas
         * with 3 nested ApplicationFunctions (parentViewObjectId = component).
         * Closes the Probe 5 case from c1-empirical-2026-05-29 (Story C1f).
         */
        private ViewContentsDto createComponentWithFunctionsView() {
            List<ElementDto> elements = List.of(
                    ElementDto.standard("comp-1", "Mobile Banking App", "ApplicationComponent",
                            null, "Application", null, null),
                    ElementDto.standard("fn-1", "Account Inquiry", "ApplicationFunction",
                            null, "Application", null, null),
                    ElementDto.standard("fn-2", "Payment Submission", "ApplicationFunction",
                            null, "Application", null, null),
                    ElementDto.standard("fn-3", "Statement Download", "ApplicationFunction",
                            null, "Application", null, null));

            List<ViewNodeDto> visualMetadata = List.of(
                    new ViewNodeDto("vo-comp-1", "comp-1", 100, 50, 400, 250),
                    new ViewNodeDto("vo-fn-1", "fn-1", 10, 30, 120, 55, "vo-comp-1"),
                    new ViewNodeDto("vo-fn-2", "fn-2", 140, 30, 120, 55, "vo-comp-1"),
                    new ViewNodeDto("vo-fn-3", "fn-3", 270, 30, 120, 55, "vo-comp-1"));

            return new ViewContentsDto("view-component-with-functions",
                    "Component with Functions View", null, null,
                    elements, List.of(), visualMetadata, List.of(), List.of(), List.of());
        }
    }
}
