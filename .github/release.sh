#!/usr/bin/env bash
set -euo pipefail

VERSION_INPUT=${1:-}

if [[ -z "$VERSION_INPUT" ]]; then
	echo "Usage: ./.github/release.sh <version>"
	echo "Example: ./.github/release.sh 1.6.0"
	echo "Example: ./.github/release.sh v1.6.0"
	exit 1
fi

if [[ "$VERSION_INPUT" == vv* ]]; then
	echo "Error: invalid version '$VERSION_INPUT'"
	echo "Example: ./.github/release.sh v1.2.3"
	exit 1
fi

VERSION="${VERSION_INPUT#v}"

SEMVER_RE='^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-[0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*)?(\+[0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*)?$'
if [[ ! "$VERSION" =~ $SEMVER_RE ]]; then
	echo "Error: invalid version '$VERSION_INPUT'"
	echo "Example: ./.github/release.sh 1.2.3"
	exit 1
fi

TAG="v$VERSION"

if [[ -n "$(git status --porcelain)" ]]; then
	echo "Error: working tree not clean"
	exit 1
fi

BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [[ "$BRANCH" != "main" ]]; then
	echo "Error: not on main branch (currently on $BRANCH)"
	exit 1
fi

git pull --ff-only

git tag -a "$TAG" -m "Release $TAG"

git push origin main
git push origin "$TAG"

echo "Released $TAG"
