package com.vigia.core.network.fcm;

import com.vigia.core.network.mqtt.MqttAlertRepository;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Named;
import okhttp3.OkHttpClient;

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
public final class VigiaFcmReceiver_MembersInjector implements MembersInjector<VigiaFcmReceiver> {
  private final Provider<MqttAlertRepository> mqttAlertRepositoryProvider;

  private final Provider<OkHttpClient> okHttpClientProvider;

  private final Provider<String> baseUrlProvider;

  public VigiaFcmReceiver_MembersInjector(Provider<MqttAlertRepository> mqttAlertRepositoryProvider,
      Provider<OkHttpClient> okHttpClientProvider, Provider<String> baseUrlProvider) {
    this.mqttAlertRepositoryProvider = mqttAlertRepositoryProvider;
    this.okHttpClientProvider = okHttpClientProvider;
    this.baseUrlProvider = baseUrlProvider;
  }

  public static MembersInjector<VigiaFcmReceiver> create(
      Provider<MqttAlertRepository> mqttAlertRepositoryProvider,
      Provider<OkHttpClient> okHttpClientProvider, Provider<String> baseUrlProvider) {
    return new VigiaFcmReceiver_MembersInjector(mqttAlertRepositoryProvider, okHttpClientProvider, baseUrlProvider);
  }

  @Override
  public void injectMembers(VigiaFcmReceiver instance) {
    injectMqttAlertRepository(instance, mqttAlertRepositoryProvider.get());
    injectOkHttpClient(instance, okHttpClientProvider.get());
    injectBaseUrl(instance, baseUrlProvider.get());
  }

  @InjectedFieldSignature("com.vigia.core.network.fcm.VigiaFcmReceiver.mqttAlertRepository")
  public static void injectMqttAlertRepository(VigiaFcmReceiver instance,
      MqttAlertRepository mqttAlertRepository) {
    instance.mqttAlertRepository = mqttAlertRepository;
  }

  @InjectedFieldSignature("com.vigia.core.network.fcm.VigiaFcmReceiver.okHttpClient")
  @Named("VigiaOkHttpClient")
  public static void injectOkHttpClient(VigiaFcmReceiver instance, OkHttpClient okHttpClient) {
    instance.okHttpClient = okHttpClient;
  }

  @InjectedFieldSignature("com.vigia.core.network.fcm.VigiaFcmReceiver.baseUrl")
  @Named("VigiaApiBaseUrl")
  public static void injectBaseUrl(VigiaFcmReceiver instance, String baseUrl) {
    instance.baseUrl = baseUrl;
  }
}
