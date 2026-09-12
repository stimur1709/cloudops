import { lazy, Suspense } from "react";
import { Navigate, Outlet, Route, Routes, useLocation } from "react-router-dom";
import { useAuth } from "../../features/auth/auth-context";
import { LoginPage } from "../../features/auth/login-page";
import { FirstUsePage } from "../../features/organization/first-use-page";
import { useAvailableOrganizations } from "../../features/organization/organization-context";
import {
  AvailableOrganizationsProvider,
  OrganizationProvider,
} from "../../features/organization/organization-provider";
import { AppShell } from "../layout/app-shell";
import { BootstrapScreen } from "../layout/bootstrap-screen";
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
const ResourcesPage = lazy(() =>
  import("../../features/resource/resources-page").then((module) => ({
    default: module.ResourcesPage,
  })),
);

function ProtectedRoutes() {
  const { status } = useAuth();
  const location = useLocation();
  if (status === "BOOTSTRAPPING") return <BootstrapScreen />;
  if (status === "UNAUTHENTICATED")
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return (
    <AvailableOrganizationsProvider>
      {(organizations) =>
        organizations.length === 0 ? <FirstUsePage /> : <Outlet />
      }
    </AvailableOrganizationsProvider>
  );
}

function LoginRoute() {
  const { status } = useAuth();
  if (status === "BOOTSTRAPPING") return <BootstrapScreen />;
  return <LoginPage />;
}

function OrganizationIndexRoute() {
  const { organizations } = useAvailableOrganizations();
  return (
    <Navigate to={`/organizations/${organizations[0]?.id}/resources`} replace />
  );
}

function organizationPage(title: string, description: string) {
  return <PlaceholderPage title={title} description={description} />;
}

export function AppRoutes() {
  return (
    <Suspense fallback={<PageLoading />}>
      <Routes>
        <Route path="/login" element={<LoginRoute />} />
        <Route element={<ProtectedRoutes />}>
          <Route index element={<OrganizationIndexRoute />} />
          <Route
            path="organizations/:organizationId"
            element={
              <OrganizationProvider>
                <AppShell />
              </OrganizationProvider>
            }
          >
            <Route index element={<Navigate to="resources" replace />} />
            <Route path="resources" element={<ResourcesPage />} />
            <Route
              path="resources/:resourceId"
              element={organizationPage(
                "Детали ресурса",
                "Страница ресурса будет добавлена отдельной задачей.",
              )}
            />
            <Route
              path="monitoring"
              element={organizationPage(
                "Мониторинг",
                "Мониторинг и история проверок появятся в отдельной задаче.",
              )}
            />
            <Route
              path="operations"
              element={organizationPage(
                "Операции",
                "Запуск и отслеживание операций будет реализован отдельно.",
              )}
            />
            <Route
              path="credentials"
              element={organizationPage(
                "Учётные данные",
                "Безопасное управление учётными данными будет добавлено отдельно.",
              )}
            />
            <Route
              path="settings"
              element={organizationPage(
                "Настройки",
                "Настройки workspace появятся в следующих задачах.",
              )}
            />
          </Route>
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </Suspense>
  );
}
