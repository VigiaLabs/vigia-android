# Vigia2 Wiki

This wiki maps the repository layout and the module relationships discovered from the codebase-memory index.

## Repository Layout

- [[app]]
- [[feature-copilot]]
- [[core-model]]
- [[core-network]]
- [[core-sensor]]
- [[core-data]]
- [[core-auth]]
- [[build-logic]]

## Dependency Overview

```mermaid
graph TD
    app[app] --> feature[feature-copilot]
    app --> sensor[core-sensor]
    app --> network[core-network]
    app --> model[core-model]
    app --> auth[core-auth]

    feature --> network
    feature --> sensor
    feature --> data[core-data]
    feature --> auth

    network --> model
    sensor --> model
    data --> model
```

## Notes

- The architecture documents describe a smaller target set, but the repository currently also contains [[core-data]] and [[core-auth]].
- Shared models live in [[core-model]].
- Runtime and hardware integration flows are centered in [[core-sensor]] and [[core-network]].