import clsx from "clsx";
import type { ComponentProps, ReactNode } from "react";

export const cn = clsx;

/* ---------- Button ---------- */

type ButtonVariant = "primary" | "secondary" | "ghost" | "danger";
const buttonStyles: Record<ButtonVariant, string> = {
  primary: "bg-brand text-white hover:bg-brand-hover shadow-card",
  secondary: "bg-surface text-ink border border-border-strong hover:bg-surface-2 shadow-card",
  ghost: "text-ink-2 hover:bg-surface-2 hover:text-ink",
  danger: "bg-critical text-white hover:opacity-90 shadow-card",
};

export function Button({
  variant = "primary",
  size = "md",
  className,
  ...props
}: ComponentProps<"button"> & { variant?: ButtonVariant; size?: "sm" | "md" }) {
  return (
    <button
      className={cn(
        "inline-flex items-center justify-center gap-2 rounded-lg font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-50",
        size === "sm" ? "h-8 px-3 text-[13px]" : "h-10 px-4 text-sm",
        buttonStyles[variant],
        className,
      )}
      {...props}
    />
  );
}

/* ---------- Card ---------- */

export function Card({ className, ...props }: ComponentProps<"div">) {
  return (
    <div
      className={cn("rounded-xl border border-border bg-surface shadow-card", className)}
      {...props}
    />
  );
}

export function CardHeader({
  title,
  description,
  action,
  icon,
}: {
  title: ReactNode;
  description?: ReactNode;
  action?: ReactNode;
  icon?: ReactNode;
}) {
  return (
    <div className="flex items-start justify-between gap-4 border-b border-border px-5 py-4">
      <div className="flex min-w-0 items-start gap-3">
        {icon && (
          <span className="mt-0.5 grid size-8 shrink-0 place-items-center rounded-lg bg-brand-soft text-brand-ink">
            {icon}
          </span>
        )}
        <div className="min-w-0">
          <h2 className="text-[15px] font-semibold text-ink">{title}</h2>
          {description && <p className="mt-0.5 text-[13px] text-muted">{description}</p>}
        </div>
      </div>
      {action}
    </div>
  );
}

/* ---------- Badge ---------- */

export type Tone = "neutral" | "brand" | "critical" | "urgent" | "ok" | "info";
const toneStyles: Record<Tone, string> = {
  neutral: "bg-neutral-soft text-ink-2",
  brand: "bg-brand-soft text-brand-ink",
  critical: "bg-critical-soft text-critical",
  urgent: "bg-urgent-soft text-urgent",
  ok: "bg-ok-soft text-ok",
  info: "bg-info-soft text-info",
};

export function Badge({
  tone = "neutral",
  dot,
  className,
  children,
}: {
  tone?: Tone;
  dot?: boolean;
  className?: string;
  children: ReactNode;
}) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium",
        toneStyles[tone],
        className,
      )}
    >
      {dot && <span className="size-1.5 rounded-full bg-current" />}
      {children}
    </span>
  );
}

/* ---------- Form fields ---------- */

export function Input({ className, ...props }: ComponentProps<"input">) {
  return (
    <input
      className={cn(
        "h-10 w-full rounded-lg border border-border-strong bg-surface px-3 text-sm text-ink placeholder:text-muted transition-colors focus:border-brand",
        className,
      )}
      {...props}
    />
  );
}

export function Select({ className, ...props }: ComponentProps<"select">) {
  return (
    <select
      className={cn(
        "h-9 rounded-lg border border-border-strong bg-surface px-3 text-sm text-ink focus:border-brand",
        className,
      )}
      {...props}
    />
  );
}

export function Label({ className, ...props }: ComponentProps<"label">) {
  return <label className={cn("text-[13px] font-medium text-ink-2", className)} {...props} />;
}

/* ---------- Feedback ---------- */

export function Skeleton({ className }: { className?: string }) {
  return <div className={cn("animate-pulse rounded-md bg-surface-2", className)} />;
}

export function EmptyState({
  icon,
  title,
  description,
  action,
}: {
  icon?: ReactNode;
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-12 text-center">
      {icon && (
        <span className="mb-3 grid size-10 place-items-center rounded-full bg-surface-2 text-muted">
          {icon}
        </span>
      )}
      <p className="text-sm font-medium text-ink">{title}</p>
      {description && <p className="mt-1 max-w-sm text-[13px] text-muted">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export function PageHeader({
  title,
  description,
  actions,
}: {
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 className="text-[22px] font-semibold tracking-tight text-ink">{title}</h1>
        {description && <p className="mt-1 text-sm text-muted">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}
