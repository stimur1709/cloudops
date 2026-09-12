import { zodResolver } from "@hookform/resolvers/zod";
import * as React from "react";
import {
  Controller,
  useForm,
  useWatch,
  type UseFormSetError,
} from "react-hook-form";
import { Link } from "react-router-dom";
import { z } from "zod";
import { ApiClientError, readableError } from "../../api/client/api-error";
import type {
  CreateResourceRequest,
  ResourceResponse,
  UpdateResourceRequest,
} from "../../api/generated/model";
import {
  resourceStatusLabels,
  resourceTypeLabels,
} from "../../components/resource-labels";
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
import { lifecycleStatuses, resourceTypes } from "./resource-api";
import { buildResourceRequest } from "./resource-form-serialization";

const optionalPort = z
  .number()
  .int()
  .min(1, "Введите значение от 1 до 65535")
  .max(65535, "Введите значение от 1 до 65535")
  .optional();

const formSchema = z
  .object({
    name: z
      .string()
      .trim()
      .min(1, "Укажите имя ресурса")
      .max(100, "Имя должно быть не длиннее 100 символов"),
    type: z.enum(resourceTypes),
    status: z.enum(lifecycleStatuses),
    config: z.object({
      host: z.string().optional(),
      port: optionalPort,
      sshPort: optionalPort,
      managementPort: optionalPort,
      database: z.string().optional(),
      url: z.string().optional(),
      expectedStatus: z
        .number()
        .int()
        .min(100, "Введите HTTP-код от 100 до 599")
        .max(599, "Введите HTTP-код от 100 до 599")
        .optional(),
    }),
  })
  .superRefine((value, context) => {
    if (
      ["SERVER", "NETWORK_DEVICE", "DATABASE"].includes(value.type) &&
      !value.config.host?.trim()
    ) {
      context.addIssue({
        code: "custom",
        path: ["config", "host"],
        message: "Укажите хост",
      });
    }
    if (value.type === "DATABASE") {
      if (!value.config.port)
        context.addIssue({
          code: "custom",
          path: ["config", "port"],
          message: "Укажите порт",
        });
      if (!value.config.database?.trim())
        context.addIssue({
          code: "custom",
          path: ["config", "database"],
          message: "Укажите базу данных",
        });
    }
    if (value.type === "SERVICE") {
      const url = value.config.url?.trim();
      if (!url) {
        context.addIssue({
          code: "custom",
          path: ["config", "url"],
          message: "Укажите URL",
        });
      } else {
        try {
          const parsed = new URL(url);
          if (parsed.protocol !== "http:" && parsed.protocol !== "https:")
            throw new Error();
        } catch {
          context.addIssue({
            code: "custom",
            path: ["config", "url"],
            message: "Введите корректный HTTP(S) URL",
          });
        }
      }
    }
  });

export type ResourceFormValues = z.infer<typeof formSchema>;
type ConfigValues = ResourceFormValues["config"];
type ServerFieldName =
  "name" | "type" | "status" | `config.${keyof ConfigValues}`;

const emptyConfig: ConfigValues = {};

function configDefaults(type: ResourceFormValues["type"]): ConfigValues {
  if (type === "SERVER") return { host: "", sshPort: 22 };
  if (type === "NETWORK_DEVICE") return { host: "" };
  if (type === "DATABASE") return { host: "", database: "" };
  if (type === "SERVICE") return { url: "", expectedStatus: 200 };
  return emptyConfig;
}

function readConfig(resource?: ResourceResponse): ConfigValues {
  const config = resource?.config;
  if (!config || typeof config !== "object")
    return configDefaults(resource?.type ?? "SERVER");
  return {
    host:
      "host" in config && typeof config.host === "string"
        ? config.host
        : undefined,
    port:
      "port" in config && typeof config.port === "number"
        ? config.port
        : undefined,
    sshPort:
      "sshPort" in config && typeof config.sshPort === "number"
        ? config.sshPort
        : undefined,
    managementPort:
      "managementPort" in config && typeof config.managementPort === "number"
        ? config.managementPort
        : undefined,
    database:
      "database" in config && typeof config.database === "string"
        ? config.database
        : undefined,
    url:
      "url" in config && typeof config.url === "string"
        ? config.url
        : undefined,
    expectedStatus:
      "expectedStatus" in config && typeof config.expectedStatus === "number"
        ? config.expectedStatus
        : undefined,
  };
}

