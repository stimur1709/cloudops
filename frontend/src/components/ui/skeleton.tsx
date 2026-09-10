import * as React from "react";
import { cn } from "../../lib/cn";

function Skeleton({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      aria-hidden="true"
      className={cn("animate-pulse rounded-sm bg-surface-active", className)}
      {...props}
    />
  );
}

export { Skeleton };
