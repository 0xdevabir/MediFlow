"use client";

import { useEffect, useState, type ReactNode } from "react";
import Link from "next/link";
import {
  ArrowRight,
  CalendarClock,
  CalendarPlus,
  ClipboardList,
  FlaskConical,
  HeartPulse,
  ListOrdered,
  Pill,
  Siren,
  Stethoscope,
  UserPlus,
  Users,
} from "lucide-react";
import { api, type Dashboard, type EncounterCard, type Role } from "@/lib/api";
import { ROLE_LABEL } from "@/lib/nav";
import { useSession } from "@/components/session";
import { Badge, Card, CardHeader, EmptyState, PageHeader, Skeleton, cn } from "@/components/ui";

function greeting() {
  const h = new Date().getHours();
  return h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
}

const timeFmt = new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" });
const dateTimeFmt = new Intl.DateTimeFormat(undefined, {
  weekday: "short",
  day: "numeric",
  month: "short",
  hour: "numeric",
  minute: "2-digit",
});

/* ---------- Stat tile ---------- */

function Stat({
  label,
  value,
  hint,
  icon,
  tone = "brand",
  href,
}: {
  label: string;
  value: ReactNode;
  hint?: string;
  icon: ReactNode;
  tone?: "brand" | "critical" | "urgent" | "info";
  href?: string;
}) {
  const toneCls = {
    brand: "bg-brand-soft text-brand-ink",
    critical: "bg-critical-soft text-critical",
    urgent: "bg-urgent-soft text-urgent",
    info: "bg-info-soft text-info",
  }[tone];
  const body = (
    <Card className={cn("p-5", href && "transition-shadow hover:shadow-pop")}>
      <div className="flex items-start justify-between">
        <p className="text-[13px] font-medium text-muted">{label}</p>
        <span className={cn("grid size-8 place-items-center rounded-lg", toneCls)}>{icon}</span>
      </div>
      <p className="mt-3 text-[28px] font-semibold leading-none tracking-tight text-ink tabular-nums">{value}</p>
      {hint && <p className="mt-2 text-[13px] text-muted">{hint}</p>}
    </Card>
  );
  return href ? (
    <a href={href} className="block rounded-xl">
      {body}
    </a>
  ) : (
    body
  );
}

/* ---------- Quick actions ---------- */

interface QuickAction {
  label: string;
  description: string;
  href: string;
  icon: ReactNode;
  roles: Role[];
}

const QUICK_ACTIONS: QuickAction[] = [
  { label: "Book appointment", description: "Find a doctor and a free slot", href: "/appointments/book", icon: <CalendarPlus className="size-4" />, roles: ["PATIENT", "FRONT_DESK", "ADMIN"] },
  { label: "Check in patient", description: "Arrival → triage → queue token", href: "/appointments/check-in", icon: <UserPlus className="size-4" />, roles: ["FRONT_DESK", "NURSE", "ADMIN"] },
  { label: "Live queue", description: "Today's OPD board", href: "/queue", icon: <ListOrdered className="size-4" />, roles: ["FRONT_DESK", "NURSE", "DOCTOR", "ADMIN"] },
  { label: "Consultations", description: "Open encounters & notes", href: "/clinical/consultations", icon: <Stethoscope className="size-4" />, roles: ["DOCTOR", "NURSE", "ADMIN"] },
  { label: "Prescriptions", description: "Drafts, AI review, signing", href: "/prescriptions", icon: <ClipboardList className="size-4" />, roles: ["DOCTOR", "PHARMACIST", "ADMIN"] },
  { label: "Lab worklist", description: "Orders, samples, results", href: "/lab", icon: <FlaskConical className="size-4" />, roles: ["LAB_TECH", "DOCTOR", "ADMIN"] },
  { label: "Dispense", description: "Pharmacy queue & stock", href: "/pharmacy", icon: <Pill className="size-4" />, roles: ["PHARMACIST", "ADMIN"] },
  { label: "Emergency", description: "ER intake & assignments", href: "/er", icon: <Siren className="size-4" />, roles: ["ER", "ADMIN"] },
  { label: "My records", description: "Profile, history, documents", href: "/patients/me", icon: <Users className="size-4" />, roles: ["PATIENT"] },
  { label: "My lab results", description: "Reports shared with you", href: "/lab", icon: <FlaskConical className="size-4" />, roles: ["PATIENT"] },
];

