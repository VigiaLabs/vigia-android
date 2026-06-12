package com.vigia.core.network.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("javax.inject.Named")
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
public final class NetworkModule_Companion_ProvideVigiaApiBaseUrlFactory implements Factory<String> {
  @Override
  public String get() {
    return provideVigiaApiBaseUrl();
  }

  public static NetworkModule_Companion_ProvideVigiaApiBaseUrlFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static String provideVigiaApiBaseUrl() {
    return Preconditions.checkNotNullFromProvides(NetworkModule.Companion.provideVigiaApiBaseUrl());
  }

  private static final class InstanceHolder {
    static final NetworkModule_Companion_ProvideVigiaApiBaseUrlFactory INSTANCE = new NetworkModule_Companion_ProvideVigiaApiBaseUrlFactory();
  }
}
