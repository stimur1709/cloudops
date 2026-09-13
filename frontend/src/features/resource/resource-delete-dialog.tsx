import { useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import type { ResourceResponse } from "../../api/generated/model";
import { Alert } from "../../components/ui/alert";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "../../components/ui/alert-dialog";
import {
  deleteResource,
  resourceKeys,
  type ResourcePage,
} from "./resource-api";

export function ResourceDeleteDialog({
  resource,
  organizationId,
  open,
  onOpenChange,
}: {
  resource: ResourceResponse;
  organizationId: number;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string>();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  async function remove(event: React.MouseEvent) {
    event.preventDefault();
    if (resource.id === undefined) return;
    setPending(true);
    setError(undefined);
    try {
      await deleteResource(resource.id);
      queryClient.removeQueries({
        queryKey: resourceKeys.detail(organizationId, resource.id),
      });
      queryClient.setQueriesData<ResourcePage>(
        { queryKey: resourceKeys.organization(organizationId) },
        (current) =>
          current
            ? {
                ...current,
                items: current.items.filter((item) => item.id !== resource.id),
                total:
                  current.total === undefined
                    ? undefined
                    : Math.max(0, current.total - 1),
              }
            : current,
      );
      await queryClient.invalidateQueries({
        queryKey: resourceKeys.organization(organizationId),
      });
      onOpenChange(false);
      navigate(`/organizations/${organizationId}/resources`, { replace: true });
      window.requestAnimationFrame(() =>
        document.getElementById("resources-heading")?.focus(),
      );
    } catch {
      setError(
        "Не удалось удалить ресурс. Проверьте доступ и повторите попытку.",
      );
    } finally {
      setPending(false);
    }
  }
  return (
    <AlertDialog
      open={open}
      onOpenChange={(next) => {
        if (!pending) {
          setError(undefined);
          onOpenChange(next);
        }
      }}
    >
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>
            Удалить ресурс {resource.name ?? "без имени"}?
          </AlertDialogTitle>
          <AlertDialogDescription>
            Ресурс будет удалён из текущей организации. Это действие нельзя
            отменить.
          </AlertDialogDescription>
        </AlertDialogHeader>
        {error && <Alert className="mt-4">{error}</Alert>}
        <AlertDialogFooter>
          <AlertDialogCancel disabled={pending}>Отмена</AlertDialogCancel>
          <AlertDialogAction
            className="min-w-36"
            disabled={pending}
            onClick={remove}
          >
            {pending ? "Удаление…" : "Удалить ресурс"}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
