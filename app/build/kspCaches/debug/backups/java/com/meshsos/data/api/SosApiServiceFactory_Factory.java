package com.meshsos.data.api;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
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
public final class SosApiServiceFactory_Factory implements Factory<SosApiServiceFactory> {
  @Override
  public SosApiServiceFactory get() {
    return newInstance();
  }

  public static SosApiServiceFactory_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static SosApiServiceFactory newInstance() {
    return new SosApiServiceFactory();
  }

  private static final class InstanceHolder {
    static final SosApiServiceFactory_Factory INSTANCE = new SosApiServiceFactory_Factory();
  }
}
