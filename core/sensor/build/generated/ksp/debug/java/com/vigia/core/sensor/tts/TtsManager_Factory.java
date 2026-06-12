package com.vigia.core.sensor.tts;

import android.content.Context;
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

  public TtsManager_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public TtsManager get() {
    return newInstance(contextProvider.get());
  }

  public static TtsManager_Factory create(Provider<Context> contextProvider) {
    return new TtsManager_Factory(contextProvider);
  }

  public static TtsManager newInstance(Context context) {
    return new TtsManager(context);
  }
}
