import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Controller, useForm, useWatch } from "react-hook-form";
import { useEffect, useState } from "react";
import { z } from "zod";
import { ApiClientError, readableError } from "../../api/client/api-error";
import type {
  ProbeSettingsRequest,
  ProbeSettingsResponse,
} from "../../api/generated/model";
import { Alert } from "../../components/ui/alert";
import { Button } from "../../components/ui/button";
import { Input } from "../../components/ui/input";
import { Label } from "../../components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "../../components/ui/select";
import { Skeleton } from "../../components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "../../components/ui/table";
import { monitoringKeys } from "./monitoring-api";
import { getMonitorTypeLabel } from "./monitoring-presentation";
import {
  getSettings,
  resetSettings,
  saveSettings,
  settingsKeys,
  type ProbeType,
  type SettingsScope,
} from "./settings-api";

const schema = z
  .object({
    enabled: z.boolean(),
    intervalSeconds: z.number().int().min(1, "Укажите положительный интервал"),
    failureThreshold: z.number().int().min(1).max(10),
    recoveryThreshold: z.number().int().min(1).max(10),
    storageMode: z.enum(["LATEST_ONLY", "HISTORY"]),
    retentionDays: z.number().int().min(1).max(365).nullish(),
    timeoutMs: z.number().int().min(1).max(60000).nullish(),
  })
  .superRefine((values, context) => {
    if (values.storageMode === "HISTORY" && values.retentionDays == null)
      context.addIssue({
        code: "custom",
        path: ["retentionDays"],
        message: "Укажите срок хранения от 1 до 365 дней",
      });
  });

const fieldLabels: Record<keyof ProbeSettingsRequest, string> = {
  enabled: "Проверка включена",
  intervalSeconds: "Интервал, сек",
  failureThreshold: "Порог ошибок",
  recoveryThreshold: "Порог восстановления",
  storageMode: "Хранение",
  retentionDays: "Хранить историю, дней",
  timeoutMs: "Таймаут, мс",
};

function sourceLabel(
  source: ProbeSettingsResponse["source"],
  scope: SettingsScope,
) {
  if (source === "RESOURCE") return "Переопределение ресурса";
  if (source === "ORGANIZATION")
    return scope === "organization"
      ? "Переопределение организации"
      : "Наследуется от организации";
  if (source === "APPLICATION") return "Наследуется от приложения";
  return "Источник неизвестен";
}

function numeric(value?: number | null, unit = "") {
  return value == null ? "—" : `${value}${unit}`;
}

function canEdit(row: ProbeSettingsResponse, scope: SettingsScope) {
  return scope === "organization" || row.supported === true;
}

