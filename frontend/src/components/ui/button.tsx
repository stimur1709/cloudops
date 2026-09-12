import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";
import * as React from "react";
import { cn } from "../../lib/cn";

const buttonVariants = cva(
  "inline-flex h-control items-center justify-center gap-2 rounded-control px-3 text-label transition-colors duration-(--motion-fast) disabled:pointer-events-none disabled:opacity-50 [&_svg]:size-icon [&_svg]:shrink-0",
  {
    variants: {
      variant: {
        primary:
          "bg-primary text-primary-foreground hover:bg-primary-hover active:bg-primary-active",
        destructive:
          "bg-destructive text-destructive-foreground hover:bg-destructive-hover",
        secondary:
          "border border-border-control bg-surface text-foreground hover:bg-surface-hover active:bg-surface-active",
        ghost:
          "text-foreground-muted hover:bg-surface-hover hover:text-foreground active:bg-surface-active",
      },
      size: {
        default: "h-control",
        icon: "size-control px-0",
      },
    },
    defaultVariants: { variant: "secondary", size: "default" },
  },
);

type ButtonProps = React.ComponentProps<"button"> &
  VariantProps<typeof buttonVariants> & { asChild?: boolean };

function Button({
  className,
  variant,
  size,
  asChild = false,
  ...props
}: ButtonProps) {
  const Component = asChild ? Slot : "button";
  return (
    <Component
      className={cn(buttonVariants({ variant, size }), className)}
      {...props}
    />
  );
}

export { Button };
