import { useQuery } from "@tanstack/react-query";
import { Building2, LoaderCircle } from "lucide-react";
import { type ReactNode, useCallback, useMemo } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { readableError } from "../../api/client/api-error";
import type { OrganizationResponse } from "../../api/generated/model";
import { Alert } from "../../components/ui/alert";
import { Button } from "../../components/ui/button";
import { EmptyState } from "../../components/empty-state";
import { useAuth } from "../auth/auth-context";
import {
  AvailableOrganizationsContext,
  OrganizationContext,
  type OrganizationRole,
  useAvailableOrganizations,
} from "./organization-context";
import {
  getCurrentMembership,
  getOrganizations,
  organizationKeys,
} from "./organization-api";

function LoadingState({ label }: { label: string }) {
  return (
    <main
      className="grid min-h-screen place-items-center bg-background px-6"
      aria-busy="true"
      aria-label={label}
    >
      <div className="flex items-center gap-3 text-body text-foreground-muted">
        <LoaderCircle aria-hidden="true" className="size-icon animate-spin" />
        {label}
      </div>
    </main>
  );
}

function FullPageError({
  error,
  retry,
}: {
  error: unknown;
  retry: () => void;
}) {
  return (
    <main className="grid min-h-screen place-items-center bg-background p-4">
      <div className="w-full max-w-[var(--layout-form-max)] space-y-4">
        <Alert>{readableError(error)}</Alert>
        <Button type="button" onClick={retry}>
          Повторить
        </Button>
      </div>
    </main>
  );
}

export function AvailableOrganizationsProvider({
  children,
}: {
  children: (organizations: OrganizationResponse[]) => ReactNode;
}) {
  const { data, error, isError, isPending, refetch } = useQuery({
    queryKey: organizationKeys.all,
    queryFn: ({ signal }) => getOrganizations(signal),
  });
  const reload = useCallback(async () => {
    await refetch();
  }, [refetch]);

  const value = useMemo(
    () => ({
      organizations: data ?? [],
      reload,
    }),
    [data, reload],
  );

  if (isPending) return <LoadingState label="Загрузка организаций" />;
  if (isError)
    return <FullPageError error={error} retry={() => void refetch()} />;

  return (
    <AvailableOrganizationsContext.Provider value={value}>
      {children(value.organizations)}
    </AvailableOrganizationsContext.Provider>
  );
}

function InvalidOrganizationState() {
  const { organizations } = useAvailableOrganizations();
  const navigate = useNavigate();
  return (
    <main className="grid min-h-screen place-items-center bg-background p-4">
      <div className="w-full max-w-[var(--layout-form-max)]">
        <EmptyState
          icon={Building2}
          title="Организация недоступна"
          description="Организация не существует или у вас больше нет к ней доступа. Выберите доступную организацию."
          action={
            <div className="flex flex-wrap justify-center gap-2">
              {organizations.map((organization) => (
                <Button
                  key={organization.id}
                  type="button"
                  onClick={() =>
                    navigate(`/organizations/${organization.id}/resources`, {
                      replace: true,
                    })
                  }
                >
                  {organization.name}
                </Button>
              ))}
            </div>
          }
        />
      </div>
    </main>
  );
}

function isRole(value: unknown): value is OrganizationRole {
  return value === "OWNER" || value === "ADMIN" || value === "MEMBER";
}

export function OrganizationProvider({ children }: { children: ReactNode }) {
  const { organizationId: routeOrganizationId } = useParams();
  const { organizations } = useAvailableOrganizations();
  const { user } = useAuth();
  const organizationId =
    routeOrganizationId && /^\d+$/.test(routeOrganizationId)
      ? Number(routeOrganizationId)
      : Number.NaN;
  const organization = organizations.find((item) => item.id === organizationId);
  const userId = user?.id;
  const membership = useQuery({
    queryKey: organizationKeys.membership(organizationId, userId ?? 0),
    queryFn: ({ signal }) =>
      getCurrentMembership(organizationId, userId as number, signal),
    enabled: Boolean(organization && userId),
  });

  if (!organization) return <InvalidOrganizationState />;
  if (!userId)
    return (
      <FullPageError
        error={new Error("Не удалось определить текущего пользователя.")}
        retry={() => window.location.reload()}
      />
    );
  if (membership.isPending)
    return <LoadingState label="Загрузка роли в организации" />;
  if (membership.isError)
    return (
      <FullPageError
        error={membership.error}
        retry={() => void membership.refetch()}
      />
    );
  if (!membership.data || !isRole(membership.data.role))
    return <InvalidOrganizationState />;

  const currentRole = membership.data.role;
  return (
    <OrganizationContext.Provider
      value={{
        organization,
        organizationId,
        currentRole,
        isManager: currentRole === "OWNER" || currentRole === "ADMIN",
      }}
    >
      {children}
    </OrganizationContext.Provider>
  );
}
