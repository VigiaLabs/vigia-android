package com.vigia.core.data;

import com.vigia.core.data.db.ChatMessageDao;
import com.vigia.core.data.db.ChatSessionDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
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
public final class ChatRepositoryImpl_Factory implements Factory<ChatRepositoryImpl> {
  private final Provider<ChatSessionDao> sessionDaoProvider;

  private final Provider<ChatMessageDao> messageDaoProvider;

  public ChatRepositoryImpl_Factory(Provider<ChatSessionDao> sessionDaoProvider,
      Provider<ChatMessageDao> messageDaoProvider) {
    this.sessionDaoProvider = sessionDaoProvider;
    this.messageDaoProvider = messageDaoProvider;
  }

  @Override
  public ChatRepositoryImpl get() {
    return newInstance(sessionDaoProvider.get(), messageDaoProvider.get());
  }

  public static ChatRepositoryImpl_Factory create(Provider<ChatSessionDao> sessionDaoProvider,
      Provider<ChatMessageDao> messageDaoProvider) {
    return new ChatRepositoryImpl_Factory(sessionDaoProvider, messageDaoProvider);
  }

  public static ChatRepositoryImpl newInstance(ChatSessionDao sessionDao,
      ChatMessageDao messageDao) {
    return new ChatRepositoryImpl(sessionDao, messageDao);
  }
}
