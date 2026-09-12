import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import type { ResourceResponse } from "../api/generated/model";
import { HealthStatus, LifecycleStatus } from "./health-status";

export function ResourceCard({
  resource,
  href,
  actions,
}: {
  resource: ResourceResponse;
  href: string;
  actions: ReactNode;
}) {
  const updatedAt = resource.updatedAt ? new Date(resource.updatedAt) : null;
  const hasValidUpdatedAt = updatedAt && !Number.isNaN(updatedAt.getTime());
  return (
    <article className="rounded-panel border border-border bg-surface p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <Link
            to={href}
            className="block truncate text-card-title text-product-accent underline-offset-4 hover:underline"
          >
            {resource.name ?? "Без имени"}
          </Link>
          <p className="mt-1 font-mono text-caption text-foreground-muted">
            {resource.type ?? "OTHER"}
          </p>
        </div>
        <div
          className="shrink-0"
          aria-label={`Действия для ${resource.name ?? "ресурса"}`}
        >
          {actions}
        </div>
      </div>
      <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
        <HealthStatus status={resource.healthStatus} />
        <LifecycleStatus status={resource.status} />
      </div>
      {hasValidUpdatedAt && (
        <time
          dateTime={resource.updatedAt}
          className="mt-3 block text-caption text-foreground-muted"
        >
          Обновлено{" "}
          {new Intl.DateTimeFormat("ru-RU", {
            dateStyle: "medium",
            timeStyle: "short",
          }).format(updatedAt)}
        </time>
      )}
    </article>
  );
}
