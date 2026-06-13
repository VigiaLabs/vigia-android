package com.vigia.feature.maps.di;

import com.vigia.feature.maps.data.SessionApiService;
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
public final class MapsModule_Companion_ProvideSessionApiServiceFactory implements Factory<SessionApiService> {
  private final Provider<Retrofit> retrofitProvider;

  public MapsModule_Companion_ProvideSessionApiServiceFactory(Provider<Retrofit> retrofitProvider) {
    this.retrofitProvider = retrofitProvider;
  }

  @Override
  public SessionApiService get() {
    return provideSessionApiService(retrofitProvider.get());
  }

  public static MapsModule_Companion_ProvideSessionApiServiceFactory create(
      Provider<Retrofit> retrofitProvider) {
    return new MapsModule_Companion_ProvideSessionApiServiceFactory(retrofitProvider);
  }

  public static SessionApiService provideSessionApiService(Retrofit retrofit) {
    return Preconditions.checkNotNullFromProvides(MapsModule.Companion.provideSessionApiService(retrofit));
  }
}
