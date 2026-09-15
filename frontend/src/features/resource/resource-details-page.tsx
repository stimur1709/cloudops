import { useQuery } from "@tanstack/react-query";
import {
  ArrowLeft,
  ArrowRight,
  Ellipsis,
  FolderSearch,
  History,
  LoaderCircle,
  Pencil,
  Trash2,
} from "lucide-react";
import { useMemo, useState } from "react";
import {
  Link,
  useLocation,
  useParams,
  useSearchParams,
} from "react-router-dom";
import { ApiClientError, readableError } from "../../api/client/api-error";
import type {
  ResourceAvailabilityResponse,
  ResourceHealthEventResponse,
  ResourceResponse,
} from "../../api/generated/model";
import { EmptyState } from "../../components/empty-state";
import { HealthStatus } from "../../components/health-status";
import { LifecycleStatus } from "../../components/lifecycle-status";
import {
  getResourceStatusLabel,
  getResourceTypeLabel,
} from "../../components/resource-labels";
import { Alert } from "../../components/ui/alert";
import { Button } from "../../components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "../../components/ui/dropdown-menu";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "../../components/ui/select";
import { Skeleton } from "../../components/ui/skeleton";
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "../../components/ui/tabs";
import { MonitoringSection } from "../monitoring/monitoring-section";
import { useOrganization } from "../organization/organization-context";
import {
  getResource,
  getResourceAvailability,
  getResourceHealthEvents,
  resourceKeys,
} from "./resource-api";
import { ResourceDeleteDialog } from "./resource-delete-dialog";
import {
  buildAvailabilityRange,
  formatDateTime,
  formatDuration,
  formatPercent,
  isAvailabilityPeriod,
  type AvailabilityPeriod,
} from "./resource-details-format";

const eventPageSize = 10;
type ResourceTab = "overview" | "health" | "monitoring";

function isResourceTab(value: string | null): value is ResourceTab {
  return value === "overview" || value === "health" || value === "monitoring";
}
const configLabels: Record<string, string> = {
  host: "Хост",
  port: "Порт",
  sshPort: "SSH-порт",
  managementPort: "Порт управления",
  database: "База данных",
  url: "URL",
  expectedStatus: "Ожидаемый HTTP-статус",
};

function flattenConfig(
  value: unknown,
  prefix = "",
): { key: string; value: string }[] {
  if (value === null || value === undefined) return [];
  if (Array.isArray(value))
    return [{ key: prefix, value: value.map(String).join(", ") }];
  if (typeof value !== "object") return [{ key: prefix, value: String(value) }];
  return Object.entries(value).flatMap(([key, nested]) =>
    flattenConfig(nested, prefix ? `${prefix}.${key}` : key),
  );
}

function ConfigDetails({ resource }: { resource: ResourceResponse }) {
  const entries = flattenConfig(resource.config);
  if (entries.length === 0)
    return <p className="text-body text-foreground-muted">Нет настроек.</p>;
  return (
    <dl className="grid gap-4 sm:grid-cols-2">
      {entries.map((entry) => (
        <div key={entry.key} className="min-w-0">
          <dt className="text-caption text-foreground-muted">
            {configLabels[entry.key] ?? entry.key}
          </dt>
          <dd className="mt-1 break-all font-mono text-technical text-foreground">
            {entry.value}
          </dd>
        </div>
      ))}
    </dl>
  );
}

function DefinitionItem({
  label,
  children,
  technical = false,
}: {
  label: string;
  children: React.ReactNode;
  technical?: boolean;
}) {
  return (
    <div>
      <dt className="text-caption text-foreground-muted">{label}</dt>
      <dd
        className={
          technical
            ? "mt-1 font-mono text-technical text-foreground"
            : "mt-1 text-body text-foreground"
        }
      >
        {children}
      </dd>
    </div>
  );
}

