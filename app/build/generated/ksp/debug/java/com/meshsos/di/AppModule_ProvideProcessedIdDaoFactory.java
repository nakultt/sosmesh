package com.meshsos.di;

import com.meshsos.data.db.MeshDatabase;
import com.meshsos.data.db.dao.ProcessedIdDao;
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
public final class AppModule_ProvideProcessedIdDaoFactory implements Factory<ProcessedIdDao> {
  private final Provider<MeshDatabase> dbProvider;

  private AppModule_ProvideProcessedIdDaoFactory(Provider<MeshDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public ProcessedIdDao get() {
    return provideProcessedIdDao(dbProvider.get());
  }

  public static AppModule_ProvideProcessedIdDaoFactory create(Provider<MeshDatabase> dbProvider) {
    return new AppModule_ProvideProcessedIdDaoFactory(dbProvider);
  }

  public static ProcessedIdDao provideProcessedIdDao(MeshDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideProcessedIdDao(db));
  }
}
