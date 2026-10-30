#!/usr/bin/env bash
#
# Installs a JDK 21 (Eclipse Temurin) and/or Node.js 24 into ~/.local/share, without sudo.
# Downloads come from the official sites and are checked against their published SHA-256.
#
#   scripts/install-tools.sh jdk
#   scripts/install-tools.sh node
#   scripts/install-tools.sh jdk node
#
set -euo pipefail
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

command_exists curl || die "curl is required to download tools"
command_exists tar || die "tar is required to unpack tools"

case "$(uname -s)" in
  Linux) OS_ADOPTIUM=linux; OS_NODE=linux ;;
  Darwin) OS_ADOPTIUM=mac; OS_NODE=darwin ;;
  *) die "Automatic install supports Linux and macOS. On Windows, use WSL or install JDK 21 and Node 24 manually." ;;
esac
case "$(uname -m)" in
  x86_64 | amd64) ARCH_ADOPTIUM=x64; ARCH_NODE=x64 ;;
  aarch64 | arm64) ARCH_ADOPTIUM=aarch64; ARCH_NODE=arm64 ;;
  *) die "Unsupported CPU architecture: $(uname -m)" ;;
esac

sha256_of() {
  if command_exists sha256sum; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi
}

download_verified() { # url file expected-sha256
  curl -fSL --progress-bar -o "$2" "$1"
  local actual
  actual="$(sha256_of "$2")"
  [[ "$actual" == "$3" ]] || { rm -f "${2:?}"; die "Checksum mismatch for $(basename "$2")"; }
}

install_jdk() {
  step "Installing JDK 21 (Eclipse Temurin) into $TOOLS_DIR/jdks"
  local api="https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=$ARCH_ADOPTIUM&image_type=jdk&os=$OS_ADOPTIUM&vendor=eclipse"
  local package
  package="$(curl -fsSL "$api" | tr -d '\n' | grep -o '"package": *{[^}]*}' | head -n 1)" ||
    die "Could not reach api.adoptium.net"
  local link checksum name
  link="$(grep -o '"link": *"[^"]*"' <<<"$package" | head -n 1 | sed 's/.*"\(http[^"]*\)"/\1/')"
  checksum="$(grep -o '"checksum": *"[^"]*"' <<<"$package" | head -n 1 | sed 's/.*: *"\([^"]*\)"/\1/')"
  name="$(grep -o '"name": *"[^"]*"' <<<"$package" | head -n 1 | sed 's/.*: *"\([^"]*\)"/\1/')"
  [[ -n "$link" && -n "$checksum" && -n "$name" ]] || die "Unexpected answer from the Adoptium API"

  mkdir -p "$TOOLS_DIR/jdks"
  local archive="$TOOLS_DIR/jdks/$name"
  download_verified "$link" "$archive" "$checksum"
  tar -xzf "$archive" -C "$TOOLS_DIR/jdks"
  rm -f "${archive:?}"
  local home
  home="$(find_java_home)" || die "JDK unpacked but not found in $TOOLS_DIR/jdks"
  ok "Installed $("$home/bin/java" -version 2>&1 | head -n 1) at $home"
}

install_node() {
  step "Installing Node.js 24 into $TOOLS_DIR/node"
  local base="https://nodejs.org/dist/latest-v24.x"
  local line file checksum
  line="$(curl -fsSL "$base/SHASUMS256.txt" | grep " node-v24[.0-9]*-$OS_NODE-$ARCH_NODE.tar.gz$" | head -n 1)" ||
    die "Could not find a Node 24 build for $OS_NODE-$ARCH_NODE on nodejs.org"
  checksum="${line%% *}"
  file="${line##* }"

  mkdir -p "$TOOLS_DIR"
  local archive="$TOOLS_DIR/$file"
  download_verified "$base/$file" "$archive" "$checksum"
  tar -xzf "$archive" -C "$TOOLS_DIR"
  rm -f "${archive:?}"
  ln -sfn "$TOOLS_DIR/${file%.tar.gz}" "$TOOLS_DIR/node"
  ok "Installed Node $("$TOOLS_DIR/node/bin/node" -v) at $TOOLS_DIR/node (start.sh and setup.sh find it automatically)"
}

[[ $# -gt 0 ]] || die "Usage: scripts/install-tools.sh jdk|node [jdk|node]"
for tool in "$@"; do
  case "$tool" in
    jdk) install_jdk ;;
    node) install_node ;;
    *) die "Unknown tool: $tool (use jdk or node)" ;;
  esac
done