function Metric({
  label,
  value,
  hint,
}: {
  label: string;
  value: string;
  hint?: string;
}) {
  return (
    <div className="p-4">
      <p className="text-label text-foreground-muted">{label}</p>
      <p className="mt-2 font-mono text-metric tabular-nums text-foreground">
        {value}
      </p>
      {hint && (
        <p className="mt-1 text-caption text-foreground-muted">{hint}</p>
      )}
    </div>
  );
}

function AvailabilitySummary({ data }: { data: ResourceAvailabilityResponse }) {
  return (
    <div className="space-y-4">
      <div className="grid divide-y divide-border overflow-hidden rounded-panel border border-border bg-surface md:grid-cols-3 md:divide-x md:divide-y-0">
        <Metric
          label="Доступность"
          value={formatPercent(data.availabilityPercent)}
          hint="UP + DEGRADED в известном времени"
        />
        <Metric
          label="Uptime"
          value={formatPercent(data.uptimePercent)}
          hint="Только подтверждённое UP"
        />
        <Metric
          label="Покрытие"
          value={formatPercent(data.coveragePercent)}
          hint="Доля периода с известным статусом"
        />
      </div>
      <dl className="grid gap-4 rounded-panel border border-border bg-surface p-4 sm:grid-cols-2 lg:grid-cols-4">
        <DefinitionItem label="UP" technical>
          {formatDuration(data.upSeconds)}
        </DefinitionItem>
        <DefinitionItem label="DEGRADED" technical>
          {formatDuration(data.degradedSeconds)}
        </DefinitionItem>
        <DefinitionItem label="DOWN" technical>
          {formatDuration(data.downSeconds)}
        </DefinitionItem>
        <DefinitionItem label="UNKNOWN" technical>
          {formatDuration(data.unknownSeconds)}
        </DefinitionItem>
        <DefinitionItem label="Известное время" technical>
          {formatDuration(data.knownSeconds)}
        </DefinitionItem>
        <DefinitionItem label="Весь период" technical>
          {formatDuration(data.periodSeconds)}
        </DefinitionItem>
        <DefinitionItem label="Начало периода" technical>
          {formatDateTime(data.from)}
        </DefinitionItem>
        <DefinitionItem label="Конец периода" technical>
          {formatDateTime(data.to)}
        </DefinitionItem>
      </dl>
    </div>
  );
}

function HealthEvents({
  resourceId,
  organizationId,
  enabled,
}: {
  resourceId: number;
  organizationId: number;
  enabled: boolean;
}) {
  const [page, setPage] = useState(0);
  const query = useQuery({
    queryKey: resourceKeys.healthEvents(organizationId, resourceId, page),
    queryFn: ({ signal }) =>
      getResourceHealthEvents(resourceId, page, eventPageSize, signal),
    enabled,
  });
  const canGoNext =
    query.data?.total !== undefined
      ? (page + 1) * eventPageSize < query.data.total
      : (query.data?.items.length ?? 0) === eventPageSize;
  const rangeStart = page * eventPageSize + 1;
  const rangeEnd = rangeStart + (query.data?.items.length ?? 0) - 1;

  if (query.isPending)
    return (
      <div className="space-y-3" aria-label="Загрузка истории" aria-busy="true">
        <Skeleton className="h-11" />
        <Skeleton className="h-11" />
        <Skeleton className="h-11" />
      </div>
    );
  if (query.isError)
    return (
      <div className="space-y-3">
        <Alert>Не удалось загрузить историю изменений здоровья.</Alert>
        <Button type="button" onClick={() => void query.refetch()}>
          Повторить
        </Button>
      </div>
    );
  if (query.data.items.length === 0)
    return (
      <EmptyState
        icon={History}
        title="Истории здоровья пока нет"
        description="Переходы появятся после изменений подтверждённого состояния ресурса."
      />
    );

  return (
    <div className="space-y-4">
      <ol className="divide-y divide-border overflow-hidden rounded-panel border border-border bg-surface">
        {query.data.items.map((event, index) => (
          <HealthEventRow
            key={event.id ?? `${event.changedAt}-${index}`}
            event={event}
          />
        ))}
      </ol>
      <nav
        aria-label="Пагинация истории"
        className="flex items-center justify-between gap-3"
      >
        <p className="text-caption text-foreground-muted">
          {query.data.total !== undefined
            ? `${rangeStart}–${rangeEnd} из ${query.data.total}`
            : `Страница ${page + 1}`}
        </p>
        <div className="flex gap-2">
          <Button
            type="button"
            disabled={page === 0}
            onClick={() => setPage(page - 1)}
          >
            Назад
          </Button>
          <Button
            type="button"
            disabled={!canGoNext}
            onClick={() => setPage(page + 1)}
          >
            Далее
          </Button>
        </div>
      </nav>
    </div>
  );
}

