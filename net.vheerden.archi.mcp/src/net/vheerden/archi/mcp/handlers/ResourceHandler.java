package net.vheerden.archi.mcp.handlers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import net.vheerden.archi.mcp.registry.ResourceRegistry;

/**
 * Loads ArchiMate reference materials and workflow templates from the
 * {@code resources/} directory and registers them as MCP Resources.
 *
 * <p>Resources are static markdown files that provide LLMs with ArchiMate
 * domain knowledge and exploration strategies. Content is loaded once at
 * startup and cached in memory.</p>
 *
 * <p>This handler has no dependency on {@link net.vheerden.archi.mcp.model.ArchiModelAccessor}
 * or {@link net.vheerden.archi.mcp.response.ResponseFormatter} — resources are
 * raw markdown, not model-derived JSON.</p>
 */
public class ResourceHandler {

	private static final Logger logger = LoggerFactory.getLogger(ResourceHandler.class);

	private static final String RESOURCE_BASE_PATH = "resources/";
	private static final String MIME_TYPE = "text/markdown";
	private static final String URI_PREFIX = "archimate://";

	private static final Map<String, ResourceDefinition> RESOURCE_DEFINITIONS = Map.ofEntries(
			Map.entry("prompts/model-exploration-guide", new ResourceDefinition(
					"Model Exploration Guide",
					"Strategy guide for LLMs on how to efficiently search and traverse ArchiMate models",
					"prompts/model-exploration-guide.md")),
			Map.entry("prompts/explore-dependencies", new ResourceDefinition(
					"Explore Dependencies",
					"Workflow template for systematic dependency analysis of ArchiMate elements",
					"prompts/explore-dependencies.md")),
			Map.entry("prompts/landscape-overview", new ResourceDefinition(
					"Landscape Overview",
					"Workflow template for generating architecture landscape summaries",
					"prompts/landscape-overview.md")),
			Map.entry("prompts/routing-preconditions-checklist", new ResourceDefinition(
					"Routing Preconditions Checklist",
					"Three-precondition checklist (hub sizing / inter-element spacing / inter-group arrangement) to verify BEFORE calling auto-route-connections or auto-layout-and-route on any non-trivial view.",
					"prompts/routing-preconditions-checklist.md")),
			Map.entry("reference/archimate-layers", new ResourceDefinition(
					"ArchiMate Layers Reference",
					"Comprehensive mapping of ArchiMate layers to element types with descriptions, plus a concept-to-element-type decision aid (Component vs Service, Process vs Function, Node vs Component, Actor vs Role)",
					"reference/archimate-layers.md")),
			Map.entry("reference/archimate-relationships", new ResourceDefinition(
					"ArchiMate Relationships Reference",
					"All ArchiMate relationship types with valid source/target combinations and usage guidance",
					"reference/archimate-relationships.md")),
			Map.entry("reference/archimate-specializations", new ResourceDefinition(
					"ArchiMate Specializations Reference",
					"Specialization (IS-A subtype) vocabulary: when to use specializations vs properties, common patterns per layer, and the discovery/create/audit/delete tool pipeline",
					"reference/archimate-specializations.md")),
			Map.entry("reference/archimate-view-patterns", new ResourceDefinition(
					"ArchiMate View Patterns",
					"Curated viewpoint patterns, layout algorithm guidance, and diagramming best practices for composing ArchiMate views",
					"reference/archimate-view-patterns.md")),
			Map.entry("recipes/index", new ResourceDefinition(
					"ArchiMate Viewpoint Recipes Index",
					"Selector for the viewpoint recipe library: states the invariant build sequence once, routes conventional viewpoints to the principles section, and points non-conventional viewpoints to their full recipe page. Fetch this first.",
					"recipes/index.md")),
			Map.entry("recipes/application-integration", new ResourceDefinition(
					"Recipe — Application Integration",
					"Hub-and-spoke recipe for Application Cooperation / Integration views: element + relationship subset, the hub-and-spoke topology block, and the per-family deltas vs the invariant build sequence.",
					"recipes/application-integration.md")),
			Map.entry("recipes/behaviour-process-flow", new ResourceDefinition(
					"Recipe — Behaviour & Process Flow",
					"Swimlane recipe for Business Process Cooperation views plus the Service Design / Customer Journey band layout.",
					"recipes/behaviour-process-flow.md")),
			Map.entry("recipes/motivation", new ResourceDefinition(
					"Recipe — Motivation",
					"Directed influence-chain recipe for Motivation views: stakeholder -> driver -> assessment -> goal -> requirement banded topology.",
					"recipes/motivation.md")),
			Map.entry("recipes/technology-deployment", new ResourceDefinition(
					"Recipe — Technology / Deployment",
					"Nested-deployment recipe: infrastructure nodes as containers with deployed software/artifacts nested inside, wired by network paths.",
					"recipes/technology-deployment.md")),
			Map.entry("recipes/roadmap-migration", new ResourceDefinition(
					"Recipe — Roadmap / Migration",
					"Left-to-right plateau-timeline recipe for Implementation & Migration views: plateaus and gap elements on a time axis with work packages below.",
					"recipes/roadmap-migration.md")));

