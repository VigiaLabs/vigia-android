package com.vigia.core.sensor.service;

import com.vigia.core.sensor.BlackboxConfig;
import com.vigia.core.sensor.ble.BleRepository;
import com.vigia.core.sensor.cdm.CdmPresenceRepository;
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
public final class VigiaForegroundService_MembersInjector implements MembersInjector<VigiaForegroundService> {
  private final Provider<CdmPresenceRepository> cdmRepositoryProvider;

  private final Provider<BleRepository> bleRepositoryProvider;

  private final Provider<BlackboxConfig> blackboxConfigProvider;

  public VigiaForegroundService_MembersInjector(
      Provider<CdmPresenceRepository> cdmRepositoryProvider,
      Provider<BleRepository> bleRepositoryProvider,
      Provider<BlackboxConfig> blackboxConfigProvider) {
    this.cdmRepositoryProvider = cdmRepositoryProvider;
    this.bleRepositoryProvider = bleRepositoryProvider;
    this.blackboxConfigProvider = blackboxConfigProvider;
  }

  public static MembersInjector<VigiaForegroundService> create(
      Provider<CdmPresenceRepository> cdmRepositoryProvider,
      Provider<BleRepository> bleRepositoryProvider,
      Provider<BlackboxConfig> blackboxConfigProvider) {
    return new VigiaForegroundService_MembersInjector(cdmRepositoryProvider, bleRepositoryProvider, blackboxConfigProvider);
  }

  @Override
  public void injectMembers(VigiaForegroundService instance) {
    injectCdmRepository(instance, cdmRepositoryProvider.get());
    injectBleRepository(instance, bleRepositoryProvider.get());
    injectBlackboxConfig(instance, blackboxConfigProvider.get());
  }

  @InjectedFieldSignature("com.vigia.core.sensor.service.VigiaForegroundService.cdmRepository")
  public static void injectCdmRepository(VigiaForegroundService instance,
      CdmPresenceRepository cdmRepository) {
    instance.cdmRepository = cdmRepository;
  }

  @InjectedFieldSignature("com.vigia.core.sensor.service.VigiaForegroundService.bleRepository")
  public static void injectBleRepository(VigiaForegroundService instance,
      BleRepository bleRepository) {
    instance.bleRepository = bleRepository;
  }

  @InjectedFieldSignature("com.vigia.core.sensor.service.VigiaForegroundService.blackboxConfig")
  public static void injectBlackboxConfig(VigiaForegroundService instance,
      BlackboxConfig blackboxConfig) {
    instance.blackboxConfig = blackboxConfig;
  }
}
