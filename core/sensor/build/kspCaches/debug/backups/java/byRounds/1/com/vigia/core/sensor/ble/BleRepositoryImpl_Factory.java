package com.vigia.core.sensor.ble;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
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
public final class BleRepositoryImpl_Factory implements Factory<BleRepositoryImpl> {
  private final Provider<BleLinkManager> linkManagerProvider;

  public BleRepositoryImpl_Factory(Provider<BleLinkManager> linkManagerProvider) {
    this.linkManagerProvider = linkManagerProvider;
  }

  @Override
  public BleRepositoryImpl get() {
    return newInstance(linkManagerProvider.get());
  }

  public static BleRepositoryImpl_Factory create(Provider<BleLinkManager> linkManagerProvider) {
    return new BleRepositoryImpl_Factory(linkManagerProvider);
  }

  public static BleRepositoryImpl newInstance(BleLinkManager linkManager) {
    return new BleRepositoryImpl(linkManager);
  }
}
