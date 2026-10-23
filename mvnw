#!/bin/sh
#
# Maven launcher for jCalculator.
#
# The build is kept self-contained so it can run the same way on a developer machine, in CI, and
# inside the isolated workspace CodeWitness creates:
#
#   1. use the Maven distribution vendored in .tools/ (no download, no network at build time);
#   2. otherwise fall back to MAVEN_HOME/M2_HOME, then to mvn on PATH.
#
# The launcher is taken from .tools/apache-maven-3.9.16/launcher/ rather than the distribution's own
# bin/ directory. CodeWitness excludes every directory called "bin" when it copies a project into its
# isolated workspace (it is build output in most ecosystems), so a Maven installed under bin/ would
# disappear from the copy and the build would fail there. launcher/mvn is the same startup script
# with its classworlds configuration pointed at launcher/m2.conf, so Maven still resolves its home
# and boot directory correctly after the copy.
#
# The script is run through /bin/sh when the copy has lost its executable bit, because a project
# copied between filesystems may not preserve permissions.
#
# The local repository is kept inside the project (.m2repo) for the same reason: the default
# ~/.m2/repository may be read-only or absent in a sandbox.

set -eu

SELF_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
VENDORED_MVN="$SELF_DIR/.tools/apache-maven-3.9.16/launcher/mvn"

if [ -f "$VENDORED_MVN" ]; then
    MVN="$VENDORED_MVN"
elif [ -f "$SELF_DIR/.tools/apache-maven-3.9.16/bin/mvn" ]; then
    MVN="$SELF_DIR/.tools/apache-maven-3.9.16/bin/mvn"
elif [ -x "${MAVEN_HOME:-}/bin/mvn" ]; then
    MVN="$MAVEN_HOME/bin/mvn"
elif [ -x "${M2_HOME:-}/bin/mvn" ]; then
    MVN="$M2_HOME/bin/mvn"
elif command -v mvn >/dev/null 2>&1; then
    MVN=$(command -v mvn)
else
    echo "mvnw: no Maven found (expected .tools/apache-maven-3.9.16 or mvn on PATH)" >&2
    exit 127
fi

if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
    DETECTED=$(/usr/libexec/java_home 2>/dev/null || true)
    if [ -n "$DETECTED" ]; then
        JAVA_HOME="$DETECTED"
        export JAVA_HOME
    fi
fi

if [ -x "$MVN" ]; then
    exec "$MVN" -Dmaven.repo.local="$SELF_DIR/.m2repo" "$@"
fi
exec /bin/sh "$MVN" -Dmaven.repo.local="$SELF_DIR/.m2repo" "$@"