function QuickActions({ roles }: { roles: Role[] }) {
  const actions = QUICK_ACTIONS.filter((a) => a.roles.some((r) => roles.includes(r))).slice(0, 6);
  if (actions.length === 0) return null;
  return (
    <Card>
      <CardHeader title="Quick actions" description="Jump into today's work" />
      <div className="grid gap-px overflow-hidden rounded-b-xl bg-border sm:grid-cols-2">
        {actions.map((a) => {
          const native = a.href === "/queue";
          const cls = "group flex items-center gap-3 bg-surface px-5 py-4 transition-colors hover:bg-surface-2";
          const inner = (
            <>
              <span className="grid size-9 shrink-0 place-items-center rounded-lg border border-border bg-surface text-brand">
                {a.icon}
              </span>
              <span className="min-w-0 flex-1">
                <span className="block text-sm font-medium text-ink">{a.label}</span>
                <span className="block truncate text-[13px] text-muted">{a.description}</span>
              </span>
              <ArrowRight className="size-4 text-muted transition-transform group-hover:translate-x-0.5 group-hover:text-ink" />
            </>
          );
          return native ? (
            <Link key={a.label} href={a.href} className={cls}>
              {inner}
            </Link>
          ) : (
            <a key={a.label} href={a.href} className={cls}>
              {inner}
            </a>
          );
        })}
      </div>
    </Card>
  );
}

/* ---------- Encounter list ---------- */

