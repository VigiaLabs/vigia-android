package com.vigia.core.sensor.context;

import android.content.Context;
import com.vigia.core.sensor.ble.BleDataStreamer;
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
public final class ContextAggregator_Factory implements Factory<ContextAggregator> {
  private final Provider<Context> contextProvider;

  private final Provider<BleDataStreamer> bleDataStreamerProvider;

  public ContextAggregator_Factory(Provider<Context> contextProvider,
      Provider<BleDataStreamer> bleDataStreamerProvider) {
    this.contextProvider = contextProvider;
    this.bleDataStreamerProvider = bleDataStreamerProvider;
  }

  @Override
  public ContextAggregator get() {
    return newInstance(contextProvider.get(), bleDataStreamerProvider.get());
  }

  public static ContextAggregator_Factory create(Provider<Context> contextProvider,
      Provider<BleDataStreamer> bleDataStreamerProvider) {
    return new ContextAggregator_Factory(contextProvider, bleDataStreamerProvider);
  }

  public static ContextAggregator newInstance(Context context, BleDataStreamer bleDataStreamer) {
    return new ContextAggregator(context, bleDataStreamer);
  }
}
