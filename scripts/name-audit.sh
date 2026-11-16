#!/bin/bash
# Fails if any old name of the mod appears in the repository.
cd "$(dirname "$0")/.."
PATTERN='trump|huffhome|huff home|hhname_|hhdebt_'
ALLOWED='scripts/name-audit.sh'

hits=$(git grep -n -i -E "$PATTERN" -- ':!*.png' ':!*.jar' | grep -v -E "^($ALLOWED):")
files=$(git ls-files | grep -i -E "$PATTERN")

if [ -n "$hits$files" ]; then
	echo "Old names found (use Goldbucks / goldbucks):"
	[ -n "$hits" ] && echo "$hits"
	[ -n "$files" ] && echo "$files"
	exit 1
fi

echo "Name audit passed: no old names."
