#!/usr/bin/env sh

# Ensure correct line endings (LF) for Unix systems.
# Gradle Wrapper script for POSIX-compliant systems.

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

# Use the maximum available, or set custom memory options here
DEFAULT_JVM_OPTS='-Xmx64m -Xms64m'

# Find java.exe
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Execute Gradle
exec "$JAVACMD" $DEFAULT_JVM_OPTS -jar "$0.jar" "$@"
