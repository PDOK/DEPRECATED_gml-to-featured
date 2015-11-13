#!/usr/bin/env sh

if [ -n "${RELEASE_LEVEL+1}" ]; then

    : "${NEXUS_USERNAME:?Need to set NEXUS_USERNAME non-empty}"
    : "${NEXUS_PASSWORD:?Need to set NEXUS_PASSWORD non-empty}"

    echo "RELEASING";
    lein build && lein release ${RELEASE_LEVEL}
else
    echo "NORMAL BUILD"
    lein build
fi
