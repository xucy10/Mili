#!/bin/bash

echo "=== Mili Build Verification Script ==="
echo ""

echo "1. Checking patch files..."
if [ -f "mili-api/build.gradle.kts.patch" ]; then
    echo "✓ mili-api/build.gradle.kts.patch exists"
    if grep -q "lophine-api/build.gradle.kts" mili-api/build.gradle.kts.patch; then
        echo "✓ Patch targets lophine-api (correct)"
    else
        echo "✗ Patch has wrong target"
        exit 1
    fi
else
    echo "✗ mili-api/build.gradle.kts.patch missing"
    exit 1
fi

if [ -f "mili-server/build.gradle.kts.patch" ]; then
    echo "✓ mili-server/build.gradle.kts.patch exists"
    if grep -q "lophine-server/build.gradle.kts" mili-server/build.gradle.kts.patch; then
        echo "✓ Patch targets lophine-server (correct)"
    else
        echo "✗ Patch has wrong target"
        exit 1
    fi
else
    echo "✗ mili-server/build.gradle.kts.patch missing"
    exit 1
fi

if [ -f "mili-server/build.gradle.kts.empty.patch" ]; then
    echo "✓ mili-server/build.gradle.kts.empty.patch exists"
else
    echo "✗ mili-server/build.gradle.kts.empty.patch missing"
    exit 1
fi

echo ""
echo "2. Checking build configuration..."
if [ -f "build.gradle.kts" ]; then
    echo "✓ Root build.gradle.kts exists"
    if grep -q 'repo = github("LuminolMC", "Lophine")' build.gradle.kts; then
        echo "✓ Upstream is Lophine (correct)"
    else
        echo "✗ Wrong upstream configuration"
        exit 1
    fi
else
    echo "✗ Root build.gradle.kts missing"
    exit 1
fi

if [ -f "gradle.properties" ]; then
    echo "✓ gradle.properties exists"
    if grep -q "lophineRef=" gradle.properties; then
        echo "✓ Lophine ref configured"
    fi
else
    echo "✗ gradle.properties missing"
    exit 1
fi

echo ""
echo "3. Verifying patch file formats..."
echo "--- mili-api/build.gradle.kts.patch ---"
head -3 mili-api/build.gradle.kts.patch
echo ""
echo "--- mili-server/build.gradle.kts.patch ---"
head -3 mili-server/build.gradle.kts.patch
echo ""
echo "--- mili-server/build.gradle.kts.empty.patch ---"
cat mili-server/build.gradle.kts.empty.patch

echo ""
echo "4. All checks passed! ✓"
echo ""
echo "To apply patches, run:"
echo "  ./gradlew --refresh-dependencies applyAllPatches"
echo ""
echo "To build the project, run:"
echo "  ./gradlew build"