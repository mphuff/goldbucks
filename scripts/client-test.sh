#!/bin/bash
# Runs the on-screen tests in a headless game client on Linux.
# Needs: xvfb, libegl-mesa0, libgl1-mesa-dri. Screenshots land in build/run/clientGameTest/screenshots.
set -e
cd "$(dirname "$0")/.."
export SDL_VIDEO_FORCE_EGL=1 LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-$(mktemp -d)}"
exec timeout 900 xvfb-run -a -s "-screen 0 ${XVFB_SCREEN:-1280x720x24} +extension GLX +render -noreset" \
	./gradlew runClientGameTest --max-workers=2 "$@"
