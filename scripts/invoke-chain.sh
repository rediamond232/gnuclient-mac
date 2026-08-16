#!/usr/bin/env sh
set -eu

ROOT=${1:-.}
SESSION_ID=${2:-unknown}
GENERATION_ID=${3:-unknown}
OUTPUT_TOKENS=${4:-1000}
TRANSCRIPT_PATH=${5:-}

CHAIN_SCRIPT="$ROOT/harness/scripts/dist/post-task-chain.js"
if [ ! -f "$CHAIN_SCRIPT" ]; then
  HARNESS_HOME=${HARNESS_HOME:-$HOME/.cursor-harness}
  CHAIN_SCRIPT="$HARNESS_HOME/harness/scripts/dist/post-task-chain.js"
fi

if [ ! -f "$CHAIN_SCRIPT" ]; then
  echo "missing chain script" >&2
  exit 1
fi

ARGS="--project-root $ROOT --session-id $SESSION_ID --generation-id $GENERATION_ID --output-tokens $OUTPUT_TOKENS"
if [ -n "$TRANSCRIPT_PATH" ] && [ -f "$TRANSCRIPT_PATH" ]; then
  ARGS="$ARGS --transcript-path $TRANSCRIPT_PATH"
fi

# shellcheck disable=SC2086
exec node "$CHAIN_SCRIPT" $ARGS
