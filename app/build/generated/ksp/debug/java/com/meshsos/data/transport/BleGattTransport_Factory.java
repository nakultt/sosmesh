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
public final class BleGattTransport_Factory implements Factory<BleGattTransport> {
  private final Provider<Context> contextProvider;

  private BleGattTransport_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public BleGattTransport get() {
    return newInstance(contextProvider.get());
  }

  public static BleGattTransport_Factory create(Provider<Context> contextProvider) {
    return new BleGattTransport_Factory(contextProvider);
  }

  public static BleGattTransport newInstance(Context context) {
    return new BleGattTransport(context);
  }
}
