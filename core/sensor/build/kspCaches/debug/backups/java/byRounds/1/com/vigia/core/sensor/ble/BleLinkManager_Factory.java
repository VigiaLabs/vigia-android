package com.vigia.core.sensor.ble;

import android.content.Context;
import com.vigia.core.sensor.keystore.KeystoreManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class BleLinkManager_Factory implements Factory<BleLinkManager> {
  private final Provider<Context> contextProvider;

  private final Provider<KeystoreManager> keystoreManagerProvider;

  public BleLinkManager_Factory(Provider<Context> contextProvider,
      Provider<KeystoreManager> keystoreManagerProvider) {
    this.contextProvider = contextProvider;
    this.keystoreManagerProvider = keystoreManagerProvider;
  }

  @Override
  public BleLinkManager get() {
    return newInstance(contextProvider.get(), keystoreManagerProvider.get());
  }

  public static BleLinkManager_Factory create(Provider<Context> contextProvider,
      Provider<KeystoreManager> keystoreManagerProvider) {
    return new BleLinkManager_Factory(contextProvider, keystoreManagerProvider);
  }

  public static BleLinkManager newInstance(Context context, KeystoreManager keystoreManager) {
    return new BleLinkManager(context, keystoreManager);
  }
}
