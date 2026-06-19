# core:auth

**Layer:** Core — leaf (no internal module dependencies)  
**Package:** `com.vigia.core.auth`  
**Path:** `core/auth/`

Cognito authentication via AWS Amplify. Provides `AuthRepository` to the rest of the app and exposes the Cognito ID token needed by `VigiaAuthInterceptor` in [[core-network]].

## Key Types

| Type | Description |
|------|-------------|
| `AuthRepository` | Interface: `signIn · signOut · signUp · confirmSignUp · currentUser · authState · getIdToken` |
| `AmplifyAuthRepository` | Production impl — delegates to `Amplify.Auth.*`; fetches Cognito ID token via `AWSCognitoAuthSession` |
| `DemoAuthRepository` | Dev/demo flavour impl — returns hardcoded user, `getIdToken() = null` (no auth interceptor overhead) |
| `AmplifyInitializer` | `ContentProvider`-based Amplify init; runs before `Application.onCreate()` |
| `AuthModule` | Hilt `@Singleton` binding: `AmplifyAuthRepository` or `DemoAuthRepository` selected by product flavour |
| `AuthState` | `Loading · SignedIn(user) · SignedOut · Failure(msg)` |
| `AuthOutcome` | `Success · Failure · ConfirmationRequired` — returned from sign-up/in operations |
| `AuthUser` | `id · email · displayName` |

## External Dependencies
- `aws.amplify.auth.cognito` — Cognito user pool + identity pool
- `androidx.datastore.preferences` — persists auth state across restarts
- `androidx.credentials` — Credential Manager for passkey/Google Sign-In

## Dependents
[[app]] (binds the flavour impl) · [[feature-copilot]] (AuthViewModel, sign-in UI) · [[core-network]] (ApiTokenProvider bridges `getIdToken` into OkHttp interceptor)

## Security Notes
- `getIdToken()` is `suspend`; bridged to synchronous `ApiTokenProvider` via `runBlocking` inside OkHttp's IO thread — safe, never called on main thread.
- `DemoAuthRepository.getIdToken()` returns `null`, so the auth interceptor sends no `Authorization` header in demo builds.