function SettingsEditor({
  row,
  onSave,
  onCancel,
  pending,
  serverError,
}: {
  row: ProbeSettingsResponse;
  onSave: (values: ProbeSettingsRequest) => Promise<void>;
  onCancel: () => void;
  pending: boolean;
  serverError: unknown;
}) {
  const effective = row.effective;
  const type = row.probeType;
  const form = useForm<ProbeSettingsRequest>({
    resolver: zodResolver(
      schema.superRefine((values, context) => {
        if (type !== "DNS_CHECK" && values.timeoutMs == null)
          context.addIssue({
            code: "custom",
            path: ["timeoutMs"],
            message: "Укажите таймаут от 1 до 60000 мс",
          });
      }),
    ),
    defaultValues: {
      enabled: effective?.enabled ?? false,
      intervalSeconds: effective?.intervalSeconds,
      failureThreshold: effective?.failureThreshold,
      recoveryThreshold: effective?.recoveryThreshold,
      storageMode: effective?.storageMode,
      retentionDays: effective?.retentionDays ?? null,
      timeoutMs: effective?.timeoutMs ?? null,
    },
  });
  const storageMode = useWatch({ control: form.control, name: "storageMode" });
  const errors = form.formState.errors;
  useEffect(() => {
    if (!(serverError instanceof ApiClientError)) return;
    for (const error of serverError.details?.errors ?? []) {
      if (error.field && error.field in fieldLabels)
        form.setError(error.field as keyof ProbeSettingsRequest, {
          type: "server",
          message: error.message,
        });
    }
  }, [serverError, form]);
  const close = () => {
    if (
      form.formState.isDirty &&
      !window.confirm("Закрыть форму и потерять изменения?")
    )
      return;
    onCancel();
  };
  const submit = form.handleSubmit(async (values) => {
    const request: ProbeSettingsRequest = {
      enabled: values.enabled,
      intervalSeconds: values.intervalSeconds,
      failureThreshold: values.failureThreshold,
      recoveryThreshold: values.recoveryThreshold,
      storageMode: values.storageMode,
      retentionDays:
        values.storageMode === "HISTORY" ? values.retentionDays : null,
      timeoutMs: type === "DNS_CHECK" ? null : values.timeoutMs,
    };
    await onSave(request);
  });
  const numberField = (
    name:
      | "intervalSeconds"
      | "failureThreshold"
      | "recoveryThreshold"
      | "retentionDays"
      | "timeoutMs",
  ) => (
    <div className="space-y-1" key={name}>
      <Label htmlFor={`setting-${name}`}>{fieldLabels[name]}</Label>
      <Input
        id={`setting-${name}`}
        type="number"
        min={name === "intervalSeconds" ? 1 : 1}
        max={
          name === "failureThreshold" || name === "recoveryThreshold"
            ? 10
            : name === "retentionDays"
              ? 365
              : name === "timeoutMs"
                ? 60000
                : undefined
        }
        aria-invalid={!!errors[name]}
        aria-describedby={errors[name] ? `setting-${name}-error` : undefined}
        {...form.register(name, {
          setValueAs: (value: string) => (value === "" ? null : Number(value)),
        })}
      />
      {errors[name] && (
        <p
          id={`setting-${name}-error`}
          className="text-caption text-status-down"
        >
          {String(errors[name].message ?? "Введите допустимое значение")}
        </p>
      )}
    </div>
  );

  return (
    <form
      onSubmit={(event) => void submit(event)}
      className="space-y-4 border-t border-border bg-surface p-4"
      aria-label={`Настройки ${getMonitorTypeLabel(type)}`}
    >
      <h3 className="text-card-title">
        {type && getMonitorTypeLabel(type)} · переопределение целиком
      </h3>
      <p className="text-body text-foreground-muted">
        Поля сохраняются вместе. Минимальный интервал определяется сервером
        (обычно 30 сек).
      </p>
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-1">
          <Label htmlFor="setting-enabled">{fieldLabels.enabled}</Label>
          <Controller
            name="enabled"
            control={form.control}
            render={({ field }) => (
              <Select
                value={field.value ? "true" : "false"}
                onValueChange={(value) => field.onChange(value === "true")}
              >
                <SelectTrigger id="setting-enabled">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="true">Да</SelectItem>
                  <SelectItem value="false">Нет</SelectItem>
                </SelectContent>
              </Select>
            )}
          />
        </div>
        {numberField("intervalSeconds")}
        {numberField("failureThreshold")}
        {numberField("recoveryThreshold")}
        <div className="space-y-1">
          <Label htmlFor="setting-storageMode">{fieldLabels.storageMode}</Label>
          <Controller
            name="storageMode"
            control={form.control}
            render={({ field }) => (
              <Select
                value={field.value}
                onValueChange={(value) => field.onChange(value)}
              >
                <SelectTrigger id="setting-storageMode">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="LATEST_ONLY">Только последнее</SelectItem>
                  <SelectItem value="HISTORY">История</SelectItem>
                </SelectContent>
              </Select>
            )}
          />
        </div>
        {storageMode === "HISTORY" && numberField("retentionDays")}
        {type !== "DNS_CHECK" && numberField("timeoutMs")}
      </div>
      {serverError != null && <Alert>{readableError(serverError)}</Alert>}
      <div className="flex gap-2">
        <Button type="submit" disabled={pending}>
          {pending ? "Сохранение…" : "Сохранить"}
        </Button>
        <Button
          type="button"
          variant="secondary"
          disabled={pending}
          onClick={close}
        >
          Отмена
        </Button>
      </div>
    </form>
  );
}

