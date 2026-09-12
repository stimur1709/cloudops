export default function transformOpenApi(schema) {
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
