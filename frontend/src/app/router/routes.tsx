import { lazy, Suspense } from "react";
import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import { AppShell } from "../layout/app-shell";
import { BootstrapScreen } from "../layout/bootstrap-screen";
import { useAuth } from "../../features/auth/auth-context";
import { LoginPage } from "../../features/auth/login-page";
import { PageLoading } from "./page-loading";

const NotFoundPage = lazy(() =>
  import("./not-found-page").then((module) => ({
    default: module.NotFoundPage,
  })),
);
const PlaceholderPage = lazy(() =>
  import("./placeholder-page").then((module) => ({
    default: module.PlaceholderPage,
  })),
);

function ProtectedRoutes() {
  const { status } = useAuth();
  const location = useLocation();
  if (status === "BOOTSTRAPPING") return <BootstrapScreen />;
  if (status === "UNAUTHENTICATED")
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <AppShell />;
}

function LoginRoute() {
  const { status } = useAuth();
  if (status === "BOOTSTRAPPING") return <BootstrapScreen />;
  return <LoginPage />;
}

export function AppRoutes() {
  return (
    <Suspense fallback={<PageLoading />}>
      <Routes>
        <Route path="/login" element={<LoginRoute />} />
        <Route element={<ProtectedRoutes />}>
          <Route index element={<Navigate to="/resources" replace />} />
          <Route
            path="resources"
            element={
              <PlaceholderPage
                title="Ресурсы"
                description="Управление ресурсами будет добавлено отдельной задачей."
              />
            }
          />
          <Route
            path="monitoring"
            element={
              <PlaceholderPage
                title="Мониторинг"
                description="Мониторинг и история проверок появятся в отдельной задаче."
              />
            }
          />
          <Route
            path="operations"
            element={
              <PlaceholderPage
                title="Операции"
                description="Запуск и отслеживание операций будет реализован отдельно."
              />
            }
          />
          <Route
            path="credentials"
            element={
              <PlaceholderPage
                title="Учётные данные"
                description="Безопасное управление учётными данными будет добавлено отдельно."
              />
            }
          />
          <Route
            path="settings"
            element={
              <PlaceholderPage
                title="Настройки"
                description="Настройки workspace появятся в следующих задачах."
              />
            }
          />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </Suspense>
  );
}
