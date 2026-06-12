package com.vigia.core.data.di;

import com.vigia.core.data.db.ChatSessionDao;
import com.vigia.core.data.db.VigiaDatabase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class DatabaseModule_ProvideChatSessionDaoFactory implements Factory<ChatSessionDao> {
  private final Provider<VigiaDatabase> dbProvider;

  public DatabaseModule_ProvideChatSessionDaoFactory(Provider<VigiaDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public ChatSessionDao get() {
    return provideChatSessionDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideChatSessionDaoFactory create(
      Provider<VigiaDatabase> dbProvider) {
    return new DatabaseModule_ProvideChatSessionDaoFactory(dbProvider);
  }

  public static ChatSessionDao provideChatSessionDao(VigiaDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideChatSessionDao(db));
  }
}
