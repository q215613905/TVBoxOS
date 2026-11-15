#!/bin/bash
# TVBoxOS 第三阶段构建验证脚本
# 用法: ./scripts/verify-build.sh

set -e

echo "=== TVBoxOS 构建验证 ==="
echo "时间: $(date)"
echo ""

# 1. Java 构建
echo "[1/3] Java debug 构建..."
./gradlew :app:assembleJavaDebug
echo "✓ Java 构建成功"
echo ""

# 2. Python 构建
echo "[2/3] Python debug 构建..."
./gradlew :app:assemblePythonDebug
echo "✓ Python 构建成功"
echo ""

# 3. APK 大小检查
echo "[3/3] APK 大小检查..."
JAVA_APK=$(ls -lh app/build/outputs/apk/java/debug/*.apk 2>/dev/null | awk '{print $5}')
PYTHON_APK=$(ls -lh app/build/outputs/apk/python/debug/*.apk 2>/dev/null | awk '{print $5}')
echo "Java APK: ${JAVA_APK:-未找到}"
echo "Python APK: ${PYTHON_APK:-未找到}"
echo ""

echo "=== 验证完成 ==="
