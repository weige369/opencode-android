#!/data/data/com.termux/files/usr/bin/bash
# ============================================================
# OpenCode Android — Termux Bootstrap 安装脚本
# 
# 一键安装 OpenCode 运行所需的全部开发工具：
#   Node.js, npm, git, python, build-essential, opencode
# ============================================================
set -e

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log()  { echo -e "${GREEN}[bootstrap]${NC} $*"; }
warn() { echo -e "${YELLOW}[bootstrap]${NC} $*"; }
err()  { echo -e "${RED}[bootstrap]${NC} $*"; }

log "============================================"
log "OpenCode Android 开发环境一键安装"
log "============================================"
log ""

# ——— 1. 基础依赖 ———
log "[1/5] 更新软件源 & 安装基础依赖..."
pkg update -y
pkg upgrade -y
pkg install -y nodejs-lts git python build-essential binutils wget curl

log "Node.js:  $(node -v)"
log "npm:      $(npm -v)"
log "git:      $(git --version | head -1)"
log "python:   $(python3 --version)"

# ——— 2. 配置 npm ———
log "[2/5] 配置 npm 镜像..."
npm config set registry https://registry.npmmirror.com
npm config set fetch-timeout 120000

# ——— 3. 安装 opencode ———
log "[3/5] 安装 opencode (全局)..."
npm install -g @anthropic-ai/opencode 2>&1 || {
    warn "官方 npm 安装失败，尝试 opencode-termux deb..."
    wget -O /tmp/opencode.deb "https://github.com/Hope2333/opencode-termux/releases/latest/download/opencode_aarch64.deb" && {
        dpkg -i /tmp/opencode.deb
        apt-get install -f -y
        rm /tmp/opencode.deb
    } || {
        warn "deb 安装也失败，尝试从源码安装..."
        npm install -g opencode
    }
}

# ——— 4. 验证 ———
log "[4/5] 验证安装..."
if command -v opencode >/dev/null 2>&1; then
    log "✅ opencode 安装成功!"
    opencode --version 2>&1 || true
else
    err "❌ opencode 命令未找到，请手动排查"
    err "   路径检查: ls -la /data/data/com.termux/files/usr/bin/opencode*"
    exit 1
fi

# ——— 5. 写入 PATH 持久化 ———
log "[5/5] 持久化 PATH..."
grep -q 'OPENCODE_BIN' ~/.bashrc 2>/dev/null || {
    cat >> ~/.bashrc << 'EOF'
# OpenCode Android bootstrap
export NODE_PATH=$(npm root -g)
export PATH="$PATH:$HOME/.local/bin"
EOF
}

log ""
log "============================================"
log "✅ 全部完成! 现在可以返回 OpenCode App 启动服务"
log "   或在 Termux 中运行: opencode serve --port 4096"
log "============================================"
