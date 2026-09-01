#!/bin/bash
# ============================================================
# 环境一键安装（macOS）
# 把 JDK 17 与 Maven 解压到 ~/tools，并写好 env.sh
# 说明：brew 在部分 macOS 版本上装不了 openjdk@17（依赖 pcre2 没有可用 bottle），
#      所以这里直接用官方 tarball 解压安装，不依赖 brew。
# GitHub 仓库不含 JDK 压缩包（超过 100MB 限制）；脚本会在缺失时自动下载。
# ============================================================
set -e
cd "$(dirname "$0")"
mkdir -p "$HOME/tools"

jdk_tarball=""
for f in jdk-17*.tar.gz; do
  if [ -f "$f" ]; then
    jdk_tarball="$f"
    break
  fi
done

existing_jdk="$(ls -d "$HOME/tools"/jdk-* 2>/dev/null | head -1 || true)"
if [ -z "$existing_jdk" ]; then
  if [ -z "$jdk_tarball" ]; then
    echo "==> 仓库未附带 JDK 压缩包（超过 GitHub 100MB 限制），正在从 Adoptium 下载…"
    arch="$(uname -m)"
    case "$arch" in
      arm64|aarch64) jdk_arch="aarch64" ;;
      x86_64|amd64)  jdk_arch="x64" ;;
      *) echo "不支持的架构: $arch"; exit 1 ;;
    esac
    jdk_tarball="jdk-17-macos-${jdk_arch}.tar.gz"
    curl -fL --progress-bar \
      "https://api.adoptium.net/v3/binary/latest/17/ga/mac/${jdk_arch}/jdk/hotspot/normal/eclipse" \
      -o "$jdk_tarball"
  fi
  echo "==> 解压 JDK 17 到 ~/tools"
  tar -xzf "$jdk_tarball" -C "$HOME/tools"
  existing_jdk="$(ls -d "$HOME/tools"/jdk-* 2>/dev/null | head -1)"
else
  echo "JDK 已存在，跳过"
fi

if [ ! -d "$HOME/tools/apache-maven-3.9.16" ]; then
  echo "==> 解压 Maven 3.9.16 到 ~/tools"
  tar -xzf apache-maven-3.9.16-bin.tar.gz -C "$HOME/tools"
else
  echo "Maven 已存在，跳过"
fi

if [ -d "$existing_jdk/Contents/Home" ]; then
  java_home="$existing_jdk/Contents/Home"
else
  java_home="$existing_jdk"
fi

cat > "$HOME/tools/env.sh" <<EOF
# 《管家婆》项目开发环境。用法：source ~/tools/env.sh
export JAVA_HOME="$java_home"
export PATH="\$JAVA_HOME/bin:$HOME/tools/apache-maven-3.9.16/bin:/usr/local/opt/mysql/bin:\$PATH"
EOF

echo
echo "==> 验证"
# shellcheck disable=SC1091
source "$HOME/tools/env.sh"
java -version 2>&1 | head -1
mvn -v | head -1
echo
echo "安装完成。每个新终端使用前先执行： source ~/tools/env.sh"
echo
echo "还需要自行准备（本脚本不含）："
echo "  - MySQL 8.0+     brew install mysql   （数据库服务端体积大且与系统绑定，未打包）"
echo "  - Node 18+       brew install node    （若只跑 jar 不改前端，可以不装）"
echo "    若要改前端且不想联网 npm install，可解压 web-node_modules.tar.gz 到 源码/web/ 下"
