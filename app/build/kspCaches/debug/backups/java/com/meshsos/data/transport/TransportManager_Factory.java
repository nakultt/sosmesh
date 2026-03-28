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
public final class TransportManager_Factory implements Factory<TransportManager> {
  private final Provider<Context> contextProvider;

  private final Provider<NearbyConnectionsTransport> nearbyTransportProvider;

  private final Provider<BleGattTransport> bleGattTransportProvider;

  private TransportManager_Factory(Provider<Context> contextProvider,
      Provider<NearbyConnectionsTransport> nearbyTransportProvider,
      Provider<BleGattTransport> bleGattTransportProvider) {
    this.contextProvider = contextProvider;
    this.nearbyTransportProvider = nearbyTransportProvider;
    this.bleGattTransportProvider = bleGattTransportProvider;
  }

  @Override
  public TransportManager get() {
    return newInstance(contextProvider.get(), nearbyTransportProvider.get(), bleGattTransportProvider.get());
  }

  public static TransportManager_Factory create(Provider<Context> contextProvider,
      Provider<NearbyConnectionsTransport> nearbyTransportProvider,
      Provider<BleGattTransport> bleGattTransportProvider) {
    return new TransportManager_Factory(contextProvider, nearbyTransportProvider, bleGattTransportProvider);
  }

  public static TransportManager newInstance(Context context,
      NearbyConnectionsTransport nearbyTransport, BleGattTransport bleGattTransport) {
    return new TransportManager(context, nearbyTransport, bleGattTransport);
  }
}
