package com.meshsos.domain.usecase;

import com.meshsos.data.transport.TransportManager;
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
public final class RelayPacketUseCase_Factory implements Factory<RelayPacketUseCase> {
  private final Provider<TransportManager> transportManagerProvider;

  private RelayPacketUseCase_Factory(Provider<TransportManager> transportManagerProvider) {
    this.transportManagerProvider = transportManagerProvider;
  }

  @Override
  public RelayPacketUseCase get() {
    return newInstance(transportManagerProvider.get());
  }

  public static RelayPacketUseCase_Factory create(
      Provider<TransportManager> transportManagerProvider) {
    return new RelayPacketUseCase_Factory(transportManagerProvider);
  }

  public static RelayPacketUseCase newInstance(TransportManager transportManager) {
    return new RelayPacketUseCase(transportManager);
  }
}
