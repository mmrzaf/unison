@echo off
setlocal
set "APP_HOME=%~dp0"
set "CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar"
if not exist "%CLASSPATH%" (
  echo Missing checked-in Gradle wrapper JAR: %CLASSPATH% 1>&2
  exit /b 1
)
java %JAVA_OPTS% %GRADLE_OPTS% -Dorg.gradle.appname=gradlew -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%
