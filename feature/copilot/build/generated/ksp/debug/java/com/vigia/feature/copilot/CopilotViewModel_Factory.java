package com.vigia.feature.copilot;

import com.vigia.core.data.ChatRepository;
import com.vigia.core.network.mqtt.MqttAlertRepository;
import com.vigia.core.network.sarvam.SarvamSttClient;
import com.vigia.core.network.search.VigiaSearchClient;
import com.vigia.core.sensor.cdm.CdmPresenceRepository;
import com.vigia.core.sensor.context.ContextAggregator;
import com.vigia.core.sensor.tts.TtsManager;
import com.vigia.core.sensor.voice.VoiceAmplitudeMonitor;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
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
public final class CopilotViewModel_Factory implements Factory<CopilotViewModel> {
  private final Provider<VigiaSearchClient> searchClientProvider;

  private final Provider<MqttAlertRepository> mqttAlertRepositoryProvider;

  private final Provider<ContextAggregator> contextAggregatorProvider;

  private final Provider<TtsManager> ttsManagerProvider;

  private final Provider<CdmPresenceRepository> cdmRepositoryProvider;

  private final Provider<ChatRepository> chatRepositoryProvider;

  private final Provider<SarvamSttClient> sarvamSttClientProvider;

  private final Provider<VoiceAmplitudeMonitor> voiceAmplitudeMonitorProvider;

  public CopilotViewModel_Factory(Provider<VigiaSearchClient> searchClientProvider,
      Provider<MqttAlertRepository> mqttAlertRepositoryProvider,
      Provider<ContextAggregator> contextAggregatorProvider,
      Provider<TtsManager> ttsManagerProvider,
      Provider<CdmPresenceRepository> cdmRepositoryProvider,
      Provider<ChatRepository> chatRepositoryProvider,
      Provider<SarvamSttClient> sarvamSttClientProvider,
      Provider<VoiceAmplitudeMonitor> voiceAmplitudeMonitorProvider) {
    this.searchClientProvider = searchClientProvider;
    this.mqttAlertRepositoryProvider = mqttAlertRepositoryProvider;
    this.contextAggregatorProvider = contextAggregatorProvider;
    this.ttsManagerProvider = ttsManagerProvider;
    this.cdmRepositoryProvider = cdmRepositoryProvider;
    this.chatRepositoryProvider = chatRepositoryProvider;
    this.sarvamSttClientProvider = sarvamSttClientProvider;
    this.voiceAmplitudeMonitorProvider = voiceAmplitudeMonitorProvider;
  }

  @Override
  public CopilotViewModel get() {
    return newInstance(searchClientProvider.get(), mqttAlertRepositoryProvider.get(), contextAggregatorProvider.get(), ttsManagerProvider.get(), cdmRepositoryProvider.get(), chatRepositoryProvider.get(), sarvamSttClientProvider.get(), voiceAmplitudeMonitorProvider.get());
  }

  public static CopilotViewModel_Factory create(Provider<VigiaSearchClient> searchClientProvider,
      Provider<MqttAlertRepository> mqttAlertRepositoryProvider,
      Provider<ContextAggregator> contextAggregatorProvider,
      Provider<TtsManager> ttsManagerProvider,
      Provider<CdmPresenceRepository> cdmRepositoryProvider,
      Provider<ChatRepository> chatRepositoryProvider,
      Provider<SarvamSttClient> sarvamSttClientProvider,
      Provider<VoiceAmplitudeMonitor> voiceAmplitudeMonitorProvider) {
    return new CopilotViewModel_Factory(searchClientProvider, mqttAlertRepositoryProvider, contextAggregatorProvider, ttsManagerProvider, cdmRepositoryProvider, chatRepositoryProvider, sarvamSttClientProvider, voiceAmplitudeMonitorProvider);
  }

  public static CopilotViewModel newInstance(VigiaSearchClient searchClient,
      MqttAlertRepository mqttAlertRepository, ContextAggregator contextAggregator,
      TtsManager ttsManager, CdmPresenceRepository cdmRepository, ChatRepository chatRepository,
      SarvamSttClient sarvamSttClient, VoiceAmplitudeMonitor voiceAmplitudeMonitor) {
    return new CopilotViewModel(searchClient, mqttAlertRepository, contextAggregator, ttsManager, cdmRepository, chatRepository, sarvamSttClient, voiceAmplitudeMonitor);
  }
}
