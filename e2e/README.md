# MediFlow Playwright E2E (T14)

Requires a running stack (`docker compose` + backend). **Do not run from the T14 agent session.**

```bash
cd e2e
npm install
npx playwright install chromium
BASE_URL=http://localhost:8080 npm test
```

Safety drills are gated by env flags (`DRILL_AI_DOWN`, `DRILL_AI_SLOW`, …) — see `tests/safety-drills.spec.ts`.
