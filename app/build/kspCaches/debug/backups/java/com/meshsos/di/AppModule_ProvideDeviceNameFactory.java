package com.meshsos.di;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata({
    "javax.inject.Named",
    "dagger.hilt.android.qualifiers.ApplicationContext"
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
public final class AppModule_ProvideDeviceNameFactory implements Factory<String> {
  private final Provider<Context> contextProvider;

  private AppModule_ProvideDeviceNameFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public String get() {
    return provideDeviceName(contextProvider.get());
  }

  public static AppModule_ProvideDeviceNameFactory create(Provider<Context> contextProvider) {
    return new AppModule_ProvideDeviceNameFactory(contextProvider);
  }

  public static String provideDeviceName(Context context) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideDeviceName(context));
  }
}
