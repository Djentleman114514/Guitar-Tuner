#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/android-env.sh"
cd "$TUNER_PROJECT_ROOT"
if [ "$#" -eq 0 ]; then set -- assembleDebug; fi
exec ./gradlew "$@"
