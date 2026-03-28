package com.meshsos.domain.usecase;

import android.content.Context;
import com.meshsos.data.api.SosApiServiceFactory;
import com.meshsos.data.db.dao.PendingPacketDao;
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
public final class UploadPacketUseCase_Factory implements Factory<UploadPacketUseCase> {
  private final Provider<Context> contextProvider;

  private final Provider<SosApiServiceFactory> apiServiceFactoryProvider;

  private final Provider<PendingPacketDao> pendingPacketDaoProvider;

  private final Provider<String> serverBaseUrlProvider;

  private final Provider<String> localDeviceIdProvider;

  private UploadPacketUseCase_Factory(Provider<Context> contextProvider,
      Provider<SosApiServiceFactory> apiServiceFactoryProvider,
      Provider<PendingPacketDao> pendingPacketDaoProvider, Provider<String> serverBaseUrlProvider,
      Provider<String> localDeviceIdProvider) {
    this.contextProvider = contextProvider;
    this.apiServiceFactoryProvider = apiServiceFactoryProvider;
    this.pendingPacketDaoProvider = pendingPacketDaoProvider;
    this.serverBaseUrlProvider = serverBaseUrlProvider;
    this.localDeviceIdProvider = localDeviceIdProvider;
  }

  @Override
  public UploadPacketUseCase get() {
    return newInstance(contextProvider.get(), apiServiceFactoryProvider.get(), pendingPacketDaoProvider.get(), serverBaseUrlProvider.get(), localDeviceIdProvider.get());
  }

  public static UploadPacketUseCase_Factory create(Provider<Context> contextProvider,
      Provider<SosApiServiceFactory> apiServiceFactoryProvider,
      Provider<PendingPacketDao> pendingPacketDaoProvider, Provider<String> serverBaseUrlProvider,
      Provider<String> localDeviceIdProvider) {
    return new UploadPacketUseCase_Factory(contextProvider, apiServiceFactoryProvider, pendingPacketDaoProvider, serverBaseUrlProvider, localDeviceIdProvider);
  }

  public static UploadPacketUseCase newInstance(Context context,
      SosApiServiceFactory apiServiceFactory, PendingPacketDao pendingPacketDao,
      String serverBaseUrl, String localDeviceId) {
    return new UploadPacketUseCase(context, apiServiceFactory, pendingPacketDao, serverBaseUrl, localDeviceId);
  }
}
