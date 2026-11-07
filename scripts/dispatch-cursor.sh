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
0. A previous agent may already have started this task in this worktree: first run 'git status' and 'git log --oneline main..HEAD' and CONTINUE from the existing files (commit any good uncommitted work first). Do not start over.
1. Read docs/plan/PLAN.md, docs/plan/CONVENTIONS.md and docs/plan/reports/T00.md (actual entity/event/port names; they override the plan). Also read any docs/plan/reports/*.md already on main (git show origin/main:docs/plan/reports/T01.md etc.) for modules you depend on.
2. Read and implement docs/plan/tasks/$slug.md completely. Stay inside the paths listed under 'Owns'. Other agents work in parallel on other modules: never edit shared/, pom.xml, application*.yml, layout templates or the V1 baseline.
3. If you need a change outside your paths, write it to docs/plan/requests/$id.md instead of editing. For capabilities of other modules not in shared ports, define a minimal interface in your own package with a safe default.
4. USER RULES: do NOT run tests (no mvnw verify / test, no pytest runs) and NEVER start Docker. Optionally run './mvnw -q -DskipTests test-compile' from backend/ to catch compile errors. Write tests as code anyway.
5. Work in small increments. After EVERY small feature:
   git add -A && git commit -m '$id: <short feature summary>' && git push -u origin $branch
   (Commit + push many times, not once at the end. Never commit secrets, .env files, build output, target/, .venv or node_modules.)
6. Java 21 is at /opt/homebrew/opt/openjdk@21 (JAVA_HOME already exported). Use the Maven wrapper (./mvnw). For Python use 'uv'.
7. When done, write docs/plan/reports/$id.md (built, deviations, open issues, note that tests were not run), commit and push it.
Never push to main, never force-push, never merge. Never weaken the safety invariants in PLAN.md section 6."

  echo "▶ $id → $wt (log: $LOG_DIR/$id.log)"
  ( cd "$wt" && "$AGENT" -p --force --trust --approve-mcps --add-dir "$REPO/research" \
      ${CURSOR_MODEL:+--model "$CURSOR_MODEL"} --output-format text "$prompt" ) \
    > "$LOG_DIR/$id.log" 2>&1 &
}

for id in "$@"; do run_task "$id"; done
wait
echo "All dispatched tasks finished. Review branches with: git -C $REPO branch --list 'feat/*'"
