import { useQueryClient } from "@tanstack/react-query";
import {
  type ReactNode,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { useNavigate } from "react-router-dom";
import { onSessionExpired, setAccessToken } from "../../api/client/http-client";
import type { LoginRequest, UserResponse } from "../../api/generated/model";
import {
  getCurrentUser,
  login as loginRequest,
  logoutSession,
  restoreSession,
} from "./auth-api";
import { AuthContext, type AuthStatus } from "./auth-context";

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>("BOOTSTRAPPING");
  const [user, setUser] = useState<UserResponse | null>(null);
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const navigateRef = useRef(navigate);

  useEffect(() => {
    navigateRef.current = navigate;
  }, [navigate]);

  const endSession = useCallback(() => {
    setAccessToken(null);
    setUser(null);
    setStatus("UNAUTHENTICATED");
    queryClient.clear();
    navigateRef.current("/login", { replace: true });
  }, [queryClient]);

  useEffect(() => {
    onSessionExpired(endSession);
    return () => onSessionExpired(null);
  }, [endSession]);

  useEffect(() => {
    let active = true;
    const bootstrap = async () => {
      try {
        await restoreSession();
        const currentUser = await getCurrentUser();
        if (active) {
          setUser(currentUser);
          setStatus("AUTHENTICATED");
        }
      } catch {
        if (active) endSession();
      }
    };
    void bootstrap();
    return () => {
      active = false;
    };
  }, [endSession]);

  const login = useCallback(
    async (credentials: LoginRequest) => {
      await loginRequest(credentials);
      const currentUser = await getCurrentUser();
      setUser(currentUser);
      setStatus("AUTHENTICATED");
      navigate("/", { replace: true });
    },
    [navigate],
  );

  const logout = useCallback(async () => {
    try {
      await logoutSession();
    } finally {
      endSession();
    }
  }, [endSession]);

  const value = useMemo(
    () => ({ status, user, login, logout }),
    [status, user, login, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
