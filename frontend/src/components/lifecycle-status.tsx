import { getResourceStatusLabel } from "./resource-labels";

export function LifecycleStatus({ status }: { status?: string }) {
  return (
    <span className="text-body text-foreground-muted">
      {getResourceStatusLabel(status)}
    </span>
  );
}
