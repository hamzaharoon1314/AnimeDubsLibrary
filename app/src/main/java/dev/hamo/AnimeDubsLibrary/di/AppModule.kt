package dev.hamo.AnimeDubsLibrary.di

import com.animedubs.AnimeDubs
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Demonstrates how developers can inject the AnimeDubs library 
     * directly into their ViewModels or UseCases, rather than 
     * interacting with the Singleton globally.
     */
    @Provides
    @Singleton
    fun provideAnimeDubsClient(): com.animedubs.AnimeDubsClient = AnimeDubs

}
