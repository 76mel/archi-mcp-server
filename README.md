# Archi MCP Server

An Eclipse PDE plugin for [Archi](https://www.archimatetool.com/) that exposes ArchiMate models through the [Model Context Protocol (MCP)](https://modelcontextprotocol.io/), enabling LLMs to query, analyse, and modify enterprise architecture models through natural language.

## What It Does

Archi MCP Server embeds an HTTP server inside Archi that speaks MCP. Once running, any MCP-compatible LLM client (Claude, Cline, LM Studio, etc.) can connect and interact with the currently open ArchiMate model — asking questions, searching elements, traversing relationships, composing view diagrams, and even creating or modifying model content.

The server provides **72 MCP tools** across querying, searching, creating, layout, routing, assessment, batch operations, images, specializations, and more — plus **14 MCP resources** with ArchiMate reference material, workflow guides, and a viewpoint recipe library for LLMs.

**Example conversation:**

> **You:** "What applications support the Customer Portal capability?"
>
> **LLM:** Searches elements, traverses relationships, and returns: *"7 applications support Customer Portal: OrderService, PaymentGateway, ..."*

## Requirements

| Requirement | Version |
|---|---|
| [Archi](https://www.archimatetool.com/) | 5.7+ |
| Java | 21+ |
| An MCP-compatible LLM client | Claude CLI, Cline, LM Studio, etc. |

**LLM model size recommendation:** 8B+ parameters minimum, 14B+ for reliable tool calling, 70B+ for complex view composition workflows.

## Installation

1. Download the latest `.archiplugin` from the [Releases](../../releases) page (or the `bin/` directory for pre-built artifacts)
2. In Archi: **Help > Manage Plug-ins > Install New...** or copy to Archi's `dropins/` folder
3. Restart Archi

## Getting Started

### 1. Start the Server

Open an ArchiMate model in Archi, then:

**Menu:** `MCP Server > Start MCP Server`

The menu toggles between Start/Stop. The default endpoint is `http://127.0.0.1:18090`.

### 2. Configure Your LLM Client

#### Claude Code (CLI)

In your project's `.mcp.json` or `~/.claude.json`:

```json
{
  "mcpServers": {
    "archi": {
      "type": "http",
      "url": "http://127.0.0.1:18090/mcp"
    }
  }
}
```

Or via the CLI:

```bash
claude mcp add --transport http archi http://127.0.0.1:18090/mcp
```

#### Claude Desktop

Claude Desktop does not natively support Streamable HTTP, so a proxy is required. Install [uv](https://docs.astral.sh/uv/) (a single Rust binary), then add to your `claude_desktop_config.json`:

**Windows:**
```json
{
  "mcpServers": {
    "archi": {
      "command": "C:\\Users\\YOUR_USER\\.local\\bin\\uvx.exe",
      "args": ["mcp-proxy", "--transport", "streamablehttp", "http://127.0.0.1:18090/mcp"]
    }
  }
}
```

**macOS:**
```json
{
  "mcpServers": {
    "archi": {
      "command": "uvx",
      "args": ["mcp-proxy", "--transport", "streamablehttp", "http://127.0.0.1:18090/mcp"]
    }
  }
}
```

#### Cline / Other MCP Clients

Point your MCP client at the Streamable-HTTP endpoint:

```
http://127.0.0.1:18090/mcp
```

SSE transport is also available at `/sse` for older clients.

### 3. Start Querying

With the server running and your LLM client connected, you can ask questions in natural language:

- *"Give me an overview of this architecture model"*
- *"Find all Application Services in the model"*
- *"What does the Order Processing component depend on?"*
- *"Show me the relationships between the CRM and ERP systems"*
- *"Create a new view showing the payment processing flow"*
- *"Auto-layout and route the connections on this view"*

## Configuration

Access via **Window > Preferences > MCP Server** in Archi.

| Setting | Default | Description |
|---|---|---|
| **Port** | `18090` | HTTP(S) server port |
| **Bind Address** | `127.0.0.1` | Network interface (localhost only by default) |
| **Auto-Start** | `false` | Start the server automatically when Archi launches |
| **Log Level** | `INFO` | Logging verbosity: `DEBUG`, `INFO`, `WARN`, `ERROR` |
| **Enable TLS** | `false` | Use HTTPS with TLS encryption |
| **Keystore File** | *(empty)* | Path to PKCS12/JKS keystore (auto-generated if using self-signed) |
| **Keystore Password** | *(empty)* | Password for the keystore file |

### TLS / HTTPS

The server supports optional TLS encryption. To enable:

1. In preferences, check **Enable TLS (HTTPS)**
2. Click **Generate Self-Signed Certificate** to create a keystore automatically
3. Restart the server — the endpoint changes to `https://127.0.0.1:18090`

Clients must trust the self-signed certificate. For `curl` testing, use the `-k` flag. For LLM clients, import the certificate into the client's trust store or the JVM `cacerts`.

## Available Tools

The server exposes **72 MCP tools** organised into functional categories.

### Query & Model Inspection (6 tools)

| Tool | Description |
|---|---|
| `get-model-info` | Model overview — name, `purpose`, custom `properties`, element/relationship/view counts by type and layer, plus specialization count. Read counterpart to `update-model` |
| `get-element` | Retrieve element(s) by ID (single via `id` or batch via `ids` array) |
| `get-views` | List views with optional viewpoint type or name filtering |
| `get-view-contents` | View diagram contents — elements, relationships, visual positions, connection routing, image visuals |
| `get-relationships` | Traverse relationships with configurable depth (0-3 hops) or multi-hop chain traversal with direction/type/layer filters |
| `find-concept-usage` | Reverse where-used lookup — given an element or relationship ID, returns every view and visual object/connection that references it. Inverse of `get-view-contents`. Use before `delete-element` / `delete-relationship` / rename / re-type to see the cross-view footprint in one round-trip |

### Search & Discovery (4 tools)

| Tool | Description |
|---|---|
| `search-elements` | Full-text search across element names, documentation, and properties with optional type, layer, and `specialization` filters |
| `search-relationships` | Search all relationships by text, type, source/target element layer, and `specialization` — no element ID needed |
| `get-or-create-element` | Discovery-first — returns existing element if exact name+type match exists, otherwise creates new |
| `search-and-create` | Combined search + conditional create with duplicate candidate display |

### Element & Relationship Creation (4 tools)

| Tool | Description |
|---|---|
| `create-element` | Create an ArchiMate element with type validation and duplicate detection. Optional `specialization` parameter auto-creates the specialization on first use |
| `create-relationship` | Create a relationship with ArchiMate specification rule enforcement. Optional `specialization` parameter auto-creates the specialization on first use. Optional type-conditional semantic attributes: `accessType` (AccessRelationship), `associationDirected` (AssociationRelationship), `influenceStrength` (InfluenceRelationship) |
| `create-view` | Create a new diagram view with optional viewpoint and connection router type |
| `clone-view` | Duplicate an existing view with all visual contents (elements, groups, notes, connections, bendpoints, styling). The clone references the same model objects |

### Element, Relationship, View & Model Updates (4 tools)

| Tool | Description |
|---|---|
| `update-element` | Update element name, documentation, properties, or `specialization` (pass `""` to clear) |
| `update-relationship` | Update relationship name, documentation, properties, or `specialization` (pass `""` to clear). Optional type-conditional semantic attributes: `accessType` (AccessRelationship), `associationDirected` (AssociationRelationship), `influenceStrength` (InfluenceRelationship). Source, target, and type are immutable |
| `update-view` | Update view name, viewpoint, documentation, properties, or connection router type |
| `update-model` | Update the loaded model's own `name`, `purpose`, and custom `properties` as a single undo unit. At least one of the three must be provided; omitted fields stay unchanged. Empty-string `purpose` clears the field; null property values remove a key |

### ArchiMate Specializations (5 tools)

Specializations are IS-A subtypes of ArchiMate concept types (e.g. "Microservice" is a kind of `ApplicationComponent`, "Cloud Server" is a kind of `Node`). Use them to classify the *kind of thing* an element is — not for per-instance attributes like environment or version. See `archimate://reference/archimate-specializations` for the full guide.

| Tool | Description |
|---|---|
| `list-specializations` | List every specialization defined on the model with `(name, conceptType, layer, usageCount, imagePath)`. Optional `conceptType` filter |
| `create-specialization` | Define a specialization explicitly without creating any element. Idempotent on duplicate `(name, conceptType)` — useful for pre-registering vocabulary at session start. Optional `imagePath` sets the specialization's icon (Archi renders it on every concept of that specialization) |
| `update-specialization` | Rename a specialization and/or set its icon. Refuses to merge into an existing target name. Existing references move with the rename. Optional `imagePath` (set/change icon) and `clearImagePath: true` (explicit clear) — mutually exclusive. At least one of `newName`, `imagePath`, or `clearImagePath` must be supplied |
| `delete-specialization` | Delete a specialization. Refuses by default if any concept uses it; pass `force: true` to detach references and delete in one atomic command |
| `get-specialization-usage` | Audit query — lists every element and relationship referencing a specialization. Call before rename or delete |

### View Composition (9 tools)

All view-composition tools that place a new visual object (`add-to-view`, `add-group-to-view`, `add-note-to-view`, `add-view-reference-to-view`, `add-image-to-view`) and `update-view-object` accept the full visual styling surface: fill / line / font colour, opacity, line width, line style (`solid` / `dashed` / `dotted` / `none`), typography (`fontName`, `fontSize`, `fontStyle`), gradient, `deriveLineColor`, `outlineOpacity`, `figureType` (`rectangular` / `tabbed`), `textAlignment`, `verticalTextAlignment`, plus note-specific `borderType` (`dogear` / `rectangle` / `none`). Existing calls are byte-identical; supply only the fields you want to set.

| Tool | Description |
|---|---|
| `add-to-view` | Place a model element onto a view diagram (same element can appear on multiple views). Optional `imagePath`, `imagePosition`, `showIcon` for custom icon overlays, and `labelExpression` for a per-view-object dynamic label template (e.g. `"${name}"`, `"${property:Owner}"`) |
| `add-group-to-view` | Add a visual grouping rectangle (pure visual container, no model representation). Optional `imagePath`, `imagePosition`, and `showIcon` for custom icon overlays |
| `add-note-to-view` | Add a text note annotation (pure visual, no model representation). Optional `imagePath`, `imagePosition`, `showIcon`, and note-specific `borderType` |
| `add-view-reference-to-view` | Embed another ArchiMate view as a clickable thumbnail (the agent-driven equivalent of Archi GUI's drag-view-onto-view). Requires `viewId` (target) and `referencedViewId` (source). The referenced view's name is not stored on the visual — renaming the referenced view auto-updates every embedding |
| `add-image-to-view` | Add a standalone image as a first-class diagram node (sibling to notes, groups, and view-references). Requires `viewId` and an `imagePath` returned by `add-image-to-model` or `list-model-images`. Default size is the image's natural dimensions; typo'd paths are rejected with `IMAGE_NOT_FOUND`. Distinct from `update-view-object`'s `imagePath`, which sets an icon overlay on an existing element |
| `add-connection-to-view` | Add a visual connection representing an existing model relationship, with optional styling, label suppression, and label positioning |
| `update-view-object` | Update position, size, styling, image, and/or `labelExpression` of a visual element on a view |
| `update-view-connection` | Replace bendpoints, update styling, toggle label visibility, and/or set label position of a connection on a view |
| `apply-positions` | Apply a complete visual layout atomically (up to 10,000 entries per call) |

### View Cleanup (2 tools)

| Tool | Description |
|---|---|
| `remove-from-view` | Remove a visual element or connection from a view (model object preserved) |
| `clear-view` | Remove all visual elements and connections from a view (model objects preserved) |

### Layout & Routing (12 tools)

> **LLM agents:** Fetch `archimate://prompts/routing-preconditions-checklist` before invoking `auto-route-connections` or `auto-layout-and-route` on any non-trivial view. The routing pipeline cannot recover from missing preconditions — it can only route the geometry the agent has set up. The spacing convenience tools self-terminate honestly: if a view is provably too dense for spacing to fix they return `terminationReason: density_floor_reflow_required` and offer a user-consentable structural reflow — surface that to the user instead of looping the spacing tools.

| Tool | Description |
|---|---|
| `compute-layout` | Apply an automatic layout algorithm (tree, spring, directed, radial, grid) to a view |
| `auto-route-connections` | Orthogonal connection routing using clearance-weighted visibility-graph A* pathfinding with corridor directionality, corridor diversity, group-wall awareness, channel-global ordered nudging, and post-routing path straightening. Two modes: `mode: "full"` (default) re-routes whole connections via visibility-graph A*; `mode: "terminals-only"` rectifies only the first/last bendpoint of each connection to make terminal segments orthogonal — best after ELK on grouped views to fix diagonal terminal entries without the crossing inflation a full re-route causes. Optional `autoNudge` mode automatically moves blocking elements (and resizes parent groups to contain them) and re-routes failed connections in a single atomic operation. The response carries a `structuredWarnings: List<{code, message, remediationTool, remediationViolatorIds}>` field for deterministic LLM iteration (most common: `AUTO_NUDGE_SKIPPED_SIBLING_OVERLAP` instructing the agent to run `layout-within-group` on the parent before re-routing) |
| `auto-layout-and-route` | Two modes: `auto` (default) uses ELK Layered to compute positions AND routes in one operation; `grouped` orchestrates the full grouped-view workflow (layout-within-group + arrange-groups + optimize-group-order + auto-route-connections) atomically. Smart iteration when `targetRating` is set — factor-aware iteration tunes the right knob per tier, with plateau detection to exit early when iterations stop improving the dominant tier |
| `layout-within-group` | Arrange child elements within a group using row, column, or grid patterns |
| `layout-flat-view` | Automatic layout for flat (non-grouped) views — row, column, or grid arrangement with optional sorting by name/type/layer and category grouping |
| `arrange-groups` | Position top-level groups relative to each other in grid, row, or column layout. **Density-aware default:** when `spacing` is omitted on a view with inter-group connections, the tool derives a connection-count-aware default (≤ 15 → 80 px, 16–30 → 100 px, > 30 → 120 px). Pass an explicit `spacing` to suppress |
| `optimize-group-order` | Reorder elements within groups to minimise inter-group edge crossings |
| `resize-elements-to-fit` | Resize all (or selected) elements on a view to fit their labels using SWT font metrics. Two-pass algorithm for nested containment: children first, then parents. **Sizes for label legibility only — not for connection fan-out.** For hub elements with high connection counts, use `detect-hub-elements` plus `update-view-object` |
| `adjust-view-spacing` | Inflate inter-element and inter-group spacing on an existing view, then re-route in a single atomic operation. Use when a view is correctly laid out but visually cramped, without re-running ELK from scratch and losing manual placement intent. **Density-aware default:** when `interElementDelta` is omitted on a view with `coincidentSegmentCount > 2` or `connectionEdgeCoincidenceCount > 4`, the tool derives a connection-count-aware default (≤ 15 → 60 px target, 16–30 → 80 px, > 30 → 100 px). Pass `interElementDelta: 0` to suppress. After spacing inflation, a post-pass detects any child element that overflows its parent group bounds and resizes the parent (B15 closure) |
| `apply-element-spacing-recommendations` | Convenience tool that runs an embedded observe → decide → density-aware-terminate control loop to inflate within-group element spacing. Per iteration it takes a small monotone step, re-runs `assess-layout`, and continues / escalates / stops; a degrading step is always reverted. Hub-aware tier selection (80/100/120 px) when `detect-hub-elements` reports candidates. Returns before/after `assess-layout` snapshots plus `terminationReason` / `iterationCount` / `appliedDeltas[]`. One call = one undo step. Set `dryRun: true` to preview |
| `apply-group-spacing-recommendations` | Sibling-symmetric convenience tool that runs the same embedded control loop to widen inter-group corridors only — preserves group ordering and topology. Hub-aware connected-pair tier rises to 100/140/160 px. Returns before/after `assess-layout` snapshots plus `terminationReason` / `iterationCount` / `appliedDeltas[]`. `dryRun: true` previews. With hub sizing and `apply-element-spacing-recommendations` this completes the routing-preconditions triad |
| `apply-spacing-recommendations` | Composed convenience tool that runs TWO coordinated control loops (element arm, then inter-group arm) in one transactional call. The `scope` parameter (`both` / `element` / `group`) selects which arm(s) run. The inflation-knee constants are **per-iteration step caps** (+80 px element / +100 px inter-group per iteration), preventing cumulative-inflation-past-the-knee without a fixed per-call ceiling; `elementKneeClampApplied` / `groupKneeClampApplied` surface when a step cap fires. Reports per-arm `terminationReason` / iteration counts / applied deltas. Use when both axes need adjustment; single-arm siblings cover single-axis changes |

### Layout Assessment & Analysis (2 tools)

> Metric acronyms below — M1–M6, R8, `parallelConnectionGap_V_p10` (V_p10), HPQ — are defined in the [glossary](docs/glossary.md).

| Tool | Description |
|---|---|
| `assess-layout` | Assess view layout quality across the legacy 8-metric severity-tiered rating (overlaps, crossings, pass-throughs, coincident segments, non-orthogonal terminals, spacing, alignment, label overlaps) plus the perception-aligned metrics (M1 visible-segment-length non-orthogonal terminals, M2 interior terminations, M3 zigzags, M4 connection-vs-edge coincidence, M5 hub-port quality, R8 corridor utilisation) and the M6 two-dimensional rating (layout tier × routing tier). Also reports the informational `parallelConnectionGap_V_p10` signal (10th-percentile parallel-segment gap on the V axis, anchored at 13.30 px on the ArchiMate reference) for narrow-corridor regime detection. Informational detections cover label truncation, parent-label obscured by child, and image sibling overlap. M3 (zigzag) skips connections already classified as pass-throughs so a single connection is not double-labelled. Self-element pass-throughs are reported but excluded from rating. The `nextSteps[]` envelope names the right precondition tool with violator IDs attached when an overlap, boundary violation, low hub-port-quality score, or grouped-view spacing/crossing breach is detected. Optional `includeViolatorIds` returns per-metric visual object IDs for targeted fixes |
| `detect-hub-elements` | Identify hub elements by counting visual connections per element, sorted descending. Hub thresholds: ≥ 5 connections is a hub candidate; > 6 connections gets an explicit 1D sizing suggestion `baseDimension + 15px × (connectionCount − 6)`; for elements with > 12 connections the response also surfaces a 2D-resize suggestion (`width += 15 × ⌈excess/2⌉`, `height += 15 × ⌊excess/2⌋`) so the agent can distribute ports across all four edges. The assessor's internal `M5_FACE_GUARD_MIN_CONNECTIONS = 4` is a separate per-face guard for the M5 hub-port-quality metric, not a hub-detection threshold |

### View Operations (1 tool)

| Tool | Description |
|---|---|
| `auto-connect-view` | Create visual connections for all existing model relationships between elements already placed on a view. Optional `showLabel: false` to suppress labels, and optional `lineColor`, `fontColor`, `lineWidth` to batch-style every connection it creates (combine with `relationshipTypes` filter to style by type) |

### Folder Management (5 tools)

| Tool | Description |
|---|---|
| `get-folders` | List folders (root-level by default, or children of a specific folder) |
| `get-folder-tree` | Folder hierarchy as a nested tree structure |
| `create-folder` | Create a new subfolder |
| `update-folder` | Update folder name, documentation, or properties |
| `move-to-folder` | Move a model object (element, relationship, view, or folder) to a different parent folder |

### Deletion (4 tools)

| Tool | Description |
|---|---|
| `delete-element` | Delete an element — cascades relationships and view references across all views |
| `delete-relationship` | Delete a relationship — cascades view connections across all views |
| `delete-view` | Delete a view and its visual contents (model elements preserved) |
| `delete-folder` | Delete a folder (requires `force: true` for non-empty folders) |

### Export (1 tool)

| Tool | Description |
|---|---|
| `export-view` | Render a view as PNG, JPG, SVG, or PDF — returned inline (base64 / blob) or written to file. Optional `quality` (1–100, default 90) for JPEG encoding; ignored for other formats. Vector formats (SVG, PDF) leave `width`/`height` unset because they are resolution-independent. Optional `outputDirectory` controls where files are saved (auto-creates directories; defaults to temp). SVG and PDF require the `com.archimatetool.export.svg` bundle that ships with Archi 5.7+ |

### Images (2 tools)

| Tool | Description |
|---|---|
| `add-image-to-model` | Import an image (icon) into the model archive. Preferred: `filePath` (local file) or `url` (HTTP download) — these bypass LLM text channel and avoid base64 corruption. Fallback: `imageData` (base64). Provide exactly ONE. Returns the archive `imagePath` for use with view composition tools. Images are deduplicated |
| `list-model-images` | List all images stored in the model archive with their paths and dimensions. Use the `imagePath` values with view composition tools to set images on elements, groups, or notes without re-importing |

### Batch & Mutation Control (4 tools)

| Tool | Description |
|---|---|
| `begin-batch` | Start batch mode — mutations are queued instead of applied immediately |
| `end-batch` | Commit all queued mutations atomically, or rollback (discard all) |
| `get-batch-status` | Check operational mode and queued operation count |
| `bulk-mutate` | Execute multiple mutations as a single compound command with back-references and optional `continueOnError` |

### Undo / Redo (2 tools)

| Tool | Description |
|---|---|
| `undo` | Undo the most recent mutation operation(s) with optional step count |
| `redo` | Redo previously undone operation(s) |

### Approval Workflow (3 tools)

| Tool | Description |
|---|---|
| `set-approval-mode` | Enable or disable human-in-the-loop approval for mutations |
| `list-pending-approvals` | List all pending mutation proposals awaiting approval |
| `decide-mutation` | Approve or reject a pending mutation proposal |

### Session Management (2 tools)

| Tool | Description |
|---|---|
| `set-session-filter` | Set persistent filters and field selection that apply to all subsequent queries |
| `get-session-filters` | Retrieve currently active session-scoped filters and field selection |

### Response Control

All query tools support response optimisation parameters:

- **Field selection:** `fields` param with presets (`minimal`, `standard`, `full`) or custom field arrays
- **Field exclusion:** `exclude` param to omit specific fields
- **Pagination:** Automatic for large result sets, with cursor-based continuation

## MCP Resources

The server provides 14 reference resources accessible to LLM clients.

### Prompts

| URI | Description |
|---|---|
| `archimate://prompts/model-exploration-guide` | Strategy guide for LLMs on how to efficiently search and traverse ArchiMate models |
| `archimate://prompts/explore-dependencies` | Workflow template for systematic dependency analysis of ArchiMate elements |
| `archimate://prompts/landscape-overview` | Workflow template for generating architecture landscape summaries |
| `archimate://prompts/routing-preconditions-checklist` | Single-page checklist for LLM agents to fetch before invoking `auto-route-connections` or `auto-layout-and-route` on any non-trivial view. Three preconditions (hub sizing, inter-element spacing, group arrangement + corridor-friendly spacing), one verification each, plus a disposition matrix mapping view shape to precondition order |

### References

| URI | Description |
|---|---|
| `archimate://reference/archimate-layers` | Comprehensive mapping of ArchiMate layers to element types with descriptions, plus a concept-to-element-type decision aid (Component vs Service, Process vs Function, Node vs Component, Actor vs Role) |
| `archimate://reference/archimate-relationships` | All ArchiMate relationship types with valid source/target combinations and usage guidance |
| `archimate://reference/archimate-specializations` | Specialization (IS-A subtype) vocabulary: when to use specializations vs properties, common patterns per layer, and the discovery/create/audit/delete tool pipeline |
| `archimate://reference/archimate-view-patterns` | Curated viewpoint patterns, layout algorithm guidance, and diagramming best practices for composing ArchiMate views |

### Viewpoint Recipes

A progressive-disclosure recipe library for laying out non-conventional ArchiMate viewpoints. Fetch the index first; it states the invariant build sequence once, routes conventional viewpoints (layered, landscape, hierarchy, information structure) to the view-patterns principles, and points non-conventional viewpoints to a single full recipe page with the topology block to match.

| URI | Description |
|---|---|
| `archimate://recipes/index` | Selector — fetch first. Conventional-vs-non-conventional decision table, the invariant build sequence, and the family selector |
| `archimate://recipes/application-integration` | Hub-and-spoke recipe for Application Cooperation / Integration views |
| `archimate://recipes/behaviour-process-flow` | Swimlane recipe for Business Process Cooperation, plus the Service Design / Customer Journey band layout |
| `archimate://recipes/motivation` | Directed influence-chain recipe for Motivation views (stakeholder → driver → assessment → goal → requirement) |
| `archimate://recipes/technology-deployment` | Nested-deployment recipe — nodes as containers with deployed software/artifacts, wired by network paths |
| `archimate://recipes/roadmap-migration` | Left-to-right plateau-timeline recipe for Implementation & Migration views |

## Mutation Safety

All write operations integrate with Archi's **CommandStack**, making every mutation **undoable** via `Ctrl+Z` in Archi or the `undo` / `redo` tools.

**Approval mode** adds a human-in-the-loop gate:

```
set-approval-mode(true)
  → LLM proposes mutations → they queue as "pending"
  → list-pending-approvals → review what's proposed
  → decide-mutation(id, "approve") → apply, or "reject" → discard
```

**Batch mode** groups mutations into atomic transactions:

```
begin-batch()
  → create-element(...)
  → create-relationship(...)
  → add-to-view(...)
end-batch()
  → All succeed together or all roll back
```

## Troubleshooting

**Server won't start**
- Check if port 18090 is already in use: `lsof -i :18090`
- Verify the bind address in preferences is valid

**LLM client can't connect**
- Confirm the server is running (menu shows "Stop MCP Server")
- Verify the port matches your client config
- Test connectivity: `curl -X POST http://127.0.0.1:18090/mcp -H "Content-Type: application/json" -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}'`

**Model appears empty**
- Ensure an ArchiMate model is open in Archi before connecting
- If the model was opened after the session started, reconnect the LLM client

**Mutations fail with validation error**
- Check the `archiMateReference` field in the error — it cites the relevant ArchiMate spec section
- Use `search-and-create` instead of `create-element` to avoid duplicates

**TLS connection issues**
- Verify the keystore file exists and the password is correct
- For self-signed certificates, ensure the client trusts the certificate or use `-k` with `curl`

---

# Developer Guide

The following sections are for developers who want to fork, extend, or contribute to the plugin.

For comprehensive technical documentation covering architecture internals, coordinate model, routing pipeline, and extension patterns, see [docs/](docs/). Metric acronyms and routing terms used throughout this README (M1–M6, R8, `parallelConnectionGap_V_p10`, HPQ, corridor, clearance, perimeter) are defined in the [glossary](docs/glossary.md).

## Project Structure

```
arch-mcp-server/
├── net.vheerden.archi.mcp/          # Main plugin bundle
│   ├── META-INF/MANIFEST.MF         # OSGi bundle configuration
│   ├── plugin.xml                    # Eclipse extension points
│   ├── src/net/vheerden/archi/mcp/
│   │   ├── McpPlugin.java           # Plugin lifecycle & preferences
│   │   ├── server/                   # Jetty + MCP SDK wiring
│   │   ├── handlers/                 # MCP tool implementations (19 handler classes)
│   │   ├── model/                    # EMF model access layer
│   │   │   ├── geometry/             # Geometry utilities
│   │   │   └── routing/              # Connection routing pipeline
│   │   ├── search/                   # Full-text search engine
│   │   ├── response/                 # Response formatting & DTOs
│   │   ├── registry/                 # Tool & resource registries
│   │   ├── session/                  # Session management & caching
│   │   ├── logging/                  # SLF4J to Eclipse ILog bridge
│   │   └── ui/                       # Preferences, menus, startup
│   ├── resources/                    # MCP resource content files
│   └── lib/                          # Bundled dependencies
│       ├── mcp-sdk/                  # MCP Java SDK 0.17.2
│       ├── jetty/                    # Jetty 12.0.18 (ee10)
│       ├── jackson/                  # Jackson 2.16.1
│       ├── elk/                      # Eclipse Layout Kernel 0.11.0
│       └── slf4j/                    # SLF4J 2.0.11
├── net.vheerden.archi.mcp.tests/    # Test fragment (OSGi)
└── README.md
```

## Architecture Layers

The codebase enforces strict layer boundaries to keep concerns separated:

```
┌─────────────────────────────────────────────────────┐
│  Layer 1 — Protocol          server/, registry/     │
│  Only MCP SDK + Jetty types. No EMF imports.        │
├─────────────────────────────────────────────────────┤
│  Layer 2 — Handlers          handlers/              │
│  DTOs + ArchiModelAccessor interface only.           │
│  No EMF or SWT imports.                             │
├─────────────────────────────────────────────────────┤
│  Layer 3 — Model             model/                 │
│  ONLY package that imports EMF / ArchimateTool.     │
│  Returns DTOs, never EObjects.                      │
├─────────────────────────────────────────────────────┤
│  Layer 4 — UI                ui/                    │
│  SWT/Eclipse UI only. Preferences, menus, status.   │
│  Never blocks Jetty threads.                        │
└─────────────────────────────────────────────────────┘
```

**Key rule:** Handlers never see EMF objects. All model access goes through the `ArchiModelAccessor` interface, which returns DTOs.

## Key Architecture Decisions

### Transport

- **Dual transport:** Streamable-HTTP (`/mcp`) + SSE (`/sse`) for backward compatibility
- Each transport gets its own `McpSyncServer` instance
- `HttpServletStreamableServerTransportProvider` and `HttpServletSseServerTransportProvider` both extend `HttpServlet`

### Threading

- **Reads:** Direct EMF access from Jetty threads (thread-safe for read-only)
- **Mutations:** Dispatched to the SWT UI thread via `Display.syncExec()` for CommandStack consistency
- UI thread is never blocked by read operations

### Response Envelope

Every tool response follows a standard structure:

```json
{
  "result": { },
  "nextSteps": ["Use get-relationships to explore connections", "..."],
  "_meta": { "totalCount": 42, "isTruncated": false, "durationMs": 12 }
}
```

### Error Handling

Structured errors with actionable guidance:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Relationship type not valid between these elements",
    "details": "ServingRelationship requires ApplicationComponent as source",
    "suggestedCorrection": "Use an ApplicationComponent or change relationship type",
    "archiMateReference": "ArchiMate 3.2 § 5.1.2"
  }
}
```

### Mutation Pattern

All mutation tools use the `PreparedMutation<T>` pattern:
1. Validate inputs and build the command (on Jetty thread)
2. Dispatch execution to UI thread via `Display.syncExec()`
3. Execute through Archi's CommandStack (enables undo/redo)
4. Return result DTO

For connections, `redo()` must null-then-reconnect due to Archi's `connect()` early-return guard.

## Adding a New Tool

1. **Create or extend a handler** in `handlers/`
2. **Register the tool** in `registerTools()` — define the JSON schema, description, and call handler
3. **Add model access** if needed — new method on `ArchiModelAccessor` interface, implemented in `ArchiModelAccessorImpl` (returns DTOs, not EObjects)
4. **Format responses** using `ResponseFormatter` with the standard envelope
5. **Handle errors** — catch at the handler boundary, translate to structured `ErrorResponse`
6. **Write tests** — mock `ArchiModelAccessor` for handler tests (no EMF runtime needed)

## Testing

The test bundle (`net.vheerden.archi.mcp.tests`) is an OSGi fragment with `Fragment-Host: net.vheerden.archi.mcp`, giving it full access to main plugin classes.

- **Run the whole suite** in Eclipse as a single "JUnit Plug-in Test" (e.g. the `AllPluginTestsRunner` launch) — the OSGi runtime it provides is required for full coverage. Individual classes can also be run one at a time.
- **Pure-Java tests** (geometry, layout algorithms, routing) can also run as standard JUnit tests without the Eclipse runtime
- Handler tests mock `ArchiModelAccessor` — no Archi installation needed
- **Tests requiring the Eclipse/OSGi runtime** — three classes touch Archi/Eclipse runtime singletons that only initialise under OSGi, so they **must** be run as a JUnit Plug-in Test (a headless JVM cannot initialise them and they are guarded to *skip* there rather than fail):
  - `McpPreferenceInitializerTest` — `org.eclipse.core.internal.preferences.ConfigurationPreferences`
  - `McpServerManagerTest` — `com.archimatetool.editor.model.IEditorModelManager`
  - `ArchiModelAccessorImplTest` (the two specialization-relationship cases) — `com.archimatetool.model.util.RelationshipsMatrix`

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| MCP Java SDK | 0.17.2 | Model Context Protocol implementation |
| Jetty | 12.0.18 (ee10) | Embedded HTTP server |
| Jackson | 2.16.1 | JSON serialization |
| Eclipse Layout Kernel (ELK) | 0.11.0 | Layered graph layout algorithms |
| SLF4J | 2.0.11 | Logging (bridged to Eclipse ILog) |
| Jakarta Servlet API | 6.0.0 | Servlet API for Jetty ee10 |
| Project Reactor | 3.7.0 | Async support (MCP SDK transitive) |

Eclipse/Archi runtime dependencies: `org.eclipse.ui`, `org.eclipse.core.runtime`, `org.eclipse.swt`, `org.eclipse.jface`, `org.eclipse.zest.layouts`, `com.archimatetool.model`, `com.archimatetool.editor`.

## PDE Build Notes

This is a pure Eclipse PDE project — no Maven or Gradle. Key files:

- `MANIFEST.MF` — OSGi bundle metadata, `Bundle-ClassPath` lists all JARs
- `build.properties` — PDE build includes
- `.classpath` — Eclipse project classpath

When adding a new JAR dependency, update **three places**: the `lib/` directory, `MANIFEST.MF` `Bundle-ClassPath`, and `.classpath`.

## Acknowledgments

- [Archi](https://www.archimatetool.com) - Archi® modelling toolkit
- [ArchiMate](https://publications.opengroup.org/archimate-library) - ArchiMate® Specification
- [Eclipse IDE](https://eclipseide.org) - Eclipse IDE™
- [MCP](https://modelcontextprotocol.io/) — Model Context Protocol™
- [ELK](https://github.com/eclipse-elk/elk) - Eclipse Layout Kernel™
- [Jackson](https://github.com/FasterXML/jackson) - Jackson JSON Library
- [Jetty](https://github.com/jetty/jetty.project) - Eclipse Jetty® - Web Container & Clients
- [SLF4J](https://github.com/qos-ch/slf4j) - Simple Logging Facade for Java

## License

This project is licensed under the MIT License.
See [LICENSE](LICENSE) for details.
