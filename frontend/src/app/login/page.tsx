"use client";

import { Suspense, useState, type FormEvent } from "react";
import { useSearchParams } from "next/navigation";
import { AlertCircle, CheckCircle2, Eye, EyeOff, Loader2 } from "lucide-react";
import { login, UnauthorizedError } from "@/lib/api";
import { Button, Input, Label } from "@/components/ui";
import { ThemeToggle } from "@/components/theme-toggle";

function LoginForm() {
  const params = useSearchParams();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(
    params.get("error") === "unavailable" ? "The server is unavailable. Try again in a moment." : null,
  );
  const loggedOut = params.has("logout");

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(username.trim(), password);
      const next = params.get("next");
      window.location.href = next && next.startsWith("/") && !next.startsWith("//") ? next : "/";
    } catch (err) {
      if (err instanceof UnauthorizedError && err.mfaRequired) {
        window.location.href = "/identity/mfa/verify";
        return;
      }
      setError(
        err instanceof UnauthorizedError
          ? "Incorrect username or password."
          : "Could not reach the server. Check that the backend is running.",
      );
      setBusy(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="space-y-5" noValidate>
      {loggedOut && !error && (
        <div className="flex items-center gap-2 rounded-lg border border-border bg-ok-soft px-3 py-2.5 text-[13px] text-ok">
          <CheckCircle2 className="size-4 shrink-0" /> You have been signed out.
        </div>
      )}
      {error && (
        <div role="alert" className="flex items-center gap-2 rounded-lg bg-critical-soft px-3 py-2.5 text-[13px] text-critical">
          <AlertCircle className="size-4 shrink-0" /> {error}
        </div>
      )}
      <div className="space-y-1.5">
        <Label htmlFor="username">Username</Label>
        <Input
          id="username"
          autoComplete="username"
          autoFocus
          required
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          placeholder="e.g. doctor"
        />
      </div>
      <div className="space-y-1.5">
        <div className="flex items-center justify-between">
          <Label htmlFor="password">Password</Label>
          <a href="/identity/password/forgot" className="text-[13px] font-medium text-brand hover:underline">
            Forgot password?
          </a>
        </div>
        <div className="relative">
          <Input
            id="password"
            type={show ? "text" : "password"}
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="pr-10"
          />
          <button
            type="button"
            onClick={() => setShow((s) => !s)}
            aria-label={show ? "Hide password" : "Show password"}
            className="absolute inset-y-0 right-0 grid w-10 place-items-center text-muted hover:text-ink"
          >
            {show ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
          </button>
        </div>
      </div>
      <Button type="submit" className="w-full" disabled={busy || !username || !password}>
        {busy && <Loader2 className="size-4 animate-spin" />}
        {busy ? "Signing in…" : "Sign in"}
      </Button>
    </form>
  );
}

export default function LoginPage() {
  return (
    <div className="grid min-h-dvh lg:grid-cols-[1.1fr_1fr]">
      {/* Brand panel */}
      <section className="relative hidden overflow-hidden bg-[#0b3f3d] p-12 text-white lg:flex lg:flex-col">
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0 opacity-[0.12]"
          style={{
            backgroundImage:
              "linear-gradient(rgba(255,255,255,.6) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.6) 1px, transparent 1px)",
            backgroundSize: "40px 40px",
            maskImage: "radial-gradient(ellipse at 30% 40%, black 20%, transparent 70%)",
          }}
        />
        <div className="relative flex items-center gap-2.5">
          <span className="grid size-9 place-items-center rounded-lg bg-white/10 ring-1 ring-white/20">
            <svg viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
              <path d="M3 12h4l2.5-6 5 12 2.5-6h4" />
            </svg>
          </span>
          <span className="text-lg font-semibold tracking-tight">MediFlow</span>
        </div>
        <div className="relative mt-auto max-w-md">
          <h1 className="text-[34px] font-semibold leading-tight tracking-tight">
            One calm workspace for the whole patient journey.
          </h1>
          <p className="mt-4 text-[15px] leading-relaxed text-white/70">
            Registration, live OPD queue, consultations, prescriptions with AI safety review, lab and
            pharmacy, connected end to end.
          </p>
          <dl className="mt-10 grid grid-cols-3 gap-6 border-t border-white/15 pt-6 text-sm">
            {[
              ["Live", "queue & triage"],
              ["FHIR R4", "interoperable"],
              ["Audited", "hash-chained log"],
            ].map(([k, v]) => (
              <div key={k}>
                <dt className="font-semibold">{k}</dt>
                <dd className="mt-0.5 text-white/60">{v}</dd>
              </div>
            ))}
          </dl>
        </div>
      </section>

      {/* Form */}
      <section className="relative flex flex-col px-6 py-10 sm:px-12">
        <div className="flex justify-end">
          <ThemeToggle />
        </div>
        <div className="mx-auto my-auto w-full max-w-sm">
          <h2 className="text-2xl font-semibold tracking-tight text-ink">Sign in</h2>
          <p className="mb-8 mt-1.5 text-sm text-muted">Use your MediFlow staff or patient account.</p>
          <Suspense>
            <LoginForm />
          </Suspense>
          <p className="mt-8 text-center text-[13px] text-muted">
            New patient?{" "}
            <a href="/patients/register" className="font-medium text-brand hover:underline">
              Create an account
            </a>
          </p>
        </div>
      </section>
    </div>
  );
}
