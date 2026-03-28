package com.meshsos.di;

import android.content.Context;
import com.meshsos.data.transport.BleGattTransport;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class AppModule_ProvideBleGattTransportFactory implements Factory<BleGattTransport> {
  private final Provider<Context> contextProvider;

  private AppModule_ProvideBleGattTransportFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public BleGattTransport get() {
    return provideBleGattTransport(contextProvider.get());
  }

  public static AppModule_ProvideBleGattTransportFactory create(Provider<Context> contextProvider) {
    return new AppModule_ProvideBleGattTransportFactory(contextProvider);
  }

  public static BleGattTransport provideBleGattTransport(Context context) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideBleGattTransport(context));
  }
}
