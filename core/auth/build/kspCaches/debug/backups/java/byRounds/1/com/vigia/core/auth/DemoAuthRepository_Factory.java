package com.vigia.core.auth;

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
public final class DemoAuthRepository_Factory implements Factory<DemoAuthRepository> {
  private final Provider<Context> contextProvider;

  public DemoAuthRepository_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public DemoAuthRepository get() {
    return newInstance(contextProvider.get());
  }

  public static DemoAuthRepository_Factory create(Provider<Context> contextProvider) {
    return new DemoAuthRepository_Factory(contextProvider);
  }

  public static DemoAuthRepository newInstance(Context context) {
    return new DemoAuthRepository(context);
  }
}
