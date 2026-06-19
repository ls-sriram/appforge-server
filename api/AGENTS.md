# AGENTS.md for api/

## Scope

- Applies to `api/**`.

## Must Follow

- Protobuf in `api/proto/` is the source of truth.
- Keep HTTP route mappings in proto annotations.
- Use `google.protobuf.Timestamp` for API time fields.
- Avoid breaking wire changes unless explicitly requested.

## Checks

From repo root:

- `npm run api:proto:lint`
- `npm run api:proto:gen`
- `npm run api:proto:check`

## Doc Update Trigger

- Update API docs only when proto schema, route mappings, or compatibility expectations changed.
- If generated output only changed with no schema meaning change, do not add doc noise.

## Key References

- `api/README.md`
- `docs/architecture/invariants.md`
- `docs/runbooks/ast-search.md`
- `docs/decisions/`

## Out of Scope

- Backend runtime implementation details.
- Frontend UX and presentation behavior.
