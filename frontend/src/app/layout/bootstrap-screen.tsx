import { Cloud } from "lucide-react";
import { Skeleton } from "../../components/ui/skeleton";

export function BootstrapScreen() {
  return (
    <main
      className="grid min-h-screen place-items-center bg-background px-6"
      aria-busy="true"
      aria-label="Восстановление сессии"
    >
      <div className="w-full max-w-[var(--layout-form-max)] space-y-4">
        <div className="flex items-center gap-3 text-foreground">
          <Cloud aria-hidden="true" className="size-6 text-product-accent" />
          <span className="text-section-title">CloudOps</span>
        </div>
        <Skeleton className="h-2 w-full" />
        <p className="text-body text-foreground-muted">
          Восстанавливаем защищённую сессию…
        </p>
      </div>
    </main>
  );
}
