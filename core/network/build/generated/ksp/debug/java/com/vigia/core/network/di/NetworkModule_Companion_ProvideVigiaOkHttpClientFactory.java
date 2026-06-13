package com.vigia.core.network.di;

import com.vigia.core.network.auth.VigiaAuthInterceptor;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import okhttp3.OkHttpClient;

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
public final class NetworkModule_Companion_ProvideVigiaOkHttpClientFactory implements Factory<OkHttpClient> {
  private final Provider<VigiaAuthInterceptor> authInterceptorProvider;

  public NetworkModule_Companion_ProvideVigiaOkHttpClientFactory(
      Provider<VigiaAuthInterceptor> authInterceptorProvider) {
    this.authInterceptorProvider = authInterceptorProvider;
  }

  @Override
  public OkHttpClient get() {
    return provideVigiaOkHttpClient(authInterceptorProvider.get());
  }

  public static NetworkModule_Companion_ProvideVigiaOkHttpClientFactory create(
      Provider<VigiaAuthInterceptor> authInterceptorProvider) {
    return new NetworkModule_Companion_ProvideVigiaOkHttpClientFactory(authInterceptorProvider);
  }

  public static OkHttpClient provideVigiaOkHttpClient(VigiaAuthInterceptor authInterceptor) {
    return Preconditions.checkNotNullFromProvides(NetworkModule.Companion.provideVigiaOkHttpClient(authInterceptor));
  }
}
