import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Building2, LoaderCircle } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { z } from "zod";
import { readableError } from "../../api/client/api-error";
import { Alert } from "../../components/ui/alert";
import { Button } from "../../components/ui/button";
import { Input } from "../../components/ui/input";
import { Label } from "../../components/ui/label";
import { createOrganization, organizationKeys } from "./organization-api";
import { useAvailableOrganizations } from "./organization-context";

const organizationSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "Введите название организации")
    .max(100, "Название не должно быть длиннее 100 символов"),
});

type OrganizationValues = z.infer<typeof organizationSchema>;

export function FirstUsePage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { reload } = useAvailableOrganizations();
  const [requestError, setRequestError] = useState<string | null>(null);
  const mutation = useMutation({ mutationFn: createOrganization });
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<OrganizationValues>({
    resolver: zodResolver(organizationSchema),
    defaultValues: { name: "" },
  });

  const onSubmit = handleSubmit(async ({ name }) => {
    setRequestError(null);
    try {
      const organization = await mutation.mutateAsync(name.trim());
      if (!organization.id)
        throw new Error("API не вернул идентификатор организации.");
      await queryClient.invalidateQueries({ queryKey: organizationKeys.all });
      await reload();
      navigate(`/organizations/${organization.id}/resources`, {
        replace: true,
      });
    } catch (error) {
      setRequestError(readableError(error));
    }
  });

  return (
    <main className="grid min-h-screen place-items-center bg-background p-4">
      <section
        className="w-full max-w-[var(--layout-form-max)] rounded-panel border border-border bg-surface p-6"
        aria-labelledby="first-organization-title"
      >
        <Building2
          aria-hidden="true"
          className="size-icon-empty text-foreground-muted"
        />
        <h1 id="first-organization-title" className="mt-4 text-page-title">
          Создайте первую организацию
        </h1>
        <p className="mt-2 text-body text-foreground-muted">
          Организация объединяет ресурсы, мониторинг и доступ участников.
        </p>
        {requestError && <Alert className="mt-4">{requestError}</Alert>}
        <form className="mt-6 space-y-4" onSubmit={onSubmit} noValidate>
          <div className="space-y-1">
            <Label htmlFor="organization-name">Название организации</Label>
            <Input
              id="organization-name"
              autoComplete="organization"
              aria-invalid={Boolean(errors.name)}
              aria-describedby={
                errors.name ? "organization-name-error" : undefined
              }
              {...register("name")}
            />
            {errors.name && (
              <p
                id="organization-name-error"
                className="text-caption text-status-down"
              >
                {errors.name.message}
              </p>
            )}
          </div>
          <Button type="submit" variant="primary" disabled={mutation.isPending}>
            {mutation.isPending && (
              <LoaderCircle aria-hidden="true" className="animate-spin" />
            )}
            {mutation.isPending ? "Создаём…" : "Создать организацию"}
          </Button>
        </form>
      </section>
    </main>
  );
}
