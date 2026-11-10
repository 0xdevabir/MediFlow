import { AppShell } from "@/components/app-shell";
import { SessionProvider } from "@/components/session";

function Loading() {
  return (
    <div className="grid min-h-dvh place-items-center">
      <div className="flex items-center gap-3 text-sm text-muted">
        <span className="size-2 rounded-full bg-brand live-dot" />
        Loading MediFlow…
      </div>
    </div>
  );
}

export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <SessionProvider fallback={<Loading />}>
      <AppShell>{children}</AppShell>
    </SessionProvider>
  );
}
