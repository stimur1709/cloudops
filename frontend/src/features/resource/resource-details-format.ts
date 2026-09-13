export type AvailabilityPeriod = "24h" | "7d" | "30d";

const periodHours: Record<AvailabilityPeriod, number> = {
  "24h": 24,
  "7d": 7 * 24,
  "30d": 30 * 24,
};

export function isAvailabilityPeriod(
  value: string,
): value is AvailabilityPeriod {
  return value in periodHours;
}

export function buildAvailabilityRange(period: AvailabilityPeriod, now: Date) {
  const to = new Date(now);
  const from = new Date(to.getTime() - periodHours[period] * 60 * 60 * 1000);
  return { from: from.toISOString(), to: to.toISOString() };
}

export function formatPercent(value?: number | null) {
  if (value == null || !Number.isFinite(value)) return "—";
  if (value > 0 && value < 0.01) return "<0,01%";
  if (value < 100 && value > 99.995) return "<100%";
  return `${new Intl.NumberFormat("ru-RU", { maximumFractionDigits: 2 }).format(value)}%`;
}

export function formatDuration(value?: number | null) {
  if (value == null || !Number.isFinite(value)) return "—";
  const seconds = Math.max(0, Math.round(value));
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const remainder = seconds % 60;
  return [
    days ? `${days} д` : "",
    hours ? `${hours} ч` : "",
    minutes ? `${minutes} мин` : "",
    remainder || seconds === 0 ? `${remainder} с` : "",
  ]
    .filter(Boolean)
    .slice(0, 2)
    .join(" ");
}

export function formatDateTime(value?: string | null) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  return new Intl.DateTimeFormat("ru-RU", {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    timeZoneName: "short",
  }).format(date);
}
