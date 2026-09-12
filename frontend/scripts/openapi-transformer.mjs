export default function transformOpenApi(schema) {
  for (const [path, pathItem] of Object.entries(schema.paths ?? {})) {
    if (path === "/api/resources/search") continue;
    for (const operation of Object.values(pathItem ?? {})) {
      if (!operation || typeof operation !== "object" || !operation.tags)
        continue;
      operation.tags = operation.tags.filter(
        (tag) => tag !== "resource-controller",
      );
    }
  }

  const schemas = schema.components?.schemas;
  if (schemas?.OtherResourceConfig) {
    schemas.OtherResourceConfig = {
      type: "object",
      additionalProperties: true,
      description: "Configuration payload for an OTHER resource.",
    };
  }
  return schema;
}
