import { createContext, useContext } from "react";
import type { OrganizationResponse } from "../../api/generated/model";

export type OrganizationRole = "OWNER" | "ADMIN" | "MEMBER";

export interface AvailableOrganizationsContextValue {
  organizations: OrganizationResponse[];
  reload: () => Promise<void>;
}

export interface OrganizationContextValue {
  organization: OrganizationResponse;
  organizationId: number;
  currentRole: OrganizationRole;
  isManager: boolean;
}

export const AvailableOrganizationsContext =
  createContext<AvailableOrganizationsContextValue | null>(null);
export const OrganizationContext =
  createContext<OrganizationContextValue | null>(null);

export function useAvailableOrganizations() {
  const context = useContext(AvailableOrganizationsContext);
  if (!context)
    throw new Error(
      "useAvailableOrganizations must be used inside AvailableOrganizationsProvider",
    );
  return context;
}

export function useOrganization() {
  const context = useContext(OrganizationContext);
  if (!context)
    throw new Error("useOrganization must be used inside OrganizationProvider");
  return context;
}
