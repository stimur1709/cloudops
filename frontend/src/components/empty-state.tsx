import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
}: {
  icon: LucideIcon;
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <section className="rounded-panel border border-border bg-surface p-6 text-center">
      <Icon
        aria-hidden="true"
        className="mx-auto size-icon-empty text-foreground-muted"
      />
      <h1 className="mt-4 text-card-title">{title}</h1>
      <p className="mx-auto mt-2 max-w-[var(--layout-form-max)] text-body text-foreground-muted">
        {description}
      </p>
      {action && <div className="mt-6 flex justify-center">{action}</div>}
    </section>
  );
}
