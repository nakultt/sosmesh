package com.meshsos.domain.usecase;

import android.content.Context;
import com.meshsos.data.transport.TransportManager;
import com.meshsos.domain.service.BatteryMonitor;
import com.meshsos.domain.service.DeduplicationService;
import com.meshsos.domain.statemachine.MeshStateMachine;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
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
public final class SendSosUseCase_Factory implements Factory<SendSosUseCase> {
  private final Provider<Context> contextProvider;

  private final Provider<MeshStateMachine> stateMachineProvider;

  private final Provider<TransportManager> transportManagerProvider;

  private final Provider<DeduplicationService> deduplicationServiceProvider;

  private final Provider<String> localDeviceIdProvider;

  private final Provider<BatteryMonitor> batteryMonitorProvider;

  private SendSosUseCase_Factory(Provider<Context> contextProvider,
      Provider<MeshStateMachine> stateMachineProvider,
      Provider<TransportManager> transportManagerProvider,
      Provider<DeduplicationService> deduplicationServiceProvider,
      Provider<String> localDeviceIdProvider, Provider<BatteryMonitor> batteryMonitorProvider) {
    this.contextProvider = contextProvider;
    this.stateMachineProvider = stateMachineProvider;
    this.transportManagerProvider = transportManagerProvider;
    this.deduplicationServiceProvider = deduplicationServiceProvider;
    this.localDeviceIdProvider = localDeviceIdProvider;
    this.batteryMonitorProvider = batteryMonitorProvider;
  }

  @Override
  public SendSosUseCase get() {
    return newInstance(contextProvider.get(), stateMachineProvider.get(), transportManagerProvider.get(), deduplicationServiceProvider.get(), localDeviceIdProvider.get(), batteryMonitorProvider.get());
  }

  public static SendSosUseCase_Factory create(Provider<Context> contextProvider,
      Provider<MeshStateMachine> stateMachineProvider,
      Provider<TransportManager> transportManagerProvider,
      Provider<DeduplicationService> deduplicationServiceProvider,
      Provider<String> localDeviceIdProvider, Provider<BatteryMonitor> batteryMonitorProvider) {
    return new SendSosUseCase_Factory(contextProvider, stateMachineProvider, transportManagerProvider, deduplicationServiceProvider, localDeviceIdProvider, batteryMonitorProvider);
  }

  public static SendSosUseCase newInstance(Context context, MeshStateMachine stateMachine,
      TransportManager transportManager, DeduplicationService deduplicationService,
      String localDeviceId, BatteryMonitor batteryMonitor) {
    return new SendSosUseCase(context, stateMachine, transportManager, deduplicationService, localDeviceId, batteryMonitor);
  }
}
