package com.meshsos;

import android.app.Activity;
import android.app.Service;
import android.view.View;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.meshsos.background.MeshForegroundService;
import com.meshsos.background.MeshForegroundService_MembersInjector;
import com.meshsos.data.api.SosApiServiceFactory;
import com.meshsos.data.db.MeshDatabase;
import com.meshsos.data.db.dao.MeshEventDao;
import com.meshsos.data.db.dao.PendingPacketDao;
import com.meshsos.data.transport.BleGattTransport;
import com.meshsos.data.transport.NearbyConnectionsTransport;
import com.meshsos.data.transport.TransportManager;
import com.meshsos.di.AppModule_ProvideBleGattTransportFactory;
import com.meshsos.di.AppModule_ProvideDeviceIdFactory;
import com.meshsos.di.AppModule_ProvideDeviceNameFactory;
import com.meshsos.di.AppModule_ProvideLocalDeviceIdFactory;
import com.meshsos.di.AppModule_ProvideMeshDatabaseFactory;
import com.meshsos.di.AppModule_ProvideMeshEventDaoFactory;
import com.meshsos.di.AppModule_ProvideNearbyTransportFactory;
import com.meshsos.di.AppModule_ProvidePendingPacketDaoFactory;
import com.meshsos.domain.service.AdaptiveScanStrategy;
import com.meshsos.domain.service.BatteryMonitor;
import com.meshsos.domain.service.DeduplicationService;
import com.meshsos.domain.statemachine.MeshStateMachine;
import com.meshsos.domain.usecase.RelayPacketUseCase;
import com.meshsos.domain.usecase.SendSosUseCase;
import com.meshsos.domain.usecase.UploadPacketUseCase;
import com.meshsos.presentation.MainActivity;
import com.meshsos.presentation.viewmodels.MeshViewModel;
import com.meshsos.presentation.viewmodels.MeshViewModel_HiltModules;
import com.meshsos.presentation.viewmodels.MeshViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.meshsos.presentation.viewmodels.MeshViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import dagger.hilt.android.ActivityRetainedLifecycle;
import dagger.hilt.android.ViewModelLifecycle;
import dagger.hilt.android.internal.builders.ActivityComponentBuilder;
import dagger.hilt.android.internal.builders.ActivityRetainedComponentBuilder;
import dagger.hilt.android.internal.builders.FragmentComponentBuilder;
import dagger.hilt.android.internal.builders.ServiceComponentBuilder;
import dagger.hilt.android.internal.builders.ViewComponentBuilder;
import dagger.hilt.android.internal.builders.ViewModelComponentBuilder;
import dagger.hilt.android.internal.builders.ViewWithFragmentComponentBuilder;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories_InternalFactoryFactory_Factory;
import dagger.hilt.android.internal.managers.ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory;
import dagger.hilt.android.internal.managers.SavedStateHandleHolder;
import dagger.hilt.android.internal.modules.ApplicationContextModule;
import dagger.hilt.android.internal.modules.ApplicationContextModule_ProvideContextFactory;
import dagger.internal.DaggerGenerated;
import dagger.internal.DoubleCheck;
import dagger.internal.LazyClassKeyMap;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

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
public final class DaggerMeshSosApp_HiltComponents_SingletonC {
  private DaggerMeshSosApp_HiltComponents_SingletonC() {
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private ApplicationContextModule applicationContextModule;

    private Builder() {
    }

    public Builder applicationContextModule(ApplicationContextModule applicationContextModule) {
      this.applicationContextModule = Preconditions.checkNotNull(applicationContextModule);
      return this;
    }

    public MeshSosApp_HiltComponents.SingletonC build() {
      Preconditions.checkBuilderRequirement(applicationContextModule, ApplicationContextModule.class);
      return new SingletonCImpl(applicationContextModule);
    }
  }

  private static final class ActivityRetainedCBuilder implements MeshSosApp_HiltComponents.ActivityRetainedC.Builder {
    private final SingletonCImpl singletonCImpl;

    private SavedStateHandleHolder savedStateHandleHolder;

    private ActivityRetainedCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ActivityRetainedCBuilder savedStateHandleHolder(
        SavedStateHandleHolder savedStateHandleHolder) {
      this.savedStateHandleHolder = Preconditions.checkNotNull(savedStateHandleHolder);
      return this;
    }

