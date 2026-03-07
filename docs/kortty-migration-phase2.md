# korTTY Migration (Phase 2)

This checklist describes the migration from the legacy terminal dependencies to SithTermFX.

## Dependency Migration

- Replace Maven dependencies:
  - `legacy-core-artifact` -> `com.sithtermfx:sithtermfx-core`
  - `legacy-ui-artifact` -> `com.sithtermfx:sithtermfx-ui`
- Align version properties to `sithtermfx.version`.

## Code Migration

- Replace imports from legacy packages to `com.sithtermfx...`.
- Update any hardcoded JPMS module names to `com.sithtermfx.core` and `com.sithtermfx.ui`.
- Validate terminal startup paths and settings providers after package rename.

## Validation

- Run korTTY build and automated tests.
- Execute integration smoke tests:
  - terminal startup
  - shell command execution
  - rendering and resize behavior
  - copy/paste and hyperlink handling

## Risk Mitigation

- If needed, create a short-lived adapter layer to keep older call sites compiling.
- Remove adapters after korTTY is fully migrated.

