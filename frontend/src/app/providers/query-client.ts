import { QueryClient } from "@tanstack/react-query";
import { ApiClientError } from "../../api/client/api-error";

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        retry: (failureCount, error) => {
          if (
            error instanceof ApiClientError &&
            error.status !== undefined &&
            error.status >= 400 &&
            error.status < 500
          )
            return false;
          return failureCount < 2;
        },
      },
      mutations: { retry: false },
    },
  });
}