    @Override
    public MeshSosApp_HiltComponents.ActivityRetainedC build() {
      Preconditions.checkBuilderRequirement(savedStateHandleHolder, SavedStateHandleHolder.class);
      return new ActivityRetainedCImpl(singletonCImpl, savedStateHandleHolder);
    }
  }

  private static final class ActivityCBuilder implements MeshSosApp_HiltComponents.ActivityC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private Activity activity;

    private ActivityCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ActivityCBuilder activity(Activity activity) {
      this.activity = Preconditions.checkNotNull(activity);
      return this;
    }

    @Override
    public MeshSosApp_HiltComponents.ActivityC build() {
      Preconditions.checkBuilderRequirement(activity, Activity.class);
      return new ActivityCImpl(singletonCImpl, activityRetainedCImpl, activity);
    }
  }

  private static final class FragmentCBuilder implements MeshSosApp_HiltComponents.FragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private Fragment fragment;

    private FragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public FragmentCBuilder fragment(Fragment fragment) {
      this.fragment = Preconditions.checkNotNull(fragment);
      return this;
    }

    @Override
    public MeshSosApp_HiltComponents.FragmentC build() {
      Preconditions.checkBuilderRequirement(fragment, Fragment.class);
      return new FragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragment);
    }
  }

  private static final class ViewWithFragmentCBuilder implements MeshSosApp_HiltComponents.ViewWithFragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private View view;

    private ViewWithFragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;
    }

    @Override
    public ViewWithFragmentCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public MeshSosApp_HiltComponents.ViewWithFragmentC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewWithFragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl, view);
    }
  }

  private static final class ViewCBuilder implements MeshSosApp_HiltComponents.ViewC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private View view;

    private ViewCBuilder(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public ViewCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public MeshSosApp_HiltComponents.ViewC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, view);
    }
  }

  private static final class ViewModelCBuilder implements MeshSosApp_HiltComponents.ViewModelC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private SavedStateHandle savedStateHandle;

    private ViewModelLifecycle viewModelLifecycle;

    private ViewModelCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ViewModelCBuilder savedStateHandle(SavedStateHandle handle) {
      this.savedStateHandle = Preconditions.checkNotNull(handle);
      return this;
    }

    @Override
    public ViewModelCBuilder viewModelLifecycle(ViewModelLifecycle viewModelLifecycle) {
      this.viewModelLifecycle = Preconditions.checkNotNull(viewModelLifecycle);
      return this;
    }

    @Override
    public MeshSosApp_HiltComponents.ViewModelC build() {
      Preconditions.checkBuilderRequirement(savedStateHandle, SavedStateHandle.class);
      Preconditions.checkBuilderRequirement(viewModelLifecycle, ViewModelLifecycle.class);
      return new ViewModelCImpl(singletonCImpl, activityRetainedCImpl, savedStateHandle, viewModelLifecycle);
    }
  }

  private static final class ServiceCBuilder implements MeshSosApp_HiltComponents.ServiceC.Builder {
    private final SingletonCImpl singletonCImpl;

    private Service service;

    private ServiceCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ServiceCBuilder service(Service service) {
      this.service = Preconditions.checkNotNull(service);
      return this;
    }

    @Override
    public MeshSosApp_HiltComponents.ServiceC build() {
      Preconditions.checkBuilderRequirement(service, Service.class);
      return new ServiceCImpl(singletonCImpl, service);
    }
  }

  private static final class ViewWithFragmentCImpl extends MeshSosApp_HiltComponents.ViewWithFragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private final ViewWithFragmentCImpl viewWithFragmentCImpl = this;

    ViewWithFragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;


    }
  }

  private static final class FragmentCImpl extends MeshSosApp_HiltComponents.FragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl = this;

    FragmentCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl, Fragment fragmentParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return activityCImpl.getHiltInternalFactoryFactory();
    }

    @Override
    public ViewWithFragmentComponentBuilder viewWithFragmentComponentBuilder() {
      return new ViewWithFragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl);
    }
  }

  private static final class ViewCImpl extends MeshSosApp_HiltComponents.ViewC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final ViewCImpl viewCImpl = this;

    ViewCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }
  }

  private static final class ActivityCImpl extends MeshSosApp_HiltComponents.ActivityC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl = this;

    ActivityCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        Activity activityParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;


    }

    @Override
    public void injectMainActivity(MainActivity mainActivity) {
    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return DefaultViewModelFactories_InternalFactoryFactory_Factory.newInstance(getViewModelKeys(), new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl));
    }

    @Override
    public Map<Class<?>, Boolean> getViewModelKeys() {
      return LazyClassKeyMap.<Boolean>of(Collections.<String, Boolean>singletonMap(MeshViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, MeshViewModel_HiltModules.KeyModule.provide()));
    }

    @Override
    public ViewModelComponentBuilder getViewModelComponentBuilder() {
      return new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public FragmentComponentBuilder fragmentComponentBuilder() {
      return new FragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    @Override
    public ViewComponentBuilder viewComponentBuilder() {
      return new ViewCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }
  }

  private static final class ViewModelCImpl extends MeshSosApp_HiltComponents.ViewModelC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ViewModelCImpl viewModelCImpl = this;

    Provider<MeshViewModel> meshViewModelProvider;

    ViewModelCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        SavedStateHandle savedStateHandleParam, ViewModelLifecycle viewModelLifecycleParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;

      initialize(savedStateHandleParam, viewModelLifecycleParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandle savedStateHandleParam,
        final ViewModelLifecycle viewModelLifecycleParam) {
      this.meshViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 0);
    }

    @Override
    public Map<Class<?>, javax.inject.Provider<ViewModel>> getHiltViewModelMap() {
      return LazyClassKeyMap.<javax.inject.Provider<ViewModel>>of(Collections.<String, javax.inject.Provider<ViewModel>>singletonMap(MeshViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (meshViewModelProvider))));
    }

    @Override
    public Map<Class<?>, Object> getHiltViewModelAssistedMap() {
      return Collections.<Class<?>, Object>emptyMap();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final ViewModelCImpl viewModelCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          ViewModelCImpl viewModelCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.viewModelCImpl = viewModelCImpl;
        this.id = id;
      }

      @Override
      @SuppressWarnings("unchecked")
      public T get() {
        switch (id) {
          case 0: // com.meshsos.presentation.viewmodels.MeshViewModel
          return (T) new MeshViewModel(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.meshStateMachineProvider.get(), singletonCImpl.transportManagerProvider.get(), singletonCImpl.sendSosUseCaseProvider.get(), singletonCImpl.batteryMonitorProvider.get(), singletonCImpl.adaptiveScanStrategyProvider.get(), singletonCImpl.provideMeshEventDaoProvider.get(), singletonCImpl.providePendingPacketDaoProvider.get(), singletonCImpl.provideLocalDeviceIdProvider.get());

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ActivityRetainedCImpl extends MeshSosApp_HiltComponents.ActivityRetainedC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl = this;

    Provider<ActivityRetainedLifecycle> provideActivityRetainedLifecycleProvider;

    ActivityRetainedCImpl(SingletonCImpl singletonCImpl,
        SavedStateHandleHolder savedStateHandleHolderParam) {
      this.singletonCImpl = singletonCImpl;

      initialize(savedStateHandleHolderParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandleHolder savedStateHandleHolderParam) {
      this.provideActivityRetainedLifecycleProvider = DoubleCheck.provider(new SwitchingProvider<ActivityRetainedLifecycle>(singletonCImpl, activityRetainedCImpl, 0));
    }

    @Override
    public ActivityComponentBuilder activityComponentBuilder() {
      return new ActivityCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public ActivityRetainedLifecycle getActivityRetainedLifecycle() {
      return provideActivityRetainedLifecycleProvider.get();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.id = id;
      }

      @Override
      @SuppressWarnings("unchecked")
      public T get() {
        switch (id) {
          case 0: // dagger.hilt.android.ActivityRetainedLifecycle
          return (T) ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory.provideActivityRetainedLifecycle();

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ServiceCImpl extends MeshSosApp_HiltComponents.ServiceC {
    private final SingletonCImpl singletonCImpl;

    private final ServiceCImpl serviceCImpl = this;

    ServiceCImpl(SingletonCImpl singletonCImpl, Service serviceParam) {
      this.singletonCImpl = singletonCImpl;


    }

    @Override
    public void injectMeshForegroundService(MeshForegroundService meshForegroundService) {
      injectMeshForegroundService2(meshForegroundService);
    }

    private MeshForegroundService injectMeshForegroundService2(MeshForegroundService instance) {
      MeshForegroundService_MembersInjector.injectTransportManager(instance, singletonCImpl.transportManagerProvider.get());
      MeshForegroundService_MembersInjector.injectStateMachine(instance, singletonCImpl.meshStateMachineProvider.get());
      MeshForegroundService_MembersInjector.injectUploadUseCase(instance, singletonCImpl.uploadPacketUseCaseProvider.get());
      MeshForegroundService_MembersInjector.injectMeshEventDao(instance, singletonCImpl.provideMeshEventDaoProvider.get());
      MeshForegroundService_MembersInjector.injectBatteryMonitor(instance, singletonCImpl.batteryMonitorProvider.get());
      MeshForegroundService_MembersInjector.injectAdaptiveScanStrategy(instance, singletonCImpl.adaptiveScanStrategyProvider.get());
      MeshForegroundService_MembersInjector.injectLocalDeviceId(instance, singletonCImpl.provideLocalDeviceIdProvider.get());
      return instance;
    }
  }

  private static final class SingletonCImpl extends MeshSosApp_HiltComponents.SingletonC {
    private final ApplicationContextModule applicationContextModule;

    private final SingletonCImpl singletonCImpl = this;

    Provider<DeduplicationService> deduplicationServiceProvider;

    Provider<String> provideDeviceIdProvider;

    Provider<String> provideDeviceNameProvider;

    Provider<NearbyConnectionsTransport> provideNearbyTransportProvider;

    Provider<BleGattTransport> provideBleGattTransportProvider;

    Provider<TransportManager> transportManagerProvider;

    Provider<RelayPacketUseCase> relayPacketUseCaseProvider;

    Provider<SosApiServiceFactory> sosApiServiceFactoryProvider;

    Provider<MeshDatabase> provideMeshDatabaseProvider;

    Provider<PendingPacketDao> providePendingPacketDaoProvider;

    Provider<String> provideLocalDeviceIdProvider;

    Provider<UploadPacketUseCase> uploadPacketUseCaseProvider;

    Provider<MeshStateMachine> meshStateMachineProvider;

    Provider<BatteryMonitor> batteryMonitorProvider;

    Provider<SendSosUseCase> sendSosUseCaseProvider;

    Provider<AdaptiveScanStrategy> adaptiveScanStrategyProvider;

    Provider<MeshEventDao> provideMeshEventDaoProvider;

    SingletonCImpl(ApplicationContextModule applicationContextModuleParam) {
      this.applicationContextModule = applicationContextModuleParam;
      initialize(applicationContextModuleParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final ApplicationContextModule applicationContextModuleParam) {
      this.deduplicationServiceProvider = DoubleCheck.provider(new SwitchingProvider<DeduplicationService>(singletonCImpl, 1));
      this.provideDeviceIdProvider = DoubleCheck.provider(new SwitchingProvider<String>(singletonCImpl, 5));
      this.provideDeviceNameProvider = DoubleCheck.provider(new SwitchingProvider<String>(singletonCImpl, 6));
      this.provideNearbyTransportProvider = DoubleCheck.provider(new SwitchingProvider<NearbyConnectionsTransport>(singletonCImpl, 4));
      this.provideBleGattTransportProvider = DoubleCheck.provider(new SwitchingProvider<BleGattTransport>(singletonCImpl, 7));
      this.transportManagerProvider = DoubleCheck.provider(new SwitchingProvider<TransportManager>(singletonCImpl, 3));
      this.relayPacketUseCaseProvider = DoubleCheck.provider(new SwitchingProvider<RelayPacketUseCase>(singletonCImpl, 2));
      this.sosApiServiceFactoryProvider = DoubleCheck.provider(new SwitchingProvider<SosApiServiceFactory>(singletonCImpl, 9));
      this.provideMeshDatabaseProvider = DoubleCheck.provider(new SwitchingProvider<MeshDatabase>(singletonCImpl, 11));
      this.providePendingPacketDaoProvider = DoubleCheck.provider(new SwitchingProvider<PendingPacketDao>(singletonCImpl, 10));
      this.provideLocalDeviceIdProvider = DoubleCheck.provider(new SwitchingProvider<String>(singletonCImpl, 12));
      this.uploadPacketUseCaseProvider = DoubleCheck.provider(new SwitchingProvider<UploadPacketUseCase>(singletonCImpl, 8));
      this.meshStateMachineProvider = DoubleCheck.provider(new SwitchingProvider<MeshStateMachine>(singletonCImpl, 0));
      this.batteryMonitorProvider = DoubleCheck.provider(new SwitchingProvider<BatteryMonitor>(singletonCImpl, 14));
      this.sendSosUseCaseProvider = DoubleCheck.provider(new SwitchingProvider<SendSosUseCase>(singletonCImpl, 13));
      this.adaptiveScanStrategyProvider = DoubleCheck.provider(new SwitchingProvider<AdaptiveScanStrategy>(singletonCImpl, 15));
      this.provideMeshEventDaoProvider = DoubleCheck.provider(new SwitchingProvider<MeshEventDao>(singletonCImpl, 16));
    }

    @Override
    public void injectMeshSosApp(MeshSosApp meshSosApp) {
    }

    @Override
    public Set<Boolean> getDisableFragmentGetContextFix() {
      return Collections.<Boolean>emptySet();
    }

    @Override
    public ActivityRetainedComponentBuilder retainedComponentBuilder() {
      return new ActivityRetainedCBuilder(singletonCImpl);
    }

    @Override
    public ServiceComponentBuilder serviceComponentBuilder() {
      return new ServiceCBuilder(singletonCImpl);
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.id = id;
      }

      @Override
      @SuppressWarnings("unchecked")
      public T get() {
        switch (id) {
          case 0: // com.meshsos.domain.statemachine.MeshStateMachine
          return (T) new MeshStateMachine(singletonCImpl.deduplicationServiceProvider.get(), singletonCImpl.relayPacketUseCaseProvider.get(), singletonCImpl.uploadPacketUseCaseProvider.get(), singletonCImpl.provideLocalDeviceIdProvider.get());

          case 1: // com.meshsos.domain.service.DeduplicationService
          return (T) new DeduplicationService();

          case 2: // com.meshsos.domain.usecase.RelayPacketUseCase
          return (T) new RelayPacketUseCase(singletonCImpl.transportManagerProvider.get());

          case 3: // com.meshsos.data.transport.TransportManager
          return (T) new TransportManager(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.provideNearbyTransportProvider.get(), singletonCImpl.provideBleGattTransportProvider.get());

          case 4: // com.meshsos.data.transport.NearbyConnectionsTransport
          return (T) AppModule_ProvideNearbyTransportFactory.provideNearbyTransport(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.provideDeviceIdProvider.get(), singletonCImpl.provideDeviceNameProvider.get());

          case 5: // @javax.inject.Named("deviceId") java.lang.String
          return (T) AppModule_ProvideDeviceIdFactory.provideDeviceId(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 6: // @javax.inject.Named("deviceName") java.lang.String
          return (T) AppModule_ProvideDeviceNameFactory.provideDeviceName(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 7: // com.meshsos.data.transport.BleGattTransport
          return (T) AppModule_ProvideBleGattTransportFactory.provideBleGattTransport(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 8: // com.meshsos.domain.usecase.UploadPacketUseCase
          return (T) new UploadPacketUseCase(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.sosApiServiceFactoryProvider.get(), singletonCImpl.providePendingPacketDaoProvider.get(), singletonCImpl.provideLocalDeviceIdProvider.get(), singletonCImpl.provideLocalDeviceIdProvider.get());

          case 9: // com.meshsos.data.api.SosApiServiceFactory
          return (T) new SosApiServiceFactory();

          case 10: // com.meshsos.data.db.dao.PendingPacketDao
          return (T) AppModule_ProvidePendingPacketDaoFactory.providePendingPacketDao(singletonCImpl.provideMeshDatabaseProvider.get());

          case 11: // com.meshsos.data.db.MeshDatabase
          return (T) AppModule_ProvideMeshDatabaseFactory.provideMeshDatabase(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 12: // java.lang.String
          return (T) AppModule_ProvideLocalDeviceIdFactory.provideLocalDeviceId(singletonCImpl.provideDeviceIdProvider.get());

          case 13: // com.meshsos.domain.usecase.SendSosUseCase
          return (T) new SendSosUseCase(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.meshStateMachineProvider.get(), singletonCImpl.transportManagerProvider.get(), singletonCImpl.deduplicationServiceProvider.get(), singletonCImpl.provideLocalDeviceIdProvider.get(), singletonCImpl.batteryMonitorProvider.get());

          case 14: // com.meshsos.domain.service.BatteryMonitor
          return (T) new BatteryMonitor(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 15: // com.meshsos.domain.service.AdaptiveScanStrategy
          return (T) new AdaptiveScanStrategy();

          case 16: // com.meshsos.data.db.dao.MeshEventDao
          return (T) AppModule_ProvideMeshEventDaoFactory.provideMeshEventDao(singletonCImpl.provideMeshDatabaseProvider.get());

          default: throw new AssertionError(id);
        }
      }
    }
  }
}
