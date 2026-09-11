import * as DropdownMenuPrimitive from "@radix-ui/react-dropdown-menu";
import { Building2, Check, ChevronsUpDown } from "lucide-react";
import { useLocation, useNavigate } from "react-router-dom";
import { Button } from "../../components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "../../components/ui/dropdown-menu";
import {
  useAvailableOrganizations,
  useOrganization,
} from "./organization-context";

const sections = new Set([
  "resources",
  "monitoring",
  "operations",
  "credentials",
  "settings",
]);

export function OrganizationSwitcher() {
  const { organization, organizationId, currentRole } = useOrganization();
  const { organizations } = useAvailableOrganizations();
  const location = useLocation();
  const navigate = useNavigate();

  const switchOrganization = (nextOrganizationId: number) => {
    const currentSection = location.pathname.split("/").filter(Boolean).at(-1);
    const section =
      currentSection && sections.has(currentSection)
        ? currentSection
        : "resources";
    navigate(`/organizations/${nextOrganizationId}/${section}`);
  };

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          className="h-auto min-h-control w-full justify-start px-3 py-2 text-left"
          aria-label={`Текущая организация: ${organization.name}. Переключить организацию`}
        >
          <Building2 aria-hidden="true" />
          <span className="min-w-0 flex-1">
            <span className="block truncate">{organization.name}</span>
            <span className="block text-caption text-foreground-muted">
              {currentRole}
            </span>
          </span>
          <ChevronsUpDown aria-hidden="true" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start">
        <DropdownMenuPrimitive.Label className="px-2 py-2 text-caption text-foreground-muted">
          Организации
        </DropdownMenuPrimitive.Label>
        {organizations.map((item) => (
          <DropdownMenuItem
            key={item.id}
            onSelect={() => switchOrganization(item.id as number)}
          >
            <span className="min-w-0 flex-1 truncate">{item.name}</span>
            {item.id === organizationId && (
              <Check aria-hidden="true" className="text-product-accent" />
            )}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
