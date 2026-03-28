package com.meshsos.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class AppModule_ProvideServerBaseUrlFactory implements Factory<String> {
  @Override
  public String get() {
    return provideServerBaseUrl();
  }

  public static AppModule_ProvideServerBaseUrlFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static String provideServerBaseUrl() {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideServerBaseUrl());
  }

  private static final class InstanceHolder {
    static final AppModule_ProvideServerBaseUrlFactory INSTANCE = new AppModule_ProvideServerBaseUrlFactory();
  }
}
