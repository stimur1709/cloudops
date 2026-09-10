import { CircleAlert } from "lucide-react";
import * as React from "react";
import { cn } from "../../lib/cn";

function Alert({ className, children, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      role="alert"
      className={cn(
        "flex gap-3 rounded-panel border border-status-down bg-status-down-soft p-3 text-body text-foreground",
        className,
      )}
      {...props}
    >
      <CircleAlert
        aria-hidden="true"
        className="mt-0.5 size-icon shrink-0 text-status-down"
      />
      <div>{children}</div>
    </div>
  );
}

export { Alert };
