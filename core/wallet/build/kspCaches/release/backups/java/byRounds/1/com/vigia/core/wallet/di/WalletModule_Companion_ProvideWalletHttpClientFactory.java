package com.vigia.core.wallet.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class WalletModule_Companion_ProvideWalletHttpClientFactory implements Factory<OkHttpClient> {
  @Override
  public OkHttpClient get() {
    return provideWalletHttpClient();
  }

  public static WalletModule_Companion_ProvideWalletHttpClientFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static OkHttpClient provideWalletHttpClient() {
    return Preconditions.checkNotNullFromProvides(WalletModule.Companion.provideWalletHttpClient());
  }

  private static final class InstanceHolder {
    static final WalletModule_Companion_ProvideWalletHttpClientFactory INSTANCE = new WalletModule_Companion_ProvideWalletHttpClientFactory();
  }
}
