import { Page, expect } from "@playwright/test";

/** Dev-seeded users (password always "password"). */
export const USERS = {
  patient: "patient",
  doctor: "doctor",
  nurse: "nurse",
  frontdesk: "frontdesk",
  labtech: "labtech",
  pharmacist: "pharmacist",
  er: "er",
  admin: "admin",
} as const;

export type UserKey = keyof typeof USERS;

export async function loginAs(page: Page, user: UserKey): Promise<void> {
  await page.goto("/login");
  await page.fill("#username", USERS[user]);
  await page.fill("#password", "password");
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL(/\/$/);
}

export async function logout(page: Page): Promise<void> {
  const form = page.locator('form[action="/logout"]');
  if (await form.count()) {
    await form.locator('button[type="submit"]').click();
  }
}
