package com.vigia.core.auth;

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
public final class AmplifyAuthRepository_Factory implements Factory<AmplifyAuthRepository> {
  @Override
  public AmplifyAuthRepository get() {
    return newInstance();
  }

  public static AmplifyAuthRepository_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static AmplifyAuthRepository newInstance() {
    return new AmplifyAuthRepository();
  }

  private static final class InstanceHolder {
    static final AmplifyAuthRepository_Factory INSTANCE = new AmplifyAuthRepository_Factory();
  }
}
