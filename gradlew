#!/bin/sh
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$CLASSPATH" ]; then
  echo "Bootstrapping verified Gradle Wrapper 9.5.0..." >&2
  java "$APP_HOME/gradle/wrapper/WrapperDownloader.java" \
    "https://services.gradle.org/distributions/gradle-9.5.0-wrapper.jar" \
    "497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7" \
    "$CLASSPATH" || exit 1
fi
exec java ${JAVA_OPTS:-} ${GRADLE_OPTS:-} -Dorg.gradle.appname=gradlew -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
