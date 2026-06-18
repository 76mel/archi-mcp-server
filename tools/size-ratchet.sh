#!/usr/bin/env bash
#
# Size ratchet + interface-signature guard for ArchiModelAccessorImpl.
#
# WHY THIS EXISTS
#   ArchiModelAccessorImpl is a thick orchestration facade that is being
#   decomposed cluster-by-cluster behind its unchanged interface. A guideline
#   ("don't let it grow back") can slip; a ratchet cannot. This script is
#   EXECUTABLE CI CODE, not a guideline: it fails the build (exit 1) the moment
#   the facade grows past a frozen ceiling, or the public API surface drifts.
#
# THREE PAWLS (each can only tighten, never loosen):
#   1. LOC ceiling      — file line count must be <= CEILING_LOC.
#   2. public ceiling   — count of the class's OWN public methods (4-space
#                         indent) must be <= CEILING_PUB. Nested-helper-class
#                         publics (indent >= 5 spaces) are deliberately NOT
#                         counted: they are not part of the accessor API.
#   3. signature guard  — the live ArchiModelAccessor interface, reduced to a
#                         deterministic normalized signature set, must be
#                         byte-identical to the committed baseline. This is the
#                         anti-behaviour-drift pawl: every extraction must leave
#                         this diff EMPTY (interface stays fixed; the impl keeps
#                         one-line forwards).
#
# IMPORTANT: pawls 1 and 2 measure THE FILE, never the model/ package. A package
# metric would make any extraction a no-op (lines just move to a sibling file).
#
# The ceilings are LOWERED (only ever lowered) in the same PR that shrinks the
# file — that manual, reviewable edit is the ratchet "click".
#
# Usage:
#   tools/size-ratchet.sh                 # check all three pawls; exit 1 on any breach
#   tools/size-ratchet.sh --generate      # (re)write the interface-signature baseline
#                                         # from the live source, then exit 0. Use only
#                                         # when the interface legitimately changed.
#
# Portable to bash 3.2 (macOS dev box) and bash 5 (Linux CI). No JDK / Archi needed.
#
set -uo pipefail

# ---- frozen ceilings (lower-only) -------------------------------------------
# Today's exact measured values (no padding). Lower these in the same PR that
# shrinks the file. NEVER raise them.
#   CEILING_LOC history (lower-only): 19859 (initial) -> 19665 (ImageOperations extraction)
#                                     -> 19531 (DtoMapper extraction)
#                                     -> 19498 (FolderOperations C4 read-facade completion)
#   CEILING_PUB stays 88: extractions keep the public API as one-line forwards, so the
#   class's own public surface is unchanged (only the bodies move out).
CEILING_LOC=19498
CEILING_PUB=88

# ---- locate repo (the script lives in <repo>/tools/) ------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$SCRIPT_DIR/.." && pwd)"

IMPL="$REPO/net.vheerden.archi.mcp/src/net/vheerden/archi/mcp/model/ArchiModelAccessorImpl.java"
IFACE="$REPO/net.vheerden.archi.mcp/src/net/vheerden/archi/mcp/model/ArchiModelAccessor.java"
BASELINE="$REPO/tools/accessor-interface-baseline.txt"

fail=0

err() {
  # GitHub Actions annotation + human-readable line.
  echo "::error::$1"
  echo "RATCHET FAIL: $1" >&2
}

# Fail loudly if the measured files are missing. Without this, an absent file makes
# `wc -l`/`grep -c` yield an empty string, which numeric comparison treats as 0 and
# the ceiling check silently PASSES — a mis-named or deleted file must never bypass
# the ratchet. (set -e is not used here, so command-substitution failures are silent.)
if [ ! -f "$IMPL" ]; then
  err "ArchiModelAccessorImpl.java not found at: $IMPL"
  exit 1
fi
if [ ! -f "$IFACE" ]; then
  err "ArchiModelAccessor.java not found at: $IFACE"
  exit 1
fi