export function SettingsSection({
  scope,
  organizationId,
  resourceId,
  isManager,
  enabled = true,
}: {
  scope: SettingsScope;
  organizationId: number;
  resourceId?: number;
  isManager: boolean;
  enabled?: boolean;
}) {
  const id = scope === "organization" ? organizationId : resourceId;
  const queryClient = useQueryClient();
  const queryKey =
    scope === "organization"
      ? settingsKeys.organization(organizationId)
      : settingsKeys.resource(organizationId, resourceId ?? Number.NaN);
  const query = useQuery({
    queryKey,
    queryFn: ({ signal }) => getSettings(scope, id!, signal),
    enabled: enabled && Number.isFinite(id),
    retry: (count, error) =>
      !(
        error instanceof ApiClientError &&
        [403, 404].includes(error.status ?? 0)
      ) && count < 2,
  });
  const [editing, setEditing] = useState<ProbeType | null>(null);
  const [resetError, setResetError] = useState<unknown>(null);
  const [notice, setNotice] = useState("");
  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey });
    if (scope === "resource")
      await queryClient.invalidateQueries({
        queryKey: monitoringKeys.resource(organizationId, resourceId!),
      });
    else
      await queryClient.invalidateQueries({
        queryKey: ["monitoring", organizationId],
      });
  };
  const save = useMutation({
    mutationFn: ({
      type,
      values,
    }: {
      type: ProbeType;
      values: ProbeSettingsRequest;
    }) => saveSettings(scope, id!, type, values),
    onSuccess: invalidate,
  });
  const reset = useMutation({
    mutationFn: (type: ProbeType) => resetSettings(scope, id!, type),
    onSuccess: invalidate,
  });

  if (query.isPending)
    return (
      <div
        aria-label="Загрузка настроек мониторинга"
        aria-busy="true"
        className="space-y-2"
      >
        <Skeleton className="h-10" />
        <Skeleton className="h-10" />
        <Skeleton className="h-10" />
      </div>
    );
  if (query.isError)
    return (
      <div className="space-y-3">
        <Alert>
          {query.error instanceof ApiClientError &&
          query.error.kind === "forbidden"
            ? "Недостаточно прав для просмотра настроек."
            : query.error instanceof ApiClientError &&
                query.error.kind === "not-found"
              ? "Настройки не найдены или недоступны."
              : readableError(query.error)}
        </Alert>
        <Button
          type="button"
          variant="secondary"
          onClick={() => void query.refetch()}
        >
          Повторить
        </Button>
      </div>
    );

  return (
    <div className="space-y-3">
      {!isManager && (
        <p className="text-body text-foreground-muted">
          Настройки доступны только для просмотра. Изменять их могут владелец и
          администратор.
        </p>
      )}
      {notice && (
        <p role="status" className="text-body text-foreground-muted">
          {notice}
        </p>
      )}
      {resetError != null && <Alert>{readableError(resetError)}</Alert>}
      <div className="overflow-x-auto rounded-panel border border-border bg-surface">
        <Table>
          <caption className="sr-only">
            Эффективные настройки проверок и источник значений
          </caption>
          <TableHeader>
            <TableRow>
              <TableHead>Проверка</TableHead>
              <TableHead>Включена</TableHead>
              <TableHead>Интервал</TableHead>
              <TableHead>Хранение</TableHead>
              <TableHead>Источник</TableHead>
              <TableHead>Переопределение</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {query.data.map((row) => {
              const type = row.probeType;
              if (!type) return null;
              const own =
                scope === "organization"
                  ? row.source === "ORGANIZATION"
                  : row.resourceOverride === true;
              return (
                <TableRow key={type}>
                  <TableCell className="font-medium">
                    {getMonitorTypeLabel(type)}
                    <span className="ml-2 text-caption text-foreground-muted">
                      {type}
                    </span>
                    <details className="mt-1 font-normal text-caption text-foreground-muted">
                      <summary className="w-fit cursor-pointer text-product-accent">
                        Все значения
                      </summary>
                      <dl className="mt-2 grid min-w-48 grid-cols-2 gap-x-3 gap-y-1">
                        <dt>Порог ошибок</dt>
                        <dd className="font-mono">
                          {numeric(row.effective?.failureThreshold)}
                        </dd>
                        <dt>Порог восстановления</dt>
                        <dd className="font-mono">
                          {numeric(row.effective?.recoveryThreshold)}
                        </dd>
                        <dt>Хранение, дней</dt>
                        <dd className="font-mono">
                          {numeric(row.effective?.retentionDays)}
                        </dd>
                        {type !== "DNS_CHECK" && (
                          <>
                            <dt>Таймаут, мс</dt>
                            <dd className="font-mono">
                              {numeric(row.effective?.timeoutMs)}
                            </dd>
                          </>
                        )}
                      </dl>
                    </details>
                  </TableCell>
                  <TableCell>
                    {scope === "resource" && row.supported === false
                      ? "Не поддерживается"
                      : row.effective?.enabled === undefined
                        ? "—"
                        : row.effective.enabled
                          ? "Да"
                          : "Нет"}
                  </TableCell>
                  <TableCell className="font-mono tabular-nums">
                    {numeric(row.effective?.intervalSeconds, " сек")}
                  </TableCell>
                  <TableCell>
                    {row.effective?.storageMode === "HISTORY"
                      ? `История · ${numeric(row.effective.retentionDays, " дн")}`
                      : row.effective?.storageMode === "LATEST_ONLY"
                        ? "Только последнее"
                        : "—"}
                  </TableCell>
                  <TableCell className="text-foreground-muted">
                    {sourceLabel(row.source, scope)}
                  </TableCell>
                  <TableCell>
                    <div className="flex flex-wrap gap-1">
                      {isManager && row.effective && canEdit(row, scope) && (
                        <Button
                          type="button"
                          variant="ghost"
                          disabled={reset.isPending || save.isPending}
                          onClick={() => {
                            setEditing(type);
                            save.reset();
                            setResetError(null);
                            setNotice("");
                          }}
                        >
                          {own ? "Изменить" : "Переопределить"}
                        </Button>
                      )}
                      {isManager && own && (
                        <Button
                          type="button"
                          variant="ghost"
                          disabled={reset.isPending || save.isPending}
                          onClick={async () => {
                            setResetError(null);
                            setNotice("");
                            try {
                              await reset.mutateAsync(type);
                              setEditing(null);
                              setNotice(
                                scope === "organization"
                                  ? `${getMonitorTypeLabel(type)}: возвращены значения приложения.`
                                  : `${getMonitorTypeLabel(type)}: восстановлено наследование.`,
                              );
                            } catch (error) {
                              setResetError(error);
                            }
                          }}
                        >
                          {scope === "organization"
                            ? "Вернуть значения приложения"
                            : "Вернуть наследование"}
                        </Button>
                      )}
                      {!isManager && (
                        <span className="text-caption text-foreground-muted">
                          Только просмотр
                        </span>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </div>
      {editing &&
        query.data.find(
          (row) => row.probeType === editing && row.effective,
        ) && (
          <SettingsEditor
            key={editing}
            row={query.data.find((row) => row.probeType === editing)!}
            pending={save.isPending}
            serverError={save.error}
            onCancel={() => setEditing(null)}
            onSave={async (values) => {
              try {
                await save.mutateAsync({ type: editing, values });
                setEditing(null);
                setNotice(
                  `${getMonitorTypeLabel(editing)}: настройки сохранены.`,
                );
              } catch {
                /* Mutation error remains in the form with the entered values. */
              }
            }}
          />
        )}
    </div>
  );
}
