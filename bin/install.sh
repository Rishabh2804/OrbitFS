#!/bin/bash
set -euo pipefail

VERSION="v0.1.0"
REPO="Rishabh2804/OrbitFS"
OS="$(uname -s)"
ARCH="$(uname -m)"

if [ "$OS" != "Darwin" ]; then
  echo "orbit: currently only macOS is supported"
  exit 1
fi

if [ "$ARCH" = "arm64" ]; then
  ASSET="orbit-macos-arm64.jar"
elif [ "$ARCH" = "x86_64" ]; then
  ASSET="orbit-macos-x86_64.jar"
else
  echo "orbit: unsupported architecture: $ARCH"
  exit 1
fi

INSTALL_DIR="${INSTALL_DIR:-$(brew --prefix)/bin}"
TMP_DIR=$(mktemp -d)
trap "rm -rf $TMP_DIR" EXIT

echo "Downloading OrbitFS ${VERSION}..."
URL="https://github.com/${REPO}/releases/download/${VERSION}/${ASSET}"
curl -fL "$URL" -o "$TMP_DIR/orbit.jar"

if [ ! -f "$TMP_DIR/orbit.jar" ]; then
  echo "orbit: failed to download from $URL"
  exit 1
fi

echo "Installing to ${INSTALL_DIR}/orbit..."
mkdir -p "$INSTALL_DIR"
cp "$TMP_DIR/orbit.jar" "${INSTALL_DIR}/orbit.jar"

cat > "${INSTALL_DIR}/orbit" << 'ORBIT_WRAPPER'
#!/bin/bash
JAR_PATH="$(cd "$(dirname "$0")" && pwd)/orbit.jar"
java -jar "$JAR_PATH" "$@"
ORBIT_WRAPPER

chmod +x "${INSTALL_DIR}/orbit"

echo "orbit installed to ${INSTALL_DIR}/orbit"
echo "Try: orbit --help"