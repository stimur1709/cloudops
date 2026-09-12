import { defineConfig } from "orval";

const openApiUrl =
  process.env.VITE_OPENAPI_URL ?? "http://localhost:8080/v3/api-docs";

export default defineConfig({
  cloudOps: {
    hooks: {
      afterAllFilesWrite: "prettier --write",
    },
    input: {
      target: openApiUrl,
      filters: {
        mode: "include",
        tags: [
          "auth-controller",
          "organization-controller",
          "organization-membership-controller",
        ],
      },
    },
    output: {
      target: "./src/api/generated/cloud-ops.ts",
      schemas: "./src/api/generated/model",
      client: "react-query",
      mode: "single",
      clean: true,
      override: {
        mutator: {
          path: "./src/api/client/generated-mutator.ts",
          name: "generatedRequest",
        },
      },
    },
  },
});
