package com.vigia.feature.maps.di;

import com.vigia.feature.maps.data.IngestionApiService;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import retrofit2.Retrofit;

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
public final class MapsModule_Companion_ProvideIngestionApiServiceFactory implements Factory<IngestionApiService> {
  private final Provider<Retrofit> retrofitProvider;

  public MapsModule_Companion_ProvideIngestionApiServiceFactory(
      Provider<Retrofit> retrofitProvider) {
    this.retrofitProvider = retrofitProvider;
  }

  @Override
  public IngestionApiService get() {
    return provideIngestionApiService(retrofitProvider.get());
  }

  public static MapsModule_Companion_ProvideIngestionApiServiceFactory create(
      Provider<Retrofit> retrofitProvider) {
    return new MapsModule_Companion_ProvideIngestionApiServiceFactory(retrofitProvider);
  }

  public static IngestionApiService provideIngestionApiService(Retrofit retrofit) {
    return Preconditions.checkNotNullFromProvides(MapsModule.Companion.provideIngestionApiService(retrofit));
  }
}