	private final Map<String, String> cachedContent = new HashMap<>();

	/**
	 * Loads all resource files and registers them with the given registry.
	 *
	 * <p>Each resource file is loaded from the classpath, cached in memory,
	 * and registered as a {@link SyncResourceSpecification}. Files that cannot
	 * be found or loaded are skipped with a warning.</p>
	 *
	 * @param registry the resource registry to register with
	 */
	public void registerResources(ResourceRegistry registry) {
		logger.info("Loading MCP resource files...");
		int registered = 0;

		for (Map.Entry<String, ResourceDefinition> entry : RESOURCE_DEFINITIONS.entrySet()) {
			String uri = URI_PREFIX + entry.getKey();
			ResourceDefinition def = entry.getValue();

			String content = loadResourceFile(def.filePath());
			if (content != null) {
				cachedContent.put(uri, content);

				McpSchema.Resource resource = McpSchema.Resource.builder()
						.uri(uri)
						.name(def.name())
						.description(def.description())
						.mimeType(MIME_TYPE)
						.build();

				SyncResourceSpecification spec = new SyncResourceSpecification(
						resource, this::handleReadResource);
				registry.registerResource(spec);
				registered++;
			} else {
				logger.warn("Resource file not found, skipping: {}", def.filePath());
			}
		}

		logger.info("Registered {} MCP resources", registered);
	}

	/**
	 * Returns the number of cached resource contents.
	 * Package-visible for testing.
	 *
	 * @return the number of cached resources
	 */
	int getCachedResourceCount() {
		return cachedContent.size();
	}

	/**
	 * Handles a read-resource request by looking up cached content for the
	 * requested URI.
	 *
	 * @param exchange the MCP server exchange
	 * @param request  the read resource request containing the URI
	 * @return the resource content, or an empty result for unknown URIs
	 */
	McpSchema.ReadResourceResult handleReadResource(
			McpSyncServerExchange exchange, McpSchema.ReadResourceRequest request) {
		logger.debug("Serving resource content for URI: {}", request.uri());

		String content = cachedContent.get(request.uri());
		if (content == null) {
			logger.warn("Resource not found for URI: {}", request.uri());
			return new McpSchema.ReadResourceResult(List.of());
		}

		McpSchema.TextResourceContents textContents =
				new McpSchema.TextResourceContents(request.uri(), MIME_TYPE, content);
		return new McpSchema.ReadResourceResult(List.of(textContents));
	}

	/**
	 * Loads a resource file from the classpath.
	 * Package-visible for testing.
	 *
	 * @param filePath the file path relative to the resources/ directory
	 * @return the file content as a string, or null if not found
	 */
	String loadResourceFile(String filePath) {
		String fullPath = RESOURCE_BASE_PATH + filePath;
		try (InputStream is = getClass().getClassLoader().getResourceAsStream(fullPath)) {
			if (is == null) {
				return null;
			}
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			logger.error("Failed to load resource file: {}", fullPath, e);
			return null;
		}
	}

	/**
	 * Definition of an MCP resource file: maps a display name and description
	 * to a file path under the resources/ directory.
	 */
	record ResourceDefinition(String name, String description, String filePath) {
	}
}
