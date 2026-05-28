# Changelog

## v1.5.0 (2026-05-28)

Authoring completeness. v1.5 adds four new tools — `add-image-to-view` (place a standalone image as a diagram node), `add-view-reference-to-view` (embed one view inside another), `update-model` (write model name, purpose, and properties), and `find-concept-usage` (reverse where-used lookup before deletes and renames) — plus broader Archi-GUI styling coverage on view objects and connections (typography, gradient, alignment, line style, opacity), two new `export-view` formats (PDF and JPG, plus a real SVG instead of the previous stub), and a batch of fixes from the v1.4 ship-gate end-to-end run. Validated end-to-end on a fresh 221-element retail-bank model authored by an unbiased agent — 11/11 views ≥ good layout, 8/11 excellent. Tool count grows from 69 to **72**; resource count is unchanged at 14.

### New Tools

- **`add-image-to-view`** — Place a standalone image as a first-class diagram node on a view, sibling to notes, groups, and view-references. Distinct from the existing `update-view-object` `imagePath` parameter, which sets an icon overlay on an element. Requires `viewId` and `imagePath` (returned by `add-image-to-model` or `list-model-images`). Optional `x`/`y` (both-or-neither, parent-relative when `parentViewObjectId` is set), `width`/`height` (default: the image's natural dimensions read from the model archive; falls back to 200×200), and the full visual styling surface (fill / line / font colour, opacity, line width, line style, gradient, alignment, border type) plus image-specific `borderColor` and `documentation`. Typo'd `imagePath` values are rejected with the new `IMAGE_NOT_FOUND` error rather than silently rendering as a broken-image placeholder. Image visuals are bulk-mutate-callable and now round-trip via `get-view-contents` in a new `images` array.
- **`add-view-reference-to-view`** — Embed one ArchiMate view inside another as a clickable thumbnail — the agent-driven equivalent of Archi GUI's drag-view-onto-view. Useful for landscape views that embed each layer view, index views that link every viewpoint, and cross-cutting documentation views that reference detail views. Requires `viewId` (target) and `referencedViewId` (source — must be an existing ArchiMate view in the same model). Optional bounds, parent nesting, and the full visual styling surface. The referenced view's name is not stored on the visual; Archi reads it dynamically at render time, so renaming the referenced view via `update-view` updates every embedding automatically.
- **`update-model`** — Write the loaded model's own `name`, `purpose`, and custom `properties` as a single undo unit (mirrors the shape of `update-view`). At least one of the three must be provided; omitted fields stay untouched. Empty-string `purpose` clears the field; null property values remove a key. Archi 5.7/5.8 exposes a single free-text field on the model — `purpose` — so there is no separate `documentation` parameter. `bulk-mutate` accepts `update-model`.
- **`find-concept-usage`** — Reverse where-used lookup: given an element or relationship ID, returns every view and every visual object or connection that references it across the model. The inverse of `get-view-contents`. Intended for impact analysis before `delete-element`, `delete-relationship`, rename, or re-type workflows — one round-trip reveals the full cross-view footprint. Placements are grouped by view; each view carries a `viewKind` (`archimate` / `sketch` / `other`) plus `viewReferenceCount` and `visualReferenceCount`. Views are ordered by name, visual objects by ID — deterministic regardless of underlying iteration order.

### Read / Write Parity

- **`get-model-info` now returns `purpose` and `properties`** — read-side companion to `update-model`. Existing callers see byte-identical responses on default-state models; the new fields appear only when set.
- **`list-specializations` now returns `imagePath`** — surfaces the archive path of the specialization's icon when one is set; omitted otherwise.
- **`get-view-contents` now returns an `images` array** — image visuals (placed via `add-image-to-view`) round-trip on view reads. The DTO carries the visual's ID, image path, bounds, parent, border colour, and documentation.

### Existing Tools Extended

- **Relationship semantic attributes on `create-relationship` and `update-relationship`** — three new type-conditional optional parameters fill out ArchiMate semantics that the create/update path previously could not set:
  - `accessType` (AccessRelationship only) — enum: `"access"` (unspecified), `"read"`, `"write"`, `"readwrite"`.
  - `associationDirected` (AssociationRelationship only) — boolean.
  - `influenceStrength` (InfluenceRelationship only) — free text, max 255 characters; empty string clears.

  Supplying a parameter on the wrong relationship subtype is rejected at the boundary with a `suggestedCorrection` naming the valid type. The three fields populate on the response DTO only when the relationship is of the matching subtype, so JSON shapes for relationships of any other type are unchanged. The new fields automatically appear on every read surface (`get-relationships`, `search-relationships`, `get-view-contents`, `find-concept-usage`).
- **Specialization icons on `create-specialization` and `update-specialization`** — both tools accept an optional `imagePath` (an archive path from `add-image-to-model` or `list-model-images`). When set, Archi renders this image as the specialization's icon on every element or relationship of that specialization. `update-specialization` also accepts `clearImagePath: true` to explicitly clear the icon (mutually exclusive with `imagePath`). `update-specialization` has relaxed `newName` from required to optional — at least one of `newName`, `imagePath`, or `clearImagePath` must now be supplied.
- **`update-view-object` `labelExpression`** — `add-to-view` and `update-view-object` accept an optional per-view-object dynamic label template, e.g. `"${name}"` or `"${property:Owner}"`. Distinct from the literal stored `text` label; empty string clears.
- **Full styling completeness on view objects and connections** — `add-to-view`, `add-group-to-view`, `add-note-to-view`, and `update-view-object` accept optional typography (`fontName`, `fontSize`, `fontStyle`), `gradient` (`none` / `top-bottom` / `bottom-top` / `left-right` / `right-left`), note-specific `borderType` (`dogear` / `rectangle` / `none`), `deriveLineColor`, `outlineOpacity`, and `lineStyle` (`solid` / `dashed` / `dotted` / `none` — the outline border style of the view object itself). `add-connection-to-view` and `update-view-connection` accept the typography fields. All fields ride the existing styling rail as a single undo unit; existing calls are byte-identical and the new fields read back on the mutation response DTOs.
- **`add-to-view`, `add-group-to-view`, `update-view-object` `figureType` and `textAlignment` / `verticalTextAlignment`** — `figureType` (`rectangular` / `tabbed`) overrides Archi's default group tab; `textAlignment` and `verticalTextAlignment` control label position within the figure. These shipped in v1.4 but are now also accepted on `update-view-object` for consistency.
- **`export-view` PDF, JPG, and a real SVG** — `export-view` now accepts `format: "pdf"` (vector, print-ready) and `format: "jpg"` (lossy raster) in addition to `png` and `svg`. A new optional `quality` parameter (1–100, default 90) controls JPEG encoding quality; it is silently ignored for other formats. The previous "not yet supported" stub on `format: "svg"` is replaced with a real SVG XML export. Vector formats (SVG, PDF) leave `width`/`height` null because they are resolution-independent. PNG behaviour is byte-identical. SVG and PDF require the `com.archimatetool.export.svg` bundle (ships with Archi 5.7+); on a stripped Archi install only SVG and PDF emit `FORMAT_NOT_AVAILABLE`, the rest of the plugin still loads.

### Fixes

- **`add-to-view` and `update-view-object` now reject typo'd `imagePath` values with `IMAGE_NOT_FOUND`** — closes the silent-acceptance asymmetry that Story 14-8 left between these surfaces and `add-image-to-view`. Until now, a typo'd archive path on `add-to-view` or `update-view-object` (also via `bulk-mutate`) was accepted at the prepare boundary and rendered as a broken-image placeholder downstream, with no error envelope for the agent to act on. The validator that 14-8 added for `add-image-to-view` / `create-specialization` / `update-specialization` is now wired into the four `add-to-view` / `update-view-object` prepare paths, producing the byte-identical `IMAGE_NOT_FOUND` reject payload across all three tool surfaces. Empty-string `imagePath` on `update-view-object` remains the documented "clear the icon" sentinel and is not validated. Story 14-8.1.
- **`auto-connect-view` now skips connections between an ancestor and its descendant on the view** (which would render as a self-pass-through), reporting them under a new `skippedDueToNesting` field on the response DTO. The model relationship is preserved; the agent can choose to draw it explicitly via `add-connection-to-view` if desired. Closes the silent-acceptance asymmetry surfaced on View K of the 2026-05-28 retail-bank empirical — the tool was creating exactly the `M4 connectionEdgeCoincidence` defect class that `assess-layout` is built to catch. Sibling-pair, cross-branch, and flat-view behaviour are unchanged. Story 14-11.
- **`auto-route-connections` now surfaces autoNudge-blocked recommendations under a new `blockedRecommendations` field with a top-level `nudgeBlockedReason` (`"sibling_overlap"`), reserving `recommendations` for the advisory (`autoNudge=false`) path.** Until now, an `autoNudge=true` call against a view with overlapping sibling elements returned `recommendations` populated AND `nudgedElements` empty — a mixed signal the agent had to disambiguate via `structuredWarnings`. The underlying refuse-to-nudge behaviour (Story 13-9) is unchanged; the `AUTO_NUDGE_SKIPPED_SIBLING_OVERLAP` structured warning continues to emit in parallel. Story 14-12, closing the silent-acceptance ratchet surfaced on View J of the 2026-05-28 retail-bank empirical.
- **`delete-view` now cascades view-reference visual placeholders correctly** — when a view that was embedded in other views (via `add-view-reference-to-view`) is deleted, the visual placeholders pointing at it across the rest of the model are now removed atomically with the view, mirroring Archi GUI's tree-delete cascade. Without the cascade, the EMF cross-reference stayed resolvable in-memory (so the live MCP session saw no problem) but `.archimate` save+reload wrote a dangling reference and Archi could not reopen the file with an "Unresolved reference" error. Reproduced against a real model on 2026-05-27. Latent in `delete-view` since v1.0 but only newly exploitable in v1.5, because v1.5 added the first MCP path to *create* view-reference visuals. Placeholders contained inside the view being deleted are correctly skipped (they die with the view).
- **`auto-route-connections` source-side reversal on lane-crossing hand-off** — when a connection crossed a swimlane boundary at a hub or anchor, the late-stage terminal-egress clearance passes treated the endpoint's ancestor container as an obstacle, occasionally reversing the source-side terminal segment. The clearance passes now mirror A*'s ancestor-exclusion rule, so the endpoint container is no longer in the obstacle set during clearance — the connection routes from the inside of its container out, matching the GUI's manual-routing result.
- **`auto-route-connections terminals-only` mode interior-termination and zigzag vetoes** — the terminals-only rectification no longer leaves a route in a worse state than it found it. A rectification that would introduce an interior termination or convert a clean L-bend into a zigzag is now vetoed and the original terminal is preserved.
- **`get-view-contents format: "graph"` no longer crashes on views with groups or notes** — the graph-format converter was assuming every view object was an element-bearing node, and threw on groups and notes. It now emits them as the documented graph nodes.
- **`bulk-mutate` operation-cap contract reconciled** — the `operations` array's own parameter description advertised "Max 50 operations" while the schema, the tool-level description, and the enforced limit all said 150 (the limit was raised in v1.2). Agents read the param description closest to the array and self-throttled to ~48-op batches. Both surfaces now derive the cap from the same constant. No behavioural change — the enforced limit was, and remains, 150.
- **`bulk-mutate` supported-tools list completed** — the tool advertised two incomplete supported-tools enumerations: the parameter description named 10 of 24, and the tool-level description named 12 — both omitting high-value mutations like `delete-element`, `update-relationship`, `update-view`, and the folder and specialization tools. An agent reading either list could wrongly conclude those tools were not bulk-callable. Both descriptions now derive from the same source of truth and list every supported tool. No behavioural change.
- **`update-view-connection` no longer falsely reports "Connection bendpoints cleared" on styling-only updates** — the next-steps message now distinguishes bendpoints-cleared, bendpoints-set, and styling-only changes accurately.

### Documentation

- The README catalogs all 72 tools and the new fields on the extended tools.
- The mutation-model and mcp-integration docs cover the new commands and DTOs, the `IMAGE_NOT_FOUND` error code, and the read/write parity additions.

## v1.4.0 (2026-05-21)

Routing-perception cycle. v1.4 reworks how layout quality is *measured* so the rating reflects what an architect actually perceives, and lifts the LLM-facing precondition surface so an agent can set a view's geometry up correctly *before* routing. Tool count grows from 65 to 69; resource count grows from 7 to 14. The metric acronyms used below — M1–M6, R8, `parallelConnectionGap_V_p10`, HPQ — are defined in the [glossary](docs/glossary.md).

v1.4 delivers improved layout and routing compared to v1.3, validated end-to-end by an LLM agent driving the tools to build a comprehensive multi-view reference model.

### New Tools

- **adjust-view-spacing** — Inflate inter-element and inter-group spacing on an existing view, then re-route, in a single atomic operation. Use it when a view is laid out correctly but looks cramped, without re-running layout from scratch and losing manual placement.
- **apply-element-spacing-recommendations** — Widen within-group element spacing until the view reaches good quality, is honestly flagged as needing a structural reflow, or the iteration budget runs out. Runs an internal control loop — take a small step, re-assess, then continue, escalate, or stop — and reverts any step that makes things worse. Returns before/after `assess-layout` snapshots plus `terminationReason`, `iterationCount`, and `appliedDeltas`. Set `dryRun: true` to preview. One call is one undo step.
- **apply-group-spacing-recommendations** — The same control loop applied to inter-group corridors only, preserving group order and topology.
- **apply-spacing-recommendations** — Runs both arms (element, then inter-group) in one transactional call. The `scope` parameter (`both` / `element` / `group`) selects which arm(s) run. Per-iteration step caps prevent over-inflation; per-arm termination reasons, iteration counts, and applied deltas are reported.

### Routing Preconditions (LLM-Facing Surface)

A precondition is a property of the view's geometry that must hold *before* routing — the pipeline can refine routes but cannot create hub sizing, spacing, or group arrangement. v1.4 makes the three load-bearing preconditions first-class.

- **Control loop with density-aware termination** — The three spacing tools no longer apply one delta and return; each iterates internally and stops honestly. A step that degrades the view is always reverted, so the tool never returns a silently-worse view.
- **Sound infeasibility certificate** — When a view is provably too dense for spacing to fix, the tool stops, reports the reason, and offers a user-consentable structural reflow instead of churning. It never reflows on its own — a reflow moves user-placed elements, so it is the user's decision.
- **`archimate://prompts/routing-preconditions-checklist` (new resource)** — The single checklist an agent fetches before `auto-route-connections` or `auto-layout-and-route` on a non-trivial view: three preconditions, one verification each, plus a matrix mapping view shape to precondition order.
- **Density-aware defaults** — `arrange-groups` and `adjust-view-spacing` derive a connection-count-aware spacing default when none is supplied, instead of a static value. Pass an explicit value to suppress.
- **`assess-layout` names the fix** — When the assessment finds a violation that maps to a precondition (low hub-port quality, edge-coincident segments on a grouped view, children outside a parent's bounds), the `nextSteps` envelope names the right tool and attaches the violator IDs.
- **Structured warnings on `auto-route-connections`** — The response carries a machine-readable `structuredWarnings` list (`code`, `message`, `remediationTool`, `remediationViolatorIds`) alongside the free-text warnings, so an agent can act deterministically.

### Assessment (Perception-Aligned)

`assess-layout` gains metrics for defect classes the old assessor could not see. See the [glossary](docs/glossary.md) for each metric and the [Layout Engine](docs/layout-engine.md) doc for thresholds.

- **New metrics** — M2 interior terminations, M3 zigzag/reversal, M4 connection-vs-edge coincidence, M5 hub-port quality (HPQ), and R8 corridor utilisation. M1 (non-orthogonal terminals) is corrected to count only the visible portion of a diagonal, ending an over-report.
- **Two-dimensional rating (M6)** — The overall rating now reports `(layoutTier, routingTier)` and takes the worse of the two, so a routing fix on a well-laid-out view no longer drags its layout tier down.
- **Re-anchored cut-points** — Any sibling overlap now caps the layout tier at `poor`, and a parent label obscured by a child is promoted into the rating — both align the score with the perception that a single visible defect reads as a broken view.
- **Informational narrow-corridor signal** — `parallelConnectionGap_V_p10` surfaces when a view is in the narrow-corridor regime, so an agent can recognise that spacing tools cannot help and the remedy is structural. It does not affect the rating.
- **Classification precedence** — A connection already flagged as a pass-through is no longer also double-counted as a zigzag.
- **Density-aware non-orthogonal scoring** — Non-orthogonal terminals are scored by ratio rather than raw count (so connection-heavy views are not over-penalised) and treated as cosmetic, reflecting that diagonal terminals rarely affect comprehension.

### Tool Surface Additions

- **Group and element styling** — `add-to-view`, `add-group-to-view`, `add-note-to-view`, and `update-view-object` accept `figureType` (`rectangular` / `tabbed`), `textAlignment`, and `verticalTextAlignment`. These close v1.3 feedback that groups were always tabbed and labels always centred. Additive and non-breaking; existing calls are unchanged, and the new fields read back on `get-view-contents`.
- **Hub 2D-resize suggestion** — For elements with more than 12 connections, `detect-hub-elements` also suggests a two-dimensional resize so ports can spread across all four edges, not just one.
- **Hub-aware spacing** — The spacing tools select a wider spacing tier when a view contains hub elements, since formula-sized hubs consume corridor space.

### Layout Fixes (vs v1.3)

- **Text boxes reserve room for their text** — `add-note-to-view` and `add-group-to-view` size the box to fit wrapped content when the caller does not pin a height, so descriptive titles and long group labels no longer clip. Explicit sizes are unchanged; heights clamp to documented maxima.
- **Containers reserve their icon corner** — A container that carries a corner-anchored icon grows enough to keep the icon clear of nested children, preventing the icon-on-child overlap. Containers without an icon are unchanged.
- **`arrange-groups` reserves a lane for standalone hubs** — Topology row/column arrangement now centres a top-level element that connects across multiple groups in an inter-group lane between its neighbours, delivering the "place it between the zones" layout the viewpoint recipes describe.
- **Parent groups resize to contain their children** — A child moved outside its parent group's bounds now triggers a parent resize, across all three paths that can move a child (auto-nudge, the spacing tools, and direct object update). Closes a long-standing defect where a group was left too small for its contents.

### Routing Fixes (vs v1.3)

- **Self-element pass-throughs resolved** — A connection whose route looped back through its own source or target is now corrected algorithmically. This closes a user-visible issue present since v1.0.
- **Terminals-only routing mode** — `auto-route-connections` accepts `mode: "terminals-only"`, which rectifies only the diagonal terminal segments of automatic-layout output without re-routing whole connections — avoiding the crossing inflation a full re-route causes on that input.

### Routing Pipeline Improvements (vs v1.3)

These pipeline improvements deliver better routing than v1.3 across the reference views, and are protected by regression tests:

- Seeded multi-start routing — picks the best of several candidate routes, and is never worse than the single-shot route by construction.
- Hub-perimeter and terminal-segment corridor handling for multi-bendpoint routes.
- Channel-global ordered nudging that spreads parallel segments across whole channels, including inter-group corridors.
- An invariant that locks a connection's perimeter-terminal face through the whole pipeline, preserving hub-port distribution end-to-end.

### Viewpoint Recipes

- **Recipe library (six new resources)** — A progressive-disclosure library under `archimate://recipes/*`. Fetch `archimate://recipes/index` first; it states the build sequence once, routes conventional viewpoints to the existing view-patterns principles, and points non-conventional viewpoints (application integration, behaviour/process flow, motivation, technology deployment, roadmap/migration) to a single recipe page with a topology block to match.
- **Element-type and nesting guidance** — The view-patterns and layers references are sharpened so an agent picks the right element type (Component vs Service, Process vs Function, Node vs Component, Actor vs Role) and nests structural parts instead of drawing a redundant Composition or Aggregation connector. These target recurring v1.3 reports of wrong element type and drawn-instead-of-nested composition.

### Quality Protection

Every quality threshold introduced in v1.4 ships with a regression test that pins it against the manual-routed reference, so a future change that regresses a metric fails a test instead of silently shipping. See [Layout Engine — JUnit-Protected Release-Gate Metrics](docs/layout-engine.md).

### Documentation

- **New: [glossary](docs/glossary.md)** — Defines every metric acronym, routing term, and rating tier in one place.
- **New: bibliography** — A numbered research bibliography for the layout engine and routing pipeline, with a per-stage citation map and a separate modelling/visual-design reference subsection backing the LLM-facing guidance.
- **Updated** — The routing-pipeline and layout-engine docs cover the assessor redesign, the control loop and infeasibility certificate, and the pipeline groundwork. The README catalogs all 69 tools, the new metrics, and the new resources. Tool descriptions are swept so an agent gets the full spacing and routing contract from the tool surface alone, with external guidance pointing to the MCP resources.

---

## v1.3.0 (2026-04-10)

Expressiveness cycle. Adds first-class support for ArchiMate **specializations** (IS-A subtypes of element and relationship types) across the read, write, search, and resource surfaces. Tool count grew from 60 to 65. Also bundles quality enhancements (B50–B55) discovered during E2E validation runs.

### New Tools

- **list-specializations** — List every specialization defined on the model with `(name, conceptType, layer, usageCount)`. Optional `conceptType` filter narrows the result to a single concept binding.
- **create-specialization** — Define a specialization explicitly without creating any element. Idempotent — returns the existing specialization on duplicate `(name, conceptType)`. Use this to pre-register a vocabulary at session start.
- **update-specialization** — Rename a specialization. Refuses to merge into an existing target name (collision-rejecting). Existing references on elements and relationships move with the rename.
- **delete-specialization** — Remove a specialization. Refuses by default if any concept uses it; pass `force: true` to detach all references and delete in one atomic command. Refuses force-delete on concepts that carry more than one specialization (clear the extras first).
- **get-specialization-usage** — Pure query. Lists every element and relationship referencing a specialization. Call before rename or delete to audit the blast radius.

### New Capabilities

- **Inline specialization on element and relationship mutations** — `create-element`, `create-relationship`, `update-element`, and `update-relationship` accept an optional `specialization` parameter. On create, the specialization is auto-created if missing — the element/relationship and the specialization land in one CompoundCommand (single undo unit). On update, pass an empty string `""` to clear all specializations from the concept. Source, target, and type remain immutable on relationship updates.
- **Specialization filter on search** — `search-elements` and `search-relationships` accept a `specialization` filter that returns only concepts of the given specialized type. Combines with existing `type`, `layer`, and text filters.
- **specializationCount on `get-model-info`** — Model overview now reports the total specialization count. Non-zero means the model uses a custom vocabulary; call `list-specializations` to browse it.
- **`specialization` field on `ElementDto` and `RelationshipDto`** — Query responses now expose the primary specialization name on every element and relationship DTO. (See "Multi-Profile Caveat" in the new resource for the single-primary semantics.)
- **Connection styling on `auto-connect-view`** — `auto-connect-view` accepts `lineColor`, `fontColor`, and `lineWidth` parameters, applied uniformly to every connection it creates. Combines with `relationshipTypes` to batch-style connections by relationship type in a single call (e.g. blue for API calls, orange for domain events).

### Routing Pipeline Improvements

- **Self-element pass-through rating tolerance (B54)** — Self-element pass-throughs (a connection's route entering its own source or target through an interior point rather than from an edge) no longer penalise the overall layout quality rating. Cross-element pass-throughs still penalise as before. Informational reporting is preserved for both types — `assess-layout` still surfaces the count, but the rating reflects only structurally meaningful violations.

### Layout Improvements

- **Containment-aware dynamic label height in `resize-elements-to-fit` (B50)** — Replaces the fixed 25px containment top margin with a per-parent label height computed from SWT font metrics and word-wrap simulation. When a parent element wraps its label across multiple lines, children are shifted down to clear the full label height instead of being obscured by the lower lines. Parent height never shrinks during the operation — only grows to accommodate the wrapped label and its children.
- **Element-count heuristic for grouped layout intra-group arrangement (B51)** — `auto-layout-and-route` with `mode: "grouped"` and `optimize-group-order` now choose intra-group arrangement based on element count and flow direction. For vertical flow (DOWN/UP): 1-3 elements arrange as a row, 4+ elements arrange as a grid. For horizontal flow (RIGHT/LEFT): column arrangement is preserved. Replaces the previous hardcoded column arrangement that produced 1:12 aspect ratio strips on vertical-flow grouped views.

### Assessment Improvements

- **Violator IDs for targeted fixes (B55)** — `assess-layout` accepts a new optional `includeViolatorIds` parameter (default `false`). When enabled, the response includes a `violatorIds` map keyed by metric name, each containing the list of visual object IDs that violate that metric. Supported metrics: `overlaps` (both element IDs per pair), `passThroughs` (connection IDs, cross-element only), `coincidentSegments` (connection IDs sharing corridors), `nonOrthogonalTerminals` (connection IDs with diagonal entry), and `boundaryViolations` (child IDs outside parent bounds). Crossings are excluded as an emergent property best addressed by global tools. The complete ID set is returned (no cap), enabling surgical per-element fixes without full re-layout.
- **Informational containment overlap suggestion** — When `assess-layout` reports containment overlaps (ancestor-descendant nesting), the suggestions list now includes an explicit informational note clarifying these are expected overlaps that need no action. Previously, the bare counter caused LLM consumers to interpret grouped views as broken.
- **Informational detections in `assess-layout` (B53)** — Three new detections appear in the assessment output but do not affect the overall rating:
  - **Label truncation** — word-wrap-aware vertical overflow check identifies elements whose label rendering exceeds the element bounds.
  - **Parent label obscured by child** — flags parent elements whose label area is overlapped by a child element. (Notes are excluded from this check.)
  - **Image sibling overlap** — flags elements with custom images that visually overlap sibling elements at the same containment level.
  These detections give LLM agents actionable signals to fix label and image quality issues without breaking the severity-tiered rating system.

### Bug Fixes

- **Bulk specialization profile deduplication (B54)** — Fixed a data-integrity bug where `bulk-mutate` creating multiple elements with the same new specialization (e.g. 8 `ApplicationComponent` elements all with `specialization: "Cloud Platform"`) produced duplicate specialization profiles — one per element in the batch — instead of sharing a single profile. Downstream operations were affected: `update-specialization` (rename) only renamed 1 of N references, `delete-specialization` only removed 1 of N, and `get-specialization-usage` understated impact. Root cause: bulk-mutate runs all prepare methods before dispatching any commands, so `resolveOrCreateProfile` could not find profiles created by earlier (not yet executed) operations in the same batch. Fix introduces a `ThreadLocal` bulk profile cache scoped to `executeBulk`, consulted before model lookup. Single-call (non-bulk) paths are unaffected.

### Resource Updates

- **archimate-specializations.md (new resource)** — Comprehensive specialization reference at `archimate://reference/archimate-specializations`. Covers identity rules (`(name, conceptType)` pair), the specializations-vs-properties decision table (do NOT use specializations for environment/version/owner/status), the full 9-step tool pipeline, common element specializations per layer, common relationship specializations, the bulk-mutate pre-registration pattern, the multi-profile caveat (primary-only semantics), and an end-to-end Cloud Server Technology landscape walkthrough. Total resource count grew from 6 to 7.
- **archimate-view-patterns.md** — Added "Specialization Hierarchy" entry to the Common Viewpoint Patterns table and a matching row in Layout Conventions by Viewpoint, cross-linking to the new specializations reference.

### Documentation

- Mutation model documentation updated with the inline `specialization` parameter contract on create/update mutations, the auto-create-on-first-use semantics, and the bulk profile deduplication cache.
- MCP integration documentation updated with the new specialization tools and the new `archimate-specializations` resource.
- Extension guide updated with `SpecializationHandler` as an example of registering a domain-focused handler with multiple related tools.
- Layout engine documentation updated with B50 dynamic label height, B51 grouped intra-group heuristic, B53 informational detections, B55 violator IDs, and containment overlap suggestion.
- Routing pipeline documentation updated with B54 self-element pass-through rating tolerance.
- README updated with the complete 65-tool catalog, new specialization section, and B55 violator IDs on assess-layout.

---

## v1.2.0 (2026-04-07)

Quality, completeness, and routing diversity cycle. Tool count grew from 57 to 60. Corridor diversity routing reduces coincident segments by spreading connections across available corridors. Auto-sizing ensures element labels are never truncated. Full CRUD coverage achieved for all core model objects.

### New Tools

- **clone-view** — Duplicate an existing view with all visual contents (elements, groups, notes, connections, bendpoints, styling). The clone references the same model objects — useful for layout experiments or presenting alternative arrangements for comparison.
- **update-relationship** — Update relationship name, documentation, or properties. Source, target, and type are immutable (delete and recreate to change those). Completes full CRUD coverage for relationships (previously only create/delete existed).
- **resize-elements-to-fit** — Resize all (or selected) elements on a view to fit their labels using SWT font metrics. Two-pass algorithm: children sized first, then parents sized to contain children + own label + padding. Recommended after placing elements without `autoSize` or when element names change.

### New Capabilities

- **Auto-size at placement** — `add-to-view` accepts `autoSize: true` to compute element dimensions from label text at placement time using SWT font metrics with aspect-ratio-aware sizing (target 1.5:1, range [1.2:1, 2.5:1]). Short names (≤15 chars) keep default 120x55. Explicit `width`/`height` take precedence. Eliminates the need for a post-placement resize pass on flat views.
- **Corridor diversity routing** — `CorridorOccupancyTracker` records which corridors are used by previously routed connections. The A* cost function applies a multiplicative penalty for occupied corridors (`effectiveDistance *= 1 + occupancyWeight * occupancy`, default weight 0.75), encouraging route diversity and reducing coincident segments without post-processing.

### Routing Pipeline Improvements

- **Occupancy-aware A* routing** — New `CorridorOccupancyTracker` (pure-geometry class) records axis-aligned corridor usage after each connection is routed. Corridor keys use tolerance-aware grouping (`H:y` / `V:x`, 2px tolerance) matching the `CoincidentSegmentDetector` and `PathOrderer` formats. The A* router queries occupancy per edge and applies a multiplicative cost: `effectiveDistance *= (1 + occupancyWeight * occupancy)`. This steers later connections away from corridors already carrying traffic, producing visually diverse paths and reducing the need for post-routing coincident segment resolution.

### Bug Fixes

- Fix autoNudge false positive on containment overlaps — `OverlapResolver.hasOverlappingElements()` now excludes containment overlaps (parent-child nesting, e.g., ApplicationFunction inside ApplicationComponent). Previously, legitimate visual nesting was incorrectly detected as sibling overlap, causing autoNudge to skip routing and report degenerate geometry.
- Fix grouped layout group overlap and boundary violations — `arrange-groups` and `layout-within-group` now correctly prevent group-on-group overlaps and child elements extending outside parent group boundaries after layout operations.
- Fix Claude Code MCP config `type` field — README client configuration example corrected from `"type": "streamable-http"` to `"type": "http"`. Added Claude Desktop configuration instructions with `mcp-proxy` for both Windows and macOS.

### Resource Updates

- **archimate-view-patterns.md** — Added `autoSize: true` guidance to all view composition workflow branches (Branch 1, 2, 3). Added "Auto-Sizing Elements to Fit Labels" section with decision table for when to use `autoSize` vs `resize-elements-to-fit` vs `layout-within-group` `autoWidth`. Added `clone-view` guidance to Tips section for layout experiments and alternative arrangement comparison.

### Documentation

- Routing pipeline documentation updated with corridor diversity section (CorridorOccupancyTracker, occupancy-aware A* cost formula, configuration constants).
- Layout engine documentation updated with auto-size and resize-elements-to-fit section.
- Architecture documentation updated with CorridorOccupancyTracker in pure-geometry routing subpackage, updated handler tool counts.
- README updated with complete 60-tool catalog, new tool entries for clone-view, update-relationship, and resize-elements-to-fit.

---

## v1.1.0 (2026-04-03)

Post-release enhancement cycle (84 commits). Tool count grew from 51 to 56. Routing pipeline refined through 19 quality iterations (B31-B46) achieving clearance-weighted pathfinding with corridor directionality, group-wall awareness, path straightening, terminal orthogonality enforcement, and severity-tiered quality assessment.

### New Tools

- **search-relationships** — Search all relationships by text, type, and source/target element layer without needing an element ID first. Mirrors the search-elements pattern with pagination and field selection.
- **detect-hub-elements** — Identify high-connectivity elements on a view, sorted by connection count. Returns sizing suggestions using the hub element formula for elements with >6 connections.
- **layout-flat-view** — Automatic layout for flat (non-grouped) views with row, column, or grid arrangement. Supports `sortBy` (name/type/layer) and `categoryField` (type/layer) for organized grouping.
- **add-image-to-model** — Import images into the model archive for use on view objects. Images are deduplicated — re-importing the same bytes returns the existing path.
- **list-model-images** — List all images stored in the model archive with paths and dimensions.

### New Capabilities

- **Grouped-view layout mode** — `auto-layout-and-route` with `mode: "grouped"` orchestrates the full grouped-view workflow (layout-within-group + arrange-groups + optimize-group-order + auto-route-connections) in a single atomic tool call with quality iteration. Replaces a manual 5-7 step sequence.
- **Custom images on elements, groups, and notes** — `add-to-view`, `add-group-to-view`, `add-note-to-view`, and `update-view-object` accept `imagePath`, `imagePosition`, and `showIcon` parameters for custom 16x16 icons.
- **Connection label positioning** — `add-connection-to-view` and `update-view-connection` accept `labelPosition` (`"source"`, `"middle"`, `"target"`) to control where labels sit along the connection path.
- **Connection label suppression** — `add-connection-to-view`, `update-view-connection`, and `auto-connect-view` accept `showLabel: false` to suppress relationship name labels.
- **Connection styling at creation** — `add-connection-to-view` accepts `lineColor`, `fontColor`, and `lineWidth` for styling connections when they are first placed.
- **View containment tree format** — `get-view-contents` with `format=tree` returns a compact group hierarchy for token-efficient group discovery before layout operations.
- **Image export directory control** — `export-view` accepts `outputDirectory` to control where PNG/SVG files are saved (auto-creates directories).
- **Topology group arrangement** — `arrange-groups` with `arrangement: "topology"` orders groups by inter-group connection density to minimize long-range crossings. Supports `direction: "horizontal"` for left-to-right flow patterns.
- **Duplicate relationship prevention** — `create-relationship` is now idempotent — creating a relationship that already exists returns the existing one instead of producing a duplicate.

### Routing Pipeline Improvements

- **Clearance-weighted A* routing** — A* cost function includes perpendicular clearance penalty (`clearanceWeight / max(clearance, 1.0)`, default weight 75.0) that steers the router toward open space. `computePerpendicularClearance()` measures distance from each graph edge to the nearest obstacle boundary. E2E result: crossings -41%, pass-throughs eliminated on test views.
- **Post-routing path straightening** — New `PathStraightener` class applies four correction passes after routing: snap-to-straight alignment for near-aligned segments, direction reversal elimination, staircase jog collapse, and redundant bend removal. Terminal anchors are protected throughout.
- **Post-simplification path shortcutting** — Late-stage greedy simplification (Stage 4.7g) finds farthest reachable point via straight line, horizontal-first L-turn, or vertical-first L-turn. Terminal bendpoints preserved as chain anchors. E2E result: crossings -27%.
- **Post-simplification coincident segment resolver** — Redesigned from endpoint-based (~350 lines) to corridor-based reuse of `CoincidentSegmentDetector` (~12 lines). Catches coincidences introduced by all post-processing stages. E2E result: coincident segments -50%.
- **Proportional corridor spacing** — Replaced fixed 10px offset delta with proportional gap distribution. Three-pass architecture: collect corridor groups, compute perpendicular gap via obstacle scanning, distribute segments proportionally with 8px minimum separation floor. Falls back to fixed-delta when corridor is too narrow. E2E result: coincident segments -80%.
- **Exterior perimeter routing** — Split obstacle clearance (10px) from perimeter boundary margin (50px), enabling routes to travel around the outside of element clusters. E2E result: routing success 12% to 100%.
- **Pass-through-aware face selection** — Phase 1.3 in `EdgeAttachmentCalculator` builds trial paths with current face assignments and checks for self-element pass-throughs. When detected, tries alternative faces in angular proximity order. Phase B re-routes terminal segments after face swap. E2E result: pass-throughs -75%.
- **Router corridor re-route** — Stage 5a re-routes failed connections (element crossings) using fresh A* search with the full pipeline cleanup sequence.
- **Terminal approach direction** — Phase 1.2 in `EdgeAttachmentCalculator` prefers natural entry direction for nearly-aligned elements (dominant axis > 2x minor axis). Hub elements excluded to preserve distributed port allocation.
- **Self-element pass-through correction** — Two-phase fix: face swap (B34) + comprehensive face selection with trial path validation (B35). Safety net re-run of `correctEndpointPassThroughs()` retained as defense-in-depth.
- **Corridor directionality penalty** — Cosine-based `computeCorridorDirectionalityCost()` penalizes A* edges that move perpendicular or away from the target (`directionalityWeight * (1 - cos(angle)) / 2`, default weight 30.0). Steers connections toward direct approach paths. E2E result: non-orthogonal terminals reduced from 33 to 2 on Application Collaboration view.
- **Clearance cap** — `MAX_EFFECTIVE_CLEARANCE` (60px) caps the clearance benefit to prevent exterior corridors with unlimited space from becoming artificially attractive ("perimeter suction"). Clearance cost formula: `clearanceWeight / max(min(clearance, 60), 1.0)`.
- **Group-wall clearance cost** — `computeGroupWallClearance()` measures perpendicular distance from A* edges to the nearest group boundary. Edges running inside groups (close to group walls) are penalized, steering the router toward inter-group gaps rather than inside-group-wall corridors. Uses the same clearance weight and MAX_EFFECTIVE_CLEARANCE cap as obstacle clearance.
- **Center-termination fix** — Stage 4.7k `fixCenterTerminatedPath()` detects bendpoints at exact element center coordinates (zero-length ChopboxAnchor ray → visual center termination) and repositions them 1px outside the correct edge face midpoint. Applied twice: after edge attachment and as defense-in-depth after cleanup.
- **Post-nudge center-termination gap** — Re-runs `fixCenterTerminatedPath()` after edge nudging to catch center-terminations introduced by nudge position shifts.
- **Interior terminal BP fix** — Stage 4.7m `fixInteriorTerminalBPs()` detects and fixes all bendpoints inside source/target element bounds (not just at exact center). Terminal BPs are repositioned to edge face midpoints; intermediate interior BPs are removed. L-bends are inserted where repositioning breaks orthogonality.
- **Post-pipeline orthogonality enforcement** — Stage 4.7n `enforceOrthogonalPaths()` detects diagonal segments remaining after all processing and inserts horizontal-first L-turn bendpoints to restore orthogonality. Catches edge cases where cleanup stages remove BPs without reinserting L-bends.
- **Approach direction threshold relaxation** — Phase 1.2 `correctApproachDirection()` threshold relaxed from 2:1 to 1.2:1 dominant-to-minor axis ratio. More connections now receive natural approach direction correction, reducing diagonal terminal segments.

### Assessment Improvements

- **Severity-tiered rating recalibration** — Overall quality rating uses a three-tier severity system instead of worst-metric-wins. Tier 1 (critical): overlaps, pass-throughs, coincident segments — drives overall rating directly. Tier 2 (moderate): crossings, non-orthogonal terminals — contribution capped at "fair". Tier 3 (cosmetic): spacing, alignment, label overlaps — contribution capped at "good".
- **Non-orthogonal terminal metric** — New `nonOrthogonalTerminalCount` metric with per-metric rating in the assessment breakdown (0 = "pass", 1-3 = "fair", 4+ = "poor").
- **Coincident segment rating** — `coincidentSegmentCount` now has its own per-metric rating (0 = "pass", 1-3 = "good", 4-8 = "fair", 9+ = "poor").
- **Relaxed leniency gate** — Grouped-view crossing leniency allows up to 3 pass-throughs (previously required 0).

### Routing Quality Improvements

- **Fallback edge port routing** — When the primary port leads to a failed route, the router tries up to 3 alternative source/target port combinations before giving up.
- **Auto-nudge on route failure** — `auto-route-connections` with `autoNudge: true` automatically moves blocking elements and re-routes failed connections in a single atomic operation (up to 2 iterations).
- **Auto-nudge group boundary awareness** — When elements are nudged, parent groups resize to accommodate the new positions.
- **Router exit point centering** — Connection terminal segments now exit from the nearest edge center instead of using ray-to-first-bendpoint geometry, producing cleaner perpendicular exits.
- **Router snap-to-straight threshold** — Eliminates Z-bends for port offsets of 20px or less, producing straight connections where near-alignment exists.
- **Hub port distribution** — Edge attachment points for high-connectivity elements are distributed evenly across the element face, reducing connection bundling.
- **Routing quality guardrails** — Crossing inflation detection and 8px minimum bendpoint clearance enforcement prevent quality regressions.
- **Route crossing delta response** — Routing responses now include before/after crossing metrics for quality comparison.

### Layout Improvements

- **Label-aware hub sizing** — Hub sizing suggestions account for label dimensions when recommending element size increases.
- **ELK group non-overlap constraint** — AABB sweep-line correction prevents ELK from overlapping groups in hierarchical layouts.
- **ELK limiting factor response** — Layout responses now report which constraint (spacing, hierarchy depth, group count) limited the result quality.
- **Multi-trial label optimizer** — Label position optimization runs multiple trials with targetRating fallback to find positions that minimize overlaps.
- **Per-group arrangement preservation** — `optimize-group-order` preserves internal arrangement patterns (row/column/grid) when reordering elements to minimize crossings.
- **Adjacent-swap local search** — `CrossingMinimizer` uses adjacent-swap local search as a secondary strategy when barycentric heuristic stalls.
- **Flat view embedded children** — `layout-flat-view` correctly repositions elements with embedded children by treating them as larger boxes.

### Bug Fixes

- Fix autoNudge `INTERNAL_ERROR` on overlapping elements — overlapping geometry is now detected and reported cleanly instead of crashing.
- Fix autoNudge `SWTException` threading error — nudge operations properly dispatch to the SWT UI thread.
- Fix multi-iteration nudge position accumulation bug — deltas are now computed from original positions.
- Fix groups and notes missing from `get-view-contents` — FieldSelector bug that filtered out non-element view objects.
- Fix orphaned relationship structural fix — `connect()` timing corrected to prevent relationships from losing their source/target references.
- Fix relationship validation error messages — valid relationship types now appear in the main error message rather than requiring a separate lookup.

### Resource Updates

- **archimate-relationships.md** — Added Common Mistakes section documenting frequent LLM errors (e.g., using Composition where Assignment is correct).
- **archimate-view-patterns.md** — Updated decision trees for grouped-view layout, added flat-view workflow, added topology arrangement guidance, note placement best practices, and hub sizing workflow.

### Documentation

- Routing pipeline documentation updated with stages 4.7g-4.7n, 5a; clearance-weighted A* cost function with corridor directionality and group-wall clearance; proportional corridor spacing; path straightening; center-termination fix; interior terminal BP correction; orthogonality enforcement safety net; and updated configuration constants.
- Layout engine documentation updated with severity-tiered rating system, new assessment metrics (`nonOrthogonalTerminals`, `coincidentSegments` rating), and tier capping logic.
- Architecture documentation updated with `PathStraightener` in pure-geometry routing subpackage.
- README updated with complete 56-tool catalog, image management section, and grouped layout mode.

---

## v1.0.0 (2026-03-16)

Initial release. 51 MCP tools across querying, searching, creating, layout, routing, assessment, batch operations, and more. 6 MCP resources with ArchiMate reference material and workflow guides.

See [README.md](README.md) for the complete tool catalog and documentation.
