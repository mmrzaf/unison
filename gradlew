#!/bin/sh
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$CLASSPATH" ]; then
  echo "Missing checked-in Gradle wrapper JAR: $CLASSPATH" >&2
  exit 1
fi
exec java ${JAVA_OPTS:-} ${GRADLE_OPTS:-} -Dorg.gradle.appname=gradlew -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
