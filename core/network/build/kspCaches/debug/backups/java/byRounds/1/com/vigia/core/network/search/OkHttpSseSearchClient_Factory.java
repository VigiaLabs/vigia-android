package com.vigia.core.network.search;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class OkHttpSseSearchClient_Factory implements Factory<OkHttpSseSearchClient> {
  private final Provider<OkHttpClient> okHttpClientProvider;

  private final Provider<String> baseUrlProvider;

  public OkHttpSseSearchClient_Factory(Provider<OkHttpClient> okHttpClientProvider,
      Provider<String> baseUrlProvider) {
    this.okHttpClientProvider = okHttpClientProvider;
    this.baseUrlProvider = baseUrlProvider;
  }

  @Override
  public OkHttpSseSearchClient get() {
    return newInstance(okHttpClientProvider.get(), baseUrlProvider.get());
  }

  public static OkHttpSseSearchClient_Factory create(Provider<OkHttpClient> okHttpClientProvider,
      Provider<String> baseUrlProvider) {
    return new OkHttpSseSearchClient_Factory(okHttpClientProvider, baseUrlProvider);
  }

  public static OkHttpSseSearchClient newInstance(OkHttpClient okHttpClient, String baseUrl) {
    return new OkHttpSseSearchClient(okHttpClient, baseUrl);
  }
}
