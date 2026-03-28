package com.meshsos.domain.service;

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
public final class BatteryMonitor_Factory implements Factory<BatteryMonitor> {
  private final Provider<Context> contextProvider;

  private BatteryMonitor_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public BatteryMonitor get() {
    return newInstance(contextProvider.get());
  }

  public static BatteryMonitor_Factory create(Provider<Context> contextProvider) {
    return new BatteryMonitor_Factory(contextProvider);
  }

  public static BatteryMonitor newInstance(Context context) {
    return new BatteryMonitor(context);
  }
}
