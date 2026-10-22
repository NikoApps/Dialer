#!/usr/bin/env bash
# Prepares a release: bumps the version, regenerates the internal changelog,
# commits and tags. Push with: git push origin main <version>
#
#   scripts/release.sh 0.2.0
set -euo pipefail

name="${1:?usage: scripts/release.sh <version, e.g. 0.2.0>}"
[[ "$name" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Version must look like 1.2.3"; exit 1; }
[[ "$(git branch --show-current)" == "main" ]] || { echo "Release from main"; exit 1; }

code=$(( $(grep '^VERSION_CODE=' gradle.properties | cut -d= -f2) + 1 ))
notes="fastlane/metadata/android/en-US/changelogs/$code.txt"
[[ -z "$(git status --porcelain -- . ":!$notes")" ]] || { echo "Working tree isn't clean"; exit 1; }
if [[ ! -s "$notes" ]]; then
  echo "Write the user-facing notes first: $notes (plain words, 500 bytes max)"
  exit 1
fi
if (( $(wc -c < "$notes") > 500 )); then
  echo "$notes is over 500 bytes"
  exit 1
fi

sed -i "s/^VERSION_NAME=.*/VERSION_NAME=$name/; s/^VERSION_CODE=.*/VERSION_CODE=$code/" gradle.properties
uvx git-cliff --tag "$name" -o CHANGELOG.md

git add gradle.properties CHANGELOG.md "$notes"
git commit -m "chore: release $name"
git tag "$name"
echo "Tagged $name (versionCode $code). Push with: git push origin main $name"
