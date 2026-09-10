import type { ApiError as ApiErrorBody } from "../generated/model";

export type ApiErrorKind =
  | "network"
  | "validation"
  | "unauthenticated"
  | "forbidden"
  | "not-found"
  | "conflict"
  | "unexpected";

const statusKinds: Partial<Record<number, ApiErrorKind>> = {
  400: "validation",
  401: "unauthenticated",
  403: "forbidden",
  404: "not-found",
  409: "conflict",
};

export class ApiClientError extends Error {
  readonly kind: ApiErrorKind;
  readonly status?: number;
  readonly code?: string;
  readonly details?: ApiErrorBody;

  constructor(
    message: string,
    options: { kind: ApiErrorKind; status?: number; details?: ApiErrorBody },
  ) {
    super(message);
    this.name = "ApiClientError";
    this.kind = options.kind;
    this.status = options.status;
    this.code = options.details?.code;
    this.details = options.details;
  }
}

export function isApiError(value: unknown): value is ApiErrorBody {
  if (typeof value !== "object" || value === null) return false;
  const error = value as Partial<ApiErrorBody>;
  return typeof error.code === "string" && typeof error.message === "string";
}

export function mapResponseError(
  status: number,
  body: unknown,
): ApiClientError {
  const details = isApiError(body) ? body : undefined;
  return new ApiClientError(
    details?.message ?? "Не удалось выполнить запрос.",
    {
      kind: statusKinds[status] ?? "unexpected",
      status,
      details,
    },
  );
}

export function readableError(error: unknown): string {
  if (error instanceof ApiClientError) {
    if (error.kind === "network")
      return "Сервер недоступен. Проверьте подключение и повторите попытку.";
    if (error.kind === "unauthenticated") return "Неверный email или пароль.";
    return error.message;
  }
  return "Произошла непредвиденная ошибка.";
}
