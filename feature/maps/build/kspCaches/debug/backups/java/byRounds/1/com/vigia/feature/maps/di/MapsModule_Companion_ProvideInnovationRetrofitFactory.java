package com.vigia.feature.maps.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import okhttp3.OkHttpClient;
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
public final class MapsModule_Companion_ProvideInnovationRetrofitFactory implements Factory<Retrofit> {
  private final Provider<OkHttpClient> clientProvider;

  public MapsModule_Companion_ProvideInnovationRetrofitFactory(
      Provider<OkHttpClient> clientProvider) {
    this.clientProvider = clientProvider;
  }

  @Override
  public Retrofit get() {
    return provideInnovationRetrofit(clientProvider.get());
  }

  public static MapsModule_Companion_ProvideInnovationRetrofitFactory create(
      Provider<OkHttpClient> clientProvider) {
    return new MapsModule_Companion_ProvideInnovationRetrofitFactory(clientProvider);
  }

  public static Retrofit provideInnovationRetrofit(OkHttpClient client) {
    return Preconditions.checkNotNullFromProvides(MapsModule.Companion.provideInnovationRetrofit(client));
  }
}
