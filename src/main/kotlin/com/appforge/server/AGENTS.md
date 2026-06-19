# AGENTS.md for server/src/main/kotlin/com.appforge/server/

## Scope

- Applies to `server/src/main/kotlin/com.appforge/server/**`.

## Must Follow

- Reuse existing provider contracts before introducing new abstractions.
- Keep domain logic separate from transport/framework concerns.
- Prefer extending existing service modules over creating parallel structures.
- Keep constructor wiring explicit and testable.
- Do not edit generated sources directly.

## Checks

- Run the most targeted Kotlin tests for touched service/module packages first.
- Expand test scope only when failures indicate cross-module impact.

## Doc Update Trigger

- Update `server/docs/*` for local runtime/implementation behavior changes.
- Update `docs/architecture/providers.md` only when provider contracts or reuse policy changes.
- Avoid updating root/global docs for local refactors.

## Key References

- `server/AGENTS.md`
- `docs/architecture/providers.md`
- `docs/architecture/dependency-rules.md`
- `server/docs/`

## Out of Scope

- API proto schema ownership (`api/proto/*`).
- Frontend app behavior and UI concerns.
