---
title: "MapsModule (Hilt)"
type: di
tags: [di, maps]
source: "feature/maps/src/main/kotlin/com/vigia/feature/maps/di/MapsModule.kt"
related: ["[[feature-maps]]", "[[maps-repository]]"]
updated: 2026-06-20
---

# MapsModule (Hilt)

`@Module @InstallIn(SingletonComponent)`. Provides `MapsApiService` (Retrofit) and binds `MapsRepository`.

## Provides

`MapsApiService` — `Retrofit.Builder().baseUrl(vigiaApiBaseUrl).client(vigiaOkHttpClient).addConverterFactory(GsonConverterFactory)...build().create(MapsApiService::class.java)`

## Binds

`MapsRepository` → `MapsRepositoryImpl`

## Links

[[feature-maps]] [[maps-repository]] [[di-network-module]] [[aws-backend]]
