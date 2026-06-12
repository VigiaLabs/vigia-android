package com.vigia.core.auth.di;

import com.vigia.core.auth.AmplifyAuthRepository;
import com.vigia.core.auth.AuthRepository;
import com.vigia.core.auth.DemoAuthRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class AuthModule_ProvideAuthRepositoryFactory implements Factory<AuthRepository> {
  private final Provider<AmplifyAuthRepository> amplifyProvider;

  private final Provider<DemoAuthRepository> demoProvider;

  public AuthModule_ProvideAuthRepositoryFactory(Provider<AmplifyAuthRepository> amplifyProvider,
      Provider<DemoAuthRepository> demoProvider) {
    this.amplifyProvider = amplifyProvider;
    this.demoProvider = demoProvider;
  }

  @Override
  public AuthRepository get() {
    return provideAuthRepository(amplifyProvider, demoProvider);
  }

  public static AuthModule_ProvideAuthRepositoryFactory create(
      Provider<AmplifyAuthRepository> amplifyProvider, Provider<DemoAuthRepository> demoProvider) {
    return new AuthModule_ProvideAuthRepositoryFactory(amplifyProvider, demoProvider);
  }

  public static AuthRepository provideAuthRepository(
      javax.inject.Provider<AmplifyAuthRepository> amplify,
      javax.inject.Provider<DemoAuthRepository> demo) {
    return Preconditions.checkNotNullFromProvides(AuthModule.INSTANCE.provideAuthRepository(amplify, demo));
  }
}
