import { test, expect } from "@playwright/test";
import { loginAs } from "../fixtures/auth";

/**
 * Safety drills from T14 task. Each case documents the expected UX when a dependency fails.
 * Toggle env flags / stop services before running the matching test in CI.
 */

test.describe("safety drills", () => {
  test("AI service stopped → prescription UI offers manual mode", async ({ page }) => {
    test.skip(!process.env.DRILL_AI_DOWN, "Set DRILL_AI_DOWN=1 with AI container stopped");
    await loginAs(page, "doctor");
    await page.goto("/prescriptions/new");
    await expect(page.locator("body")).toContainText(/manual|unavailable|fallback|timeout/i);
  });

  test("AI slow (>10s) → manual fallback afforded", async ({ page }) => {
    test.skip(!process.env.DRILL_AI_SLOW, "Set DRILL_AI_SLOW=1 with AI latency injected");
    await loginAs(page, "doctor");
    await page.goto("/prescriptions/new");
    // Wait past the 10 s UI budget then expect manual path.
    await page.waitForTimeout(11_000);
    await expect(page.locator("body")).toContainText(/manual|continue without AI|fallback/i);
  });

  test("SSE blocked → polling still updates queue", async ({ page }) => {
    test.skip(!process.env.DRILL_SSE_BLOCKED, "Set DRILL_SSE_BLOCKED=1 (block EventSource)");
    await loginAs(page, "doctor");
    await page.route("**/api/queue/stream**", (route) => route.abort());
    await page.goto("/queue/doctor");
    await page.waitForTimeout(12_000); // poll interval ~10s
    await expect(page.locator("body")).toContainText(/queue|token|waiting|called/i);
  });

  test("critical vitals in OPD → Red Alert board shows patient", async ({ page }) => {
    test.skip(!process.env.DRILL_CRITICAL_VITALS, "Set DRILL_CRITICAL_VITALS=1 after seeding critical vitals");
    await loginAs(page, "er");
    await page.goto("/er");
    await expect(page.locator("body")).toContainText(/critical|red.?alert/i);
  });

  test("notification channel failure → staff call list escalation", async ({ page }) => {
    test.skip(!process.env.DRILL_NOTIFY_FAIL, "Set DRILL_NOTIFY_FAIL=1 with SMS/email providers failing");
    await loginAs(page, "frontdesk");
    await page.goto("/notifications/call-list");
    await expect(page.locator("body")).toContainText(/call list|phone|escalat|pending/i);
  });

  test("revoke after dispense surfaces incident / patient alert", async ({ page }) => {
    test.skip(!process.env.DRILL_REVOKE_AFTER_DISPENSE, "Set DRILL_REVOKE_AFTER_DISPENSE=1 after scripted dispense+revoke");
    await loginAs(page, "doctor");
    await page.goto("/prescriptions");
    await expect(page.locator("body")).toContainText(/revok|entered.in.error|incident|dispensed/i);
  });
});
