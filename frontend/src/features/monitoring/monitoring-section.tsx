import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Activity,
  ArrowLeft,
  ArrowRight,
  History,
  LoaderCircle,
  Play,
} from "lucide-react";
import { Fragment, useId, useState } from "react";
import { ApiClientError, readableError } from "../../api/client/api-error";
import type {
  MonitorResponse,
  MonitoringResultResponse,
  ProbeExecutionResult,
} from "../../api/generated/model";
import { HealthStatus } from "../../components/health-status";
import { Alert } from "../../components/ui/alert";
import { Button } from "../../components/ui/button";
import { PageRefreshStatus } from "../../components/ui/page-refresh-status";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "../../components/ui/select";
import { Skeleton } from "../../components/ui/skeleton";
import {
  TechnicalDataGrid,
  TechnicalDisclosureTrigger,
} from "../../components/ui/technical-data-grid";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "../../components/ui/table";
import { cn } from "../../lib/cn";
import { scopedPreviousData } from "../../lib/scoped-previous-data";
import { formatDateTime } from "../resource/resource-details-format";
import {
  getMonitorHistory,
  getResourceMonitors,
  monitoringKeys,
  requestMonitorRun,
} from "./monitoring-api";
import {
  formatCompactMonitorTime,
  getMonitorTypeLabel,
  getProbeKeyMetric,
  getProbeResultLabel,
  presentProbeResult,
} from "./monitoring-presentation";

const historyPageSize = 10;
const pollingIntervalMs = 2_000;
const pollingLimitMs = 16_000;

interface PollState {
  baseline?: string | null;
  startedAt: number;
}

type PollStates = Record<number, PollState>;
type RunFeedback = "polling" | "timeout";

function controlledRunError(error: unknown) {
  if (!(error instanceof ApiClientError)) return readableError(error);
  if (error.code === "MONITOR_DISABLED")
    return "Монитор отключён. Измените настройки мониторинга перед запуском.";
  if (error.code === "MONITOR_INCOMPATIBLE")
    return "Монитор несовместим с текущей конфигурацией ресурса.";
  if (error.kind === "not-found")
    return "Монитор не найден или больше недоступен.";
  return readableError(error);
}

function CompactTime({
  value,
  emptyText,
}: {
  value?: string | null;
  emptyText: string;
}) {
  if (!value) return <span className="text-foreground-muted">{emptyText}</span>;

  const fullValue = formatDateTime(value);
  return (
    <time dateTime={value} title={fullValue} aria-label={fullValue}>
      {formatCompactMonitorTime(value)}
    </time>
  );
}

function ResultSummary({ result }: { result?: ProbeExecutionResult | null }) {
  const label = getProbeResultLabel(result);
  return (
    <span
      className={cn(
        "text-label",
        !result
          ? "text-foreground-muted"
          : result.success
            ? "text-status-up"
            : "text-status-down",
      )}
    >
      {label}
    </span>
  );
}

function ResultTechnicalData({
  monitor,
  result,
  includeMonitorMetadata = false,
}: {
  monitor: MonitorResponse;
  result?: ProbeExecutionResult | null;
  includeMonitorMetadata?: boolean;
}) {
  const presentation = result ? presentProbeResult(monitor.type, result) : null;
  return (
    <TechnicalDataGrid
      fields={[
        ...(result && "error" in result && result.error
          ? [{ label: "Причина", value: presentation?.summary ?? "—" }]
          : []),
        ...(presentation?.errorCode
          ? [{ label: "Код ошибки", value: presentation.errorCode }]
          : []),
        ...(presentation?.fields ?? []),
      ]}
      metadata={
        includeMonitorMetadata
          ? [
              { label: "ID монитора", value: String(monitor.id ?? "—") },
              { label: "Raw type", value: monitor.type ?? "—" },
            ]
          : []
      }
    />
  );
}

function hasResultDetails(
  type: MonitorResponse["type"],
  result?: ProbeExecutionResult | null,
) {
  if (!result) return false;
  if ("error" in result && result.error) return true;
  return presentProbeResult(type, result).fields.length > 0;
}

