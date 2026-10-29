#!/usr/bin/env bash
set -euo pipefail

# The merge hook runs from the repository root, outside the pnpm workspace.
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

CI=true pnpm install --frozen-lockfile

# Preserve the existing Supabase schema. Database migrations require explicit
# approval and must not run automatically after a task merge.
# Do not run tests, type checks, builds, or browser verification in this hook.
