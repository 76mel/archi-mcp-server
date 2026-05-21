---
title: Glossary
description: Definitions of the layout-quality metrics, routing terms, and rating tiers used across the ArchiMate MCP Server tools, resources, and documentation.
---

# Glossary

This glossary is the single source of truth for the metric acronyms (M1–M6, R8, `parallelConnectionGap_V_p10`), routing terms, and rating tiers that appear in tool descriptions, MCP resources, the [README](../README.md), and the rest of the technical documentation. When a term is used elsewhere without a definition, look it up here.

## Layout-quality metrics

`assess-layout` reports these metrics. Each one names a specific, perceptible defect class so an LLM agent can act on it directly. For the full rating model and thresholds, see [Layout Engine — Assessor Redesign](layout-engine.md).

| Metric | Result field | What it measures |
|---|---|---|
| **M1** | `nonOrthogonalTerminalCount` | Diagonal connection segments at an element terminal. The metric counts only the *visible* portion of the segment, so diagonals that Archi clips inside an element's bounds are not over-reported. |
| **M2** | `interiorTerminatingCount` | Connections whose endpoint lands *inside* an element's body instead of on its perimeter face. |
| **M3** | `zigzagCount` | Route shapes that backtrack or reverse along an axis. A connection already classified as a pass-through is not also counted as a zigzag. |
| **M4** | `connectionEdgeCoincidenceCount` | Connection segments running parallel to, and within a few pixels of, another element's edge — so the line reads as if it is stuck to the box. |
| **M5** | `hubPortQualityScore` (**HPQ**) | How evenly a hub element's connections are distributed across its perimeter faces, scored 0–1. A low score means many connections collapse onto one attachment point. |
| **M6** | `(layoutTier, routingTier)` | The two-dimensional overall rating — see [Rating tiers](#rating-tiers) below. |
| **R8** | `corridorUtilisationScore` | How well wide corridors carry connections in proportion to their width, scored 0–1. |
| **`parallelConnectionGap_V_p10`** | `vAxisParallelGapP10` | Informational only (no rating impact). The 10th-percentile gap between parallel connection segments on the vertical axis, in pixels. A small value signals the narrow-corridor regime, where more spacing cannot help and the remedy is structural. |

### Related metric terms

- **HPQ** — Hub-port quality. The conversational name for the M5 `hubPortQualityScore`.
- **V_p10** — Shorthand for the `parallelConnectionGap_V_p10` informational signal (the `vAxisParallelGapP10` field).
- **coincSeg / coincident segment** — A segment counted by the legacy `coincidentSegmentCount` metric: two *connections* running parallel and overlapping in the same corridor. Distinct from M4, which measures a connection coinciding with an *element edge*.
- **pass-through** — A connection whose route crosses *through* an element's body. A *self* pass-through (through its own source or target) is reported but does not affect the rating; a *cross-element* pass-through does.

## Rating tiers

The overall rating uses a severity-tiered model so cosmetic issues cannot mask structural ones. Each metric belongs to a tier, and each tier caps how far it can pull the rating down.

| Tier | Severity | Effect on rating |
|---|---|---|
| **Tier 1** | Critical | Drives the overall rating directly (no cap). |
| **Tier 2** | Moderate | Capped at `fair`. |
| **Tier 3** | Cosmetic | Capped at `good`. |

Tier 1 is split by what the defect affects:

- **Tier 1L** — A critical *layout* defect (for example, sibling overlap, or a parent's label obscured by a child). Caps the **layout tier** at `poor`.
- **Tier 1R** — A critical *routing* defect (M2 interior terminations, M3 zigzags, M4 edge coincidence). Caps the **routing tier** at `poor`.

Rating levels run `excellent` → `good` → `fair` → `poor`. The **M6** overall rating reports the layout tier and routing tier separately and takes the worse of the two, so a routing fix on a well-laid-out view does not drag its layout tier down (and vice versa).

## Routing and layout terms

- **Hub element** — An element with many connections (5 or more is the detection threshold). Hubs need enough perimeter length to spread their connection ports; `detect-hub-elements` suggests a size.
- **Perimeter / perimeter-terminal** — The boundary face of an element where a connection attaches. A perimeter-terminal is a connection endpoint locked to a face slot on that boundary.
- **Corridor** — The axis-aligned channel of free space between elements (or between groups) through which connections are routed. Wider corridors carry more parallel routes without crowding.
- **Clearance** — The perpendicular distance between a routed segment and the nearest obstacle. The router weights paths to keep clearance high, so lines do not hug element edges.
- **Channel nudging** — A post-routing pass that shifts parallel segments sharing a channel so they spread out evenly instead of overlapping.
- **Bendpoint** — A corner point on an orthogonal route where the connection changes axis.
- **Zigzag** — A route that backtracks or reverses direction along an axis; counted by M3 (`zigzagCount`).
- **Visibility graph** — The graph of obstacle-free straight-line connections the router searches over.
- **A\* pathfinding** — The shortest-path search the router runs over the visibility graph, weighted by clearance and corridor preferences.
- **ELK (Eclipse Layout Kernel)** — The external layout library used for automatic hierarchical (`Layered`) placement of elements.
- **Zest** — The Eclipse graph-layout library behind the `compute-layout` tree, spring, directed, radial, and grid presets.
- **Precondition** — A property of the view's geometry (hub sizing, inter-element spacing, inter-group arrangement) that must hold *before* routing. The pipeline can refine routes but cannot create these conditions; see the `archimate://prompts/routing-preconditions-checklist` resource.
- **Density floor / reflow** — The point at which a view has too many elements for its area and no amount of spacing can satisfy a precondition. The spacing tools detect this and offer a structural *reflow* (repositioning elements) rather than churning.

## Tool behaviour terms

- **Control loop** — The internal `observe → decide → terminate` cycle the spacing convenience tools run: take a small spacing step, re-run `assess-layout`, then continue, escalate, or stop. One tool call is one undo-stack entry regardless of how many iterations run.
- **Density-aware termination** — The control loop's stop logic. It stops when quality is reached, when more spacing provably cannot help (density floor), or when the iteration budget is exhausted; a step that degrades the view is always reverted.
- **`terminationReason`** — The field a spacing tool returns explaining *why* its control loop stopped (for example, target reached, budget exhausted, or a structural reflow is required).
- **Infeasibility certificate** — A sound, pre-loop check that proves a view's spacing precondition cannot be satisfied on its current canvas. When it fires, the tool returns the view untouched and offers a structural reflow.
- **autoNudge** — An `auto-route-connections` mode that automatically moves a blocking element (and resizes its parent group to contain it), then re-routes, in one atomic operation.

## Reference baseline

- **Manual-routed reference (oracle)** — A hand-routed ArchiMate view used to calibrate metric thresholds and to pin them with regression tests. Metric anchor values quoted in the documentation (for example, the `parallelConnectionGap_V_p10` anchor of 13.30 px) are measured against this reference.