function RunMonitorControl({
  monitor,
  organizationId,
  resourceId,
  feedback,
  onAccepted,
}: {
  monitor: MonitorResponse;
  organizationId: number;
  resourceId: number;
  feedback?: RunFeedback;
  onAccepted: (monitor: MonitorResponse) => void;
}) {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: () => requestMonitorRun(monitor.id!),
    onSuccess: () => {
      onAccepted(monitor);
      return queryClient.invalidateQueries({
        queryKey: monitoringKeys.resource(organizationId, resourceId),
      });
    },
  });
  const waiting = mutation.isPending || feedback === "polling";
  const feedbackText =
    feedback === "timeout"
      ? "Запуск принят, результат пока не появился."
      : undefined;

  return (
    <div className="space-y-1">
      <Button
        type="button"
        disabled={waiting || monitor.id === undefined}
        onClick={() => {
          mutation.reset();
          mutation.mutate();
        }}
      >
        {waiting ? (
          <LoaderCircle aria-hidden="true" className="animate-spin" />
        ) : (
          <Play aria-hidden="true" />
        )}
        {mutation.isPending
          ? "Запрашиваем…"
          : feedback === "polling"
            ? "Запуск запрошен…"
            : "Запустить сейчас"}
      </Button>
      {feedbackText && (
        <p aria-live="polite" className="text-caption text-foreground-muted">
          {feedbackText}
        </p>
      )}
      {mutation.isError && <Alert>{controlledRunError(mutation.error)}</Alert>}
    </div>
  );
}

function MobileLabel({ children }: { children: string }) {
  return (
    <span className="mr-2 text-caption text-foreground-muted md:hidden">
      {children}:
    </span>
  );
}

function MonitorRow({
  monitor,
  organizationId,
  resourceId,
  selected,
  feedback,
  onSelect,
  onRunAccepted,
}: {
  monitor: MonitorResponse;
  organizationId: number;
  resourceId: number;
  selected: boolean;
  feedback?: RunFeedback;
  onSelect: () => void;
  onRunAccepted: (monitor: MonitorResponse) => void;
}) {
  const monitorLabel = getMonitorTypeLabel(monitor.type);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const detailsId = useId();
  return (
    <Fragment>
      <TableRow
        aria-selected={selected}
        className={cn(
          "block p-4 md:table-row md:p-0",
          selected && "bg-product-accent-soft",
        )}
      >
        <TableCell className="block h-auto px-0 py-1 md:table-cell md:h-row md:px-3 md:py-2">
          <MobileLabel>Проверка</MobileLabel>
          <span className="text-label text-foreground">{monitorLabel}</span>
        </TableCell>
        <TableCell className="block h-auto px-0 py-1 md:table-cell md:h-row md:px-3 md:py-2">
          <MobileLabel>Состояние</MobileLabel>
          <span className="inline-flex items-center gap-2">
            <HealthStatus status={monitor.healthStatus} />
            {feedback === "polling" && (
              <span className="text-caption text-foreground-muted">
                Запуск выполняется
              </span>
            )}
          </span>
        </TableCell>
        <TableCell className="block h-auto px-0 py-1 font-mono text-technical-sm md:table-cell md:h-row md:px-3 md:py-2">
          <MobileLabel>Последняя проверка</MobileLabel>
          <CompactTime
            value={monitor.lastCheckedAt}
            emptyText="Проверка ещё не выполнялась"
          />
        </TableCell>
        <TableCell className="hidden h-row px-3 py-2 font-mono text-technical-sm md:table-cell">
          <CompactTime value={monitor.nextRunAt} emptyText="Не запланирован" />
        </TableCell>
        <TableCell className="block h-auto px-0 py-1 md:table-cell md:h-row md:px-3 md:py-2">
          <MobileLabel>Последний результат</MobileLabel>
          <ResultSummary result={monitor.lastResult} />
          <TechnicalDisclosureTrigger
            aria-expanded={detailsOpen}
            aria-controls={detailsId}
            className="ml-2"
            onClick={() => setDetailsOpen((current) => !current)}
          >
            Технические детали
          </TechnicalDisclosureTrigger>
        </TableCell>
        <TableCell className="block h-auto px-0 pt-3 pb-0 md:table-cell md:h-row md:px-3 md:py-2">
          <div className="flex flex-wrap items-start gap-2 md:justify-end">
            <RunMonitorControl
              monitor={monitor}
              organizationId={organizationId}
              resourceId={resourceId}
              feedback={feedback}
              onAccepted={onRunAccepted}
            />
            <Button
              type="button"
              variant="ghost"
              aria-label={`Показать историю ${monitorLabel}`}
              disabled={monitor.id === undefined}
              onClick={onSelect}
            >
              <History aria-hidden="true" />
              История
            </Button>
          </div>
        </TableCell>
      </TableRow>
      {detailsOpen && (
        <TableRow id={detailsId} className="block md:table-row">
          <TableCell colSpan={6} className="block px-4 py-3 md:table-cell">
            <ResultTechnicalData
              monitor={monitor}
              result={monitor.lastResult}
              includeMonitorMetadata
            />
          </TableCell>
        </TableRow>
      )}
    </Fragment>
  );
}