function EncounterList({
  title,
  description,
  items,
  emptyTitle,
  icon,
}: {
  title: string;
  description: string;
  items: EncounterCard[];
  emptyTitle: string;
  icon: ReactNode;
}) {
  return (
    <Card>
      <CardHeader
        title={title}
        description={description}
        icon={icon}
        action={items.length > 0 ? <Badge tone="brand">{items.length}</Badge> : undefined}
      />
      {items.length === 0 ? (
        <EmptyState title={emptyTitle} description="New items appear here automatically." />
      ) : (
        <ul className="divide-y divide-border">
          {items.map((e) => (
            <li key={e.id}>
              <a
                href={`/clinical/consultations/${e.id}`}
                className="flex items-center gap-3 px-5 py-3 transition-colors hover:bg-surface-2"
              >
                <span className="grid size-9 shrink-0 place-items-center rounded-full bg-surface-2 text-[13px] font-semibold text-ink-2">
                  {e.patientName
                    .split(" ")
                    .map((p) => p[0])
                    .slice(0, 2)
                    .join("")}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium text-ink">{e.patientName}</span>
                  <span className="block truncate font-mono text-xs text-muted">{e.mrn ?? "No MRN"}</span>
                </span>
                {e.startedAt && (
                  <span className="hidden text-[13px] text-muted tabular-nums sm:block">
                    {timeFmt.format(new Date(e.startedAt))}
                  </span>
                )}
                <Badge tone={e.status === "IN_PROGRESS" ? "info" : e.status === "COMPLETED" ? "ok" : "neutral"} dot>
                  {e.status.replace("_", " ").toLowerCase()}
                </Badge>
              </a>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

/* ---------- Patient view ---------- */

function AdherenceRing({ pct }: { pct: number }) {
  const r = 34;
  const c = 2 * Math.PI * r;
  const tone = pct >= 80 ? "var(--ok)" : pct >= 50 ? "var(--urgent)" : "var(--critical)";
  return (
    <svg viewBox="0 0 80 80" className="size-24" role="img" aria-label={`${pct}% adherence`}>
      <circle cx="40" cy="40" r={r} fill="none" stroke="var(--surface-2)" strokeWidth="8" />
      <circle
        cx="40"
        cy="40"
        r={r}
        fill="none"
        stroke={tone}
        strokeWidth="8"
        strokeLinecap="round"
        strokeDasharray={`${(pct / 100) * c} ${c}`}
        transform="rotate(-90 40 40)"
      />
      <text x="40" y="45" textAnchor="middle" className="fill-ink text-[16px] font-semibold">
        {pct}%
      </text>
    </svg>
  );
}

function PatientPanels({ d }: { d: Dashboard }) {
  return (
    <div className="grid gap-4 md:grid-cols-2">
      <Card>
        <CardHeader title="Next visit" icon={<CalendarClock className="size-4" />} />
        {d.nextVisit ? (
          <div className="p-5">
            <p className="text-lg font-semibold text-ink">{dateTimeFmt.format(new Date(d.nextVisit.when))}</p>
            <p className="mt-1 text-sm text-muted">{d.nextVisit.summary}</p>
            <div className="mt-4 flex gap-2">
              <a href={`/appointments/${d.nextVisit.appointmentId}`} className="text-sm font-medium text-brand hover:underline">
                View details
              </a>
              <span className="text-border-strong">·</span>
              <a href={`/appointments/${d.nextVisit.appointmentId}/reschedule`} className="text-sm font-medium text-brand hover:underline">
                Reschedule
              </a>
            </div>
          </div>
        ) : (
          <EmptyState
            title="No upcoming visits"
            description="Book an appointment with any doctor in the directory."
            action={
              <a href="/appointments/book" className="text-sm font-medium text-brand hover:underline">
                Book an appointment →
              </a>
            }
          />
        )}
      </Card>
      <Card>
        <CardHeader title="Medication adherence" description="Last 30 days of reminders" icon={<HeartPulse className="size-4" />} />
        {d.adherencePercent != null ? (
          <div className="flex items-center gap-5 p-5">
            <AdherenceRing pct={d.adherencePercent} />
            <p className="text-sm text-ink-2">
              {d.adherencePercent >= 80
                ? "Great consistency, keep it up."
                : "A few doses were missed. Reminders can help you stay on track."}
            </p>
          </div>
        ) : (
          <EmptyState title="No reminders yet" description="Adherence appears once a prescription has dose reminders." />
        )}
      </Card>
    </div>
  );
}

/* ---------- Page ---------- */

export default function DashboardPage() {
  const me = useSession();
  const [d, setD] = useState<Dashboard | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    api.get<Dashboard>("/api/v1/dashboard").then(setD).catch(() => setFailed(true));
  }, []);

  const firstName = (me.displayName || me.username).split(" ")[0];
  const isPatient = me.roles.includes("PATIENT");
  const clinical = me.roles.some((r) => ["DOCTOR", "NURSE", "ADMIN", "ER"].includes(r));
  const isDoctorish = me.roles.some((r) => ["DOCTOR", "ADMIN"].includes(r));
  const today = new Intl.DateTimeFormat(undefined, { weekday: "long", day: "numeric", month: "long" }).format(new Date());

  return (
    <>
      <PageHeader
        title={`${greeting()}, ${firstName}`}
        description={
          <>
            {today}
            {me.roles.length > 0 && (
              <>
                {" · "}
                {me.roles.map((r) => ROLE_LABEL[r] ?? r).join(", ")}
              </>
            )}
          </>
        }
      />

      {failed && (
        <Card className="mb-6 border-critical/30 bg-critical-soft p-4 text-sm text-critical">
          Couldn&apos;t load dashboard data. The backend may still be starting. Refresh in a moment.
        </Card>
      )}

      <div className="space-y-6">
        {clinical && (
          <div className="grid gap-4 sm:grid-cols-3">
            {d ? (
              <>
                {isDoctorish && (
                  <Stat label="Open consultations today" value={d.openToday.length} hint="In progress or awaiting notes" icon={<Stethoscope className="size-4" />} href="/clinical/consultations" />
                )}
                <Stat label="Vitals pending" value={d.vitalsPending.length} hint="Encounters without vitals" icon={<HeartPulse className="size-4" />} tone="urgent" />
                <Stat
                  label="Unread lab results"
                  value={d.labBadgeCount}
                  hint={d.labBadgeCount > 0 ? "New reports need review" : "All caught up"}
                  icon={<FlaskConical className="size-4" />}
                  tone={d.labBadgeCount > 0 ? "critical" : "info"}
                  href="/lab"
                />
              </>
            ) : (
              [0, 1, 2].map((i) => <Skeleton key={i} className="h-[124px] rounded-xl" />)
            )}
          </div>
        )}

        {isPatient && (d ? <PatientPanels d={d} /> : <Skeleton className="h-48 rounded-xl" />)}

        <div className={cn("grid gap-6", clinical && "xl:grid-cols-[1fr_1fr]")}>
          {clinical && (
            <div className="space-y-6">
              {isDoctorish &&
                (d ? (
                  <EncounterList title="Today's consultations" description="Your open encounters" items={d.openToday} emptyTitle="No open consultations" icon={<Stethoscope className="size-4" />} />
                ) : (
                  <Skeleton className="h-64 rounded-xl" />
                ))}
              {d ? (
                <EncounterList title="Awaiting vitals" description="Capture vitals before the consultation" items={d.vitalsPending} emptyTitle="No patients waiting on vitals" icon={<HeartPulse className="size-4" />} />
              ) : (
                <Skeleton className="h-64 rounded-xl" />
              )}
            </div>
          )}
          <div>
            <QuickActions roles={me.roles} />
          </div>
        </div>
      </div>
    </>
  );
}
