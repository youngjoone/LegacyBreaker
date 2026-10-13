#!/bin/sh
set -eu
cd "$(dirname "$0")"
./build.sh
mkdir -p build/test-classes
"$LEGACYBREAKER_JDK/bin/javac" -encoding UTF-8 -source 1.8 -target 1.8 -proc:none \
    -classpath "build/classes:$LEGACYBREAKER_IDEA/lib/*" -d build/test-classes \
    src/test/java/com/github/youngjoone/legacybreaker/*.java
"$LEGACYBREAKER_JDK/bin/java" -Djava.awt.headless=true -cp "build/classes:build/test-classes" \
    com.github.youngjoone.legacybreaker.ModelTest

# The integration suite uses the actual IDE and its indexes, in an isolated sandbox.
# It does not install anything into the user's normal IntelliJ profile.
test_sandbox="$PWD/build/test-sandbox"
rm -rf "$test_sandbox"
mkdir -p "$test_sandbox/plugins/legacybreaker/lib" "$test_sandbox/plugins/tests/lib" "$test_sandbox/config"
cp build/distributions/legacybreaker-0.1.0.jar "$test_sandbox/plugins/legacybreaker/lib/"
cp -R src/test/resources/. build/test-classes/
"$LEGACYBREAKER_JDK/bin/jar" cf "$test_sandbox/plugins/tests/lib/tests.jar" -C build/test-classes .
"$LEGACYBREAKER_JDK/bin/java" -Xmx1024m -Djava.awt.headless=true -Didea.is.internal=true \
    -Didea.home.path="$LEGACYBREAKER_IDEA" -Didea.config.path="$test_sandbox/config" \
    -Didea.system.path="$test_sandbox/system" -Didea.plugins.path="$test_sandbox/plugins" \
    -Didea.log.path="$test_sandbox/log" -Didea.platform.prefix=Idea -Didea.initially.ask.config=false \
    -Dlegacybreaker.test.project="$test_sandbox/project" -Dlegacybreaker.test.sample="$PWD/samples/Example.java" \
    -Dlegacybreaker.test.jdk="$LEGACYBREAKER_JDK" \
    -Dlegacybreaker.test.preview="$PWD/build/integration-preview.png" \
    -cp "$LEGACYBREAKER_JDK/lib/tools.jar:$LEGACYBREAKER_IDEA/lib/*" com.intellij.idea.Main legacybreaker-test
if grep -q ' ERROR - ' "$test_sandbox/log/idea.log"; then
    echo "FAIL: IDE logged errors; inspect $test_sandbox/log/idea.log" >&2
    exit 1
fi
echo "PASS: IDE log contains no errors"
