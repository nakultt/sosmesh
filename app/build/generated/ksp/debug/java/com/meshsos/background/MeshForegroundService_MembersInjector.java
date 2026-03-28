package com.meshsos.background;

import com.meshsos.data.db.dao.MeshEventDao;
import com.meshsos.data.transport.TransportManager;
import com.meshsos.domain.service.AdaptiveScanStrategy;
import com.meshsos.domain.service.BatteryMonitor;
import com.meshsos.domain.statemachine.MeshStateMachine;
import com.meshsos.domain.usecase.UploadPacketUseCase;
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
public final class MeshForegroundService_MembersInjector implements MembersInjector<MeshForegroundService> {
  private final Provider<TransportManager> transportManagerProvider;

  private final Provider<MeshStateMachine> stateMachineProvider;

  private final Provider<UploadPacketUseCase> uploadUseCaseProvider;

  private final Provider<MeshEventDao> meshEventDaoProvider;

  private final Provider<BatteryMonitor> batteryMonitorProvider;

  private final Provider<AdaptiveScanStrategy> adaptiveScanStrategyProvider;

  private final Provider<String> localDeviceIdProvider;

  private MeshForegroundService_MembersInjector(Provider<TransportManager> transportManagerProvider,
      Provider<MeshStateMachine> stateMachineProvider,
      Provider<UploadPacketUseCase> uploadUseCaseProvider,
      Provider<MeshEventDao> meshEventDaoProvider, Provider<BatteryMonitor> batteryMonitorProvider,
      Provider<AdaptiveScanStrategy> adaptiveScanStrategyProvider,
      Provider<String> localDeviceIdProvider) {
    this.transportManagerProvider = transportManagerProvider;
    this.stateMachineProvider = stateMachineProvider;
    this.uploadUseCaseProvider = uploadUseCaseProvider;
    this.meshEventDaoProvider = meshEventDaoProvider;
    this.batteryMonitorProvider = batteryMonitorProvider;
    this.adaptiveScanStrategyProvider = adaptiveScanStrategyProvider;
    this.localDeviceIdProvider = localDeviceIdProvider;
  }

  @Override
  public void injectMembers(MeshForegroundService instance) {
    injectTransportManager(instance, transportManagerProvider.get());
    injectStateMachine(instance, stateMachineProvider.get());
    injectUploadUseCase(instance, uploadUseCaseProvider.get());
    injectMeshEventDao(instance, meshEventDaoProvider.get());
    injectBatteryMonitor(instance, batteryMonitorProvider.get());
    injectAdaptiveScanStrategy(instance, adaptiveScanStrategyProvider.get());
    injectLocalDeviceId(instance, localDeviceIdProvider.get());
  }

  public static MembersInjector<MeshForegroundService> create(
      Provider<TransportManager> transportManagerProvider,
      Provider<MeshStateMachine> stateMachineProvider,
      Provider<UploadPacketUseCase> uploadUseCaseProvider,
      Provider<MeshEventDao> meshEventDaoProvider, Provider<BatteryMonitor> batteryMonitorProvider,
      Provider<AdaptiveScanStrategy> adaptiveScanStrategyProvider,
      Provider<String> localDeviceIdProvider) {
    return new MeshForegroundService_MembersInjector(transportManagerProvider, stateMachineProvider, uploadUseCaseProvider, meshEventDaoProvider, batteryMonitorProvider, adaptiveScanStrategyProvider, localDeviceIdProvider);
  }

  @InjectedFieldSignature("com.meshsos.background.MeshForegroundService.transportManager")
  public static void injectTransportManager(MeshForegroundService instance,
      TransportManager transportManager) {
    instance.transportManager = transportManager;
  }

  @InjectedFieldSignature("com.meshsos.background.MeshForegroundService.stateMachine")
  public static void injectStateMachine(MeshForegroundService instance,
      MeshStateMachine stateMachine) {
    instance.stateMachine = stateMachine;
  }

  @InjectedFieldSignature("com.meshsos.background.MeshForegroundService.uploadUseCase")
  public static void injectUploadUseCase(MeshForegroundService instance,
      UploadPacketUseCase uploadUseCase) {
    instance.uploadUseCase = uploadUseCase;
  }

  @InjectedFieldSignature("com.meshsos.background.MeshForegroundService.meshEventDao")
  public static void injectMeshEventDao(MeshForegroundService instance, MeshEventDao meshEventDao) {
    instance.meshEventDao = meshEventDao;
  }

  @InjectedFieldSignature("com.meshsos.background.MeshForegroundService.batteryMonitor")
  public static void injectBatteryMonitor(MeshForegroundService instance,
      BatteryMonitor batteryMonitor) {
    instance.batteryMonitor = batteryMonitor;
  }

  @InjectedFieldSignature("com.meshsos.background.MeshForegroundService.adaptiveScanStrategy")
  public static void injectAdaptiveScanStrategy(MeshForegroundService instance,
      AdaptiveScanStrategy adaptiveScanStrategy) {
    instance.adaptiveScanStrategy = adaptiveScanStrategy;
  }

  @InjectedFieldSignature("com.meshsos.background.MeshForegroundService.localDeviceId")
  public static void injectLocalDeviceId(MeshForegroundService instance, String localDeviceId) {
    instance.localDeviceId = localDeviceId;
  }
}
