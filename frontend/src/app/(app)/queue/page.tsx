"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { BellRing, ExternalLink, ListOrdered, Loader2, RefreshCw, Siren, UserPlus } from "lucide-react";
import { api, postForm, type QueueBoardRow, type QueueContext } from "@/lib/api";
import { useSession } from "@/components/session";
import { Badge, Button, Card, EmptyState, PageHeader, Select, Skeleton, cn, type Tone } from "@/components/ui";

const LEVEL: Record<string, { label: string; tone: Tone }> = {
  CRITICAL: { label: "Critical", tone: "critical" },
  URGENT: { label: "Urgent", tone: "urgent" },
  NORMAL: { label: "Normal", tone: "neutral" },
};
const STATUS: Record<string, { label: string; tone: Tone }> = {
  WAITING: { label: "Waiting", tone: "neutral" },
  CALLED: { label: "Called", tone: "info" },
  IN_SERVICE: { label: "In consult", tone: "brand" },
  DONE: { label: "Done", tone: "ok" },
  LEFT: { label: "Left", tone: "neutral" },
};
const VITALS: Record<string, { label: string; tone: Tone }> = {
  NORMAL: { label: "Normal", tone: "ok" },
  BORDERLINE: { label: "Borderline", tone: "urgent" },
  CRITICAL: { label: "Critical", tone: "critical" },
};

const timeFmt = new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" });

function todayIso() {
  const d = new Date();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}

function waitedMinutes(arrivedAt: string | null, now: number) {
  if (!arrivedAt) return null;
  return Math.max(0, Math.round((now - new Date(arrivedAt).getTime()) / 60000));
}

