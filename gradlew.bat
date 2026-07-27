@ECHO OFF
SET DIR=%~dp0
IF NOT EXIST "%DIR%gradle\wrapper\gradle-wrapper.jar" (
  ECHO Bootstrapping verified Gradle Wrapper 9.5.0...
  java "%DIR%gradle\wrapper\WrapperDownloader.java" "https://services.gradle.org/distributions/gradle-9.5.0-wrapper.jar" "497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7" "%DIR%gradle\wrapper\gradle-wrapper.jar"
  IF ERRORLEVEL 1 EXIT /B 1
)
java %JAVA_OPTS% %GRADLE_OPTS% -Dorg.gradle.appname=gradlew -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
