import { Component, type ErrorInfo, type ReactNode } from "react";
import { Alert } from "../components/ui/alert";
import { Button } from "../components/ui/button";

interface State {
  hasError: boolean;
}

export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("Unexpected application error", error, info.componentStack);
  }

  render() {
    if (!this.state.hasError) return this.props.children;
    return (
      <main className="grid min-h-screen place-items-center bg-background px-4">
        <section className="w-full max-w-lg space-y-4">
          <h1 className="text-page-title">Не удалось открыть CloudOps</h1>
          <Alert>
            Произошла непредвиденная ошибка. Перезагрузите приложение и
            повторите действие.
          </Alert>
          <Button variant="primary" onClick={() => window.location.reload()}>
            Перезагрузить
          </Button>
        </section>
      </main>
    );
  }
}
