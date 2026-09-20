#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"
PORT="${SERVER_PORT:-8087}"
echo "Starting SRM 供应商管理 on http://127.0.0.1:$PORT"
exec java ${JAVA_OPTS:-} -jar srm-backend-1.0.0.jar --server.port="$PORT" 
