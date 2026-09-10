# CloudOps Agent Guidelines

## Project

- Work on one GitHub issue at a time and stay within its scope.
- Treat `backend/` as the Spring Boot API module and `frontend/` as the Web UI module.
- Before changing a module, read and follow that module's `AGENTS.md` in addition to this file.
- Change backend and frontend independently unless an issue requires changes to both modules.
- Keep CloudOps a simple modular monolith on Java 25 and Spring Boot.
- Do not add distributed infrastructure unless the issue requires it.

## Repository structure

- Keep the root `pom.xml` as a Maven reactor entry point for IDE import and repository-wide builds.
- Keep module-specific build tools and configuration inside their modules.
- Do not commit `.idea`, generated build output, secrets, `.env` files, or other machine-local state.
- Update README when developer setup, repository structure, or commands change.
- Check current official documentation before relying on version-sensitive third-party behavior.

## Git

- Create a separate branch from the main development branch for each issue.
- Use Conventional Commits: `type(scope): short description`.
- Do not mix unrelated refactoring or cleanup into an issue.
