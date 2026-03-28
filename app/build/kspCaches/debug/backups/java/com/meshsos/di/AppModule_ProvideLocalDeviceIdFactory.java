package com.meshsos.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("javax.inject.Named")
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
public final class AppModule_ProvideLocalDeviceIdFactory implements Factory<String> {
  private final Provider<String> deviceIdProvider;

  private AppModule_ProvideLocalDeviceIdFactory(Provider<String> deviceIdProvider) {
    this.deviceIdProvider = deviceIdProvider;
  }

  @Override
  public String get() {
    return provideLocalDeviceId(deviceIdProvider.get());
  }

  public static AppModule_ProvideLocalDeviceIdFactory create(Provider<String> deviceIdProvider) {
    return new AppModule_ProvideLocalDeviceIdFactory(deviceIdProvider);
  }

  public static String provideLocalDeviceId(String deviceId) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideLocalDeviceId(deviceId));
  }
}
