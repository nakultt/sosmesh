package com.meshsos.domain.statemachine;

import com.meshsos.domain.service.DeduplicationService;
import com.meshsos.domain.usecase.RelayPacketUseCase;
import com.meshsos.domain.usecase.UploadPacketUseCase;
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
public final class MeshStateMachine_Factory implements Factory<MeshStateMachine> {
  private final Provider<DeduplicationService> deduplicationServiceProvider;

  private final Provider<RelayPacketUseCase> relayPacketUseCaseProvider;

  private final Provider<UploadPacketUseCase> uploadPacketUseCaseProvider;

  private final Provider<String> deviceIdProvider;

  private MeshStateMachine_Factory(Provider<DeduplicationService> deduplicationServiceProvider,
      Provider<RelayPacketUseCase> relayPacketUseCaseProvider,
      Provider<UploadPacketUseCase> uploadPacketUseCaseProvider,
      Provider<String> deviceIdProvider) {
    this.deduplicationServiceProvider = deduplicationServiceProvider;
    this.relayPacketUseCaseProvider = relayPacketUseCaseProvider;
    this.uploadPacketUseCaseProvider = uploadPacketUseCaseProvider;
    this.deviceIdProvider = deviceIdProvider;
  }

  @Override
  public MeshStateMachine get() {
    return newInstance(deduplicationServiceProvider.get(), relayPacketUseCaseProvider.get(), uploadPacketUseCaseProvider.get(), deviceIdProvider.get());
  }

  public static MeshStateMachine_Factory create(
      Provider<DeduplicationService> deduplicationServiceProvider,
      Provider<RelayPacketUseCase> relayPacketUseCaseProvider,
      Provider<UploadPacketUseCase> uploadPacketUseCaseProvider,
      Provider<String> deviceIdProvider) {
    return new MeshStateMachine_Factory(deduplicationServiceProvider, relayPacketUseCaseProvider, uploadPacketUseCaseProvider, deviceIdProvider);
  }

  public static MeshStateMachine newInstance(DeduplicationService deduplicationService,
      RelayPacketUseCase relayPacketUseCase, UploadPacketUseCase uploadPacketUseCase,
      String deviceId) {
    return new MeshStateMachine(deduplicationService, relayPacketUseCase, uploadPacketUseCase, deviceId);
  }
}