function HistoryRow({
  item,
  monitor,
}: {
  item: MonitoringResultResponse;
  monitor: MonitorResponse;
}) {
  const [detailsOpen, setDetailsOpen] = useState(false);
  const detailsId = useId();
  const keyMetric = getProbeKeyMetric(monitor.type, item.result);
  const hasDetails = hasResultDetails(monitor.type, item.result);
  return (
    <Fragment>
      <TableRow>
        <TableCell className="font-mono text-technical-sm text-foreground-muted">
          <CompactTime value={item.checkedAt} emptyText="—" />
        </TableCell>
        <TableCell>
          <ResultSummary result={item.result} />
        </TableCell>
        <TableCell className="font-mono text-technical-sm text-foreground-muted">
          {keyMetric ?? "—"}
        </TableCell>
        <TableCell className="text-right">
          {hasDetails && (
            <TechnicalDisclosureTrigger
              aria-expanded={detailsOpen}
              aria-controls={detailsId}
              onClick={() => setDetailsOpen((current) => !current)}
            >
              {detailsOpen ? "Скрыть детали" : "Подробнее"}
            </TechnicalDisclosureTrigger>
          )}
        </TableCell>
      </TableRow>
      {detailsOpen && (
        <TableRow id={detailsId}>
          <TableCell colSpan={4} className="px-4 py-3">
            <ResultTechnicalData monitor={monitor} result={item.result} />
          </TableCell>
        </TableRow>
      )}
    </Fragment>
  );
}

