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
public final class SarvamSttClientImpl_Factory implements Factory<SarvamSttClientImpl> {
  private final Provider<OkHttpClient> okHttpClientProvider;

  private final Provider<String> apiKeyProvider;

  public SarvamSttClientImpl_Factory(Provider<OkHttpClient> okHttpClientProvider,
      Provider<String> apiKeyProvider) {
    this.okHttpClientProvider = okHttpClientProvider;
    this.apiKeyProvider = apiKeyProvider;
  }

  @Override
  public SarvamSttClientImpl get() {
    return newInstance(okHttpClientProvider.get(), apiKeyProvider.get());
  }

  public static SarvamSttClientImpl_Factory create(Provider<OkHttpClient> okHttpClientProvider,
      Provider<String> apiKeyProvider) {
    return new SarvamSttClientImpl_Factory(okHttpClientProvider, apiKeyProvider);
  }

  public static SarvamSttClientImpl newInstance(OkHttpClient okHttpClient, String apiKey) {
    return new SarvamSttClientImpl(okHttpClient, apiKey);
  }
}