function HealthEventRow({ event }: { event: ResourceHealthEventResponse }) {
  return (
    <li className="flex flex-wrap items-center justify-between gap-3 p-3">
      <div className="flex items-center gap-3">
        <HealthStatus status={event.fromStatus} />
        <ArrowRight
          aria-hidden="true"
          className="size-icon text-foreground-muted"
        />
        <HealthStatus status={event.toStatus} />
      </div>
      <time
        dateTime={event.changedAt}
        className="font-mono text-technical-sm text-foreground-muted"
      >
        {formatDateTime(event.changedAt)}
      </time>
    </li>
  );
}

function LoadingDetails() {
  return (
    <section
      className="max-w-(--layout-reading-max) space-y-6"
      aria-label="Загрузка ресурса"
      aria-busy="true"
    >
      <Skeleton className="h-8 w-64" />
      <Skeleton className="h-24" />
      <Skeleton className="h-48" />
    </section>
  );
}

export function ResourceDetailsPage() {
  const { organizationId, isManager } = useOrganization();
  const { resourceId: routeResourceId } = useParams();
  const resourceId =
    routeResourceId && /^\d+$/.test(routeResourceId)
      ? Number(routeResourceId)
      : Number.NaN;
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const periodValue = searchParams.get("period") ?? "24h";
  const period: AvailabilityPeriod = isAvailabilityPeriod(periodValue)
    ? periodValue
    : "24h";
  const tabValue = searchParams.get("tab");
  const activeTab: ResourceTab = isResourceTab(tabValue)
    ? tabValue
    : "overview";
  const [rangeEnd] = useState(() => new Date());
  const range = useMemo(
    () => buildAvailabilityRange(period, rangeEnd),
    [period, rangeEnd],
  );
  const [deleteOpen, setDeleteOpen] = useState(false);
  const listSearch = (location.state as { resourceListSearch?: string } | null)
    ?.resourceListSearch;
  const listPath = `/organizations/${organizationId}/resources${listSearch ? `?${listSearch}` : ""}`;

  const resourceQuery = useQuery({
    queryKey: resourceKeys.detail(organizationId, resourceId),
    queryFn: ({ signal }) => getResource(resourceId, signal),
    enabled: Number.isFinite(resourceId),
  });
  const belongsToOrganization =
    resourceQuery.data?.organizationId === organizationId;
  const availabilityQuery = useQuery({
    queryKey: resourceKeys.availability(
      organizationId,
      resourceId,
      range.from,
      range.to,
    ),
    queryFn: ({ signal }) =>
      getResourceAvailability(resourceId, range.from, range.to, signal),
    enabled: belongsToOrganization && activeTab === "health",
  });

  if (!Number.isFinite(resourceId))
    return (
      <ResourceUnavailable
        listPath={listPath}
        title="Ресурс не найден"
        description="Проверьте адрес или вернитесь к списку ресурсов."
      />
    );
  if (resourceQuery.isPending) return <LoadingDetails />;
  if (resourceQuery.isError) {
    const controlled =
      resourceQuery.error instanceof ApiClientError &&
      ["not-found", "forbidden"].includes(resourceQuery.error.kind);
    if (controlled)
      return (
        <ResourceUnavailable
          listPath={listPath}
          title="Ресурс не найден или недоступен"
          description="Ресурс не существует либо у вас нет доступа к нему."
        />
      );
    return (
      <div className="space-y-4">
        <Alert>{readableError(resourceQuery.error)}</Alert>
        <Button type="button" onClick={() => void resourceQuery.refetch()}>
          Повторить
        </Button>
      </div>
    );
  }
  if (!belongsToOrganization)
    return (
      <ResourceUnavailable
        listPath={listPath}
        title="Ресурс недоступен"
        description="Ресурс не относится к выбранной организации."
      />
    );

  const resource = resourceQuery.data;
  return (
    <section className="max-w-(--layout-reading-max) space-y-8">
      <header className="space-y-4">
        <Link
          to={listPath}
          className="inline-flex items-center gap-2 text-label text-product-accent hover:underline"
        >
          <ArrowLeft aria-hidden="true" className="size-icon" />К списку
          ресурсов
        </Link>
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="break-words text-page-title">
                {resource.name ?? "Без имени"}
              </h1>
              <HealthStatus status={resource.healthStatus} />
              {resourceQuery.isFetching && (
                <LoaderCircle
                  aria-label="Обновление данных"
                  className="size-icon animate-spin text-foreground-muted"
                />
              )}
            </div>
            <p className="mt-1 text-body text-foreground-muted">
              {getResourceTypeLabel(resource.type)} ·{" "}
              {getResourceStatusLabel(resource.status)}
            </p>
          </div>
          {isManager && (
            <div className="flex flex-wrap gap-2">
              <Button asChild>
                <Link
                  to={`/organizations/${organizationId}/resources/${resourceId}/edit`}
                >
                  <Pencil aria-hidden="true" />
                  Редактировать
                </Link>
              </Button>
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button
                    type="button"
                    size="icon"
                    variant="ghost"
                    aria-label="Действия с ресурсом"
                  >
                    <Ellipsis aria-hidden="true" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  <DropdownMenuItem
                    className="text-status-down"
                    onSelect={() => setDeleteOpen(true)}
                  >
                    <Trash2 aria-hidden="true" className="size-icon" />
                    Удалить ресурс
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          )}
        </div>
      </header>

      <Tabs
        value={activeTab}
        onValueChange={(value) => {
          const next = new URLSearchParams(searchParams);
          if (value === "overview") next.delete("tab");
          else next.set("tab", value);
          setSearchParams(next, { replace: true });
        }}
      >
        <TabsList aria-label="Разделы ресурса">
          <TabsTrigger value="overview">Обзор</TabsTrigger>
          <TabsTrigger value="health">Здоровье</TabsTrigger>
          <TabsTrigger value="monitoring">Мониторинг</TabsTrigger>
        </TabsList>

        <TabsContent value="overview">
          <section aria-labelledby="overview-heading" className="space-y-6">
            <h2 id="overview-heading" className="sr-only">
              Обзор
            </h2>
            <dl className="grid gap-4 md:grid-cols-2">
              <DefinitionItem label="Имя">
                {resource.name ?? "—"}
              </DefinitionItem>
              <DefinitionItem label="Тип">
                {getResourceTypeLabel(resource.type)}
              </DefinitionItem>
              <DefinitionItem label="Статус жизненного цикла">
                <LifecycleStatus status={resource.status} />
              </DefinitionItem>
              <DefinitionItem label="Текущее здоровье">
                <HealthStatus status={resource.healthStatus} />
              </DefinitionItem>
            </dl>

            <section className="rounded-panel border border-border bg-surface p-4">
              <h3 className="mb-4 text-card-title">Конфигурация</h3>
              <ConfigDetails resource={resource} />
            </section>

            <section className="border-t border-border pt-4">
              <h3 className="text-card-title">Технические данные</h3>
              <dl className="mt-4 grid gap-4 sm:grid-cols-2">
                <DefinitionItem label="ID ресурса" technical>
                  {resource.id ?? "—"}
                </DefinitionItem>
                <DefinitionItem label="ID организации" technical>
                  {resource.organizationId ?? "—"}
                </DefinitionItem>
                <DefinitionItem label="Создано" technical>
                  {formatDateTime(resource.createdAt)}
                </DefinitionItem>
                <DefinitionItem label="Изменено" technical>
                  {formatDateTime(resource.updatedAt)}
                </DefinitionItem>
              </dl>
            </section>
          </section>
        </TabsContent>

        <TabsContent value="health">
          <section aria-labelledby="health-heading" className="space-y-6">
            <div className="flex flex-wrap items-end justify-between gap-4">
              <div>
                <h2 id="health-heading" className="text-section-title">
                  Здоровье
                </h2>
                <p className="mt-1 text-body text-foreground-muted">
                  Серверная сводка доступности и реальные переходы статуса.
                </p>
              </div>
              <label className="w-36">
                <span className="mb-1 block text-label">Период</span>
                <Select
                  value={period}
                  onValueChange={(value) => {
                    const nextPeriod = isAvailabilityPeriod(value)
                      ? value
                      : "24h";
                    const next = new URLSearchParams(searchParams);
                    if (nextPeriod === "24h") next.delete("period");
                    else next.set("period", nextPeriod);
                    setSearchParams(next, { replace: true });
                  }}
                >
                  <SelectTrigger
                    className="w-full"
                    aria-label="Период доступности"
                  >
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="24h">24 часа</SelectItem>
                    <SelectItem value="7d">7 дней</SelectItem>
                    <SelectItem value="30d">30 дней</SelectItem>
                  </SelectContent>
                </Select>
              </label>
            </div>
            {availabilityQuery.isPending ? (
              <div
                className="grid gap-3 md:grid-cols-3"
                aria-label="Загрузка доступности"
                aria-busy="true"
              >
                <Skeleton className="h-28" />
                <Skeleton className="h-28" />
                <Skeleton className="h-28" />
              </div>
            ) : availabilityQuery.isError ? (
              <div className="space-y-3">
                <Alert>
                  Не удалось загрузить доступность за выбранный период.
                </Alert>
                <Button
                  type="button"
                  onClick={() => void availabilityQuery.refetch()}
                >
                  Повторить
                </Button>
              </div>
            ) : (
              <AvailabilitySummary data={availabilityQuery.data} />
            )}

            <div className="space-y-4">
              <h3 className="text-card-title">Переходы здоровья</h3>
              <HealthEvents
                resourceId={resourceId}
                organizationId={organizationId}
                enabled={belongsToOrganization}
              />
            </div>
          </section>
        </TabsContent>

        <TabsContent value="monitoring">
          <section aria-labelledby="monitoring-heading" className="space-y-6">
            <div>
              <h2 id="monitoring-heading" className="text-section-title">
                Мониторинг
              </h2>
              <p className="mt-1 text-body text-foreground-muted">
                Текущее состояние проверок, ручной запуск и сохранённая история.
              </p>
            </div>
            <MonitoringSection
              organizationId={organizationId}
              resourceId={resourceId}
              enabled={belongsToOrganization}
            />
          </section>
        </TabsContent>
      </Tabs>

      {isManager && (
        <ResourceDeleteDialog
          resource={resource}
          organizationId={organizationId}
          open={deleteOpen}
          onOpenChange={setDeleteOpen}
        />
      )}
    </section>
  );
}

function ResourceUnavailable({
  listPath,
  title,
  description,
}: {
  listPath: string;
  title: string;
  description: string;
}) {
  return (
    <EmptyState
      icon={FolderSearch}
      title={title}
      description={description}
      action={
        <Button asChild>
          <Link to={listPath}>К списку ресурсов</Link>
        </Button>
      }
    />
  );
}
