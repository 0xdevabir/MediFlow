#!/usr/bin/env bash
# Dispatch MediFlow tasks to Cursor CLI agents, one git worktree + branch per task.
#
# Usage:
#   scripts/dispatch-cursor.sh wave0            # T00 (run, review, merge to main first)
#   scripts/dispatch-cursor.sh wave1            # T01..T11 in parallel
#   scripts/dispatch-cursor.sh wave2            # T12, T13
#   scripts/dispatch-cursor.sh wave3            # T14
#   scripts/dispatch-cursor.sh T05 T06          # specific tasks
#
# Env: CURSOR_MODEL (optional, e.g. "sonnet-4.5"), WORKTREE_ROOT (default ../MediFlow-wt)
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
WT_ROOT="${WORKTREE_ROOT:-$REPO/../MediFlow-wt}"
LOG_DIR="$REPO/docs/plan/logs"
mkdir -p "$WT_ROOT" "$LOG_DIR"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"
export PATH="$JAVA_HOME/bin:$HOME/.local/bin:$PATH"

if command -v cursor-agent >/dev/null 2>&1; then AGENT=cursor-agent
elif command -v agent >/dev/null 2>&1; then AGENT=agent
else
  echo "Cursor CLI not found. Install: curl https://cursor.com/install -fsS | bash" >&2; exit 1
fi

case "${1:-}" in
  wave0) set -- T00 ;;
  wave1) set -- T01 T02 T03 T04 T05 T06 T07 T08 T09 T10 T11 ;;
  wave2) set -- T12 T13 ;;
  wave3) set -- T14 ;;
  "") echo "usage: $0 wave0|wave1|wave2|wave3|T01 [T02...]" >&2; exit 1 ;;
esac

run_task() {
  local id="$1"
  local task_file; task_file="$(ls "$REPO"/docs/plan/tasks/"$id"-*.md)"
  local slug; slug="$(basename "$task_file" .md)"
  local wt="$WT_ROOT/$slug"
  local branch="feat/$slug"

  if [ ! -d "$wt" ]; then
    git -C "$REPO" worktree add -b "$branch" "$wt" main
  fi

  local prompt
  prompt="You are a Cursor agent implementing one task of the MediFlow AI project.
Working directory: $wt (git branch $branch). Research files are in research/ (if missing, read them from $REPO/research/).
1. Read docs/plan/PLAN.md and docs/plan/CONVENTIONS.md completely.
2. Read and implement docs/plan/tasks/$slug.md completely. Stay inside the paths listed under 'Owns'.
3. If you need a change outside your paths, write it to docs/plan/requests/$id.md instead of editing.
4. Work in small increments. After EVERY small feature that compiles and whose tests pass:
   git add -A && git commit -m '$id: <short feature summary>' && git push -u origin $branch
   (Commit + push many times, not once at the end. Never commit secrets, .env files, build output or node_modules.)
5. Java 21 is at /opt/homebrew/opt/openjdk@21 (JAVA_HOME already exported). Use the Maven wrapper (./mvnw). Docker is available for Testcontainers. For Python use 'uv' (uv python install 3.11, uv venv, uv pip).
6. When everything is done and green, write docs/plan/reports/$id.md (built, deviations, open issues, how to test), commit and push it.
Never push to main, never force-push, never merge. Never weaken the safety invariants in PLAN.md section 6."

  echo "▶ $id → $wt (log: $LOG_DIR/$id.log)"
  ( cd "$wt" && "$AGENT" -p --force --trust --approve-mcps --add-dir "$REPO/research" \
      ${CURSOR_MODEL:+--model "$CURSOR_MODEL"} --output-format text "$prompt" ) \
    > "$LOG_DIR/$id.log" 2>&1 &
}

for id in "$@"; do run_task "$id"; done
wait
echo "All dispatched tasks finished. Review branches with: git -C $REPO branch --list 'feat/*'"
