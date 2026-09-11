import { Skeleton } from "../../components/ui/skeleton";

export function PageLoading() {
  return (
    <section
      aria-busy="true"
      aria-label="Загрузка страницы"
      className="space-y-4"
    >
      <Skeleton className="h-8 w-48" />
      <Skeleton className="h-24 w-full max-w-2xl" />
    </section>
  );
}
