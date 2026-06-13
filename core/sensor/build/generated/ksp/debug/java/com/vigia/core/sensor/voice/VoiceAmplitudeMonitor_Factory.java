package com.vigia.core.sensor.voice;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class VoiceAmplitudeMonitor_Factory implements Factory<VoiceAmplitudeMonitor> {
  @Override
  public VoiceAmplitudeMonitor get() {
    return newInstance();
  }

  public static VoiceAmplitudeMonitor_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static VoiceAmplitudeMonitor newInstance() {
    return new VoiceAmplitudeMonitor();
  }

  private static final class InstanceHolder {
    static final VoiceAmplitudeMonitor_Factory INSTANCE = new VoiceAmplitudeMonitor_Factory();
  }
}