# -----------------------------------------------------------------------------
# Deterministic interface-signature extraction.
#
# Produces one normalized signature per interface method, sorted, so the dump is
# stable under reformatting AND reordering (neither is behaviour drift). Steps:
#   1. strip block comments (/* ... */, incl. javadoc with its {@code ...} braces)
#   2. strip line comments (// ...)
#   3. keep only the interface BODY (everything after the `interface ... {` line;
#      the opening brace lives on that excluded line, so only default-method
#      braces remain below)
#   4. collapse every balanced { ... } (default-method bodies) to a single `;`
#   5. flatten whitespace, split on `;` into one statement per line
#   6. keep only statements with a parameter list `(` (methods; drops the lone
#      trailing interface-close `}` and any constants)
#   7. strip leading annotations (@Deprecated, incl. parameterized forms) and trim; sort
# -----------------------------------------------------------------------------
extract_signatures() {
  perl -0777 -pe 's{/\*.*?\*/}{}gs' "$IFACE" \
    | perl -pe 's{//.*}{}' \
    | awk 'f{print} /public[ \t]+interface[ \t]+ArchiModelAccessor/{f=1}' \
    | perl -0777 -pe '1 while s/\{[^{}]*\}/;/g' \
    | perl -0777 -pe 's/\s+/ /g' \
    | tr ';' '\n' \
    | sed -E 's/^ +//; s/ +$//' \
    | grep '(' \
    | sed -E 's/^(@[A-Za-z][A-Za-z0-9]*(\([^)]*\))? )+//' \
    | sed -E 's/^ +//; s/ +$//' \
    | sort
}

# ---- --generate mode --------------------------------------------------------
if [ "${1:-}" = "--generate" ]; then
  extract_signatures > "$BASELINE"
  echo "Wrote interface-signature baseline: $BASELINE ($(wc -l < "$BASELINE" | tr -d ' ') signatures)"
  exit 0
fi

# ---- pawl 1: LOC ceiling ----------------------------------------------------
loc=$(wc -l < "$IMPL" | tr -d ' ')
if [ "$loc" -gt "$CEILING_LOC" ]; then
  err "ArchiModelAccessorImpl.java LOC $loc > ceiling $CEILING_LOC. The facade must not grow; extract a collaborator instead."
  fail=1
else
  echo "OK  LOC      : $loc <= $CEILING_LOC"
fi

# ---- pawl 2: public-method ceiling (class's OWN methods only) ---------------
# Counts lines beginning at the class's own 4-space indent with `public ` — i.e. the
# first line of each top-level public method declaration (one such line per method,
# even when the parameter list wraps onto 8-space-indented continuation lines).
# Nested-helper-class publics live at >= 5-space indent and are deliberately excluded.
pub=$(grep -cE '^    public ' "$IMPL")
if [ "$pub" -gt "$CEILING_PUB" ]; then
  err "ArchiModelAccessorImpl top-level public methods $pub > ceiling $CEILING_PUB. Do not add public API to the facade."
  fail=1
else
  echo "OK  publics  : $pub <= $CEILING_PUB"
fi

# ---- pawl 3: interface-signature drift --------------------------------------
if [ ! -f "$BASELINE" ]; then
  err "interface-signature baseline missing: $BASELINE (run: tools/size-ratchet.sh --generate)"
  fail=1
else
  sig_diff="$(mktemp "${TMPDIR:-/tmp}/accessor-sig.XXXXXX")"
  trap 'rm -f "$sig_diff"' EXIT
  if diff -u "$BASELINE" <(extract_signatures) > "$sig_diff"; then
    echo "OK  signature: ArchiModelAccessor matches baseline ($(wc -l < "$BASELINE" | tr -d ' ') methods)"
  else
    err "ArchiModelAccessor interface signature drifted from baseline. Extraction must keep the interface fixed (impl forwards). Diff:"
    cat "$sig_diff" >&2
    fail=1
  fi
fi

if [ "$fail" -ne 0 ]; then
  exit 1
fi
echo "RATCHET PASS"
exit 0
