package com.vigia.feature.maps.di;

import com.vigia.feature.maps.data.InnovationApiService;
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
public final class MapsModule_Companion_ProvideInnovationApiServiceFactory implements Factory<InnovationApiService> {
  private final Provider<Retrofit> retrofitProvider;

  public MapsModule_Companion_ProvideInnovationApiServiceFactory(
      Provider<Retrofit> retrofitProvider) {
    this.retrofitProvider = retrofitProvider;
  }

  @Override
  public InnovationApiService get() {
    return provideInnovationApiService(retrofitProvider.get());
  }

  public static MapsModule_Companion_ProvideInnovationApiServiceFactory create(
      Provider<Retrofit> retrofitProvider) {
    return new MapsModule_Companion_ProvideInnovationApiServiceFactory(retrofitProvider);
  }

  public static InnovationApiService provideInnovationApiService(Retrofit retrofit) {
    return Preconditions.checkNotNullFromProvides(MapsModule.Companion.provideInnovationApiService(retrofit));
  }
}
