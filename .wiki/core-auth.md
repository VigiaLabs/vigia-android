# core-auth

The `:core:auth` module contains authentication and session-related code.

## Connects To

- No explicit module dependency on [[core-model]] in the current Gradle file.

## Role

- Owns auth-specific repository and DI wiring.
- Is consumed by [[app]] and [[feature-copilot]].