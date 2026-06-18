#!/usr/bin/env bash
#
# Build the installable Archi plug-in (.archiplugin) by compiling the bundle FROM SOURCE
# and assembling it into Archi's plug-in package format — no Eclipse IDE / PDE export needed.
#
# This mirrors the compile-from-source approach proven by tools/run-tests.sh (which the project
# found cleaner than the Eclipse PDE build), and packages the result into the exact .archiplugin
# layout Archi's "Manage plug-ins" installer expects:
#
#   <out>.archiplugin   (a zip)
#   ├── archi-plugin                              # 54-byte magic marker Archi looks for
#   └── <Bundle-SymbolicName>_<version.TIMESTAMP>/
#       ├── META-INF/MANIFEST.MF                  # .qualifier replaced by TIMESTAMP
#       ├── plugin.xml, plugin.properties         # if present
#       ├── <compiled classes tree>              # Bundle-ClassPath starts with '.', so classes
#       │                                           live at the bundle root (exploded), not a jar
#       ├── resources/, img/                      # bin.includes
#       └── lib/...                               # vendored third-party jars (Bundle-ClassPath)
#
# The bundle contents are driven by build.properties `bin.includes` + MANIFEST.MF, so this stays
# in lockstep with what the Eclipse export would ship.
#
# Usage:
#   tools/ci/package-plugin.sh                    # -> build/dist/<sym>_<version>.archiplugin
#   ARCHI_HOME=/path/to/Archi/Eclipse tools/ci/package-plugin.sh
#
# Env vars (macOS dev-box defaults, same contract as run-tests.sh):
#   ARCHI_HOME   Archi's Eclipse dir   (default /Applications/Archi.app/Contents/Eclipse)
#   BUILD_DIR    compile output dir    (default <repo>/build/plugin-build)
#   DIST_DIR     output dir            (default <repo>/build/dist)
#   QUALIFIER    build qualifier       (default: UTC yyyyMMddHHmm)
#
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$SCRIPT_DIR/../.." && pwd)"
PROD="$REPO/net.vheerden.archi.mcp"

ARCHI_HOME="${ARCHI_HOME:-/Applications/Archi.app/Contents/Eclipse}"
BUILD_DIR="${BUILD_DIR:-$REPO/build/plugin-build}"
DIST_DIR="${DIST_DIR:-$REPO/build/dist}"
QUALIFIER="${QUALIFIER:-$(date -u +%Y%m%d%H%M)}"

die() { echo "ERROR: $*" >&2; exit 2; }

ARCHI_PLUGINS="$ARCHI_HOME/plugins"
MANIFEST="$PROD/META-INF/MANIFEST.MF"
[ -d "$ARCHI_PLUGINS" ] || die "ARCHI_HOME/plugins not found: $ARCHI_PLUGINS (set ARCHI_HOME)"
[ -d "$PROD/src" ]      || die "production source not found: $PROD/src"
[ -f "$MANIFEST" ]      || die "MANIFEST.MF not found: $MANIFEST"
command -v javac >/dev/null || die "javac not on PATH (need a JDK 21+)"
command -v zip   >/dev/null || die "zip not on PATH"

# ---- read bundle identity from MANIFEST.MF (continuation-line safe) ----
manifest_value() {
  # prints the value of header $1, folding RFC822 continuation lines (leading space) and
  # stripping OSGi attributes after ';'
  awk -v h="$1:" '
    $0 ~ "^"h {val=$0; sub("^"h" *","",val); getline; while ($0 ~ /^ /){sub(/^ /,"");val=val$0; getline} print val}
  ' "$MANIFEST" | sed -E 's/;.*//' | tr -d '\r' | head -1
}
SYM="$(manifest_value 'Bundle-SymbolicName')"
RAW_VERSION="$(manifest_value 'Bundle-Version')"
[ -n "$SYM" ]         || die "could not read Bundle-SymbolicName from MANIFEST"
[ -n "$RAW_VERSION" ] || die "could not read Bundle-Version from MANIFEST"
# Replace a trailing .qualifier with the build timestamp (what Eclipse export does).
VERSION="${RAW_VERSION/.qualifier/.$QUALIFIER}"
BUNDLE_ID="${SYM}_${VERSION}"

echo "== Archi plug-in packager =="
echo "   bundle:      $SYM"
echo "   version:     $RAW_VERSION -> $VERSION"
echo "   ARCHI_HOME:  $ARCHI_HOME"

# ---- compile production source FROM SOURCE against the Archi/lib classpath ----
OUT_CLASSES="$BUILD_DIR/classes"
LIBS="$(find "$PROD/lib" -name '*.jar' 2>/dev/null | tr '\n' ':')"
ARCHIJARS="$(find "$ARCHI_PLUGINS" -name '*.jar' 2>/dev/null | tr '\n' ':')"
COMPILE_CP="$LIBS$ARCHIJARS"
rm -rf "$OUT_CLASSES"; mkdir -p "$OUT_CLASSES"
echo "-- compiling ($(find "$PROD/src" -name '*.java' | wc -l | tr -d ' ') files) ..."
find "$PROD/src" -name '*.java' > "$BUILD_DIR/srcs.txt"
javac --release 21 -encoding UTF-8 -nowarn -cp "$COMPILE_CP" -d "$OUT_CLASSES" "@$BUILD_DIR/srcs.txt" \
  || die "production compile failed (see javac output above)"

# ---- assemble the bundle folder per bin.includes (META-INF, plugin.*, classes at '.', resources, img, lib) ----
STAGE="$BUILD_DIR/stage"
BUNDLE_DIR="$STAGE/$BUNDLE_ID"
rm -rf "$STAGE"; mkdir -p "$BUNDLE_DIR/META-INF"
# MANIFEST with .qualifier resolved to the timestamp (mirrors Eclipse export).
sed "s/^Bundle-Version: .*/Bundle-Version: $VERSION/" "$MANIFEST" > "$BUNDLE_DIR/META-INF/MANIFEST.MF"
for f in plugin.xml plugin.properties; do
  [ -f "$PROD/$f" ] && cp "$PROD/$f" "$BUNDLE_DIR/$f"
done
# Bundle-ClassPath starts with '.', so the compiled classes go at the bundle root (exploded).
cp -R "$OUT_CLASSES/." "$BUNDLE_DIR/"
for d in resources img lib; do
  [ -d "$PROD/$d" ] && cp -R "$PROD/$d" "$BUNDLE_DIR/$d"
done

# ---- the magic marker (verified byte-identical to shipped Archi plug-ins) ----
printf 'Magic file to signify this is an Archi plug-in bundle.' > "$STAGE/archi-plugin"

# ---- zip it up as <sym>_<version>.archiplugin (marker + bundle folder at the zip root) ----
mkdir -p "$DIST_DIR"
ARCHIPLUGIN="$DIST_DIR/${BUNDLE_ID}.archiplugin"
rm -f "$ARCHIPLUGIN"
( cd "$STAGE" && zip -qr "$ARCHIPLUGIN" archi-plugin "$BUNDLE_ID" ) || die "failed to zip .archiplugin"

echo "-- wrote $ARCHIPLUGIN ($(du -h "$ARCHIPLUGIN" | cut -f1))"
echo "RESULT: OK"
