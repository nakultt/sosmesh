package com.meshsos.presentation.viewmodels;

import android.content.Context;
import com.meshsos.data.db.dao.MeshEventDao;
import com.meshsos.data.db.dao.PendingPacketDao;
import com.meshsos.data.transport.TransportManager;
import com.meshsos.domain.service.AdaptiveScanStrategy;
import com.meshsos.domain.service.BatteryMonitor;
import com.meshsos.domain.statemachine.MeshStateMachine;
import com.meshsos.domain.usecase.SendSosUseCase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
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
public final class MeshViewModel_Factory implements Factory<MeshViewModel> {
  private final Provider<Context> contextProvider;

  private final Provider<MeshStateMachine> stateMachineProvider;

  private final Provider<TransportManager> transportManagerProvider;

  private final Provider<SendSosUseCase> sendSosUseCaseProvider;

  private final Provider<BatteryMonitor> batteryMonitorProvider;

  private final Provider<AdaptiveScanStrategy> adaptiveScanStrategyProvider;

  private final Provider<MeshEventDao> meshEventDaoProvider;

  private final Provider<PendingPacketDao> pendingPacketDaoProvider;

  private final Provider<String> localDeviceIdProvider;

  private MeshViewModel_Factory(Provider<Context> contextProvider,
      Provider<MeshStateMachine> stateMachineProvider,
      Provider<TransportManager> transportManagerProvider,
      Provider<SendSosUseCase> sendSosUseCaseProvider,
      Provider<BatteryMonitor> batteryMonitorProvider,
      Provider<AdaptiveScanStrategy> adaptiveScanStrategyProvider,
      Provider<MeshEventDao> meshEventDaoProvider,
      Provider<PendingPacketDao> pendingPacketDaoProvider, Provider<String> localDeviceIdProvider) {
    this.contextProvider = contextProvider;
    this.stateMachineProvider = stateMachineProvider;
    this.transportManagerProvider = transportManagerProvider;
    this.sendSosUseCaseProvider = sendSosUseCaseProvider;
    this.batteryMonitorProvider = batteryMonitorProvider;
    this.adaptiveScanStrategyProvider = adaptiveScanStrategyProvider;
    this.meshEventDaoProvider = meshEventDaoProvider;
    this.pendingPacketDaoProvider = pendingPacketDaoProvider;
    this.localDeviceIdProvider = localDeviceIdProvider;
  }

  @Override
  public MeshViewModel get() {
    return newInstance(contextProvider.get(), stateMachineProvider.get(), transportManagerProvider.get(), sendSosUseCaseProvider.get(), batteryMonitorProvider.get(), adaptiveScanStrategyProvider.get(), meshEventDaoProvider.get(), pendingPacketDaoProvider.get(), localDeviceIdProvider.get());
  }

  public static MeshViewModel_Factory create(Provider<Context> contextProvider,
      Provider<MeshStateMachine> stateMachineProvider,
      Provider<TransportManager> transportManagerProvider,
      Provider<SendSosUseCase> sendSosUseCaseProvider,
      Provider<BatteryMonitor> batteryMonitorProvider,
      Provider<AdaptiveScanStrategy> adaptiveScanStrategyProvider,
      Provider<MeshEventDao> meshEventDaoProvider,
      Provider<PendingPacketDao> pendingPacketDaoProvider, Provider<String> localDeviceIdProvider) {
    return new MeshViewModel_Factory(contextProvider, stateMachineProvider, transportManagerProvider, sendSosUseCaseProvider, batteryMonitorProvider, adaptiveScanStrategyProvider, meshEventDaoProvider, pendingPacketDaoProvider, localDeviceIdProvider);
  }

  public static MeshViewModel newInstance(Context context, MeshStateMachine stateMachine,
      TransportManager transportManager, SendSosUseCase sendSosUseCase,
      BatteryMonitor batteryMonitor, AdaptiveScanStrategy adaptiveScanStrategy,
      MeshEventDao meshEventDao, PendingPacketDao pendingPacketDao, String localDeviceId) {
    return new MeshViewModel(context, stateMachine, transportManager, sendSosUseCase, batteryMonitor, adaptiveScanStrategy, meshEventDao, pendingPacketDao, localDeviceId);
  }
}
