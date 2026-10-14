#!/bin/sh
set -eu
cd "$(dirname "$0")"

: "${LEGACYBREAKER_JDK:?Set LEGACYBREAKER_JDK to an existing JDK 8 directory}"
: "${LEGACYBREAKER_IDEA:?Set LEGACYBREAKER_IDEA to the IntelliJ directory (Contents on macOS)}"

jdk_version=$("$LEGACYBREAKER_JDK/bin/javac" -version 2>&1)
case "$jdk_version" in
    "javac 1.8."*) ;;
    *) echo "JDK 8 is required. Found: $jdk_version" >&2; exit 1 ;;
esac

idea_build_file="$LEGACYBREAKER_IDEA/build.txt"
if [ ! -f "$idea_build_file" ]; then
    idea_build_file="$LEGACYBREAKER_IDEA/Resources/build.txt"
fi
idea_build=$(tr -d '\r\n' < "$idea_build_file")
case "$idea_build" in
    IC-183.*|IU-183.*) ;;
    *) echo "Provisional target is IDEA 2018.3 (183). Found: $idea_build" >&2
       echo "Review SDK compatibility and plugin.xml before targeting another version." >&2
       exit 1 ;;
esac

# Only project output is cleaned. IDE libraries are compile-time dependencies.
rm -rf build/classes
mkdir -p build/classes build/distributions
#4차수정: 수정판 0.3.0 산출물 경로.
rm -f build/distributions/legacybreaker-0.3.0.jar
#4차수정: 새 변수 탐색 Action도 컴파일 시작점에 포함한다.
"$LEGACYBREAKER_JDK/bin/javac" -encoding UTF-8 -source 1.8 -target 1.8 -proc:none \
    -classpath "$LEGACYBREAKER_IDEA/lib/*" \
    -sourcepath src/main/java -d build/classes \
    src/main/java/com/github/youngjoone/legacybreaker/action/ExploreCodeRelationsAction.java \
    src/main/java/com/github/youngjoone/legacybreaker/action/TraceJspValueAction.java \
    src/main/java/com/github/youngjoone/legacybreaker/action/ExploreVariableRelationsAction.java
cp -R src/main/resources/. build/classes/
#4차수정: 수정판 0.3.0 산출물 경로.
"$LEGACYBREAKER_JDK/bin/jar" cf build/distributions/legacybreaker-0.3.0.jar \
    -C build/classes .
#4차수정: 수정판 0.3.0 산출물 경로.
echo "Built build/distributions/legacybreaker-0.3.0.jar"
