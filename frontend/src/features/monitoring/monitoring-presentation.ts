import type {
  DnsCheckResult,
  HttpCheckResult,
  MonitorResponseType,
  PingResult,
  PortCheckResult,
  ProbeExecutionResult,
  SshCheckResult,
  TlsCheckResult,
} from "../../api/generated/model";

export interface ProbeResultField {
  label: string;
  value: string;
}

export interface ProbeResultPresentation {
  outcome: "success" | "failure";
  summary: string;
  fields: ProbeResultField[];
  errorCode?: string;
}

const monitorTypeLabels: Record<MonitorResponseType, string> = {
  HTTP_CHECK: "HTTP",
  PORT_CHECK: "Port",
  DNS_CHECK: "DNS",
  PING: "Ping",
  TLS_CHECK: "TLS",
  SSH_CHECK: "SSH",
};

export function getMonitorTypeLabel(type?: MonitorResponseType) {
  return type ? monitorTypeLabels[type] : "Неизвестная проверка";
}

export function formatCompactMonitorTime(
  value?: string | null,
  now = new Date(),
) {
  if (!value) return undefined;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return undefined;
  const sameDay =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate();
  const time = new Intl.DateTimeFormat("ru-RU", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).format(date);
  if (sameDay) return `Сегодня, ${time}`;
  return new Intl.DateTimeFormat("ru-RU", {
    day: "numeric",
    month: "short",
    year: date.getFullYear() === now.getFullYear() ? undefined : "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).format(date);
}

function present(value: unknown): string | undefined {
  if (value === null || value === undefined || value === "") return undefined;
  if (Array.isArray(value)) return value.length ? value.join(", ") : undefined;
  if (typeof value === "boolean") return value ? "Да" : "Нет";
  return String(value);
}

function latency(value?: number) {
  if (value === undefined || !Number.isFinite(value)) return undefined;
  if (value >= 1000)
    return `${new Intl.NumberFormat("ru-RU", { maximumFractionDigits: 2 }).format(value / 1000)} с`;
  return `${new Intl.NumberFormat("ru-RU", { maximumFractionDigits: value < 1 ? 2 : 0 }).format(value)} мс`;
}

function fields(entries: Array<[string, unknown]>): ProbeResultField[] {
  return entries.flatMap(([label, value]) => {
    const formatted = present(value);
    return formatted ? [{ label, value: formatted }] : [];
  });
}

function dataFields(
  type: MonitorResponseType | undefined,
  data: unknown,
): ProbeResultField[] {
  switch (type) {
    case "HTTP_CHECK": {
      const result = data as HttpCheckResult;
      return fields([
        ["URL", result.url],
        ["HTTP-статус", result.statusCode],
        ["Ожидаемый статус", result.expectedStatus],
        ["Совпадает с ожидаемым", result.matchedExpectedStatus],
        ["Время ответа", latency(result.responseTimeMs)],
      ]);
    }
    case "PORT_CHECK": {
      const result = data as PortCheckResult;
      return fields([
        ["Хост", result.host],
        ["Порт", result.port],
        ["Время ответа", latency(result.responseTimeMs)],
      ]);
    }
    case "DNS_CHECK": {
      const result = data as DnsCheckResult;
      return fields([
        ["Hostname", result.hostname],
        ["Адреса", result.addresses],
        ["Время ответа", latency(result.responseTimeMs)],
      ]);
    }
    case "PING": {
      const result = data as PingResult;
      return fields([
        ["Хост", result.host],
        ["Время ответа", latency(result.responseTimeMs)],
      ]);
    }
    case "TLS_CHECK": {
      const result = data as TlsCheckResult;
      return fields([
        ["Хост", result.host],
        ["Порт", result.port],
        ["Subject", result.subject],
        ["Issuer", result.issuer],
        ["Действует с", result.notBefore],
        ["Действует до", result.notAfter],
        ["Дней до истечения", result.daysUntilExpiry],
        ["Время ответа", latency(result.responseTimeMs)],
      ]);
    }
    case "SSH_CHECK": {
      const result = data as SshCheckResult;
      return fields([
        ["Хост", result.host],
        ["Порт", result.port],
        ["Пользователь", result.username],
        ["Метод аутентификации", result.authMethod],
        ["Версия сервера", result.serverVersion],
        ["Время ответа", latency(result.responseTimeMs)],
      ]);
    }
    default:
      return [];
  }
}

export function presentProbeResult(
  type: MonitorResponseType | undefined,
  result: ProbeExecutionResult,
): ProbeResultPresentation {
  if ("error" in result && result.error) {
    return {
      outcome: "failure",
      summary: result.error.message ?? "Проверка завершилась ошибкой.",
      errorCode: result.error.code,
      fields: [],
    };
  }

  const successful = result.success === true;
  return {
    outcome: successful ? "success" : "failure",
    summary: successful
      ? "Проверка выполнена успешно."
      : "Проверка завершилась с отрицательным результатом.",
    fields: "data" in result ? dataFields(type, result.data) : [],
  };
}

export function getProbeResultLabel(result?: ProbeExecutionResult | null) {
  if (!result) return "Не выполнялась";
  if ("error" in result && result.error) return "Ошибка";
  return result.success === true ? "Успешно" : "Неуспешно";
}
