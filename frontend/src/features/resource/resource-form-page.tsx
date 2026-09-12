import { useQuery, useQueryClient } from "@tanstack/react-query";
import { FolderSearch, LoaderCircle, LockKeyhole } from "lucide-react";
import { useNavigate, useParams } from "react-router-dom";
import { ApiClientError, readableError } from "../../api/client/api-error";
import type {
  CreateResourceRequest,
  UpdateResourceRequest,
} from "../../api/generated/model";
import { EmptyState } from "../../components/empty-state";
import { Alert } from "../../components/ui/alert";
import { Button } from "../../components/ui/button";
import { useOrganization } from "../organization/organization-context";
import {
  createResource,
  getResource,
  resourceKeys,
  updateResource,
} from "./resource-api";
import { ResourceForm } from "./resource-form";

function ManagerOnly({ children }: { children: React.ReactNode }) {
  const { isManager } = useOrganization();
  if (isManager) return children;
  return (
    <EmptyState
      icon={LockKeyhole}
      title="Недостаточно прав"
      description="Создавать и изменять ресурсы могут только владельцы и администраторы организации."
    />
  );
}

function PageHeader({
  title,
  description,
}: {
  title: string;
  description: string;
}) {
  return (
    <header>
      <h1 className="text-page-title">{title}</h1>
      <p className="mt-1 text-body text-foreground-muted">{description}</p>
    </header>
  );
}

export function NewResourcePage() {
  const { organizationId } = useOrganization();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const listPath = `/organizations/${organizationId}/resources`;
  async function submit(
    request: CreateResourceRequest | UpdateResourceRequest,
  ) {
    await createResource(request as CreateResourceRequest);
    await queryClient.invalidateQueries({
      queryKey: resourceKeys.organization(organizationId),
    });
    navigate(listPath, { replace: true });
  }
  return (
    <ManagerOnly>
      <section className="space-y-6">
        <PageHeader
          title="Новый ресурс"
          description="Добавьте инфраструктурный ресурс в текущую организацию."
        />
        <ResourceForm organizationId={organizationId} onSubmit={submit} />
      </section>
    </ManagerOnly>
  );
}

export function EditResourcePage() {
  return (
    <ManagerOnly>
      <EditResourceContent />
    </ManagerOnly>
  );
}

function EditResourceContent() {
  const { organizationId } = useOrganization();
  const { resourceId: routeResourceId } = useParams();
  const resourceId =
    routeResourceId && /^\d+$/.test(routeResourceId)
      ? Number(routeResourceId)
      : Number.NaN;
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const query = useQuery({
    queryKey: resourceKeys.detail(organizationId, resourceId),
    queryFn: ({ signal }) => getResource(resourceId, signal),
    enabled: Number.isFinite(resourceId),
  });
  const listPath = `/organizations/${organizationId}/resources`;

  if (!Number.isFinite(resourceId))
    return (
      <EmptyState
        icon={FolderSearch}
        title="Ресурс не найден"
        description="Проверьте адрес или вернитесь к списку ресурсов."
        action={
          <Button type="button" onClick={() => navigate(listPath)}>
            К списку ресурсов
          </Button>
        }
      />
    );
  if (query.isPending)
    return (
      <div
        className="flex items-center gap-2 text-body text-foreground-muted"
        role="status"
      >
        <LoaderCircle aria-hidden="true" className="size-icon animate-spin" />
        Загрузка ресурса
      </div>
    );
  if (
    query.error instanceof ApiClientError &&
    (query.error.kind === "not-found" || query.error.kind === "forbidden")
  )
    return (
      <EmptyState
        icon={query.error.kind === "forbidden" ? LockKeyhole : FolderSearch}
        title={
          query.error.kind === "forbidden"
            ? "Недостаточно прав"
            : "Ресурс не найден"
        }
        description={
          query.error.kind === "forbidden"
            ? "У вас нет доступа к этому ресурсу."
            : "Ресурс не существует или больше недоступен."
        }
        action={
          <Button type="button" onClick={() => navigate(listPath)}>
            К списку ресурсов
          </Button>
        }
      />
    );
  if (query.isError)
    return (
      <div className="space-y-4">
        <Alert>{readableError(query.error)}</Alert>
        <Button type="button" onClick={() => void query.refetch()}>
          Повторить
        </Button>
      </div>
    );
  if (query.data.organizationId !== organizationId)
    return (
      <EmptyState
        icon={FolderSearch}
        title="Ресурс недоступен"
        description="Ресурс не относится к выбранной организации."
        action={
          <Button type="button" onClick={() => navigate(listPath)}>
            К списку ресурсов
          </Button>
        }
      />
    );

  async function submit(
    request: CreateResourceRequest | UpdateResourceRequest,
  ) {
    const updated = await updateResource(
      resourceId,
      request as UpdateResourceRequest,
    );
    queryClient.setQueryData(
      resourceKeys.detail(organizationId, resourceId),
      updated,
    );
    await queryClient.invalidateQueries({
      queryKey: resourceKeys.organization(organizationId),
    });
    navigate(listPath, { replace: true });
  }

  return (
    <section className="space-y-6">
      <PageHeader
        title={`Редактировать ${query.data.name ?? "ресурс"}`}
        description="Измените основные параметры и конфигурацию ресурса."
      />
      <ResourceForm
        organizationId={organizationId}
        resource={query.data}
        onSubmit={submit}
      />
    </section>
  );
}
