#!/usr/bin/env bash

prepare_standalone_kotlin() {
  local check_name="$1"
  local check_root="$PWD/app/build/standalone-kotlin-check"
  local check_lib="$check_root/lib"

  if [[ ! -d "$check_lib" ]]; then
    echo "$check_name: preparing repository-pinned Kotlin compiler classpath"
    ./gradlew --no-daemon --quiet :app:prepareStandaloneKotlinChecks
  fi

  local compiler_jars=()
  local coroutines_jars=()
  local stdlib_jars=()
  shopt -s nullglob
  compiler_jars=("$check_lib"/kotlin-compiler-embeddable-*.jar)
  coroutines_jars=("$check_lib"/kotlinx-coroutines-core-jvm-*.jar)
  stdlib_jars=("$check_lib"/kotlin-stdlib-*.jar)
  shopt -u nullglob

  if (( ${#compiler_jars[@]} != 1 )); then
    echo "$check_name failed: expected one pinned Kotlin compiler JAR, found ${#compiler_jars[@]}" >&2
    exit 1
  fi
  if (( ${#coroutines_jars[@]} != 1 )); then
    echo "$check_name failed: expected one pinned coroutines JAR, found ${#coroutines_jars[@]}" >&2
    exit 1
  fi
  if (( ${#stdlib_jars[@]} != 1 )); then
    echo "$check_name failed: expected one pinned Kotlin standard-library JAR, found ${#stdlib_jars[@]}" >&2
    exit 1
  fi

  STANDALONE_KOTLIN_ROOT="$check_root"
  STANDALONE_KOTLIN_LIB="$check_lib"
  STANDALONE_KOTLIN_RUNTIME_CLASSPATH="${stdlib_jars[0]}:${coroutines_jars[0]}"
  export STANDALONE_KOTLIN_ROOT STANDALONE_KOTLIN_LIB STANDALONE_KOTLIN_RUNTIME_CLASSPATH
}

run_standalone_kotlinc() {
  local compiler_java_options=()
  if [[ -n "${JAVA_OPTS:-}" ]]; then
    read -r -a compiler_java_options <<< "$JAVA_OPTS"
  fi
  java "${compiler_java_options[@]}" \
    -Dkotlin.home="$STANDALONE_KOTLIN_ROOT" \
    -cp "$STANDALONE_KOTLIN_LIB/*" \
    org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    -no-stdlib \
    "$@"
}
