package com.vigia.core.network.sarvam;

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
public final class SarvamTtsClientImpl_Factory implements Factory<SarvamTtsClientImpl> {
  private final Provider<OkHttpClient> okHttpClientProvider;

  private final Provider<String> apiKeyProvider;

  public SarvamTtsClientImpl_Factory(Provider<OkHttpClient> okHttpClientProvider,
      Provider<String> apiKeyProvider) {
    this.okHttpClientProvider = okHttpClientProvider;
    this.apiKeyProvider = apiKeyProvider;
  }

  @Override
  public SarvamTtsClientImpl get() {
    return newInstance(okHttpClientProvider.get(), apiKeyProvider.get());
  }

  public static SarvamTtsClientImpl_Factory create(Provider<OkHttpClient> okHttpClientProvider,
      Provider<String> apiKeyProvider) {
    return new SarvamTtsClientImpl_Factory(okHttpClientProvider, apiKeyProvider);
  }

  public static SarvamTtsClientImpl newInstance(OkHttpClient okHttpClient, String apiKey) {
    return new SarvamTtsClientImpl(okHttpClient, apiKey);
  }
}
