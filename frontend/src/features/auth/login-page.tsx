import { zodResolver } from "@hookform/resolvers/zod";
import { Cloud, LoaderCircle } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Navigate, useLocation } from "react-router-dom";
import { z } from "zod";
import { readableError } from "../../api/client/api-error";
import { Alert } from "../../components/ui/alert";
import { Button } from "../../components/ui/button";
import { Input } from "../../components/ui/input";
import { Label } from "../../components/ui/label";
import { useAuth } from "./auth-context";

const loginSchema = z.object({
  email: z.email("Введите корректный email").max(254, "Email слишком длинный"),
  password: z.string().min(1, "Введите пароль"),
});

type LoginValues = z.infer<typeof loginSchema>;

export function LoginPage() {
  const { status, login } = useAuth();
  const location = useLocation();
  const [requestError, setRequestError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  });

  if (status === "AUTHENTICATED") {
    const destination =
      (location.state as { from?: string } | null)?.from ?? "/";
    return <Navigate to={destination} replace />;
  }

  const onSubmit = handleSubmit(async (values) => {
    setRequestError(null);
    try {
      await login(values);
    } catch (error) {
      setRequestError(readableError(error));
    }
  });

  return (
    <main className="grid min-h-screen place-items-center bg-background px-4 py-8">
      <section
        className="w-full max-w-[var(--layout-form-max)] rounded-panel border border-border bg-surface p-6"
        aria-labelledby="login-title"
      >
        <div className="mb-6 flex items-center gap-3">
          <Cloud aria-hidden="true" className="size-6 text-product-accent" />
          <div>
            <p className="text-label text-foreground-muted">CloudOps</p>
            <h1 id="login-title" className="text-page-title">
              Вход в консоль
            </h1>
          </div>
        </div>
        {requestError && <Alert className="mb-4">{requestError}</Alert>}
        <form onSubmit={onSubmit} noValidate className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="email">Email</Label>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              aria-invalid={Boolean(errors.email)}
              aria-describedby={errors.email ? "email-error" : undefined}
              {...register("email")}
            />
            {errors.email && (
              <p id="email-error" className="text-caption text-status-down">
                {errors.email.message}
              </p>
            )}
          </div>
          <div className="space-y-2">
            <Label htmlFor="password">Пароль</Label>
            <Input
              id="password"
              type="password"
              autoComplete="current-password"
              aria-invalid={Boolean(errors.password)}
              aria-describedby={errors.password ? "password-error" : undefined}
              {...register("password")}
            />
            {errors.password && (
              <p id="password-error" className="text-caption text-status-down">
                {errors.password.message}
              </p>
            )}
          </div>
          <Button
            type="submit"
            variant="primary"
            className="w-full"
            disabled={isSubmitting}
          >
            {isSubmitting && (
              <LoaderCircle aria-hidden="true" className="animate-spin" />
            )}
            {isSubmitting ? "Входим…" : "Войти"}
          </Button>
        </form>
      </section>
    </main>
  );
}
