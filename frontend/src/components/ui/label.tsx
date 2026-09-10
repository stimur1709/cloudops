import * as LabelPrimitive from "@radix-ui/react-label";
import * as React from "react";
import { cn } from "../../lib/cn";

function Label({
  className,
  ...props
}: React.ComponentProps<typeof LabelPrimitive.Root>) {
  return (
    <LabelPrimitive.Root
      className={cn("text-label text-foreground", className)}
      {...props}
    />
  );
}

export { Label };
