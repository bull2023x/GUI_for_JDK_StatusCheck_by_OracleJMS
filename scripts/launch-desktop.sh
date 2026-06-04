#!/usr/bin/env bash
set -Eeuo pipefail

BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"
BACKEND_URL="http://127.0.0.1:${BACKEND_PORT}"
FRONTEND_URL="http://127.0.0.1:${FRONTEND_PORT}"
OPEN_BROWSER=1

if [[ "${1:-}" == "--help" ]]; then
  cat <<'USAGE'
Usage:
  ./scripts/launch-desktop.sh [--no-open]

Starts the Spring Boot backend and Vite frontend, then opens the WebApp.

Environment:
  BACKEND_PORT   Default: 8080
  FRONTEND_PORT  Default: 5173
USAGE
  exit 0
fi

if [[ "${1:-}" == "--no-open" ]]; then
  OPEN_BROWSER=0
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
RUN_DIR="${APP_ROOT}/.desktop-run"
BACKEND_LOG="${RUN_DIR}/backend.log"
FRONTEND_LOG="${RUN_DIR}/frontend.log"
BACKEND_PID=""
FRONTEND_PID=""

mkdir -p "${RUN_DIR}"

info() {
  printf '[JMS Data Viewer] %s\n' "$*"
}

fail() {
  printf '[JMS Data Viewer] ERROR: %s\n' "$*" >&2
  exit 1
}

command_exists() {
  command -v "$1" >/dev/null 2>&1
}

port_in_use() {
  lsof -tiTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
}

show_port_owner() {
  lsof -nP -iTCP:"$1" -sTCP:LISTEN || true
}

backend_ready() {
  curl -fsS "${BACKEND_URL}/api/jms-data/status" >/dev/null 2>&1
}

frontend_ready() {
  curl -fsS "${FRONTEND_URL}" >/dev/null 2>&1
}

wait_until_ready() {
  local name="$1"
  local check_command="$2"
  local log_file="$3"
  local seconds="${4:-90}"

  for _ in $(seq 1 "${seconds}"); do
    if eval "${check_command}"; then
      info "${name} is ready."
      return 0
    fi
    sleep 1
  done

  info "${name} did not become ready within ${seconds}s."
  info "Last log lines from ${log_file}:"
  tail -n 80 "${log_file}" 2>/dev/null || true
  return 1
}

cleanup() {
  info "Stopping processes started by this launcher..."
  if [[ -n "${FRONTEND_PID}" ]] && kill -0 "${FRONTEND_PID}" >/dev/null 2>&1; then
    kill "${FRONTEND_PID}" >/dev/null 2>&1 || true
  fi
  if [[ -n "${BACKEND_PID}" ]] && kill -0 "${BACKEND_PID}" >/dev/null 2>&1; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi
}

trap cleanup INT TERM EXIT

command_exists java || fail "Java is not installed or not found in PATH."
command_exists mvn || fail "Maven is not installed or not found in PATH."
command_exists npm || fail "npm is not installed or not found in PATH."
command_exists curl || fail "curl is not installed or not found in PATH."

info "Project: ${APP_ROOT}"
info "Logs: ${RUN_DIR}"

if backend_ready; then
  info "Backend is already running on ${BACKEND_URL}; reusing it."
elif port_in_use "${BACKEND_PORT}"; then
  info "Port ${BACKEND_PORT} is already in use:"
  show_port_owner "${BACKEND_PORT}"
  fail "Backend port ${BACKEND_PORT} is busy. Stop that process and run this launcher again."
else
  info "Starting backend on ${BACKEND_URL}..."
  (
    cd "${APP_ROOT}/backend"
    mvn spring-boot:run
  ) >"${BACKEND_LOG}" 2>&1 &
  BACKEND_PID="$!"
  wait_until_ready "Backend" "backend_ready" "${BACKEND_LOG}" 120 || fail "Backend startup failed."
fi

if [[ ! -d "${APP_ROOT}/frontend/node_modules" ]]; then
  info "frontend/node_modules is missing. Running npm install..."
  (
    cd "${APP_ROOT}/frontend"
    npm install
  ) >"${FRONTEND_LOG}" 2>&1 || {
    tail -n 80 "${FRONTEND_LOG}" 2>/dev/null || true
    fail "npm install failed."
  }
fi

if frontend_ready; then
  info "Frontend is already running on ${FRONTEND_URL}; reusing it."
elif port_in_use "${FRONTEND_PORT}"; then
  info "Port ${FRONTEND_PORT} is already in use:"
  show_port_owner "${FRONTEND_PORT}"
  fail "Frontend port ${FRONTEND_PORT} is busy. Stop that process and run this launcher again."
else
  info "Starting frontend on ${FRONTEND_URL}..."
  (
    cd "${APP_ROOT}/frontend"
    npm run dev -- --host 127.0.0.1
  ) >"${FRONTEND_LOG}" 2>&1 &
  FRONTEND_PID="$!"
  wait_until_ready "Frontend" "frontend_ready" "${FRONTEND_LOG}" 60 || fail "Frontend startup failed."
fi

if [[ "${OPEN_BROWSER}" == "1" ]]; then
  info "Opening ${FRONTEND_URL}..."
  open "${FRONTEND_URL}" >/dev/null 2>&1 || info "Could not open browser automatically. Open ${FRONTEND_URL} manually."
fi

info "WebApp is running."
info "Press Ctrl-C in this terminal window to stop the backend/frontend started by this launcher."

while true; do
  sleep 3600
done
