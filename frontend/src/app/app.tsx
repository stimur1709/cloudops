import { ErrorBoundary } from "./error-boundary";
import { AppProviders } from "./providers/app-providers";
import { AppRoutes } from "./router/routes";

export function App() {
  return (
    <ErrorBoundary>
      <AppProviders>
        <AppRoutes />
      </AppProviders>
    </ErrorBoundary>
  );
}
