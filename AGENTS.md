# CloudOps Agent Guidelines

## Project

- Work on one GitHub issue at a time and stay within its scope.
- Keep CloudOps a simple modular monolith on Java 25 and Spring Boot.
- Do not add distributed infrastructure unless the issue requires it.

## Architecture

- Organize code by functional module and group larger modules by responsibility.
- Add layers, packages, domain models, ports, and abstractions only when they solve a practical need.
- Keep shared code in `common`; do not move feature-specific behavior there.
- Do not expose JPA entities through the API.
- Use constructor injection.

## Java

- Prefer clear names, small methods, and immutable `record` DTOs.
- Use `Instant` for timestamps.
- Do not use `Optional` in entity or API fields.
- Do not catch broad exceptions unless they are translated at an application boundary.

## Database and SQL

- Use PostgreSQL only, with schema changes managed by Liquibase and Hibernate set to validate.
- Use database-generated `BIGINT IDENTITY` identifiers unless an issue says otherwise.
- Keep structural integrity and stable invariants in DB constraints; validate changeable feature rules in Java.
- Do not encode extensible enum/type lists in DB constraints when normal extension would require a migration.
- Keep one source of truth for finite value sets and business rules. If duplication is required for performance, explain it and test it.
- Format embedded and Liquibase SQL with UPPER CASE keywords and `lower_snake_case` identifiers.

## API and errors

- Use `/api` as the base path and Bean Validation at the API boundary.
- Use class-level constraints with a dedicated `ConstraintValidator` for related request fields, attaching violations to fields.
- Return the shared API error format without stack traces, SQL errors, or internal exception messages.
- Keep `common.api.error` limited to common HTTP, API, and framework errors.
- Handle feature-specific exceptions beside that feature's API; do not teach `GlobalExceptionHandler` feature types.
- Explicitly allow-list dynamic search fields and operations, and convert values before building JPA criteria.

## Testing

- Test every changed behavior with JUnit 5 and Spring Boot test support.
- Use PostgreSQL Testcontainers for integration tests; do not add H2 or replace integration persistence with mocks.
- Prefer API integration tests and focused unit tests for domain logic.
- When an issue specifies performance, SQL, transaction, or concurrency behavior, assert the actual behavior in an integration test.

## Formatting and verification

- Format Java through Spotless with `./mvnw spotless:apply`; do not maintain an individual manual style.
- Run `./mvnw.cmd verify` on Windows or `./mvnw verify` on macOS and Linux before completion.
- For version-sensitive third-party behavior, check current official documentation.
- Update README when developer setup or commands change.

## Git

- Create a separate branch from the main development branch for each issue.
- Use Conventional Commits: `type(scope): short description`.
- Do not mix unrelated refactoring or cleanup into an issue.
- Never commit secrets, `.env` files, IDE-local settings, or build outputs.