function MonitorHistory({
  monitor,
  organizationId,
  resourceId,
}: {
  monitor: MonitorResponse;
  organizationId: number;
  resourceId: number;
}) {
  const [page, setPage] = useState(0);
  const [order, setOrder] = useState<"asc" | "desc">("desc");
  const monitorId = monitor.id ?? Number.NaN;
  const query = useQuery<
    Awaited<ReturnType<typeof getMonitorHistory>> & { requestedPage: number }
  >({
    queryKey: monitoringKeys.history(
      organizationId,
      resourceId,
      monitorId,
      page,
      historyPageSize,
      order,
    ),
    queryFn: async ({ signal }) => ({
      ...(await getMonitorHistory(
        monitorId,
        page,
        historyPageSize,
        order,
        signal,
      )),
      requestedPage: page,
    }),
    enabled: Number.isFinite(monitorId),
    placeholderData: scopedPreviousData<
      Awaited<ReturnType<typeof getMonitorHistory>> & { requestedPage: number }
    >(monitoringKeys.historyScope(organizationId, resourceId, monitorId)),
  });
  const displayPage = query.data?.requestedPage ?? page;
  const canGoNext =
    query.data?.total !== undefined
      ? (displayPage + 1) * historyPageSize < query.data.total
      : (query.data?.items.length ?? 0) === historyPageSize;
  const historyUnavailable =
    query.error instanceof ApiClientError &&
    query.error.code === "MONITOR_HISTORY_NOT_ENABLED";
  const rangeStart = displayPage * historyPageSize + 1;
  const rangeEnd = rangeStart + (query.data?.items.length ?? 0) - 1;

  return (
    <section
      aria-labelledby="monitor-history-heading"
      className="space-y-4 rounded-panel border border-border bg-surface p-4"
    >
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h3 id="monitor-history-heading" className="text-card-title">
            История {getMonitorTypeLabel(monitor.type)}
          </h3>
          <p className="mt-1 text-caption text-foreground-muted">
            Результаты выбранной проверки.
          </p>
          {query.isPlaceholderData && (
            <PageRefreshStatus label="Обновление истории…" />
          )}
        </div>
        {!query.isError && (
          <div className="w-44">
            <Select
              value={order}
              disabled={query.isPlaceholderData}
              onValueChange={(value) => {
                setOrder(value === "asc" ? "asc" : "desc");
                setPage(0);
              }}
            >
              <SelectTrigger className="w-full" aria-label="Сортировка истории">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="desc">Сначала новые</SelectItem>
                <SelectItem value="asc">Сначала старые</SelectItem>
              </SelectContent>
            </Select>
          </div>
        )}
      </div>

      {query.isPending ? (
        <div
          className="space-y-3"
          aria-label="Загрузка истории монитора"
          aria-busy="true"
        >
          <Skeleton className="h-11" />
          <Skeleton className="h-11" />
          <Skeleton className="h-11" />
        </div>
      ) : historyUnavailable ? (
        <div className="border-t border-border pt-4">
          <p className="text-card-title">История не включена</p>
          <p className="mt-1 text-body text-foreground-muted">
            Для этого монитора сохраняется только последнее состояние.
          </p>
        </div>
      ) : query.isError ? (
        <div className="space-y-3">
          <Alert>
            {query.error instanceof ApiClientError &&
            query.error.kind === "not-found"
              ? "Монитор не найден или больше недоступен."
              : "Не удалось загрузить историю этого монитора."}
          </Alert>
          <Button type="button" onClick={() => void query.refetch()}>
            Повторить
          </Button>
        </div>
      ) : query.data.items.length === 0 ? (
        <div className="py-4 text-center">
          <History
            aria-hidden="true"
            className="mx-auto size-icon-empty text-foreground-muted"
          />
          <p className="mt-3 text-card-title">Результатов пока нет</p>
          <p className="mt-1 text-body text-foreground-muted">
            История появится после выполнения монитора.
          </p>
        </div>
      ) : (
        <div
          aria-busy={query.isPlaceholderData}
          className="overflow-x-auto rounded-panel border border-border"
        >
          <Table>
            <caption className="sr-only">История результатов монитора</caption>
            <TableHeader>
              <TableRow>
                <TableHead>Время проверки</TableHead>
                <TableHead>Результат</TableHead>
                <TableHead>Время ответа</TableHead>
                <TableHead className="text-right">Детали</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {query.data.items.map((item, index) => (
                <HistoryRow
                  key={item.id ?? `${item.checkedAt}-${index}`}
                  item={item}
                  monitor={monitor}
                />
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      {!query.isPending && !query.isError && query.data.items.length > 0 && (
        <nav
          aria-label="Пагинация истории монитора"
          className="flex items-center justify-between gap-3"
        >
          <p className="text-caption text-foreground-muted">
            {query.data.total !== undefined
              ? `${rangeStart}–${rangeEnd} из ${query.data.total}`
              : `Страница ${displayPage + 1}`}
          </p>
          {(query.data.total === undefined ||
            query.data.total > historyPageSize) && (
            <div className="flex gap-2">
              <Button
                type="button"
                disabled={displayPage === 0 || query.isPlaceholderData}
                onClick={() => setPage((current) => current - 1)}
              >
                <ArrowLeft aria-hidden="true" />
                Назад
              </Button>
              <Button
                type="button"
                disabled={!canGoNext || query.isPlaceholderData}
                onClick={() => setPage((current) => current + 1)}
              >
                Далее
                <ArrowRight aria-hidden="true" />
              </Button>
            </div>
          )}
        </nav>
      )}
    </section>
  );
}

export function MonitoringSection({
  organizationId,
  resourceId,
  enabled,
}: {
  organizationId: number;
  resourceId: number;
  enabled: boolean;
}) {
  const [selectedMonitorId, setSelectedMonitorId] = useState<number>();
  const [pollStates, setPollStates] = useState<PollStates>({});
  const monitorsQuery = useQuery({
    queryKey: monitoringKeys.resource(organizationId, resourceId),
    queryFn: ({ signal }) => getResourceMonitors(resourceId, signal),
    enabled,
    refetchInterval: (query) => {
      const monitors = query.state.data ?? [];
      const shouldPoll = Object.entries(pollStates).some(
        ([monitorId, pollState]) => {
          const monitor = monitors.find(
            (candidate) => candidate.id === Number(monitorId),
          );
          return (
            (!monitor?.lastCheckedAt ||
              monitor.lastCheckedAt === pollState.baseline) &&
            Date.now() - pollState.startedAt < pollingLimitMs
          );
        },
      );
      return shouldPoll ? pollingIntervalMs : false;
    },
  });

  if (monitorsQuery.isPending)
    return (
      <div
        className="space-y-2"
        aria-label="Загрузка мониторов"
        aria-busy="true"
      >
        <Skeleton className="h-11" />
        <Skeleton className="h-11" />
        <Skeleton className="h-11" />
      </div>
    );
  if (monitorsQuery.isError)
    return (
      <div className="space-y-3">
        <Alert>
          {monitorsQuery.error instanceof ApiClientError &&
          monitorsQuery.error.kind === "not-found"
            ? "Ресурс или его мониторы не найдены."
            : "Не удалось загрузить мониторы ресурса."}
        </Alert>
        <Button type="button" onClick={() => void monitorsQuery.refetch()}>
          Повторить
        </Button>
      </div>
    );
  if (monitorsQuery.data.length === 0)
    return (
      <div className="rounded-panel border border-border bg-surface p-6 text-center">
        <Activity
          aria-hidden="true"
          className="mx-auto size-icon-empty text-foreground-muted"
        />
        <p className="mt-4 text-card-title">Мониторов пока нет</p>
        <p className="mt-2 text-body text-foreground-muted">
          Для этого ресурса ещё не настроены совместимые проверки.
        </p>
      </div>
    );

  const selectedMonitor = monitorsQuery.data.find(
    (monitor) => monitor.id === selectedMonitorId,
  );

  function feedbackFor(monitor: MonitorResponse): RunFeedback | undefined {
    if (monitor.id === undefined) return undefined;
    const pollState = pollStates[monitor.id];
    if (!pollState) return undefined;
    if (monitor.lastCheckedAt && monitor.lastCheckedAt !== pollState.baseline)
      return undefined;
    if (monitorsQuery.dataUpdatedAt - pollState.startedAt >= pollingLimitMs)
      return "timeout";
    return "polling";
  }

  return (
    <div className="space-y-6">
      {monitorsQuery.isFetching && (
        <p className="flex items-center gap-2 text-caption text-foreground-muted">
          <LoaderCircle aria-hidden="true" className="size-icon animate-spin" />
          Обновление данных мониторов…
        </p>
      )}
      <div className="overflow-hidden rounded-panel border border-border bg-surface">
        <Table className="block md:table">
          <caption className="sr-only">Мониторы ресурса</caption>
          <TableHeader className="hidden md:table-header-group">
            <TableRow>
              <TableHead>Проверка</TableHead>
              <TableHead>Состояние</TableHead>
              <TableHead>Последняя проверка</TableHead>
              <TableHead>Следующий запуск</TableHead>
              <TableHead>Последний результат</TableHead>
              <TableHead className="text-right">Действия</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody className="block divide-y divide-border md:table-row-group md:divide-y-0">
            {monitorsQuery.data.map((monitor, index) => (
              <MonitorRow
                key={monitor.id ?? `${monitor.type}-${index}`}
                monitor={monitor}
                organizationId={organizationId}
                resourceId={resourceId}
                selected={monitor.id === selectedMonitorId}
                feedback={feedbackFor(monitor)}
                onSelect={() => setSelectedMonitorId(monitor.id)}
                onRunAccepted={(acceptedMonitor) => {
                  const monitorId = acceptedMonitor.id;
                  if (monitorId === undefined) return;
                  setPollStates((current) => ({
                    ...current,
                    [monitorId]: {
                      baseline: acceptedMonitor.lastCheckedAt,
                      startedAt: Date.now(),
                    },
                  }));
                }}
              />
            ))}
          </TableBody>
        </Table>
      </div>
      {selectedMonitor && (
        <MonitorHistory
          key={selectedMonitor.id}
          monitor={selectedMonitor}
          organizationId={organizationId}
          resourceId={resourceId}
        />
      )}
    </div>
  );
}
