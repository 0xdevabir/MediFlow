"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { ArrowUpRight, Bell, LogOut, Menu, X } from "lucide-react";
import { logout } from "@/lib/api";
import { navFor, ROLE_LABEL, type NavItem } from "@/lib/nav";
import { useSession } from "./session";
import { ThemeToggle } from "./theme-toggle";
import { cn } from "./ui";

function Logo() {
  return (
    <div className="flex items-center gap-2.5">
      <span className="grid size-8 place-items-center rounded-lg bg-brand text-white shadow-card">
        <svg viewBox="0 0 24 24" className="size-[18px]" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
          <path d="M3 12h4l2.5-6 5 12 2.5-6h4" />
        </svg>
      </span>
      <span className="text-[15px] font-semibold tracking-tight text-ink">MediFlow</span>
    </div>
  );
}

function NavLink({ item, active, onNavigate }: { item: NavItem; active: boolean; onNavigate?: () => void }) {
  const Icon = item.icon;
  const className = cn(
    "group flex h-9 items-center gap-3 rounded-lg px-2.5 text-[13.5px] font-medium transition-colors",
    active ? "bg-brand-soft text-brand-ink" : "text-ink-2 hover:bg-surface-2 hover:text-ink",
  );
  const content = (
    <>
      <Icon className={cn("size-[18px] shrink-0", active ? "text-brand" : "text-muted group-hover:text-ink-2")} />
      <span className="truncate">{item.label}</span>
      {!item.native && (
        <ArrowUpRight
          className="ml-auto size-3.5 text-muted opacity-0 transition-opacity group-hover:opacity-100"
          aria-label="classic view"
        />
      )}
    </>
  );
  // Classic (not yet migrated) pages are proxied from Spring: use a full navigation, not the client router.
  return item.native ? (
    <Link href={item.href} className={className} onClick={onNavigate} aria-current={active ? "page" : undefined}>
      {content}
    </Link>
  ) : (
    <a href={item.href} className={className} title={`${item.label} (classic view)`}>
      {content}
    </a>
  );
}

function Sidebar({ onNavigate }: { onNavigate?: () => void }) {
  const me = useSession();
  const pathname = usePathname();
  const groups = navFor(me.roles);

  return (
    <div className="flex h-full flex-col">
      <div className="flex h-16 items-center px-5">
        <Logo />
      </div>
      <nav className="flex-1 space-y-6 overflow-y-auto px-3 pb-6 pt-2" aria-label="Main">
        {groups.map((g) => (
          <div key={g.label}>
            <p className="mb-1.5 px-2.5 text-[11px] font-semibold uppercase tracking-wider text-muted">{g.label}</p>
            <div className="space-y-0.5">
              {g.items.map((item) => (
                <NavLink
                  key={item.href}
                  item={item}
                  onNavigate={onNavigate}
                  active={item.href === "/" ? pathname === "/" : pathname.startsWith(item.href)}
                />
              ))}
            </div>
          </div>
        ))}
      </nav>
      <UserCard />
    </div>
  );
}

function initials(name: string) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase())
    .join("");
}

function UserCard() {
  const me = useSession();
  const [busy, setBusy] = useState(false);
  const primary = me.roles[0];

  async function signOut() {
    setBusy(true);
    try {
      await logout();
    } finally {
      window.location.href = "/login?logout";
    }
  }

  return (
    <div className="border-t border-border p-3">
      <div className="flex items-center gap-3 rounded-lg px-2 py-2">
        <span className="grid size-9 shrink-0 place-items-center rounded-full bg-brand-soft text-[13px] font-semibold text-brand-ink">
          {initials(me.displayName || me.username) || "?"}
        </span>
        <div className="min-w-0 flex-1">
          <p className="truncate text-[13.5px] font-medium text-ink">{me.displayName || me.username}</p>
          <p className="truncate text-xs text-muted">{primary ? ROLE_LABEL[primary] ?? primary : me.username}</p>
        </div>
        <button
          onClick={signOut}
          disabled={busy}
          aria-label="Sign out"
          title="Sign out"
          className="grid size-8 place-items-center rounded-lg text-muted transition-colors hover:bg-surface-2 hover:text-critical"
        >
          <LogOut className="size-4" />
        </button>
      </div>
    </div>
  );
}

export function AppShell({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false);
  const pathname = usePathname();

  useEffect(() => setOpen(false), [pathname]);

  return (
    <div className="min-h-dvh">
      {/* Desktop sidebar */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 border-r border-border bg-surface lg:block">
        <Sidebar />
      </aside>

      {/* Mobile drawer */}
      {open && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div className="absolute inset-0 bg-black/40" onClick={() => setOpen(false)} />
          <aside className="absolute inset-y-0 left-0 w-72 max-w-[85vw] border-r border-border bg-surface shadow-pop">
            <button
              onClick={() => setOpen(false)}
              aria-label="Close menu"
              className="absolute right-3 top-4 grid size-8 place-items-center rounded-lg text-muted hover:bg-surface-2"
            >
              <X className="size-4" />
            </button>
            <Sidebar onNavigate={() => setOpen(false)} />
          </aside>
        </div>
      )}

      <div className="lg:pl-64">
        <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-border bg-surface/80 px-4 backdrop-blur sm:px-6">
          <button
            onClick={() => setOpen(true)}
            aria-label="Open menu"
            className="grid size-9 place-items-center rounded-lg text-ink-2 hover:bg-surface-2 lg:hidden"
          >
            <Menu className="size-5" />
          </button>
          <div className="lg:hidden">
            <Logo />
          </div>
          <div className="ml-auto flex items-center gap-1">
            <a
              href="/notifications"
              aria-label="Notifications"
              className="grid size-9 place-items-center rounded-lg text-ink-2 transition-colors hover:bg-surface-2 hover:text-ink"
            >
              <Bell className="size-[18px]" />
            </a>
            <ThemeToggle />
          </div>
        </header>
        <main id="main" className="mx-auto w-full max-w-7xl px-4 py-6 sm:px-6 lg:px-8 lg:py-8">
          {children}
        </main>
      </div>
    </div>
  );
}
