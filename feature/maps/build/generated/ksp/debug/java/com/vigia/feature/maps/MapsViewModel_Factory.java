package com.vigia.feature.maps;

import com.vigia.core.sensor.context.ContextAggregator;
import com.vigia.feature.maps.data.MapsRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class MapsViewModel_Factory implements Factory<MapsViewModel> {
  private final Provider<MapsRepository> repositoryProvider;

  private final Provider<ContextAggregator> contextAggregatorProvider;

  public MapsViewModel_Factory(Provider<MapsRepository> repositoryProvider,
      Provider<ContextAggregator> contextAggregatorProvider) {
    this.repositoryProvider = repositoryProvider;
    this.contextAggregatorProvider = contextAggregatorProvider;
  }

  @Override
  public MapsViewModel get() {
    return newInstance(repositoryProvider.get(), contextAggregatorProvider.get());
  }

  public static MapsViewModel_Factory create(Provider<MapsRepository> repositoryProvider,
      Provider<ContextAggregator> contextAggregatorProvider) {
    return new MapsViewModel_Factory(repositoryProvider, contextAggregatorProvider);
  }

  public static MapsViewModel newInstance(MapsRepository repository,
      ContextAggregator contextAggregator) {
    return new MapsViewModel(repository, contextAggregator);
  }
}
