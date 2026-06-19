# AGENTS.md for server/

## Scope

- Applies to `server/**`.

## Must Follow

- Edit source definitions, not generated artifacts.
- Preserve domain/service boundaries.
- Keep transport concerns out of core domain logic.
- Check existing providers before adding new server abstractions; prefer reuse or extension.
- Prefer explicit error/null handling.

## Checks

- Run targeted server tests for changed packages first.
- Expand to broader suites only when signal indicates wider impact.

## Doc Update Trigger

- Update `docs/architecture/providers.md` only when provider contracts, ownership, or reuse policy changed.
- Update `server/docs/*` for module-local runtime or operational changes.
- Avoid touching global architecture docs for purely local implementation edits.

## Key References

- `docs/INDEX.md`
- `docs/architecture/dependency-rules.md`
- `docs/architecture/providers.md`
- `docs/runbooks/ast-search.md`
- `server/docs/`

## Out of Scope

- API schema design decisions owned by `api/proto/*`.
- Frontend UI and navigation conventions.
