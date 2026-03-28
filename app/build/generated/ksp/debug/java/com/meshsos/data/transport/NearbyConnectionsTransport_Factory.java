package com.meshsos.data.transport;

import android.content.Context;
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
public final class NearbyConnectionsTransport_Factory implements Factory<NearbyConnectionsTransport> {
  private final Provider<Context> contextProvider;

  private final Provider<String> localDeviceIdProvider;

  private final Provider<String> localDeviceNameProvider;

  private NearbyConnectionsTransport_Factory(Provider<Context> contextProvider,
      Provider<String> localDeviceIdProvider, Provider<String> localDeviceNameProvider) {
    this.contextProvider = contextProvider;
    this.localDeviceIdProvider = localDeviceIdProvider;
    this.localDeviceNameProvider = localDeviceNameProvider;
  }

  @Override
  public NearbyConnectionsTransport get() {
    return newInstance(contextProvider.get(), localDeviceIdProvider.get(), localDeviceNameProvider.get());
  }

  public static NearbyConnectionsTransport_Factory create(Provider<Context> contextProvider,
      Provider<String> localDeviceIdProvider, Provider<String> localDeviceNameProvider) {
    return new NearbyConnectionsTransport_Factory(contextProvider, localDeviceIdProvider, localDeviceNameProvider);
  }

  public static NearbyConnectionsTransport newInstance(Context context, String localDeviceId,
      String localDeviceName) {
    return new NearbyConnectionsTransport(context, localDeviceId, localDeviceName);
  }
}
