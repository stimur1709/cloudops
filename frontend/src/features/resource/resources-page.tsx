import {
  flexRender,
  getCoreRowModel,
  type ColumnDef,
  type SortingState,
  useReactTable,
} from "@tanstack/react-table";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import {
  ArrowDown,
  ArrowUp,
  ArrowUpDown,
  ChevronLeft,
  ChevronRight,
  Ellipsis,
  FolderSearch,
  LoaderCircle,
  LockKeyhole,
  Search,
  Server,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { ApiClientError, readableError } from "../../api/client/api-error";
import type { ResourceResponse } from "../../api/generated/model";
import { EmptyState } from "../../components/empty-state";
import { HealthStatus } from "../../components/health-status";
import { ResourceCard } from "../../components/resource-card";
import {
  getResourceTypeLabel,
  resourceStatusLabels,
  resourceTypeLabels,
} from "../../components/resource-labels";
import { LifecycleStatus } from "../../components/lifecycle-status";
import { Alert } from "../../components/ui/alert";
import { Button } from "../../components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "../../components/ui/dropdown-menu";
import { Input } from "../../components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "../../components/ui/select";
import { Skeleton } from "../../components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "../../components/ui/table";
import { useOrganization } from "../organization/organization-context";
import {
  getResources,
  healthStatuses,
  lifecycleStatuses,
  pageSizes,
  resourceKeys,
  resourceTypes,
} from "./resource-api";
import { parseResourceListState } from "./resource-list-state";

const allFilterValues = "__all";
const resourceColumnClasses: Record<string, string> = {
  name: "min-w-50",
  healthStatus: "min-w-32",
  type: "min-w-36",
  status: "hidden min-w-32 lg:table-cell",
  updatedAt: "hidden min-w-40 lg:table-cell",
  actions: "w-11 text-right",
};

function formatUpdatedAt(value?: string) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  return new Intl.DateTimeFormat("ru-RU", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
}

function ResourceActions({
  resource,
  href,
}: {
  resource: ResourceResponse;
  href: string;
}) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          aria-label={`Действия для ${resource.name ?? "ресурса"}`}
        >
          <Ellipsis aria-hidden="true" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem asChild>
          <Link to={href}>Открыть детали</Link>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

function SortHeader({
  label,
  sortLabel,
  column,
}: {
  label: string;
  sortLabel: string;
  column: {
    getIsSorted: () => false | "asc" | "desc";
    getToggleSortingHandler: () => ((event: unknown) => void) | undefined;
  };
}) {
  const direction = column.getIsSorted();
  return (
    <Button
      type="button"
      variant="ghost"
      className="-ml-3"
      onClick={column.getToggleSortingHandler()}
      aria-label={"Сортировать по " + sortLabel}
    >
      {label}
      {direction === "asc" ? (
        <ArrowUp aria-hidden="true" />
      ) : direction === "desc" ? (
        <ArrowDown aria-hidden="true" />
      ) : (
        <ArrowUpDown aria-hidden="true" />
      )}
    </Button>
  );
}

function LoadingTable() {
  return (
    <div
      className="rounded-panel border border-border bg-surface p-4"
      aria-busy="true"
      aria-label="Загрузка ресурсов"
    >
      <div className="space-y-4">
        {Array.from({ length: 6 }, (_, index) => (
          <Skeleton key={index} className="h-row w-full" />
        ))}
      </div>
    </div>
  );
}

export function ResourcesPage() {
  const { organizationId } = useOrganization();
  const [searchParams, setSearchParams] = useSearchParams();
  const state = useMemo(
    () => parseResourceListState(searchParams),
    [searchParams],
  );
  const [searchDraft, setSearchDraft] = useState(state.search);

  const updateParams = useCallback(
    (changes: Record<string, string | undefined>, resetPage = false) => {
      const next = new URLSearchParams(searchParams);
      Object.entries(changes).forEach(([key, value]) => {
        if (value) next.set(key, value);
        else next.delete(key);
      });
      if (resetPage) next.delete("page");
      setSearchParams(next);
    },
    [searchParams, setSearchParams],
  );

  useEffect(() => setSearchDraft(state.search), [state.search]);
  useEffect(() => {
    if (searchDraft === state.search) return;
    const timeout = window.setTimeout(
      () => updateParams({ search: searchDraft.trim() || undefined }, true),
      250,
    );
    return () => window.clearTimeout(timeout);
  }, [searchDraft, state.search, updateParams]);

  const query = useQuery({
    queryKey: resourceKeys.list(organizationId, state),
    queryFn: ({ signal }) => getResources(organizationId, state, signal),
    placeholderData: keepPreviousData,
  });

  useEffect(() => {
    if (!query.isFetching && query.data?.items.length === 0 && state.page > 0)
      updateParams({ page: String(state.page) }, false);
  }, [query.data, query.isFetching, state.page, updateParams]);

  const columns = useMemo<ColumnDef<ResourceResponse>[]>(
    () => [
      {
        accessorKey: "name",
        header: ({ column }) => (
          <SortHeader label="Имя" sortLabel="имени" column={column} />
        ),
        cell: ({ row }) => (
          <Link
            to={`/organizations/${organizationId}/resources/${row.original.id}`}
            className="font-medium text-product-accent underline-offset-4 hover:underline"
          >
            {row.original.name ?? "Без имени"}
          </Link>
        ),
      },
      {
        accessorKey: "healthStatus",
        header: "Здоровье",
        enableSorting: false,
        cell: ({ row }) => <HealthStatus status={row.original.healthStatus} />,
      },
      {
        accessorKey: "type",
        header: ({ column }) => (
          <SortHeader label="Тип" sortLabel="типу" column={column} />
        ),
        cell: ({ row }) => (
          <span className="text-body">
            {getResourceTypeLabel(row.original.type)}
          </span>
        ),
      },
      {
        accessorKey: "status",
        header: ({ column }) => (
          <SortHeader label="Статус" sortLabel="статусу" column={column} />
        ),
        cell: ({ row }) => <LifecycleStatus status={row.original.status} />,
      },
      {
        accessorKey: "updatedAt",
        header: ({ column }) => (
          <SortHeader
            label="Обновлено"
            sortLabel="дате обновления"
            column={column}
          />
        ),
        cell: ({ row }) => (
          <time
            dateTime={row.original.updatedAt}
            className="text-caption text-foreground-muted"
          >
            {formatUpdatedAt(row.original.updatedAt)}
          </time>
        ),
      },
      {
        id: "actions",
        header: () => <span className="sr-only">Действия</span>,
        enableSorting: false,
        cell: ({ row }) => {
          const href = `/organizations/${organizationId}/resources/${row.original.id}`;
          return <ResourceActions resource={row.original} href={href} />;
        },
      },
    ],
    [organizationId],
  );

  const sorting: SortingState = state.sort
    ? [{ id: state.sort, desc: state.order === "desc" }]
    : [];
  // TanStack Table exposes stateful functions that React Compiler intentionally skips.
  // eslint-disable-next-line react-hooks/incompatible-library
  const table = useReactTable({
    data: query.data?.items ?? [],
    columns,
    getCoreRowModel: getCoreRowModel(),
    manualFiltering: true,
    manualSorting: true,
    manualPagination: true,
    rowCount: query.data?.total,
    state: {
      sorting,
      pagination: { pageIndex: state.page, pageSize: state.size },
    },
    onSortingChange: (updater) => {
      const next = typeof updater === "function" ? updater(sorting) : updater;
      const first = next[0];
      updateParams(
        {
          sort: first?.id,
          order: first ? (first.desc ? "desc" : "asc") : undefined,
        },
        true,
      );
    },
  });

  const hasFilters = Boolean(
    state.search || state.type || state.status || state.healthStatus,
  );
  const total = query.data?.total;
  const canGoNext =
    total !== undefined
      ? (state.page + 1) * state.size < total
      : (query.data?.items.length ?? 0) === state.size;

  if (query.isPending) return <LoadingTable />;
  if (query.isError) {
    const kind =
      query.error instanceof ApiClientError ? query.error.kind : undefined;
    if (kind === "forbidden" || kind === "not-found")
      return (
        <EmptyState
          icon={kind === "forbidden" ? LockKeyhole : FolderSearch}
          title={
            kind === "forbidden"
              ? "Нет доступа к ресурсам"
              : "Ресурсы недоступны"
          }
          description={
            kind === "forbidden"
              ? "У вашей роли нет разрешения просматривать ресурсы этой организации."
              : "Организация или её ресурсы не найдены."
          }
        />
      );
    return (
      <div className="space-y-4">
        <Alert>{readableError(query.error)}</Alert>
        <Button type="button" onClick={() => void query.refetch()}>
          Повторить
        </Button>
      </div>
    );
  }

  return (
    <section aria-labelledby="resources-heading" className="space-y-6">
      <header>
        <div className="flex flex-wrap items-center gap-3">
          <h1 id="resources-heading" className="text-page-title">
            Ресурсы
          </h1>
          {query.isFetching && (
            <span
              className="inline-flex items-center gap-2 text-caption text-foreground-muted"
              role="status"
            >
              <LoaderCircle
                aria-hidden="true"
                className="size-icon animate-spin"
              />
              Обновление данных
            </span>
          )}
        </div>
        <p className="mt-1 text-body text-foreground-muted">
          Инфраструктура выбранной организации.
        </p>
      </header>

      <div className="flex flex-wrap items-end gap-3 rounded-panel border border-border bg-surface p-4">
        <label className="min-w-64 flex-1">
          <span className="mb-1 block text-label">Поиск по имени</span>
          <span className="relative block">
            <Search
              aria-hidden="true"
              className="absolute top-1/2 left-3 size-icon -translate-y-1/2 text-foreground-muted"
            />
            <Input
              className="pl-12"
              placeholder="Поиск ресурсов по имени…"
              value={searchDraft}
              onChange={(event) => setSearchDraft(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  updateParams(
                    { search: searchDraft.trim() || undefined },
                    true,
                  );
                }
              }}
            />
          </span>
        </label>
        <label className="w-48">
          <span className="mb-1 block text-label">Тип</span>
          <Select
            value={state.type || allFilterValues}
            onValueChange={(value) =>
              updateParams(
                { type: value === allFilterValues ? undefined : value },
                true,
              )
            }
          >
            <SelectTrigger className="w-full" aria-label="Тип">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={allFilterValues}>Все типы</SelectItem>
              {resourceTypes.map((value) => (
                <SelectItem key={value} value={value}>
                  {resourceTypeLabels[value]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </label>
        <label className="w-48">
          <span className="mb-1 block text-label">Статус</span>
          <Select
            value={state.status || allFilterValues}
            onValueChange={(value) =>
              updateParams(
                { status: value === allFilterValues ? undefined : value },
                true,
              )
            }
          >
            <SelectTrigger className="w-full" aria-label="Статус">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={allFilterValues}>Все статусы</SelectItem>
              {lifecycleStatuses.map((value) => (
                <SelectItem key={value} value={value}>
                  {resourceStatusLabels[value]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </label>
        <label className="w-48">
          <span className="mb-1 block text-label">Здоровье</span>
          <Select
            value={state.healthStatus || allFilterValues}
            onValueChange={(value) =>
              updateParams(
                { health: value === allFilterValues ? undefined : value },
                true,
              )
            }
          >
            <SelectTrigger className="w-full" aria-label="Здоровье">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={allFilterValues}>Любое</SelectItem>
              {healthStatuses.map((value) => (
                <SelectItem key={value} value={value}>
                  {value}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </label>
        {hasFilters && (
          <Button
            type="button"
            variant="ghost"
            onClick={() => {
              setSearchDraft("");
              updateParams(
                {
                  search: undefined,
                  type: undefined,
                  status: undefined,
                  health: undefined,
                },
                true,
              );
            }}
          >
            Сбросить фильтры
          </Button>
        )}
      </div>

      {query.data.items.length === 0 ? (
        <EmptyState
          icon={hasFilters ? Search : Server}
          title={hasFilters ? "Ничего не найдено" : "Ресурсов ещё нет"}
          description={
            hasFilters
              ? "Измените поиск или сбросьте фильтры."
              : "В этой организации ещё нет ресурсов."
          }
          action={
            hasFilters ? (
              <Button
                type="button"
                onClick={() => {
                  setSearchDraft("");
                  setSearchParams({});
                }}
              >
                Сбросить фильтры
              </Button>
            ) : undefined
          }
        />
      ) : (
        <>
          <div className="hidden overflow-hidden rounded-panel border border-border bg-surface md:block">
            <Table>
              <caption className="sr-only">Ресурсы организации</caption>
              <TableHeader>
                {table.getHeaderGroups().map((group) => (
                  <TableRow key={group.id} className="hover:bg-transparent">
                    {group.headers.map((header) => (
                      <TableHead
                        key={header.id}
                        aria-sort={
                          header.column.getIsSorted() === "asc"
                            ? "ascending"
                            : header.column.getIsSorted() === "desc"
                              ? "descending"
                              : undefined
                        }
                        className={resourceColumnClasses[header.id]}
                      >
                        {header.isPlaceholder
                          ? null
                          : flexRender(
                              header.column.columnDef.header,
                              header.getContext(),
                            )}
                      </TableHead>
                    ))}
                  </TableRow>
                ))}
              </TableHeader>
              <TableBody>
                {table.getRowModel().rows.map((row) => (
                  <TableRow key={row.id}>
                    {row.getVisibleCells().map((cell) => (
                      <TableCell
                        key={cell.id}
                        className={resourceColumnClasses[cell.column.id]}
                      >
                        {flexRender(
                          cell.column.columnDef.cell,
                          cell.getContext(),
                        )}
                      </TableCell>
                    ))}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <div className="grid gap-3 md:hidden">
            {query.data.items.map((resource, index) => {
              const href = `/organizations/${organizationId}/resources/${resource.id}`;
              return (
                <ResourceCard
                  key={resource.id ?? index}
                  resource={resource}
                  href={href}
                  actions={<ResourceActions resource={resource} href={href} />}
                />
              );
            })}
          </div>
        </>
      )}

      {query.data.items.length > 0 && (
        <nav
          aria-label="Пагинация ресурсов"
          className="flex flex-wrap items-center justify-between gap-3"
        >
          <p className="text-caption text-foreground-muted">
            {total !== undefined
              ? `${state.page * state.size + 1}–${Math.min(state.page * state.size + query.data.items.length, total)} из ${total}`
              : `Страница ${state.page + 1}`}
          </p>
          <div className="flex items-center gap-2">
            <label className="flex items-center gap-2 text-label">
              Строк на странице
              <Select
                value={String(state.size)}
                onValueChange={(value) =>
                  updateParams(
                    {
                      size: value === "20" ? undefined : value,
                    },
                    true,
                  )
                }
              >
                <SelectTrigger className="w-20" aria-label="Строк на странице">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {pageSizes.map((value) => (
                    <SelectItem key={value} value={String(value)}>
                      {value}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </label>
            <Button
              type="button"
              size="icon"
              disabled={state.page === 0}
              aria-label="Предыдущая страница"
              onClick={() =>
                updateParams({
                  page: state.page === 1 ? undefined : String(state.page),
                })
              }
            >
              <ChevronLeft aria-hidden="true" />
            </Button>
            <Button
              type="button"
              size="icon"
              disabled={!canGoNext}
              aria-label="Следующая страница"
              onClick={() => updateParams({ page: String(state.page + 2) })}
            >
              <ChevronRight aria-hidden="true" />
            </Button>
          </div>
        </nav>
      )}
    </section>
  );
}
