package com.vigia.core.sensor.cdm;

import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;

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
public final class CdmPresenceService_MembersInjector implements MembersInjector<CdmPresenceService> {
  private final Provider<CdmPresenceRepositoryImpl> repositoryProvider;

  public CdmPresenceService_MembersInjector(
      Provider<CdmPresenceRepositoryImpl> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  public static MembersInjector<CdmPresenceService> create(
      Provider<CdmPresenceRepositoryImpl> repositoryProvider) {
    return new CdmPresenceService_MembersInjector(repositoryProvider);
  }

  @Override
  public void injectMembers(CdmPresenceService instance) {
    injectRepository(instance, repositoryProvider.get());
  }

  @InjectedFieldSignature("com.vigia.core.sensor.cdm.CdmPresenceService.repository")
  public static void injectRepository(CdmPresenceService instance,
      CdmPresenceRepositoryImpl repository) {
    instance.repository = repository;
  }
}
