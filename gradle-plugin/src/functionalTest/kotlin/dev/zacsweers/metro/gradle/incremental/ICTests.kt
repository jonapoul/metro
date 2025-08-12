// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
@file:Suppress("UPPER_BOUND_VIOLATED_BASED_ON_JAVA_ANNOTATIONS")

package dev.zacsweers.metro.gradle.incremental

import com.autonomousapps.kit.GradleBuilder.build
import com.autonomousapps.kit.GradleBuilder.buildAndFail
import com.autonomousapps.kit.GradleProject
import com.autonomousapps.kit.GradleProject.DslKind
import com.autonomousapps.kit.gradle.Dependency
import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Ignore
import org.junit.Test

class ICTests : BaseIncrementalCompilationTest() {

  /**
   * This test covers an issue where incremental compilation fails to detect when an `@Includes`
   * parameter changes an accessor.
   *
   * Regression test for https://github.com/ZacSweers/metro/issues/314, based on the repro project:
   * https://github.com/kevinguitar/metro-playground/tree/ic-issue-sample
   */
  @Test
  fun removingDependencyPropertyShouldFailOnIc() {
    val fixture =
      object : MetroProject() {
        override fun sources() = listOf(appGraph, featureGraph, featureScreen)

        private val appGraph =
          source(
            """
          @DependencyGraph(Unit::class)
          interface AppGraph

          @Inject
          @ContributesBinding(Unit::class)
          class DependencyImpl : Dependency
          """
          )

        private val featureGraph =
          source(
            """
          @DependencyGraph
          interface FeatureGraph {
              fun inject(screen: FeatureScreen)

              @DependencyGraph.Factory
              interface Factory {
                  fun create(
                      @Includes serviceProvider: FeatureScreen.ServiceProvider
                  ): FeatureGraph
              }
          }
          """
          )

        val featureScreen =
          source(
            """
            class FeatureScreen {
                @Inject
                lateinit var dependency: Dependency

                @ContributesTo(Unit::class)
                interface ServiceProvider {
                    val dependency: Dependency // comment this line to break incremental
                }
            }

            interface Dependency
          """
          )
      }

    val project = fixture.gradleProject

    // First build should succeed
    val firstBuildResult = build(project.rootDir, "compileKotlin")
    assertThat(firstBuildResult.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    // Modify the FeatureScreen class to comment out the dependency property
    project.modify(
      fixture.featureScreen,
      """
      class FeatureScreen {
          @Inject
          lateinit var dependency: Dependency

          @ContributesTo(Unit::class)
          interface ServiceProvider {
              // val dependency: Dependency
          }
      }

      interface Dependency
      """
        .trimIndent(),
    )

    // Second build should fail correctly on a missing binding
    val secondBuildResult = buildAndFail(project.rootDir, "compileKotlin")

    // Verify that the build failed with the expected error message
    assertThat(secondBuildResult.output)
      .contains(
        """
          FeatureGraph.kt:7:11 [Metro/MissingBinding] Cannot find an @Inject constructor or @Provides-annotated function/property for: test.Dependency

              test.Dependency is injected at
                  [test.FeatureGraph] test.FeatureGraph#inject()
              dev.zacsweers.metro.MembersInjector<test.FeatureScreen> is requested at
                  [test.FeatureGraph] test.FeatureGraph#inject()
        """
          .trimIndent()
      )
  }

  @Test
  fun includesDependencyWithRemovedAccessorsShouldBeDetected() {
    val fixture =
      object : MetroProject() {
        override fun sources() = listOf(baseGraph, serviceProvider, target)

        private val baseGraph =
          source(
            """
          @DependencyGraph
          interface BaseGraph {
              val target: Target

              @DependencyGraph.Factory
              interface Factory {
                  fun create(@Includes provider: ServiceProvider): BaseGraph
              }
          }
          """
              .trimIndent()
          )

        val serviceProvider =
          source(
            """
          interface ServiceProvider {
            val dependency: String
          }
          """
              .trimIndent()
          )

        private val target = source("@Inject class Target(val string: String)")
      }

    val project = fixture.gradleProject

    val firstBuildResult = build(project.rootDir, "compileKotlin")
    assertThat(firstBuildResult.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    project.modify(
      fixture.serviceProvider,
      """
      interface ServiceProvider {
          // val dependency: String // Removed accessor
      }
      """
        .trimIndent(),
    )

    val secondBuildResult = buildAndFail(project.rootDir, "compileKotlin")
    assertThat(secondBuildResult.output)
      .contains(
        """
        BaseGraph.kt:7:11 [Metro/MissingBinding] Cannot find an @Inject constructor or @Provides-annotated function/property for: kotlin.String

            kotlin.String is injected at
                [test.BaseGraph] test.Target(…, string)
            test.Target is requested at
                [test.BaseGraph] test.BaseGraph#target
      """
          .trimIndent()
      )
  }

  @Test
  fun extendingGraphChangesDetected() {
    val fixture =
      object : MetroProject() {
        override fun sources() = listOf(childGraph, appGraph, target)

        private val childGraph =
          source(
            """
          @DependencyGraph
          interface ChildGraph {
            val target: Target

            @DependencyGraph.Factory
            interface Factory {
              fun create(@Extends appGraph: AppGraph): ChildGraph
            }
          }
          """
              .trimIndent()
          )

        val appGraph =
          source(
            """
          @DependencyGraph(isExtendable = true)
          interface AppGraph {
            @Provides
            fun provideString(): String = ""
          }
          """
              .trimIndent()
          )

        private val target = source("@Inject class Target(val string: String)")
      }

    val project = fixture.gradleProject

    val firstBuildResult = build(project.rootDir, "compileKotlin")
    assertThat(firstBuildResult.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    project.modify(
      fixture.appGraph,
      """
      @DependencyGraph(isExtendable = true)
      interface AppGraph {
        // Removed provider
        // @Provides
        // fun provideString(): String = ""
      }
      """
        .trimIndent(),
    )

    val secondBuildResult = buildAndFail(project.rootDir, "compileKotlin")
    assertThat(secondBuildResult.output)
      .contains(
        """
          ChildGraph.kt:7:11 [Metro/MissingBinding] Cannot find an @Inject constructor or @Provides-annotated function/property for: kotlin.String

              kotlin.String is injected at
                  [test.ChildGraph] test.Target(…, string)
              test.Target is requested at
                  [test.ChildGraph] test.ChildGraph#target
        """
          .trimIndent()
      )
  }

  @Test
  fun supertypeProviderChangesDetected() {
    val fixture =
      object : MetroProject() {
        override fun sources() = listOf(stringProvider, appGraph, target)

        private val appGraph =
          source(
            """
          @DependencyGraph
          interface AppGraph : StringProvider {
            val target: Target
          }
          """
              .trimIndent()
          )

        val stringProvider =
          source(
            """
          interface StringProvider {
            @Provides
            fun provideString(): String = ""
          }
          """
              .trimIndent()
          )

        private val target = source("@Inject class Target(val string: String)")
      }

    val project = fixture.gradleProject

    val firstBuildResult = build(project.rootDir, "compileKotlin")
    assertThat(firstBuildResult.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    project.modify(
      fixture.stringProvider,
      """
      interface StringProvider {
        // Removed provider
        // @Provides
        // fun provideString(): String = ""
      }
      """
        .trimIndent(),
    )

    val secondBuildResult = buildAndFail(project.rootDir, "compileKotlin")
    assertThat(secondBuildResult.output)
      .contains(
        """
          AppGraph.kt:7:11 [Metro/MissingBinding] Cannot find an @Inject constructor or @Provides-annotated function/property for: kotlin.String

              kotlin.String is injected at
                  [test.AppGraph] test.Target(…, string)
              test.Target is requested at
                  [test.AppGraph] test.AppGraph#target
        """
          .trimIndent()
      )
  }

  @Test
  fun supertypeProviderCompanionChangesDetected() {
    val fixture =
      object : MetroProject() {
        override fun sources() = listOf(stringProvider, appGraph, target)

        private val appGraph =
          source(
            """
          @DependencyGraph
          interface AppGraph : StringProvider {
            val target: Target
          }
          """
              .trimIndent()
          )

        val stringProvider =
          source(
            """
          interface StringProvider {
            companion object {
              @Provides
              fun provideString(): String = ""
            }
          }
          """
              .trimIndent()
          )

        private val target = source("@Inject class Target(val string: String)")
      }

    val project = fixture.gradleProject

    val firstBuildResult = build(project.rootDir, "compileKotlin")
    assertThat(firstBuildResult.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    project.modify(
      fixture.stringProvider,
      """
      interface StringProvider {
        companion object {
          // Removed provider
          // @Provides
          // fun provideString(): String = ""
        }
      }
      """
        .trimIndent(),
    )

    val secondBuildResult = buildAndFail(project.rootDir, "compileKotlin")
    assertThat(secondBuildResult.output)
      .contains(
        """
          AppGraph.kt:7:11 [Metro/MissingBinding] Cannot find an @Inject constructor or @Provides-annotated function/property for: kotlin.String

              kotlin.String is injected at
                  [test.AppGraph] test.Target(…, string)
              test.Target is requested at
                  [test.AppGraph] test.AppGraph#target
        """
          .trimIndent()
      )
  }

  @Test
  fun newContributesIntoSetDetected() {
    val fixture =
      object : MetroProject() {
        override fun sources() = listOf(exampleGraph, contributedInterfaces)

        private val exampleGraph =
          source(
            """
            @DependencyGraph(Unit::class)
            interface ExampleGraph {
              val set: Set<ContributedInterface>
            }
            interface ContributedInterface
          """
              .trimIndent()
          )

        val contributedInterfaces =
          source(
            """
            @Inject
            @ContributesIntoSet(Unit::class)
            class Impl1 : ContributedInterface
          """
              .trimIndent()
          )
      }

    val project = fixture.gradleProject

    val firstBuildResult = build(project.rootDir, "compileKotlin")
    assertThat(firstBuildResult.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    project.modify(
      fixture.contributedInterfaces,
      """
      @Inject
      @ContributesIntoSet(Unit::class)
      class Impl1 : ContributedInterface

      @Inject
      @ContributesIntoSet(Unit::class)
      class NewContribution : ContributedInterface
      """
        .trimIndent(),
    )

    val secondBuildResult = build(project.rootDir, "compileKotlin")
    assertThat(secondBuildResult.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    // Verify that the new contribution is included in the interfaces
    val classLoader = project.classLoader()
    val exampleGraph = classLoader.loadClass("test.ExampleGraph")
    assertThat(exampleGraph.interfaces.map { it.name })
      .contains("test.NewContribution$$\$MetroContributionToUnit")
  }

  @Test
  fun removedContributesIntoSetDetected() {
    val fixture =
      object : MetroProject() {
        override fun sources() = listOf(exampleGraph, contributedInterfaces)

        private val exampleGraph =
          source(
            """
            @DependencyGraph(Unit::class)
            interface ExampleGraph {
              val set: Set<ContributedInterface>
            }
            interface ContributedInterface
          """
              .trimIndent()
          )

        val contributedInterfaces =
          source(
            """
            @Inject
            @ContributesIntoSet(Unit::class)
            class Impl1 : ContributedInterface

            @Inject
            @ContributesIntoSet(Unit::class)
            class Impl2 : ContributedInterface
          """
              .trimIndent()
          )
      }

    val project = fixture.gradleProject

    val firstBuildResult = build(project.rootDir, "compileKotlin")
    assertThat(firstBuildResult.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    // Verify that the new contribution is included in the interfaces
    with(project.classLoader()) {
      val exampleGraph = loadClass("test.ExampleGraph")
      assertThat(exampleGraph.interfaces.map { it.name })
        .contains("test.Impl2$$\$MetroContributionToUnit")
    }

    project.modify(
      fixture.contributedInterfaces,
      """
      @Inject
      @ContributesIntoSet(Unit::class)
      class Impl1 : ContributedInterface
      """
        .trimIndent(),
    )

    val secondBuildResult = build(project.rootDir, "compileKotlin")
    assertThat(secondBuildResult.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    // Verify that the removed contribution is removed from supertypes
    val classLoader = project.classLoader()
    val exampleGraph = classLoader.loadClass("test.ExampleGraph")
    assertThat(exampleGraph.interfaces.map { it.name })
      .doesNotContain("test.Impl2$$\$MetroContributionToUnit")
  }

  @Test
  fun newContributesToDetected() {
    val fixture =
      object : MetroProject() {
        override fun sources() = listOf(exampleGraph, contributedInterfaces)

        private val exampleGraph =
          source(
            """
          interface ContributedInterface

          @DependencyGraph(Unit::class)
          interface ExampleGraph
          """
              .trimIndent()
          )

        val contributedInterfaces =
          source(
            """
          @ContributesTo(Unit::class)
          interface ContributedInterface1
          """
              .trimIndent()
          )
      }
    val project = fixture.gradleProject

    val firstBuildResult = build(project.rootDir, "compileKotlin")
    assertThat(firstBuildResult.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    project.modify(
      fixture.contributedInterfaces,
      """
      @ContributesTo(Unit::class)
      interface ContributedInterface1

      @ContributesTo(Unit::class)
      interface ContributedInterface2
      """
        .trimIndent(),
    )

    val secondBuildResult = build(project.rootDir, "compileKotlin")
    assertThat(secondBuildResult.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    // Check that ContributedInterface2 was added as a supertype
    val classLoader = project.classLoader()
    val exampleGraph = classLoader.loadClass("test.ExampleGraph")
    assertThat(exampleGraph.interfaces.map { it.name })
      .contains("test.ContributedInterface2$$\$MetroContributionToUnit")
  }

  @Test
  fun removedContributesToDetected() {
    val fixture =
      object : MetroProject() {
        override fun sources() = listOf(exampleGraph, contributedInterfaces)

        private val exampleGraph =
          source(
            """
          interface ContributedInterface

          @DependencyGraph(Unit::class)
          interface ExampleGraph
          """
              .trimIndent()
          )

        val contributedInterfaces =
          source(
            """
          @ContributesTo(Unit::class)
          interface ContributedInterface1

          @ContributesTo(Unit::class)
          interface ContributedInterface2
          """
              .trimIndent()
          )
      }
    val project = fixture.gradleProject

    val firstBuildResult = build(project.rootDir, "compileKotlin")
    assertThat(firstBuildResult.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    with(project.classLoader()) {
      val exampleGraph = loadClass("test.ExampleGraph")
      assertThat(exampleGraph.interfaces.map { it.name })
        .contains("test.ContributedInterface2$$\$MetroContributionToUnit")
    }

    project.modify(
      fixture.contributedInterfaces,
      """
      @ContributesTo(Unit::class)
      interface ContributedInterface1
      """
        .trimIndent(),
    )

    val secondBuildResult = build(project.rootDir, "compileKotlin")
    assertThat(secondBuildResult.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    // Check that ContributedInterface2 was removed as a supertype
    val classLoader = project.classLoader()
    val exampleGraph = classLoader.loadClass("test.ExampleGraph")
    assertThat(exampleGraph.interfaces.map { it.name })
      .doesNotContain("test.ContributedInterface2$$\$MetroContributionToUnit")
  }

  @Test
  fun scopingChangeOnProviderIsDetected() {
    val fixture =
      object : MetroProject() {
        override fun sources() = listOf(exampleGraph, main)

        val exampleGraph =
          source(
            """
          @DependencyGraph(Unit::class)
          abstract class ExampleGraph {
            abstract val int: Int

            private var count: Int = 0

            @Provides fun provideInt(): Int = count++
          }
          """
              .trimIndent()
          )

        private val main =
          source(
            """
            fun main(): Int {
              val graph = createGraph<ExampleGraph>()
              return graph.int + graph.int
            }
            """
              .trimIndent()
          )
      }
    val project = fixture.gradleProject

    val firstBuildResult = build(project.rootDir, "compileKotlin")
    assertThat(firstBuildResult.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    with(project.classLoader()) {
      val mainClass = loadClass("test.MainKt")
      val int = mainClass.declaredMethods.first { it.name == "main" }.invoke(null) as Int
      assertThat(int).isEqualTo(1)
    }

    project.modify(
      fixture.exampleGraph,
      """
      @DependencyGraph(Unit::class)
      abstract class ExampleGraph {
        abstract val int: Int

        private var count: Int = 0

        @Provides @SingleIn(Unit::class) fun provideInt(): Int = count++
      }
      """
        .trimIndent(),
    )

    val secondBuildResult = build(project.rootDir, "compileKotlin")
    assertThat(secondBuildResult.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    // Check that count is scoped now and never increments
    with(project.classLoader()) {
      val mainClass = loadClass("test.MainKt")
      val int = mainClass.declaredMethods.first { it.name == "main" }.invoke(null) as Int
      assertThat(int).isEqualTo(0)
    }

    project.modify(
      fixture.exampleGraph,
      """
      @DependencyGraph(Unit::class)
      abstract class ExampleGraph {
        abstract val int: Int

        private var count: Int = 0

        @Provides fun provideInt(): Int = count++
      }
      """
        .trimIndent(),
    )

    val thirdBuildResult = build(project.rootDir, "compileKotlin")
    assertThat(thirdBuildResult.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    // Check that count is unscoped again and increments
    with(project.classLoader()) {
      val mainClass = loadClass("test.MainKt")
      val int = mainClass.declaredMethods.first { it.name == "main" }.invoke(null) as Int
      assertThat(int).isEqualTo(1)
    }
  }

  @Test
  fun scopingChangeOnContributedClassIsDetected() {
    val fixture =
      object : MetroProject() {
        override fun sources() = listOf(exampleClass, exampleGraph, main)

        val exampleClass =
          source(
            """
          @ContributesBinding(Unit::class)
          @Inject
          class ExampleClass : Counter {
            override var count: Int = 0
          }
          """
              .trimIndent()
          )

        private val exampleGraph =
          source(
            """
              interface Counter {
                var count: Int
              }
          @SingleIn(AppScope::class)
          @DependencyGraph(Unit::class)
          interface ExampleGraph {
            val counter: Counter
          }
          """
              .trimIndent()
          )

        private val main =
          source(
            """
            fun main(): Int {
              val graph = createGraph<ExampleGraph>()
              return graph.counter.count++ + graph.counter.count++
            }
            """
              .trimIndent()
          )
      }
    val project = fixture.gradleProject

    val firstBuildResult = build(project.rootDir, "compileKotlin")
    assertThat(firstBuildResult.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    with(project.classLoader()) {
      val mainClass = loadClass("test.MainKt")
      val int = mainClass.declaredMethods.first { it.name == "main" }.invoke(null) as Int
      assertThat(int).isEqualTo(0)
    }

    project.modify(
      fixture.exampleClass,
      """
      @SingleIn(AppScope::class)
      @ContributesBinding(Unit::class)
      @Inject
      class ExampleClass : Counter {
        override var count: Int = 0
      }
      """
        .trimIndent(),
    )

    val secondBuildResult = build(project.rootDir, "compileKotlin")
    assertThat(secondBuildResult.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    // Check that count is scoped now and never increments
    with(project.classLoader()) {
      val mainClass = loadClass("test.MainKt")
      val int = mainClass.declaredMethods.first { it.name == "main" }.invoke(null) as Int
      assertThat(int).isEqualTo(1)
    }

    project.modify(
      fixture.exampleClass,
      """
      @ContributesBinding(Unit::class)
      @Inject
      class ExampleClass : Counter {
        override var count: Int = 0
      }
      """
        .trimIndent(),
    )

    val thirdBuildResult = build(project.rootDir, "compileKotlin")
    assertThat(thirdBuildResult.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    // Check that count is unscoped again and increments
    with(project.classLoader()) {
      val mainClass = loadClass("test.MainKt")
      val int = mainClass.declaredMethods.first { it.name == "main" }.invoke(null) as Int
      assertThat(int).isEqualTo(0)
    }
  }

  @Test
  fun scopingChangeOnNonContributedClassIsDetected() {
    val fixture =
      object : MetroProject(metroOptions = MetroOptionOverrides(enableScopedInjectClassHints = true)) {
        override fun sources() =
          listOf(unusedScope, exampleClass, exampleGraph, loggedInGraph, main)

        val unusedScope =
          source(
            """
              interface UnusedScope
            """
              .trimIndent()
          )

        val exampleClass =
          source(
            """
              @Inject
              @SingleIn(UnusedScope::class)
              class ExampleClass
            """
              .trimIndent()
          )

        private val exampleGraph =
          source(
            """
              @DependencyGraph(scope = AppScope::class, isExtendable = true)
              interface ExampleGraph
            """
              .trimIndent()
          )

        private val loggedInGraph =
          source(
            """
                sealed interface LoggedInScope

                @ContributesGraphExtension(LoggedInScope::class)
                interface LoggedInGraph {
                  val exampleClass: ExampleClass

                    @ContributesGraphExtension.Factory(AppScope::class)
                    interface Factory {
                        fun createLoggedInGraph(): LoggedInGraph
                    }
                }
              """
              .trimIndent()
          )

        private val main =
          source(
            """
            fun main(): Any {
              val graph = createGraph<ExampleGraph>().createLoggedInGraph()
              return graph.exampleClass
            }
            """
              .trimIndent()
          )
      }
    val project = fixture.gradleProject

    // First build should fail because [ExampleClass] is scoped incompatibly with both graph nodes
    val firstBuildResult = buildAndFail(project.rootDir, "compileKotlin")

    assertThat(firstBuildResult.output.cleanOutputLine())
      .contains(
        """
          e: ExampleGraph.kt:7:11 [Metro/IncompatiblyScopedBindings] test.ExampleGraph.$${'$'}ContributedLoggedInGraph (scopes '@SingleIn(LoggedInScope::class)') may not reference bindings from different scopes:
              test.ExampleClass (scoped to '@SingleIn(UnusedScope::class)')
              test.ExampleClass is requested at
                  [test.ExampleGraph.$${'$'}ContributedLoggedInGraph] test.LoggedInGraph#exampleClass
        """
          .trimIndent()
      )

    project.modify(
      fixture.exampleClass,
      """
        @Inject
        @SingleIn(AppScope::class)
        class ExampleClass
      """
        .trimIndent(),
    )

    val secondBuildResult = build(project.rootDir, "compileKotlin")
    assertThat(secondBuildResult.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    with(project.classLoader()) {
      val mainClass = loadClass("test.MainKt")
      val scopedDep = mainClass.declaredMethods.first { it.name == "main" }.invoke(null) as Any
      assertThat(scopedDep).isNotNull()
    }

    // TODO We need to add or remove an annotation at this point to trigger the graph regen,
    //  IC doesn't seem to pick up an annotation argument change when the previous compilation
    //  was successful
    project.modify(
      fixture.exampleClass,
      """
        @Inject
        class ExampleClass
      """
        .trimIndent(),
    )

    val thirdBuildResult = build(project.rootDir, "compileKotlin")
    assertThat(thirdBuildResult.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    project.modify(
      fixture.exampleClass,
      """
        @Inject
        @SingleIn(UnusedScope::class)
        class ExampleClass
      """
        .trimIndent(),
    )

    // We expect that changing the source back to what we started with should again give us the
    // original error
    val fourthBuildResult = buildAndFail(project.rootDir, "compileKotlin")
    assertThat(fourthBuildResult.output.cleanOutputLine())
      .contains(
        """
          e: ExampleGraph.kt:7:11 [Metro/IncompatiblyScopedBindings] test.ExampleGraph.$${'$'}ContributedLoggedInGraph (scopes '@SingleIn(LoggedInScope::class)') may not reference bindings from different scopes:
              test.ExampleClass (scoped to '@SingleIn(UnusedScope::class)')
              test.ExampleClass is requested at
                  [test.ExampleGraph.$${'$'}ContributedLoggedInGraph] test.LoggedInGraph#exampleClass


          (Hint)
          $${'$'}ContributedLoggedInGraph is contributed by 'test.LoggedInGraph' to 'test.ExampleGraph'.
        """
          .trimIndent()
      )
  }

  @Ignore("Not working yet, pending https://youtrack.jetbrains.com/issue/KT-77938")
  @Test
  fun classVisibilityChangeDetected() {
    val fixture =
      object : MetroProject() {
        override fun sources() = listOf(exampleGraph, contributedClass)

        private val exampleGraph =
          source(
            """
          interface ContributedInterface

          @DependencyGraph(Unit::class)
          interface ExampleGraph
          """
              .trimIndent()
          )

        val contributedClass =
          source(
            """
          @Inject
          @ContributesBinding(Unit::class)
          class ContributedInterfaceImpl : ContributedInterface
          """
              .trimIndent()
          )
      }
    val project = fixture.gradleProject

    val firstBuildResult = build(project.rootDir, "compileKotlin")
    assertThat(firstBuildResult.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    project.modify(
      fixture.contributedClass,
      """
      @Inject
      @ContributesBinding(Unit::class)
      internal class ContributedInterfaceImpl : ContributedInterface
      """
        .trimIndent(),
    )

    // Second build should fail correctly on class visibility
    val secondBuildResult = buildAndFail(project.rootDir, "compileKotlin")

    // Verify that the build failed with the expected error message
    assertThat(secondBuildResult.output)
      .contains(
        "ContributedInterface.kt:9:11 DependencyGraph declarations may not extend declarations with narrower visibility. Contributed supertype 'test.ContributedInterfaceImpl' is internal but graph declaration 'test.ExampleGraph' is public."
      )
  }

  @Test
  fun fieldWrappedWithLazyIsDetected() {
    val fixture =
      object : MetroProject() {
        override fun sources() = listOf(exampleGraph, exampleClass, main)

        private val exampleGraph =
          source(
            """
          @DependencyGraph
          interface ExampleGraph {
            fun inject(exampleClass: ExampleClass)

            @Provides fun provideString(): String = "Hello, world!"
          }
            """
              .trimIndent()
          )

        val exampleClass =
          source(
            """
          class ExampleClass {
            @Inject lateinit var string: String
          }
          """
              .trimIndent()
          )

        val main =
          source(
            """
            fun main(): String {
              val graph = createGraph<ExampleGraph>()
              val exampleClass = ExampleClass()
              graph.inject(exampleClass)
              return exampleClass.string
            }
            """
              .trimIndent()
          )
      }
    val project = fixture.gradleProject

    fun buildAndAssertOutput() {
      val buildResult = build(project.rootDir, "compileKotlin")
      assertThat(buildResult.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

      val mainClass = project.classLoader().loadClass("test.MainKt")
      val string = mainClass.declaredMethods.first { it.name == "main" }.invoke(null) as String
      assertThat(string).isEqualTo("Hello, world!")
    }

    buildAndAssertOutput()

    project.modify(
      fixture.exampleClass,
      """
      class ExampleClass {
        @Inject lateinit var string: Lazy<String>
      }
      """
        .trimIndent(),
    )

    project.modify(
      fixture.main,
      """
      fun main(): String {
        val graph = createGraph<ExampleGraph>()
        val exampleClass = ExampleClass()
        graph.inject(exampleClass)
        return exampleClass.string.value
      }
      """
        .trimIndent(),
    )

    buildAndAssertOutput()
  }

  @Test
  fun icWorksWhenChangingAContributionScope() {
    val fixture =
      object : MetroProject() {
        override fun sources() =
          listOf(unusedScope, exampleClass, exampleGraph, loggedInGraph, main)

        val unusedScope =
          source(
            """
              interface UnusedScope
              interface Foo
            """
              .trimIndent()
          )

        val exampleClass =
          source(
            """
              @Inject
              @ContributesBinding(UnusedScope::class)
              class ExampleClass : Foo
            """
              .trimIndent()
          )

        private val exampleGraph =
          source(
            """
              @DependencyGraph(scope = AppScope::class, isExtendable = true)
              interface ExampleGraph
            """
              .trimIndent()
          )

        private val loggedInGraph =
          source(
            """
                sealed interface LoggedInScope

                @ContributesGraphExtension(LoggedInScope::class)
                interface LoggedInGraph {
                  val childDependency: Foo

                    @ContributesGraphExtension.Factory(AppScope::class)
                    interface Factory {
                        fun createLoggedInGraph(): LoggedInGraph
                    }
                }
              """
              .trimIndent()
          )

        private val main =
          source(
            """
            fun main(): Any {
              val graph = createGraph<ExampleGraph>().createLoggedInGraph()
              return graph.childDependency
            }
            """
              .trimIndent()
          )
      }
    val project = fixture.gradleProject

    // First build should fail because `ExampleClass` is not contributed to the scopes of either
    // graph
    val firstBuildResult = buildAndFail(project.rootDir, "compileKotlin")

    assertThat(firstBuildResult.output.cleanOutputLine())
      .contains(
        """
          e: LoggedInScope.kt:10:7 [Metro/MissingBinding] Cannot find an @Inject constructor or @Provides-annotated function/property for: test.Foo

              test.Foo is requested at
                  [test.ExampleGraph.$${'$'}ContributedLoggedInGraph] test.LoggedInGraph#childDependency
        """
          .trimIndent()
      )

    // Change to contribute to the scope of the root graph node -- will pass
    project.modify(
      fixture.exampleClass,
      """
        @Inject
        @ContributesBinding(AppScope::class)
        class ExampleClass : Foo
      """
        .trimIndent(),
    )

    val secondBuildResult = build(project.rootDir, "compileKotlin")
    assertThat(secondBuildResult.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    with(project.classLoader()) {
      val mainClass = loadClass("test.MainKt")
      val scopedDep = mainClass.declaredMethods.first { it.name == "main" }.invoke(null) as Any
      assertThat(scopedDep).isNotNull()
    }

    // Change back to the original state -- should fail again for a missing binding
    project.modify(
      fixture.exampleClass,
      """
        @Inject
        @ContributesBinding(UnusedScope::class)
        class ExampleClass : Foo
      """
        .trimIndent(),
    )

    val thirdBuildResult = buildAndFail(project.rootDir, "compileKotlin")
    assertThat(thirdBuildResult.output.cleanOutputLine())
      .contains(
        """
          e: ExampleGraph.kt:7:11 [Metro/MissingBinding] Cannot find an @Inject constructor or @Provides-annotated function/property for: test.Foo

              test.Foo is requested at
                  [test.ExampleGraph.$${'$'}ContributedLoggedInGraph] test.LoggedInGraph#childDependency
        """
          .trimIndent()
      )
  }

  @Test
  fun icWorksWhenAddingAParamToExistingInjectedTypeWithScopeWithZeroToOneParams() {
    val fixture =
      object : MetroProject() {
        override fun sources() = listOf(appGraph, main)

        override val gradleProject: GradleProject
          get() =
            newGradleProjectBuilder(DslKind.KOTLIN)
              .withRootProject {
                withBuildScript {
                  sources = sources()
                  applyMetroDefault()
                  dependencies(
                    Dependency.implementation(":common"),
                    Dependency.implementation(":lib"),
                  )
                }
              }
              .withSubproject("common") {
                sources.add(bar)
                withBuildScript { applyMetroDefault() }
              }
              .withSubproject("lib") {
                sources.add(foo)
                withBuildScript {
                  applyMetroDefault()
                  dependencies(Dependency.implementation(":common"))
                }
              }
              .write()

        private val bar =
          source(
            """
              interface Bar

              @Inject
              @ContributesBinding(AppScope::class)
              class BarImpl : Bar
            """
              .trimIndent()
          )

        val foo =
          source(
            """
              interface Foo

              @SingleIn(AppScope::class)
              @Inject
              @ContributesBinding(AppScope::class)
              class FooImpl : Foo
            """
              .trimIndent()
          )

        private val appGraph =
          source(
            """
              @DependencyGraph(AppScope::class, isExtendable = true)
              interface AppGraph
            """
              .trimIndent()
          )

        private val main =
          source(
            """
            fun main(): Any {
              return createGraph<AppGraph>()
            }
            """
              .trimIndent()
          )
      }

    val project = fixture.gradleProject
    val libProject = project.subprojects.first { it.name == "lib" }

    fun buildAndAssertOutput() {
      val buildResult = build(project.rootDir, "compileKotlin")
      assertThat(buildResult.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

      val mainClass = project.classLoader().loadClass("test.MainKt")
      val graph = mainClass.declaredMethods.first { it.name == "main" }.invoke(null) as Any
      assertThat(graph).isNotNull()
    }

    buildAndAssertOutput()

    // Adding a bar param to FooImpl, FooImpl.$$MetroFactory should be regenerated with member field
    libProject.modify(
      project.rootDir,
      fixture.foo,
      """
      interface Foo

      @SingleIn(AppScope::class)
      @Inject
      @ContributesBinding(AppScope::class)
      class FooImpl(bar: Bar) : Foo
      """
        .trimIndent(),
    )

    buildAndAssertOutput()
  }

  @Test
  fun icWorksWhenAddingAParamToExistingInjectedTypeWithScopeWithMultipleParams() {
    val fixture =
      object : MetroProject() {
        override fun sources() = listOf(appGraph, main)

        override val gradleProject: GradleProject
          get() =
            newGradleProjectBuilder(DslKind.KOTLIN)
              .withRootProject {
                withBuildScript {
                  sources = sources()
                  applyMetroDefault()
                  dependencies(
                    Dependency.implementation(":common"),
                    Dependency.implementation(":lib"),
                  )
                }
              }
              .withSubproject("common") {
                sources.add(bar)
                withBuildScript { applyMetroDefault() }
              }
              .withSubproject("lib") {
                sources.add(foo)
                withBuildScript {
                  applyMetroDefault()
                  dependencies(Dependency.implementation(":common"))
                }
              }
              .write()

        private val bar =
          source(
            """
              interface Bar

              @Inject
              @ContributesBinding(AppScope::class)
              class BarImpl : Bar
            """
              .trimIndent()
          )

        val foo =
          source(
            """
              interface Foo

              @SingleIn(AppScope::class)
              @Inject
              @ContributesBinding(AppScope::class)
              class FooImpl(int: Int) : Foo
            """
              .trimIndent()
          )

        private val appGraph =
          source(
            """
              @DependencyGraph(AppScope::class, isExtendable = true)
              interface AppGraph {
                @Provides fun provideInt(): Int = 0
              }
            """
              .trimIndent()
          )

        private val main =
          source(
            """
            fun main(): Any {
              return createGraph<AppGraph>()
            }
            """
              .trimIndent()
          )
      }

    val project = fixture.gradleProject
    val libProject = project.subprojects.first { it.name == "lib" }

    fun buildAndAssertOutput() {
      val buildResult = build(project.rootDir, "compileKotlin")
      assertThat(buildResult.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

      val mainClass = project.classLoader().loadClass("test.MainKt")
      val graph = mainClass.declaredMethods.first { it.name == "main" }.invoke(null) as Any
      assertThat(graph).isNotNull()
    }

    buildAndAssertOutput()

    // Adding a bar param to FooImpl, FooImpl.$$MetroFactory should be regenerated with member field
    libProject.modify(
      project.rootDir,
      fixture.foo,
      """
      interface Foo

      @SingleIn(AppScope::class)
      @Inject
      @ContributesBinding(AppScope::class)
      class FooImpl(int: Int, bar: Bar) : Foo
      """
        .trimIndent(),
    )

    buildAndAssertOutput()
  }

  @Test
  fun multipleBindingReplacementsAreRespectedWhenAddingNewContribution() {
    val fixture =
      object : MetroProject(debug = true) {
        override fun sources() = listOf(appGraph, fakeImpl, main)

        override val gradleProject: GradleProject
          get() =
            newGradleProjectBuilder(DslKind.KOTLIN)
              .withRootProject {
                withBuildScript {
                  sources = sources()
                  applyMetroDefault()
                  dependencies(
                    Dependency.implementation(":common"),
                    Dependency.implementation(":lib"),
                  )
                }
              }
              .withSubproject("common") {
                sources.add(fooBar)
                withBuildScript { applyMetroDefault() }
              }
              .withSubproject("lib") {
                sources.add(realImpl)
                withBuildScript {
                  applyMetroDefault()
                  dependencies(Dependency.implementation(":common"))
                }
              }
              .write()

        private val appGraph =
          source(
            """
          @DependencyGraph(AppScope::class)
          interface AppGraph {
            val bar: Bar
          }
            """
              .trimIndent()
          )

        private val fooBar =
          source(
            """
          interface Foo
          interface Bar : Foo {
            val str: String
          }
            """.trimIndent()
          )

        val realImpl =
          source(
            """
          @Inject
          @ContributesBinding(AppScope::class, binding = binding<Foo>())
          @ContributesBinding(AppScope::class, binding = binding<Bar>())
          class RealImpl : Bar {
            override val str: String = "real"
          }
          """
              .trimIndent()
          )

        private val fakeImpl =
          source(
            """
          @Inject
          @ContributesBinding(AppScope::class, binding = binding<Foo>(), replaces = [RealImpl::class])
          @ContributesBinding(AppScope::class, binding = binding<Bar>(), replaces = [RealImpl::class])
          class FakeImpl : Bar {
            override val str: String = "fake"
          }
          """
              .trimIndent()
          )

        val placeholder =
          source("")

        val main =
          source(
            """
            fun main(): String {
              val graph = createGraph<AppGraph>()
              return graph.bar.str
            }
            """
              .trimIndent()
          )
      }
    val project = fixture.gradleProject
    val libProject = project.subprojects.first { it.name == "lib" }

    fun buildAndAssertOutput() {
      val buildResult = build(project.rootDir, "compileKotlin")
      assertThat(buildResult.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

      val mainClass = project.classLoader().loadClass("test.MainKt")
      val string = mainClass.declaredMethods.first { it.name == "main" }.invoke(null) as String
      assertThat(string).isEqualTo("fake")
    }

    buildAndAssertOutput()

    // Adding a new binding contribution should be alright
    libProject.modify(
      project.rootDir,
      fixture.placeholder,
      """
      interface Baz

      @Inject
      @ContributesBinding(AppScope::class)
      class BazImpl : Baz
      """
        .trimIndent(),
    )

    buildAndAssertOutput()
  }
}
