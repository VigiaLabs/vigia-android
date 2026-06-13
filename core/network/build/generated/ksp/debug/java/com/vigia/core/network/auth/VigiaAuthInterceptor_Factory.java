package com.vigia.core.network.auth;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class VigiaAuthInterceptor_Factory implements Factory<VigiaAuthInterceptor> {
  private final Provider<ApiTokenProvider> tokenProvider;

  public VigiaAuthInterceptor_Factory(Provider<ApiTokenProvider> tokenProvider) {
    this.tokenProvider = tokenProvider;
  }

  @Override
  public VigiaAuthInterceptor get() {
    return newInstance(tokenProvider.get());
  }

  public static VigiaAuthInterceptor_Factory create(Provider<ApiTokenProvider> tokenProvider) {
    return new VigiaAuthInterceptor_Factory(tokenProvider);
  }

  public static VigiaAuthInterceptor newInstance(ApiTokenProvider tokenProvider) {
    return new VigiaAuthInterceptor(tokenProvider);
  }
}
