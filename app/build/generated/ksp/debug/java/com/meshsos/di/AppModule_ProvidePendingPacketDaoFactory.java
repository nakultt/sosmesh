package com.meshsos.di;

import com.meshsos.data.db.MeshDatabase;
import com.meshsos.data.db.dao.PendingPacketDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
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
public final class AppModule_ProvidePendingPacketDaoFactory implements Factory<PendingPacketDao> {
  private final Provider<MeshDatabase> dbProvider;

  private AppModule_ProvidePendingPacketDaoFactory(Provider<MeshDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public PendingPacketDao get() {
    return providePendingPacketDao(dbProvider.get());
  }

  public static AppModule_ProvidePendingPacketDaoFactory create(Provider<MeshDatabase> dbProvider) {
    return new AppModule_ProvidePendingPacketDaoFactory(dbProvider);
  }

  public static PendingPacketDao providePendingPacketDao(MeshDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.providePendingPacketDao(db));
  }
}
