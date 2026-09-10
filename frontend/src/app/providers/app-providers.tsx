import { QueryClientProvider } from "@tanstack/react-query";
import { type ReactNode, useState } from "react";
import { BrowserRouter } from "react-router-dom";
import { TooltipProvider } from "../../components/ui/tooltip";
import { AuthProvider } from "../../features/auth/auth-provider";
import { createQueryClient } from "./query-client";

export function AppProviders({ children }: { children: ReactNode }) {
  const [queryClient] = useState(createQueryClient);
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <TooltipProvider delayDuration={400}>
          <AuthProvider>{children}</AuthProvider>
        </TooltipProvider>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
