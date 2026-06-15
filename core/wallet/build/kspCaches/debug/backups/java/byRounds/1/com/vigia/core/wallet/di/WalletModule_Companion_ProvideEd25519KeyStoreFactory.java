package com.vigia.core.wallet.di;

import android.content.Context;
import com.vigia.core.wallet.Ed25519KeyStore;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class WalletModule_Companion_ProvideEd25519KeyStoreFactory implements Factory<Ed25519KeyStore> {
  private final Provider<Context> contextProvider;

  public WalletModule_Companion_ProvideEd25519KeyStoreFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public Ed25519KeyStore get() {
    return provideEd25519KeyStore(contextProvider.get());
  }

  public static WalletModule_Companion_ProvideEd25519KeyStoreFactory create(
      Provider<Context> contextProvider) {
    return new WalletModule_Companion_ProvideEd25519KeyStoreFactory(contextProvider);
  }

  public static Ed25519KeyStore provideEd25519KeyStore(Context context) {
    return Preconditions.checkNotNullFromProvides(WalletModule.Companion.provideEd25519KeyStore(context));
  }
}
