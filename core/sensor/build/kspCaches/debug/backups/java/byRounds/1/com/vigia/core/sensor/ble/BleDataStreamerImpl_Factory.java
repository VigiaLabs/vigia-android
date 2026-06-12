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
public final class BleDataStreamerImpl_Factory implements Factory<BleDataStreamerImpl> {
  private final Provider<BleLinkManager> linkManagerProvider;

  public BleDataStreamerImpl_Factory(Provider<BleLinkManager> linkManagerProvider) {
    this.linkManagerProvider = linkManagerProvider;
  }

  @Override
  public BleDataStreamerImpl get() {
    return newInstance(linkManagerProvider.get());
  }

  public static BleDataStreamerImpl_Factory create(Provider<BleLinkManager> linkManagerProvider) {
    return new BleDataStreamerImpl_Factory(linkManagerProvider);
  }

  public static BleDataStreamerImpl newInstance(BleLinkManager linkManager) {
    return new BleDataStreamerImpl(linkManager);
  }
}