export default function QueuePage() {
  const me = useSession();
  const isDoctor = me.roles.includes("DOCTOR");

  const [ctx, setCtx] = useState<QueueContext | null>(null);
  const [doctorId, setDoctorId] = useState<string>("");
  const [date, setDate] = useState(todayIso);
  const [rows, setRows] = useState<QueueBoardRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [live, setLive] = useState(false);
  const [calling, setCalling] = useState(false);
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    api
      .get<QueueContext>("/api/v1/queue/context")
      .then((c) => {
        setCtx(c);
        setDoctorId(c.myDoctorId ?? c.doctors[0]?.id ?? "");
      })
      .catch(() => setError("Couldn't load the doctor list."));
  }, []);

  const load = useCallback(async () => {
    if (!doctorId) return;
    try {
      const data = await api.get<QueueBoardRow[]>(
        `/api/queue/board?doctorId=${encodeURIComponent(doctorId)}&date=${date}`,
      );
      setRows(data);
      setError(null);
    } catch {
      setError("Couldn't load the queue.");
    }
  }, [doctorId, date]);

  useEffect(() => {
    setRows(null);
    load();
  }, [load]);

  // Live updates: SSE pushes a "queue" event on every change; polling is the safety net.
  useEffect(() => {
    if (!doctorId) return;
    const es = new EventSource(`/api/queue/stream?doctorId=${encodeURIComponent(doctorId)}&date=${date}`);
    es.addEventListener("connected", () => setLive(true));
    es.addEventListener("queue", () => load());
    es.onerror = () => setLive(false);
    const poll = setInterval(load, 20000);
    return () => {
      es.close();
      clearInterval(poll);
      setLive(false);
    };
  }, [doctorId, date, load]);

  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 30000);
    return () => clearInterval(t);
  }, []);

  const counts = useMemo(() => {
    const c = { WAITING: 0, CALLED: 0, IN_SERVICE: 0, DONE: 0, critical: 0 };
    for (const r of rows ?? []) {
      if (r.status in c) c[r.status as keyof typeof c]++;
      if ((r.level === "CRITICAL" || r.emergencyFlag) && r.status !== "DONE" && r.status !== "LEFT") c.critical++;
    }
    return c;
  }, [rows]);

  const active = (rows ?? []).filter((r) => r.status !== "DONE" && r.status !== "LEFT");
  const finished = (rows ?? []).filter((r) => r.status === "DONE" || r.status === "LEFT");
  const isToday = date === todayIso();

  async function callNext() {
    setCalling(true);
    try {
      await postForm("/queue/call-next", { doctorId, date });
      await load();
    } catch {
      setError("Couldn't call the next patient.");
    } finally {
      setCalling(false);
    }
  }

  return (
    <>
      <PageHeader
        title="Live queue"
        description={
          <span className="inline-flex items-center gap-2">
            <span className={cn("size-2 rounded-full", live ? "live-dot bg-ok" : "bg-border-strong")} />
            {live ? "Live: updates automatically" : "Refreshing every 20 seconds"}
          </span>
        }
        actions={
          <>
            {ctx && ctx.doctors.length > 0 && !isDoctor && (
              <Select value={doctorId} onChange={(e) => setDoctorId(e.target.value)} aria-label="Doctor">
                {ctx.doctors.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name ?? "Unnamed"}
                    {d.roleLabel ? ` · ${d.roleLabel}` : ""}
                  </option>
                ))}
              </Select>
            )}
            <input
              type="date"
              value={date}
              onChange={(e) => e.target.value && setDate(e.target.value)}
              aria-label="Queue date"
              className="h-9 rounded-lg border border-border-strong bg-surface px-3 text-sm text-ink"
            />
            <Button variant="secondary" size="sm" onClick={load} aria-label="Refresh" className="h-9 w-9 px-0">
              <RefreshCw className="size-4" />
            </Button>
            <a href="/appointments/check-in">
              <Button variant="secondary" size="sm" className="h-9">
                <UserPlus className="size-4" /> Check in
              </Button>
            </a>
            {isDoctor && isToday && (
              <Button size="sm" className="h-9" onClick={callNext} disabled={calling || counts.WAITING === 0}>
                {calling ? <Loader2 className="size-4 animate-spin" /> : <BellRing className="size-4" />}
                Call next
              </Button>
            )}
          </>
        }
      />

      {error && (
        <Card className="mb-4 border-critical/30 bg-critical-soft p-4 text-sm text-critical">{error}</Card>
      )}

      <div className="mb-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
        {[
          { label: "Waiting", value: counts.WAITING, cls: "text-ink" },
          { label: "Called", value: counts.CALLED, cls: "text-info" },
          { label: "In consult", value: counts.IN_SERVICE, cls: "text-brand" },
          { label: "Critical / emergency", value: counts.critical, cls: counts.critical ? "text-critical" : "text-ink" },
        ].map((s) => (
          <Card key={s.label} className="px-5 py-4">
            <p className="text-[13px] font-medium text-muted">{s.label}</p>
            <p className={cn("mt-1.5 text-2xl font-semibold tabular-nums", s.cls)}>{rows ? s.value : "—"}</p>
          </Card>
        ))}
      </div>

      <Card className="overflow-hidden">
        {rows === null && !error ? (
          <div className="space-y-3 p-5">
            {[0, 1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        ) : !doctorId ? (
          <EmptyState icon={<ListOrdered className="size-5" />} title="No doctors found" description="Add practitioners in the directory to start a queue." />
        ) : active.length === 0 && finished.length === 0 ? (
          <EmptyState
            icon={<ListOrdered className="size-5" />}
            title="Queue is empty"
            description="Patients appear here as soon as they're checked in and triaged."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-sm">
              <thead>
                <tr className="border-b border-border bg-surface-2 text-left text-xs font-medium uppercase tracking-wide text-muted">
                  <th className="px-5 py-3 font-medium">Token</th>
                  <th className="px-3 py-3 font-medium">Patient</th>
                  <th className="px-3 py-3 font-medium">Triage</th>
                  <th className="px-3 py-3 font-medium">Vitals</th>
                  <th className="px-3 py-3 font-medium">Status</th>
                  <th className="px-3 py-3 font-medium">Arrived</th>
                  <th className="px-5 py-3 text-right font-medium">Waiting</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {[...active, ...finished].map((r) => {
                  const level = LEVEL[r.level] ?? { label: r.level, tone: "neutral" as Tone };
                  const status = STATUS[r.status] ?? { label: r.status, tone: "neutral" as Tone };
                  const vitals = r.vitals ? VITALS[r.vitals] : null;
                  const waited = waitedMinutes(r.arrivedAt, now);
                  const done = r.status === "DONE" || r.status === "LEFT";
                  const hot = !done && (r.level === "CRITICAL" || r.emergencyFlag);
                  return (
                    <tr
                      key={r.queueEntryId}
                      className={cn(
                        "transition-colors hover:bg-surface-2",
                        done && "opacity-55",
                        hot && "bg-critical-soft/60",
                        r.status === "CALLED" && "bg-info-soft/60",
                      )}
                    >
                      <td className="px-5 py-3">
                        <span className="inline-flex items-center gap-2">
                          <span className="font-mono text-[15px] font-semibold text-ink">{r.tokenNo}</span>
                          {r.emergencyFlag && <Siren className="size-4 text-critical" aria-label="Emergency" />}
                        </span>
                      </td>
                      <td className="px-3 py-3">
                        <a href={`/appointments/${r.appointmentId}`} className="font-medium text-ink hover:text-brand">
                          {r.patientLabel}
                        </a>
                        {!done && r.ahead > 0 && <p className="text-xs text-muted">{r.ahead} ahead</p>}
                      </td>
                      <td className="px-3 py-3">
                        <Badge tone={level.tone} dot>
                          {level.label}
                        </Badge>
                      </td>
                      <td className="px-3 py-3">
                        {vitals ? <Badge tone={vitals.tone}>{vitals.label}</Badge> : <span className="text-xs text-muted">Not taken</span>}
                      </td>
                      <td className="px-3 py-3">
                        <Badge tone={status.tone}>{status.label}</Badge>
                      </td>
                      <td className="px-3 py-3 tabular-nums text-ink-2">
                        {r.arrivedAt ? timeFmt.format(new Date(r.arrivedAt)) : "—"}
                      </td>
                      <td className="px-5 py-3 text-right tabular-nums">
                        {done || waited == null ? (
                          <span className="text-muted">—</span>
                        ) : (
                          <span className={cn(waited >= 45 ? "font-medium text-urgent" : "text-ink-2")}>{waited} min</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <p className="mt-4 flex items-center gap-1.5 text-[13px] text-muted">
        Need to triage, reorder or move a patient up?
        <a href={`/queue/home?doctorId=${doctorId}&date=${date}`} className="inline-flex items-center gap-1 font-medium text-brand hover:underline">
          Open the full board <ExternalLink className="size-3.5" />
        </a>
      </p>
    </>
  );
}
