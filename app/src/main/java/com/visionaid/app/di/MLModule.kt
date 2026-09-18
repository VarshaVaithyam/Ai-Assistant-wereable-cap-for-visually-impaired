package com.visionaid.app.di

import android.content.Context
import com.visionaid.app.ml.DirectionEstimator
import com.visionaid.app.ml.FrameAnalyzer
import com.visionaid.app.ml.ObjectDetector
import com.visionaid.app.ml.TextRecognizer
import com.visionaid.app.ml.YoloV11Processor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Machine-learning singletons (spec 15.4).
 *
 * All ML classes are provided here (not constructor-injected) so that model
 * loading happens exactly once, lazily, and failures are contained.
 */
@Module
@InstallIn(SingletonComponent::class)
object MLModule {

    @Provides
    @Singleton
    fun provideYoloProcessor(@ApplicationContext context: Context): YoloV11Processor =
        YoloV11Processor(context)

    @Provides
    @Singleton
    fun provideDirectionEstimator(): DirectionEstimator = DirectionEstimator()

    /** Main detection orchestrator; loads the model on first use. */
    @Provides
    @Singleton
    fun provideObjectDetector(
        yoloProcessor: YoloV11Processor,
        directionEstimator: DirectionEstimator
    ): ObjectDetector {
        val detector = ObjectDetector(yoloProcessor, directionEstimator)
        detector.initialize() // loads + configures the TFLite interpreter
        return detector
    }

    @Provides
    @Singleton
    fun provideTextRecognizer(): TextRecognizer = TextRecognizer()

    @Provides
    @Singleton
    fun provideFrameAnalyzer(): FrameAnalyzer = FrameAnalyzer()
}
