import * as React from "react";
import { cn } from "../../lib/cn";

export function Table({ className, ...props }: React.ComponentProps<"table">) {
  return (
    <table className={cn("w-full border-collapse", className)} {...props} />
  );
}

export function TableHeader(props: React.ComponentProps<"thead">) {
  return <thead {...props} />;
}

export function TableBody(props: React.ComponentProps<"tbody">) {
  return <tbody {...props} />;
}

export function TableRow({ className, ...props }: React.ComponentProps<"tr">) {
  return (
    <tr
      className={cn(
        "border-b border-border transition-colors duration-(--motion-fast) last:border-b-0 hover:bg-surface-hover",
        className,
      )}
      {...props}
    />
  );
}

export function TableHead({ className, ...props }: React.ComponentProps<"th">) {
  return (
    <th
      scope="col"
      className={cn(
        "h-11 border-b border-border bg-surface px-3 text-left text-label text-foreground-muted",
        className,
      )}
      {...props}
    />
  );
}

export function TableCell({ className, ...props }: React.ComponentProps<"td">) {
  return (
    <td className={cn("min-h-11 px-3 py-3 text-body", className)} {...props} />
  );
}
