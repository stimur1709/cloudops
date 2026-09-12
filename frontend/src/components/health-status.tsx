import { cn } from "../lib/cn";

const healthPresentation = {
  UP: { label: "UP", className: "bg-status-up" },
  DEGRADED: { label: "DEGRADED", className: "bg-status-degraded" },
  DOWN: { label: "DOWN", className: "bg-status-down" },
  UNKNOWN: { label: "UNKNOWN", className: "bg-status-unknown" },
} as const;

export function HealthStatus({ status }: { status?: string }) {
  const presentation = healthPresentation[
    status as keyof typeof healthPresentation
  ] ?? {
    label: "Неизвестный статус",
    className: "bg-status-unknown",
  };
  return (
    <span className="inline-flex items-center gap-2 text-label text-foreground">
      <span
        aria-hidden="true"
        className={cn("size-1.5 rounded-full", presentation.className)}
      />
      {presentation.label}
    </span>
  );
}
