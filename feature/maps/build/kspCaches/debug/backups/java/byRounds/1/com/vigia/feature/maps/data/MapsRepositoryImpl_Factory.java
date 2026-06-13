package com.vigia.feature.maps.data;

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
public final class MapsRepositoryImpl_Factory implements Factory<MapsRepositoryImpl> {
  private final Provider<SessionApiService> sessionApiProvider;

  private final Provider<InnovationApiService> innovationApiProvider;

  private final Provider<IngestionApiService> ingestionApiProvider;

  public MapsRepositoryImpl_Factory(Provider<SessionApiService> sessionApiProvider,
      Provider<InnovationApiService> innovationApiProvider,
      Provider<IngestionApiService> ingestionApiProvider) {
    this.sessionApiProvider = sessionApiProvider;
    this.innovationApiProvider = innovationApiProvider;
    this.ingestionApiProvider = ingestionApiProvider;
  }

  @Override
  public MapsRepositoryImpl get() {
    return newInstance(sessionApiProvider.get(), innovationApiProvider.get(), ingestionApiProvider.get());
  }

  public static MapsRepositoryImpl_Factory create(Provider<SessionApiService> sessionApiProvider,
      Provider<InnovationApiService> innovationApiProvider,
      Provider<IngestionApiService> ingestionApiProvider) {
    return new MapsRepositoryImpl_Factory(sessionApiProvider, innovationApiProvider, ingestionApiProvider);
  }

  public static MapsRepositoryImpl newInstance(SessionApiService sessionApi,
      InnovationApiService innovationApi, IngestionApiService ingestionApi) {
    return new MapsRepositoryImpl(sessionApi, innovationApi, ingestionApi);
  }
}
