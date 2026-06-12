package com.vigia.core.data.di;

import com.vigia.core.data.db.ChatMessageDao;
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
public final class DatabaseModule_ProvideChatMessageDaoFactory implements Factory<ChatMessageDao> {
  private final Provider<VigiaDatabase> dbProvider;

  public DatabaseModule_ProvideChatMessageDaoFactory(Provider<VigiaDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public ChatMessageDao get() {
    return provideChatMessageDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideChatMessageDaoFactory create(
      Provider<VigiaDatabase> dbProvider) {
    return new DatabaseModule_ProvideChatMessageDaoFactory(dbProvider);
  }

  public static ChatMessageDao provideChatMessageDao(VigiaDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideChatMessageDao(db));
  }
}
