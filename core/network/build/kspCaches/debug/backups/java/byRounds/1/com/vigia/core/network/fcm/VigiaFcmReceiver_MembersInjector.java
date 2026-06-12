package com.vigia.core.network.fcm;

import com.vigia.core.network.mqtt.MqttAlertRepository;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;

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
public final class VigiaFcmReceiver_MembersInjector implements MembersInjector<VigiaFcmReceiver> {
  private final Provider<MqttAlertRepository> mqttAlertRepositoryProvider;

  public VigiaFcmReceiver_MembersInjector(
      Provider<MqttAlertRepository> mqttAlertRepositoryProvider) {
    this.mqttAlertRepositoryProvider = mqttAlertRepositoryProvider;
  }

  public static MembersInjector<VigiaFcmReceiver> create(
      Provider<MqttAlertRepository> mqttAlertRepositoryProvider) {
    return new VigiaFcmReceiver_MembersInjector(mqttAlertRepositoryProvider);
  }

  @Override
  public void injectMembers(VigiaFcmReceiver instance) {
    injectMqttAlertRepository(instance, mqttAlertRepositoryProvider.get());
  }

  @InjectedFieldSignature("com.vigia.core.network.fcm.VigiaFcmReceiver.mqttAlertRepository")
  public static void injectMqttAlertRepository(VigiaFcmReceiver instance,
      MqttAlertRepository mqttAlertRepository) {
    instance.mqttAlertRepository = mqttAlertRepository;
  }
}