function resourceFormDefaults(resource?: ResourceResponse): ResourceFormValues {
  return {
    name: resource?.name ?? "",
    type: resource?.type ?? "SERVER",
    status: resource?.status ?? "ACTIVE",
    config: readConfig(resource),
  };
}

function applyServerErrors(
  error: ApiClientError,
  setError: UseFormSetError<ResourceFormValues>,
) {
  let applied = false;
  for (const fieldError of error.details?.errors ?? []) {
    const field = fieldError.field;
    if (
      field === "name" ||
      field === "type" ||
      field === "status" ||
      field?.startsWith("config.")
    ) {
      setError(
        field as ServerFieldName,
        { type: "server", message: fieldError.message },
        { shouldFocus: !applied },
      );
      applied = true;
    }
  }
  if (error.kind === "conflict" && error.code === "RESOURCE_NAME_CONFLICT") {
    setError(
      "name",
      {
        type: "server",
        message: "Ресурс с таким именем уже существует в организации.",
      },
      { shouldFocus: true },
    );
    applied = true;
  }
  return applied;
}

function Field({
  id,
  label,
  error,
  children,
}: {
  id: string;
  label: string;
  error?: string;
  children: React.ReactNode;
}) {
  const errorId = `${id}-error`;
  return (
    <div className="space-y-1">
      <Label htmlFor={id}>{label}</Label>
      {children}
      {error && (
        <p id={errorId} className="text-caption text-status-down">
          {error}
        </p>
      )}
    </div>
  );
}

function ConfigFields({
  type,
  register,
  errors,
}: Pick<ReturnType<typeof useForm<ResourceFormValues>>, "register"> & {
  type: ResourceFormValues["type"];
  errors: ReturnType<typeof useForm<ResourceFormValues>>["formState"]["errors"];
}) {
  const numberOptions = {
    setValueAs: (value: string) => (value === "" ? undefined : Number(value)),
  };
  const input = (name: keyof ConfigValues, label: string, type = "text") => {
    const id = `config-${name}`;
    const error = errors.config?.[name]?.message;
    return (
      <Field
        key={name}
        id={id}
        label={label}
        error={typeof error === "string" ? error : undefined}
      >
        <Input
          id={id}
          type={type}
          aria-invalid={Boolean(error)}
          aria-describedby={error ? `${id}-error` : undefined}
          {...register(
            `config.${name}`,
            type === "number" ? numberOptions : undefined,
          )}
        />
      </Field>
    );
  };
  if (type === "SERVER")
    return (
      <>
        {input("host", "Хост")}
        {input("port", "Порт (необязательно)", "number")}
        {input("sshPort", "SSH-порт (необязательно)", "number")}
      </>
    );
  if (type === "NETWORK_DEVICE")
    return (
      <>
        {input("host", "Хост")}
        {input("managementPort", "Порт управления (необязательно)", "number")}
      </>
    );
  if (type === "DATABASE")
    return (
      <>
        {input("host", "Хост")}
        {input("port", "Порт", "number")}
        {input("database", "База данных")}
      </>
    );
  if (type === "SERVICE")
    return (
      <>
        {input("url", "URL")}
        {input(
          "expectedStatus",
          "Ожидаемый HTTP-код (необязательно)",
          "number",
        )}
      </>
    );
  return (
    <p className="text-body text-foreground-muted">
      Для типа «Другое» дополнительных параметров нет.
    </p>
  );
}

