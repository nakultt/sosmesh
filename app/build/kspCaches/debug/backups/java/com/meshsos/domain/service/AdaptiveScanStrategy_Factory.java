package com.meshsos.domain.service;

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
public final class AdaptiveScanStrategy_Factory implements Factory<AdaptiveScanStrategy> {
  @Override
  public AdaptiveScanStrategy get() {
    return newInstance();
  }

  public static AdaptiveScanStrategy_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static AdaptiveScanStrategy newInstance() {
    return new AdaptiveScanStrategy();
  }

  private static final class InstanceHolder {
    static final AdaptiveScanStrategy_Factory INSTANCE = new AdaptiveScanStrategy_Factory();
  }
}
