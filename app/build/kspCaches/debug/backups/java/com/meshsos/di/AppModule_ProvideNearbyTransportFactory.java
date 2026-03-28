package com.meshsos.di;

import android.content.Context;
import com.meshsos.data.transport.NearbyConnectionsTransport;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata({
    "dagger.hilt.android.qualifiers.ApplicationContext",
    "javax.inject.Named"
})
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
public final class AppModule_ProvideNearbyTransportFactory implements Factory<NearbyConnectionsTransport> {
  private final Provider<Context> contextProvider;

  private final Provider<String> deviceIdProvider;

  private final Provider<String> deviceNameProvider;

  private AppModule_ProvideNearbyTransportFactory(Provider<Context> contextProvider,
      Provider<String> deviceIdProvider, Provider<String> deviceNameProvider) {
    this.contextProvider = contextProvider;
    this.deviceIdProvider = deviceIdProvider;
    this.deviceNameProvider = deviceNameProvider;
  }

  @Override
  public NearbyConnectionsTransport get() {
    return provideNearbyTransport(contextProvider.get(), deviceIdProvider.get(), deviceNameProvider.get());
  }

  public static AppModule_ProvideNearbyTransportFactory create(Provider<Context> contextProvider,
      Provider<String> deviceIdProvider, Provider<String> deviceNameProvider) {
    return new AppModule_ProvideNearbyTransportFactory(contextProvider, deviceIdProvider, deviceNameProvider);
  }

  public static NearbyConnectionsTransport provideNearbyTransport(Context context, String deviceId,
      String deviceName) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideNearbyTransport(context, deviceId, deviceName));
  }
}
