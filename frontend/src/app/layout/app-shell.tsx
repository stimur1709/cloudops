import * as DropdownMenuPrimitive from "@radix-ui/react-dropdown-menu";
import {
  Activity,
  ChevronDown,
  Cloud,
  KeyRound,
  LogOut,
  Settings,
  SquareTerminal,
  Boxes,
} from "lucide-react";
import { NavLink, Outlet } from "react-router-dom";
import { Button } from "../../components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "../../components/ui/dropdown-menu";
import { Separator } from "../../components/ui/separator";
import { useAuth } from "../../features/auth/auth-context";
import { cn } from "../../lib/cn";

const navigation = [
  { to: "/resources", label: "Ресурсы", icon: Boxes },
  { to: "/monitoring", label: "Мониторинг", icon: Activity },
  { to: "/operations", label: "Операции", icon: SquareTerminal },
  { to: "/credentials", label: "Учётные данные", icon: KeyRound },
  { to: "/settings", label: "Настройки", icon: Settings },
];

export function AppShell() {
  const { user, logout } = useAuth();
  return (
    <div className="min-h-screen bg-background md:grid md:grid-cols-[var(--layout-sidebar-width)_minmax(0,1fr)]">
      <aside className="border-b border-border bg-surface md:min-h-screen md:border-r md:border-b-0">
        <div className="flex h-14 items-center gap-3 px-4">
          <Cloud aria-hidden="true" className="size-5 text-product-accent" />
          <span className="text-card-title">CloudOps</span>
        </div>
        <Separator />
        <nav
          aria-label="Основная навигация"
          className="flex gap-1 overflow-x-auto p-2 md:block md:space-y-1"
        >
          {navigation.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                cn(
                  "flex h-control shrink-0 items-center gap-3 rounded-control px-3 text-label text-foreground-muted transition-colors duration-(--motion-fast) hover:bg-surface-hover hover:text-foreground",
                  isActive &&
                    "bg-product-accent-soft text-foreground before:h-4 before:w-0.5 before:rounded-full before:bg-product-accent",
                )
              }
            >
              <Icon aria-hidden="true" className="size-icon" />
              {label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <div className="min-w-0">
        <header className="flex h-14 items-center justify-between border-b border-border bg-surface px-4 md:px-6">
          <div>
            <p className="text-label text-foreground">Operations console</p>
            <p className="text-caption text-foreground-muted">
              Infrastructure workspace
            </p>
          </div>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" aria-label="Открыть меню пользователя">
                <span className="hidden max-w-48 truncate md:inline">
                  {user?.displayName}
                </span>
                <ChevronDown aria-hidden="true" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <div className="px-2 py-2">
                <p className="text-label">{user?.displayName}</p>
                <p className="text-caption text-foreground-muted">
                  {user?.email}
                </p>
              </div>
              <DropdownMenuPrimitive.Separator className="my-1 h-px bg-border" />
              <DropdownMenuItem onSelect={() => void logout()}>
                <LogOut aria-hidden="true" className="size-icon" />
                Выйти
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </header>
        <main className="mx-auto w-full max-w-[var(--layout-content-max)] p-4 md:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