export function ResourceForm({
  organizationId,
  resource,
  onSubmit,
}: {
  organizationId: number;
  resource?: ResourceResponse;
  onSubmit: (
    request: CreateResourceRequest | UpdateResourceRequest,
  ) => Promise<void>;
}) {
  const form = useForm<ResourceFormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: resourceFormDefaults(resource),
    mode: "onBlur",
  });
  const type = useWatch({ control: form.control, name: "type" });
  const [submitError, setSubmitError] = React.useState<string>();
  const listPath = `/organizations/${organizationId}/resources`;

  async function submit(values: ResourceFormValues) {
    setSubmitError(undefined);
    try {
      await onSubmit(buildResourceRequest(values, organizationId));
    } catch (error) {
      if (error instanceof ApiClientError) {
        const hasFieldErrors = applyServerErrors(error, form.setError);
        if (error.kind === "forbidden")
          setSubmitError(
            "Недостаточно прав для изменения ресурсов этой организации.",
          );
        else if (error.kind === "not-found")
          setSubmitError("Ресурс не найден или больше недоступен.");
        else if (!hasFieldErrors) setSubmitError(readableError(error));
      } else setSubmitError(readableError(error));
    }
  }

  return (
    <form
      className="max-w-[var(--layout-form-max)] space-y-6"
      noValidate
      onSubmit={form.handleSubmit(submit)}
    >
      {submitError && <Alert>{submitError}</Alert>}
      <div className="space-y-4 rounded-panel border border-border bg-surface p-4">
        <Field
          id="resource-name"
          label="Имя"
          error={form.formState.errors.name?.message}
        >
          <Input
            id="resource-name"
            autoFocus
            aria-invalid={Boolean(form.formState.errors.name)}
            aria-describedby={
              form.formState.errors.name ? "resource-name-error" : undefined
            }
            {...form.register("name")}
          />
        </Field>
        <Field
          id="resource-type"
          label="Тип"
          error={form.formState.errors.type?.message}
        >
          <Controller
            control={form.control}
            name="type"
            render={({ field }) => (
              <Select
                value={field.value}
                onValueChange={(value: ResourceFormValues["type"]) => {
                  field.onChange(value);
                  form.setValue("config", configDefaults(value), {
                    shouldDirty: true,
                  });
                  form.clearErrors("config");
                }}
              >
                <SelectTrigger
                  id="resource-type"
                  className="w-full"
                  aria-invalid={Boolean(form.formState.errors.type)}
                  aria-describedby={
                    form.formState.errors.type
                      ? "resource-type-error"
                      : undefined
                  }
                >
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {resourceTypes.map((value) => (
                    <SelectItem key={value} value={value}>
                      {resourceTypeLabels[value]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
        </Field>
        <Field
          id="resource-status"
          label="Статус"
          error={form.formState.errors.status?.message}
        >
          <Controller
            control={form.control}
            name="status"
            render={({ field }) => (
              <Select value={field.value} onValueChange={field.onChange}>
                <SelectTrigger
                  id="resource-status"
                  className="w-full"
                  aria-invalid={Boolean(form.formState.errors.status)}
                  aria-describedby={
                    form.formState.errors.status
                      ? "resource-status-error"
                      : undefined
                  }
                >
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {lifecycleStatuses.map((value) => (
                    <SelectItem key={value} value={value}>
                      {resourceStatusLabels[value]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
        </Field>
      </div>
      <fieldset className="space-y-4 rounded-panel border border-border bg-surface p-4">
        <legend className="px-1 text-card-title">Параметры подключения</legend>
        <ConfigFields
          type={type}
          register={form.register}
          errors={form.formState.errors}
        />
      </fieldset>
      <div className="flex flex-wrap justify-end gap-2">
        <Button asChild variant="secondary">
          <Link to={listPath}>Отмена</Link>
        </Button>
        <Button
          type="submit"
          variant="primary"
          disabled={form.formState.isSubmitting}
        >
          {form.formState.isSubmitting
            ? "Сохранение…"
            : resource
              ? "Сохранить"
              : "Создать ресурс"}
        </Button>
      </div>
    </form>
  );
}
