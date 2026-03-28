package com.meshsos.di;

import com.meshsos.data.db.MeshDatabase;
import com.meshsos.data.db.dao.MeshEventDao;
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
public final class AppModule_ProvideMeshEventDaoFactory implements Factory<MeshEventDao> {
  private final Provider<MeshDatabase> dbProvider;

  private AppModule_ProvideMeshEventDaoFactory(Provider<MeshDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public MeshEventDao get() {
    return provideMeshEventDao(dbProvider.get());
  }

  public static AppModule_ProvideMeshEventDaoFactory create(Provider<MeshDatabase> dbProvider) {
    return new AppModule_ProvideMeshEventDaoFactory(dbProvider);
  }

  public static MeshEventDao provideMeshEventDao(MeshDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideMeshEventDao(db));
  }
}
