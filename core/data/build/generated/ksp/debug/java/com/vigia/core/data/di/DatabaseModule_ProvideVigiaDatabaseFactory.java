package com.vigia.core.data.di;

import android.content.Context;
import com.vigia.core.data.db.VigiaDatabase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class DatabaseModule_ProvideVigiaDatabaseFactory implements Factory<VigiaDatabase> {
  private final Provider<Context> contextProvider;

  public DatabaseModule_ProvideVigiaDatabaseFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public VigiaDatabase get() {
    return provideVigiaDatabase(contextProvider.get());
  }

  public static DatabaseModule_ProvideVigiaDatabaseFactory create(
      Provider<Context> contextProvider) {
    return new DatabaseModule_ProvideVigiaDatabaseFactory(contextProvider);
  }

  public static VigiaDatabase provideVigiaDatabase(Context context) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideVigiaDatabase(context));
  }
}
