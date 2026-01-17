***REMOVED***!/usr/bin/env bash
set -euo pipefail
IFS=$'\n\t'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCE_SCRIPT="${SCRIPT_DIR}/repo.sh"
TARGET_DIR="${HOME}/bin"
TARGET_SCRIPT="${TARGET_DIR}/repo.sh"
ZSHRC="${HOME}/.zshrc"

mkdir -p "${TARGET_DIR}"

if [[ -e "${TARGET_SCRIPT}" || -L "${TARGET_SCRIPT}" ]]; then
  rm -f "${TARGET_SCRIPT}"
fi

ln -s "${SOURCE_SCRIPT}" "${TARGET_SCRIPT}"

if [[ ! -f "${ZSHRC}" ]]; then
  touch "${ZSHRC}"
fi

if ! grep -q "^repo() { ~/bin/repo\\.sh; }$" "${ZSHRC}" 2>/dev/null; then
  {
    echo ""
    echo "***REMOVED*** scanium repo command"
    echo "repo() { ~/bin/repo.sh; }"
  } >> "${ZSHRC}"
fi

echo "Installed repo command. Restart your shell or run: source ~/.zshrc"
