import { ArrowLeft } from "lucide-react";
import { Link } from "react-router-dom";
import { Button } from "../../components/ui/button";

export function NotFoundPage() {
  return (
    <main className="grid min-h-screen place-items-center bg-background px-4">
      <section
        className="max-w-lg text-center"
        aria-labelledby="not-found-title"
      >
        <p className="font-mono text-label text-product-accent">404</p>
        <h1 id="not-found-title" className="mt-2 text-page-title">
          Страница не найдена
        </h1>
        <p className="mt-2 text-body text-foreground-muted">
          Адрес неверен или страница была перемещена.
        </p>
        <Button asChild variant="secondary" className="mt-6">
          <Link to="/">
            <ArrowLeft aria-hidden="true" />
            Вернуться в CloudOps
          </Link>
        </Button>
      </section>
    </main>
  );
}
