import { createContext, useContext } from "react";
import type { LoginRequest, UserResponse } from "../../api/generated/model";

export type AuthStatus = "BOOTSTRAPPING" | "AUTHENTICATED" | "UNAUTHENTICATED";

export interface AuthContextValue {
  status: AuthStatus;
  user: UserResponse | null;
  login: (credentials: LoginRequest) => Promise<void>;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used inside AuthProvider");
  return context;
}
