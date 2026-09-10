import { Construction } from "lucide-react";

export function PlaceholderPage({
  title,
  description,
}: {
  title: string;
  description: string;
}) {
  return (
    <section aria-labelledby="page-title">
      <h1 id="page-title" className="text-page-title">
        {title}
      </h1>
      <div className="mt-6 flex max-w-2xl gap-4 rounded-panel border border-border bg-surface p-4">
        <Construction
          aria-hidden="true"
          className="size-5 shrink-0 text-foreground-muted"
        />
        <div>
          <h2 className="text-card-title">Раздел готов к развитию</h2>
          <p className="mt-1 text-body text-foreground-muted">{description}</p>
        </div>
      </div>
    </section>
  );
}
