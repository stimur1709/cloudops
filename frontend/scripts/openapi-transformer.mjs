export default function transformOpenApi(schema) {
  const schemas = schema.components?.schemas;
  if (schemas?.OtherResourceConfig) {
    schemas.OtherResourceConfig = {
      type: "object",
      additionalProperties: true,
      description: "Configuration payload for an OTHER resource.",
    };
  }
  const availabilityOperation =
    schema.paths?.["/api/resources/{resourceId}/health/availability"]?.get;
  if (availabilityOperation?.parameters) {
    availabilityOperation.parameters = availabilityOperation.parameters.flatMap(
      (parameter) =>
        parameter.in === "query" && parameter.name === "request"
          ? ["from", "to"].map((name) => ({
              name,
              in: "query",
              required: true,
              schema: { type: "string", format: "date-time" },
            }))
          : [parameter],
    );
  }
  return schema;
}
