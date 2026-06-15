package com.vigia.core.wallet;

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
public final class WalletRepositoryImpl_Factory implements Factory<WalletRepositoryImpl> {
  private final Provider<Ed25519KeyStore> keyStoreProvider;

  private final Provider<OkHttpClient> httpClientProvider;

  private final Provider<String> baseUrlProvider;

  public WalletRepositoryImpl_Factory(Provider<Ed25519KeyStore> keyStoreProvider,
      Provider<OkHttpClient> httpClientProvider, Provider<String> baseUrlProvider) {
    this.keyStoreProvider = keyStoreProvider;
    this.httpClientProvider = httpClientProvider;
    this.baseUrlProvider = baseUrlProvider;
  }

  @Override
  public WalletRepositoryImpl get() {
    return newInstance(keyStoreProvider.get(), httpClientProvider.get(), baseUrlProvider.get());
  }

  public static WalletRepositoryImpl_Factory create(Provider<Ed25519KeyStore> keyStoreProvider,
      Provider<OkHttpClient> httpClientProvider, Provider<String> baseUrlProvider) {
    return new WalletRepositoryImpl_Factory(keyStoreProvider, httpClientProvider, baseUrlProvider);
  }

  public static WalletRepositoryImpl newInstance(Ed25519KeyStore keyStore, OkHttpClient httpClient,
      String baseUrl) {
    return new WalletRepositoryImpl(keyStore, httpClient, baseUrl);
  }
}
