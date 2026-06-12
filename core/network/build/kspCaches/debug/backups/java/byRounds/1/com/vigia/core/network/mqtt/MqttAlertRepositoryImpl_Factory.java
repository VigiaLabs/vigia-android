package com.vigia.core.network.mqtt;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata({
    "dagger.hilt.android.qualifiers.ApplicationContext",
    "javax.inject.Named"
})
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
public final class MqttAlertRepositoryImpl_Factory implements Factory<MqttAlertRepositoryImpl> {
  private final Provider<Context> contextProvider;

  private final Provider<String> brokerUriProvider;

  public MqttAlertRepositoryImpl_Factory(Provider<Context> contextProvider,
      Provider<String> brokerUriProvider) {
    this.contextProvider = contextProvider;
    this.brokerUriProvider = brokerUriProvider;
  }

  @Override
  public MqttAlertRepositoryImpl get() {
    return newInstance(contextProvider.get(), brokerUriProvider.get());
  }

  public static MqttAlertRepositoryImpl_Factory create(Provider<Context> contextProvider,
      Provider<String> brokerUriProvider) {
    return new MqttAlertRepositoryImpl_Factory(contextProvider, brokerUriProvider);
  }

  public static MqttAlertRepositoryImpl newInstance(Context context, String brokerUri) {
    return new MqttAlertRepositoryImpl(context, brokerUri);
  }
}
