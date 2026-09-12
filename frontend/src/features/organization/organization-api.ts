import { create1, search4, search5 } from "../../api/generated/cloud-ops";
import type {
  OrganizationMemberResponse,
  OrganizationResponse,
  SearchRequest,
} from "../../api/generated/model";

const allOrganizationsRequest: SearchRequest = {
  start: 0,
  size: 100,
  sort: [{ field: "name", order: "ASC" }],
  getTotal: false,
};

export const organizationKeys = {
  all: ["organizations"] as const,
  membership: (organizationId: number, userId: number) =>
    ["organizations", organizationId, "membership", userId] as const,
  scoped: (organizationId: number) =>
    ["organizations", organizationId] as const,
};

export async function getOrganizations(
  signal?: AbortSignal,
): Promise<OrganizationResponse[]> {
  const response = await search5(allOrganizationsRequest, { signal });
  return "items" in response.data ? response.data.items : [];
}

export async function getCurrentMembership(
  organizationId: number,
  userId: number,
  signal?: AbortSignal,
): Promise<OrganizationMemberResponse | null> {
  const response = await search4(
    organizationId,
    {
      start: 0,
      size: 1,
      filter: {
        operator: "AND",
        conditions: [
          { field: "userId", operation: "EQ", value: String(userId) },
        ],
      },
      getTotal: false,
    },
    { signal },
  );
  return "items" in response.data ? (response.data.items[0] ?? null) : null;
}

export async function createOrganization(
  name: string,
): Promise<OrganizationResponse> {
  const response = await create1({ name });
  return response.data as OrganizationResponse;
}
