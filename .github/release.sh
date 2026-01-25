#!/usr/bin/env bash
set -euo pipefail

ALLOW_DIRTY=false
VERSION_INPUT=""

for arg in "$@"; do
	case "$arg" in
		--allow-dirty) ALLOW_DIRTY=true ;;
		*) VERSION_INPUT="$arg" ;;
	esac
done

if [[ -z "$VERSION_INPUT" ]]; then
	echo "Usage: ./.github/release.sh <version> [--allow-dirty]"
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

if [[ "$ALLOW_DIRTY" == false && -n "$(git status --porcelain)" ]]; then
	echo "Error: working tree not clean (use --allow-dirty to override)"
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
