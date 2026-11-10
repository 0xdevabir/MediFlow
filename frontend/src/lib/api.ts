/**
 * Browser-side client for the Spring backend (proxied same-origin via next.config rewrites).
 * Unauthenticated requests get a 302 to /login from Spring, so we never follow redirects:
 * an opaque redirect or 401 means "signed out".
 */

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

export class UnauthorizedError extends ApiError {
  constructor(public mfaRequired = false) {
    super(401, mfaRequired ? "MFA required" : "Not signed in");
  }
}

let csrfCache: string | null = null;

export async function csrfToken(force = false): Promise<string> {
  if (csrfCache && !force) return csrfCache;
  const res = await fetch("/api/csrf", { credentials: "same-origin", cache: "no-store" });
  if (!res.ok) throw new ApiError(res.status, "Could not get CSRF token");
  csrfCache = ((await res.json()) as { token: string }).token;
  return csrfCache;
}

export function resetCsrf() {
  csrfCache = null;
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (method !== "GET") {
    headers["X-XSRF-TOKEN"] = await csrfToken();
    if (body !== undefined) headers["Content-Type"] = "application/json";
  }
  const res = await fetch(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    credentials: "same-origin",
    redirect: "manual",
    cache: "no-store",
  });
  if (res.type === "opaqueredirect" || res.status === 401) {
    const text = res.type === "opaqueredirect" ? "" : await res.text().catch(() => "");
    throw new UnauthorizedError(text.includes("MFA"));
  }
  if (!res.ok) throw new ApiError(res.status, `${method} ${path} failed (${res.status})`);
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export const api = {
  get: <T>(path: string) => request<T>("GET", path),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, body),
};

/**
 * POST to a classic Spring MVC form endpoint (it answers with a redirect, which we don't follow).
 * Used for actions that don't have a JSON API yet.
 */
export async function postForm(path: string, fields: Record<string, string>) {
  const res = await fetch(path, {
    method: "POST",
    body: new URLSearchParams({ ...fields, _csrf: await csrfToken() }),
    credentials: "same-origin",
    redirect: "manual",
  });
  if (res.status === 401) throw new UnauthorizedError();
  if (res.type !== "opaqueredirect" && !res.ok) throw new ApiError(res.status, `POST ${path} failed`);
}

/** Spring form login. Success is confirmed by /api/v1/me, since the response is a redirect. */
export async function login(username: string, password: string): Promise<Me> {
  const form = new URLSearchParams({ username, password, _csrf: await csrfToken(true) });
  await fetch("/auth/login", {
    method: "POST",
    body: form,
    credentials: "same-origin",
    redirect: "manual",
  });
  resetCsrf(); // Spring rotates the token on authentication
  return api.get<Me>("/api/v1/me");
}

export async function logout() {
  const form = new URLSearchParams({ _csrf: await csrfToken(true) });
  await fetch("/auth/logout", { method: "POST", body: form, credentials: "same-origin", redirect: "manual" });
  resetCsrf();
}

/* ---------- API types (mirror SpaApiController / QueueApiController records) ---------- */

export type Role =
  | "PATIENT"
  | "FRONT_DESK"
  | "NURSE"
  | "DOCTOR"
  | "ER"
  | "LAB_TECH"
  | "PHARMACIST"
  | "ADMIN"
  | "CLINICAL_APPROVER";

export interface Me {
  id: string | null;
  username: string;
  displayName: string;
  locale: string;
  roles: Role[];
}

export interface EncounterCard {
  id: string;
  patientId: string;
  patientName: string;
  mrn: string | null;
  status: string;
  startedAt: string | null;
}

export interface Dashboard {
  roles: Role[];
  patientId: string | null;
  nextVisit: { appointmentId: string; when: string; summary: string } | null;
  adherencePercent: number | null;
  openToday: EncounterCard[];
  vitalsPending: EncounterCard[];
  labBadgeCount: number;
}

export interface Doctor {
  id: string;
  name: string | null;
  roleLabel: string | null;
}

export interface QueueContext {
  myDoctorId: string | null;
  doctors: Doctor[];
}

export type TriageLevel = string; // e.g. EMERGENCY / URGENT / ROUTINE (backend enum)
export type QueueStatus = string; // e.g. WAITING / CALLED / IN_CONSULT / DONE / NO_SHOW

export interface QueueBoardRow {
  queueEntryId: string;
  appointmentId: string;
  patientId: string;
  tokenNo: string;
  tokenSeq: number;
  level: TriageLevel;
  status: QueueStatus;
  vitals: string | null;
  arrivedAt: string | null;
  position: number;
  ahead: number;
  emergencyFlag: boolean;
  patientLabel: string;
}
