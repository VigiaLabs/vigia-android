package com.vigia.core.sensor.tts;

import android.content.Context;
import com.vigia.core.network.sarvam.SarvamTtsClient;
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
public final class TtsManager_Factory implements Factory<TtsManager> {
  private final Provider<Context> contextProvider;

  private final Provider<SarvamTtsClient> sarvamTtsClientProvider;

  public TtsManager_Factory(Provider<Context> contextProvider,
      Provider<SarvamTtsClient> sarvamTtsClientProvider) {
    this.contextProvider = contextProvider;
    this.sarvamTtsClientProvider = sarvamTtsClientProvider;
  }

  @Override
  public TtsManager get() {
    return newInstance(contextProvider.get(), sarvamTtsClientProvider.get());
  }

  public static TtsManager_Factory create(Provider<Context> contextProvider,
      Provider<SarvamTtsClient> sarvamTtsClientProvider) {
    return new TtsManager_Factory(contextProvider, sarvamTtsClientProvider);
  }

  public static TtsManager newInstance(Context context, SarvamTtsClient sarvamTtsClient) {
    return new TtsManager(context, sarvamTtsClient);
  }
}
