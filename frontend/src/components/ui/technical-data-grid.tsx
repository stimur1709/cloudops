import * as React from "react";
import { cn } from "../../lib/cn";

export interface TechnicalDataField {
  label: string;
  value: string;
}

export function TechnicalDisclosureTrigger({
  className,
  ...props
}: React.ComponentProps<"button">) {
  return (
    <button
      type="button"
      className={cn(
        "inline-flex h-control items-center text-caption text-product-accent hover:underline",
        className,
      )}
      {...props}
    />
  );
}

function Field({
  label,
  value,
  muted = false,
}: TechnicalDataField & { muted?: boolean }) {
  return (
    <div className="flex min-w-0 flex-wrap items-baseline gap-x-2 gap-y-1">
      <dt className="text-caption text-foreground-muted">{label}</dt>
      <dd
        className={
          muted
            ? "min-w-0 break-all font-mono text-technical-sm text-foreground-muted"
            : "min-w-0 break-all font-mono text-technical text-foreground"
        }
      >
        {value}
      </dd>
    </div>
  );
}

export function TechnicalDataGrid({
  fields,
  metadata = [],
}: {
  fields: TechnicalDataField[];
  metadata?: TechnicalDataField[];
}) {
  return (
    <div className="space-y-3">
      {fields.length > 0 && (
        <dl className="grid gap-x-6 gap-y-2 md:grid-cols-2">
          {fields.map((field) => (
            <Field key={field.label} {...field} />
          ))}
        </dl>
      )}
      {metadata.length > 0 && (
        <dl className="grid gap-x-6 gap-y-2 border-t border-border pt-2 md:grid-cols-2">
          {metadata.map((field) => (
            <Field key={field.label} {...field} muted />
          ))}
        </dl>
      )}
    </div>
  );
}
