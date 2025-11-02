#!/usr/bin/env sh
##############################################################################
## Gradle start up script for UN*X
##############################################################################

APP_HOME=$( cd "${0%/*}" && pwd -P )
APP_NAME="Gradle"
APP_BASE_NAME=${0##*/}

DEFAULT_JVM_OPTS=""
MAX_FD="maximum"

warn () {
    echo "$*"
}

die () {
    echo
    echo "$*"
    echo
    exit 1
}

if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/bin/java" ] ; then
        JAVACMD="$JAVA_HOME/bin/java"
    else
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
    fi
else
    JAVACMD="java"
fi

if [ "$MAX_FD" = "maximum" ] ; then
    MAX_FD_LIMIT=$( ulimit -H -n )
    if [ $? -eq 0 ] ; then
        ulimit -n "$MAX_FD_LIMIT" || warn "Could not set maximum file descriptor limit: $MAX_FD_LIMIT"
    fi
fi

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
exec "$JAVACMD" $DEFAULT_JVM_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
