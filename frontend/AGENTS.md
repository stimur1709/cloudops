# CloudOps Frontend Agent Guidelines

These instructions apply to changes in `frontend/**` and extend the repository-level `AGENTS.md`.

## Design system

- Read `DESIGN.md` before changing frontend code or assets.
- Treat `DESIGN.md` as the source of truth for visual design, layout, interaction, UI states, shared components, and accessibility.
- Add a shared visual or component pattern to `DESIGN.md` and the shared frontend implementation before using it in feature screens.
- Do not create local UI conventions that bypass the design system.

## API contract

- Use the existing backend API and generated OpenAPI description as the contract.
- Do not invent response fields, enum values, or endpoints in the frontend.
- Keep authentication compatible with the backend session model: access token in memory and refresh token in an HttpOnly cookie.

## Scope and verification

- Keep feature-specific UI inside its feature module and reusable primitives in shared frontend code.
- Add automated tests for changed behavior once the frontend test stack exists.
- Run the frontend formatter, type checker, tests, and production build defined by the module before completion.
