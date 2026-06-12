package com.vigia.core.network.stripe;

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
public final class StripePayRepositoryImpl_Factory implements Factory<StripePayRepositoryImpl> {
  private final Provider<Context> contextProvider;

  public StripePayRepositoryImpl_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public StripePayRepositoryImpl get() {
    return newInstance(contextProvider.get());
  }

  public static StripePayRepositoryImpl_Factory create(Provider<Context> contextProvider) {
    return new StripePayRepositoryImpl_Factory(contextProvider);
  }

  public static StripePayRepositoryImpl newInstance(Context context) {
    return new StripePayRepositoryImpl(context);
  }
}
