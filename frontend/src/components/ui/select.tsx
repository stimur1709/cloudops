import * as React from "react";
import { cn } from "../../lib/cn";

export function Select({
  className,
  ...props
}: React.ComponentProps<"select">) {
  return (
    <select
      className={cn(
        "h-control rounded-control border border-border-control bg-surface px-3 text-body text-foreground disabled:pointer-events-none disabled:opacity-50",
        className,
      )}
      {...props}
    />
  );
}
