# vigia2 Android System Rules

## Architecture & Integration
- Language & UI: Kotlin with Jetpack Compose.
- Architecture: MVVM following official Google guidance.
- Dependency Injection: Hilt.

## Dynamic Context & Execution Constraints
- Do NOT run blind file searches or grep commands. Use our local `codebase-memory-mcp` tools to trace functional architecture, layout dependencies, and Kotlin call trees before executing edits.
- You MUST explicitly load and apply the rules from the active 'Compose expert' personal plugin before modifying UI structure.
- You MUST explicitly load and apply the rules from the active 'Ui ux pro max' personal plugin to validate design tokens, responsive layouts, and visual alignment before marking a task as complete.
