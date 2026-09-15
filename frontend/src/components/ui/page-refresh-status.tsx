import { LoaderCircle } from "lucide-react";

export function PageRefreshStatus({ label }: { label: string }) {
  return (
    <span
      className="inline-flex items-center gap-2 text-caption text-foreground-muted"
      role="status"
    >
      <LoaderCircle aria-hidden="true" className="size-icon animate-spin" />
      {label}
    </span>
  );
}
