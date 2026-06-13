# core-model

The `:core:model` module is the shared domain model layer.

## Connects To

- No upstream module dependencies.
- Used by [[app]], [[feature-copilot]], [[core-network]], [[core-sensor]], [[core-data]], and [[core-auth]].

## Role

- Holds pure Kotlin types shared across the repository.
- Acts as the lowest common dependency for feature and core modules.