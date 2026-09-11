import * as React from "react";
import { cn } from "../../lib/cn";

function Input({ className, type, ...props }: React.ComponentProps<"input">) {
  return (
    <input
      type={type}
      className={cn(
        "h-control w-full rounded-control border border-border-control bg-surface px-3 text-body text-foreground placeholder:text-foreground-subtle disabled:cursor-not-allowed disabled:opacity-50 aria-invalid:border-status-down",
        className,
      )}
      {...props}
    />
  );
}

export { Input };
