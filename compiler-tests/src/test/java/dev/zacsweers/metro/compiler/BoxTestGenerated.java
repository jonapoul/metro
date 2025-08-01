

package dev.zacsweers.metro.compiler;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.util.KtTestUtil;
import org.jetbrains.kotlin.test.TargetBackend;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link dev.zacsweers.metro.compiler.GenerateTestsKt}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@TestMetadata("compiler-tests/src/test/data/box")
@TestDataPath("$PROJECT_ROOT")
public class BoxTestGenerated extends AbstractBoxTest {
  @Test
  public void testAllFilesPresentInBox() {
    KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("compiler-tests/src/test/data/box"), Pattern.compile("^(.+)\\.kt$"), null, TargetBackend.JVM_IR, true);
  }

  @Nested
  @TestMetadata("compiler-tests/src/test/data/box/aggregation")
  @TestDataPath("$PROJECT_ROOT")
  public class Aggregation {
    @Test
    public void testAllFilesPresentInAggregation() {
      KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("compiler-tests/src/test/data/box/aggregation"), Pattern.compile("^(.+)\\.kt$"), null, TargetBackend.JVM_IR, true);
    }

    @Test
    @TestMetadata("ComputedPropertiesIntoSet.kt")
    public void testComputedPropertiesIntoSet() {
      runTest("compiler-tests/src/test/data/box/aggregation/ComputedPropertiesIntoSet.kt");
    }

    @Test
    @TestMetadata("ContributedBindingContainerExclusions.kt")
    public void testContributedBindingContainerExclusions() {
      runTest("compiler-tests/src/test/data/box/aggregation/ContributedBindingContainerExclusions.kt");
    }

    @Test
    @TestMetadata("ContributedBindingContainerGetsExcluded.kt")
    public void testContributedBindingContainerGetsExcluded() {
      runTest("compiler-tests/src/test/data/box/aggregation/ContributedBindingContainerGetsExcluded.kt");
    }

    @Test
    @TestMetadata("ContributedBindingContainerGetsExcludedInContributedGraph.kt")
    public void testContributedBindingContainerGetsExcludedInContributedGraph() {
      runTest("compiler-tests/src/test/data/box/aggregation/ContributedBindingContainerGetsExcludedInContributedGraph.kt");
    }

    @Test
    @TestMetadata("ContributedBindingContainerReplacements.kt")
    public void testContributedBindingContainerReplacements() {
      runTest("compiler-tests/src/test/data/box/aggregation/ContributedBindingContainerReplacements.kt");
    }

    @Test
    @TestMetadata("ContributedBindingContainerReplacesContributedBinding.kt")
    public void testContributedBindingContainerReplacesContributedBinding() {
      runTest("compiler-tests/src/test/data/box/aggregation/ContributedBindingContainerReplacesContributedBinding.kt");
    }

    @Test
    @TestMetadata("ContributedBindingContainerReplacesContributedBindingInContributedGraph.kt")
    public void testContributedBindingContainerReplacesContributedBindingInContributedGraph() {
      runTest("compiler-tests/src/test/data/box/aggregation/ContributedBindingContainerReplacesContributedBindingInContributedGraph.kt");
    }

    @Test
    @TestMetadata("ContributesMultibindingInteropAnnotationsAddBindingToSetOrMapWithMapKey.kt")
    public void testContributesMultibindingInteropAnnotationsAddBindingToSetOrMapWithMapKey() {
      runTest("compiler-tests/src/test/data/box/aggregation/ContributesMultibindingInteropAnnotationsAddBindingToSetOrMapWithMapKey.kt");
    }

    @Test
    @TestMetadata("ContributingMultibileNullableBindings.kt")
    public void testContributingMultibileNullableBindings() {
      runTest("compiler-tests/src/test/data/box/aggregation/ContributingMultibileNullableBindings.kt");
    }

    @Test
    @TestMetadata("InternalHintsInContributedGraph.kt")
    public void testInternalHintsInContributedGraph() {
      runTest("compiler-tests/src/test/data/box/aggregation/InternalHintsInContributedGraph.kt");
    }

    @Test
    @TestMetadata("InternalHintsInGraph.kt")
    public void testInternalHintsInGraph() {
      runTest("compiler-tests/src/test/data/box/aggregation/InternalHintsInGraph.kt");
    }

    @Test
    @TestMetadata("RepeatedContributesBindingAnvilInteropWorksForBoundTypeAndIgnoreQualifier.kt")
    public void testRepeatedContributesBindingAnvilInteropWorksForBoundTypeAndIgnoreQualifier() {
      runTest("compiler-tests/src/test/data/box/aggregation/RepeatedContributesBindingAnvilInteropWorksForBoundTypeAndIgnoreQualifier.kt");
    }
  }

  @Nested
  @TestMetadata("compiler-tests/src/test/data/box/bindingcontainers")
  @TestDataPath("$PROJECT_ROOT")
  public class Bindingcontainers {
    @Test
    public void testAllFilesPresentInBindingcontainers() {
      KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("compiler-tests/src/test/data/box/bindingcontainers"), Pattern.compile("^(.+)\\.kt$"), null, TargetBackend.JVM_IR, true);
    }

    @Test
    @TestMetadata("ContributedAcrossModules.kt")
    public void testContributedAcrossModules() {
      runTest("compiler-tests/src/test/data/box/bindingcontainers/ContributedAcrossModules.kt");
    }

    @Test
    @TestMetadata("ContributedAcrossModulesToContributedGraphs.kt")
    public void testContributedAcrossModulesToContributedGraphs() {
      runTest("compiler-tests/src/test/data/box/bindingcontainers/ContributedAcrossModulesToContributedGraphs.kt");
    }

    @Test
    @TestMetadata("ContributedEmpty.kt")
    public void testContributedEmpty() {
      runTest("compiler-tests/src/test/data/box/bindingcontainers/ContributedEmpty.kt");
    }

    @Test
    @TestMetadata("ContributedFromClassParameter.kt")
    public void testContributedFromClassParameter() {
      runTest("compiler-tests/src/test/data/box/bindingcontainers/ContributedFromClassParameter.kt");
    }

    @Test
    @TestMetadata("ContributedWithOnlyMultibinds.kt")
    public void testContributedWithOnlyMultibinds() {
      runTest("compiler-tests/src/test/data/box/bindingcontainers/ContributedWithOnlyMultibinds.kt");
    }

    @Test
    @TestMetadata("MultibindsOnlyInContainer.kt")
    public void testMultibindsOnlyInContainer() {
      runTest("compiler-tests/src/test/data/box/bindingcontainers/MultibindsOnlyInContainer.kt");
    }

    @Test
    @TestMetadata("SimpleContainersWithHintsWork.kt")
    public void testSimpleContainersWithHintsWork() {
      runTest("compiler-tests/src/test/data/box/bindingcontainers/SimpleContainersWithHintsWork.kt");
    }
  }

  @Nested
  @TestMetadata("compiler-tests/src/test/data/box/contributesgraphextension")
  @TestDataPath("$PROJECT_ROOT")
  public class Contributesgraphextension {
    @Test
    public void testAllFilesPresentInContributesgraphextension() {
      KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("compiler-tests/src/test/data/box/contributesgraphextension"), Pattern.compile("^(.+)\\.kt$"), null, TargetBackend.JVM_IR, true);
    }

    @Test
    @TestMetadata("AsContributionExample.kt")
    public void testAsContributionExample() {
      runTest("compiler-tests/src/test/data/box/contributesgraphextension/AsContributionExample.kt");
    }

    @Test
    @TestMetadata("ContributedFactoryIsAvailableAsBinding.kt")
    public void testContributedFactoryIsAvailableAsBinding() {
      runTest("compiler-tests/src/test/data/box/contributesgraphextension/ContributedFactoryIsAvailableAsBinding.kt");
    }

    @Test
    @TestMetadata("ContributingMultipleGraphsToSameParent.kt")
    public void testContributingMultipleGraphsToSameParent() {
      runTest("compiler-tests/src/test/data/box/contributesgraphextension/ContributingMultipleGraphsToSameParent.kt");
    }

    @Test
    @TestMetadata("ParentIncludesArePropgatedToExtensions.kt")
    public void testParentIncludesArePropgatedToExtensions() {
      runTest("compiler-tests/src/test/data/box/contributesgraphextension/ParentIncludesArePropgatedToExtensions.kt");
    }

    @Test
    @TestMetadata("ParentIncludesTypesAreSeenByExtensions.kt")
    public void testParentIncludesTypesAreSeenByExtensions() {
      runTest("compiler-tests/src/test/data/box/contributesgraphextension/ParentIncludesTypesAreSeenByExtensions.kt");
    }

    @Test
    @TestMetadata("ProvidesWithMemberInjection.kt")
    public void testProvidesWithMemberInjection() {
      runTest("compiler-tests/src/test/data/box/contributesgraphextension/ProvidesWithMemberInjection.kt");
    }

    @Test
    @TestMetadata("QualifiedMemberInjectionPropagatesAcrossModules.kt")
    public void testQualifiedMemberInjectionPropagatesAcrossModules() {
      runTest("compiler-tests/src/test/data/box/contributesgraphextension/QualifiedMemberInjectionPropagatesAcrossModules.kt");
    }

    @Test
    @TestMetadata("WithContributesBinding.kt")
    public void testWithContributesBinding() {
      runTest("compiler-tests/src/test/data/box/contributesgraphextension/WithContributesBinding.kt");
    }
  }

  @Nested
  @TestMetadata("compiler-tests/src/test/data/box/cycles")
  @TestDataPath("$PROJECT_ROOT")
  public class Cycles {
    @Test
    public void testAllFilesPresentInCycles() {
      KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("compiler-tests/src/test/data/box/cycles"), Pattern.compile("^(.+)\\.kt$"), null, TargetBackend.JVM_IR, true);
    }

    @Test
    @TestMetadata("AssistedFactoryCycle.kt")
    public void testAssistedFactoryCycle() {
      runTest("compiler-tests/src/test/data/box/cycles/AssistedFactoryCycle.kt");
    }

    @Test
    @TestMetadata("AssistedFactoryCycleWithInjectedType.kt")
    public void testAssistedFactoryCycleWithInjectedType() {
      runTest("compiler-tests/src/test/data/box/cycles/AssistedFactoryCycleWithInjectedType.kt");
    }

    @Test
    @TestMetadata("BindsCycleGraph.kt")
    public void testBindsCycleGraph() {
      runTest("compiler-tests/src/test/data/box/cycles/BindsCycleGraph.kt");
    }

    @Test
    @TestMetadata("CycleGraph.kt")
    public void testCycleGraph() {
      runTest("compiler-tests/src/test/data/box/cycles/CycleGraph.kt");
    }

    @Test
    @TestMetadata("CycleMapGraph.kt")
    public void testCycleMapGraph() {
      runTest("compiler-tests/src/test/data/box/cycles/CycleMapGraph.kt");
    }

    @Test
    @TestMetadata("LongCycle.kt")
    public void testLongCycle() {
      runTest("compiler-tests/src/test/data/box/cycles/LongCycle.kt");
    }

    @Test
    @TestMetadata("SelfCycle.kt")
    public void testSelfCycle() {
      runTest("compiler-tests/src/test/data/box/cycles/SelfCycle.kt");
    }

    @Test
    @TestMetadata("SimpleBindingIntoMulti.kt")
    public void testSimpleBindingIntoMulti() {
      runTest("compiler-tests/src/test/data/box/cycles/SimpleBindingIntoMulti.kt");
    }

    @Test
    @TestMetadata("SmokeTest.kt")
    public void testSmokeTest() {
      runTest("compiler-tests/src/test/data/box/cycles/SmokeTest.kt");
    }
  }

  @Nested
  @TestMetadata("compiler-tests/src/test/data/box/dependencygraph")
  @TestDataPath("$PROJECT_ROOT")
  public class Dependencygraph {
    @Test
    public void testAllFilesPresentInDependencygraph() {
      KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("compiler-tests/src/test/data/box/dependencygraph"), Pattern.compile("^(.+)\\.kt$"), null, TargetBackend.JVM_IR, true);
    }

    @Test
    @TestMetadata("GraphFactoriesSupportGenericProviders.kt")
    public void testGraphFactoriesSupportGenericProviders() {
      runTest("compiler-tests/src/test/data/box/dependencygraph/GraphFactoriesSupportGenericProviders.kt");
    }

    @Test
    @TestMetadata("IncludedGraphsCanStillUseNonGraphs.kt")
    public void testIncludedGraphsCanStillUseNonGraphs() {
      runTest("compiler-tests/src/test/data/box/dependencygraph/IncludedGraphsCanStillUseNonGraphs.kt");
    }

    @Test
    @TestMetadata("IncludesDeepInheritedInterfacesWork.kt")
    public void testIncludesDeepInheritedInterfacesWork() {
      runTest("compiler-tests/src/test/data/box/dependencygraph/IncludesDeepInheritedInterfacesWork.kt");
    }

    @Test
    @TestMetadata("IncludesGraphOnlyIncludesAccessors.kt")
    public void testIncludesGraphOnlyIncludesAccessors() {
      runTest("compiler-tests/src/test/data/box/dependencygraph/IncludesGraphOnlyIncludesAccessors.kt");
    }

    @Test
    @TestMetadata("InitsAreChunkedBox.kt")
    public void testInitsAreChunkedBox() {
      runTest("compiler-tests/src/test/data/box/dependencygraph/InitsAreChunkedBox.kt");
    }

    @Test
    @TestMetadata("InitsAreChunkedWithCycleBox.kt")
    public void testInitsAreChunkedWithCycleBox() {
      runTest("compiler-tests/src/test/data/box/dependencygraph/InitsAreChunkedWithCycleBox.kt");
    }

    @Test
    @TestMetadata("MultiLevelTransitiveIncludes.kt")
    public void testMultiLevelTransitiveIncludes() {
      runTest("compiler-tests/src/test/data/box/dependencygraph/MultiLevelTransitiveIncludes.kt");
    }

    @Test
    @TestMetadata("MultibindingGraphWithWithScopedSetDeps.kt")
    public void testMultibindingGraphWithWithScopedSetDeps() {
      runTest("compiler-tests/src/test/data/box/dependencygraph/MultibindingGraphWithWithScopedSetDeps.kt");
    }

    @Test
    @TestMetadata("MultipleBindsInSeparateGraphsAreValid.kt")
    public void testMultipleBindsInSeparateGraphsAreValid() {
      runTest("compiler-tests/src/test/data/box/dependencygraph/MultipleBindsInSeparateGraphsAreValid.kt");
    }

    @Test
    @TestMetadata("OverrideCompatibleBindingAccessors.kt")
    public void testOverrideCompatibleBindingAccessors() {
      runTest("compiler-tests/src/test/data/box/dependencygraph/OverrideCompatibleBindingAccessors.kt");
    }

    @Test
    @TestMetadata("PrivateBinds.kt")
    public void testPrivateBinds() {
      runTest("compiler-tests/src/test/data/box/dependencygraph/PrivateBinds.kt");
    }

    @Test
    @TestMetadata("PrivateBindsInOtherModule.kt")
    public void testPrivateBindsInOtherModule() {
      runTest("compiler-tests/src/test/data/box/dependencygraph/PrivateBindsInOtherModule.kt");
    }

    @Test
    @TestMetadata("QualifiersWithEnumsWork.kt")
    public void testQualifiersWithEnumsWork() {
      runTest("compiler-tests/src/test/data/box/dependencygraph/QualifiersWithEnumsWork.kt");
    }

    @Test
    @TestMetadata("StaticGraphCompanions.kt")
    public void testStaticGraphCompanions() {
      runTest("compiler-tests/src/test/data/box/dependencygraph/StaticGraphCompanions.kt");
    }

    @Test
    @TestMetadata("StaticGraphCompanionsSeparateModules.kt")
    public void testStaticGraphCompanionsSeparateModules() {
      runTest("compiler-tests/src/test/data/box/dependencygraph/StaticGraphCompanionsSeparateModules.kt");
    }

    @Nested
    @TestMetadata("compiler-tests/src/test/data/box/dependencygraph/bindingcontainers")
    @TestDataPath("$PROJECT_ROOT")
    public class Bindingcontainers {
      @Test
      public void testAllFilesPresentInBindingcontainers() {
        KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("compiler-tests/src/test/data/box/dependencygraph/bindingcontainers"), Pattern.compile("^(.+)\\.kt$"), null, TargetBackend.JVM_IR, true);
      }

      @Test
      @TestMetadata("BindingContainerViaAnnotation.kt")
      public void testBindingContainerViaAnnotation() {
        runTest("compiler-tests/src/test/data/box/dependencygraph/bindingcontainers/BindingContainerViaAnnotation.kt");
      }

      @Test
      @TestMetadata("BindingContainerViaAnnotationCycleIsOk.kt")
      public void testBindingContainerViaAnnotationCycleIsOk() {
        runTest("compiler-tests/src/test/data/box/dependencygraph/bindingcontainers/BindingContainerViaAnnotationCycleIsOk.kt");
      }

      @Test
      @TestMetadata("BindingContainerViaAnnotationTransitive.kt")
      public void testBindingContainerViaAnnotationTransitive() {
        runTest("compiler-tests/src/test/data/box/dependencygraph/bindingcontainers/BindingContainerViaAnnotationTransitive.kt");
      }

      @Test
      @TestMetadata("BindingContainerViaContributesTo.kt")
      public void testBindingContainerViaContributesTo() {
        runTest("compiler-tests/src/test/data/box/dependencygraph/bindingcontainers/BindingContainerViaContributesTo.kt");
      }

      @Test
      @TestMetadata("BindingContainerViaCreator.kt")
      public void testBindingContainerViaCreator() {
        runTest("compiler-tests/src/test/data/box/dependencygraph/bindingcontainers/BindingContainerViaCreator.kt");
      }
    }

    @Nested
    @TestMetadata("compiler-tests/src/test/data/box/dependencygraph/extensions")
    @TestDataPath("$PROJECT_ROOT")
    public class Extensions {
      @Test
      public void testAllFilesPresentInExtensions() {
        KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("compiler-tests/src/test/data/box/dependencygraph/extensions"), Pattern.compile("^(.+)\\.kt$"), null, TargetBackend.JVM_IR, true);
      }

      @Test
      @TestMetadata("ParentBindingsAreAlsoIncluded.kt")
      public void testParentBindingsAreAlsoIncluded() {
        runTest("compiler-tests/src/test/data/box/dependencygraph/extensions/ParentBindingsAreAlsoIncluded.kt");
      }
    }
  }

  @Nested
  @TestMetadata("compiler-tests/src/test/data/box/inject")
  @TestDataPath("$PROJECT_ROOT")
  public class Inject {
    @Test
    public void testAllFilesPresentInInject() {
      KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("compiler-tests/src/test/data/box/inject"), Pattern.compile("^(.+)\\.kt$"), null, TargetBackend.JVM_IR, true);
    }

    @Test
    @TestMetadata("InjectedConstructorParametersWithGenericsWork.kt")
    public void testInjectedConstructorParametersWithGenericsWork() {
      runTest("compiler-tests/src/test/data/box/inject/InjectedConstructorParametersWithGenericsWork.kt");
    }

    @Test
    @TestMetadata("InjectedFunInterfaceParametersWithLambdaDefaultsWork.kt")
    public void testInjectedFunInterfaceParametersWithLambdaDefaultsWork() {
      runTest("compiler-tests/src/test/data/box/inject/InjectedFunInterfaceParametersWithLambdaDefaultsWork.kt");
    }

    @Test
    @TestMetadata("InjectedFunctionParametersWithLambdaDefaultsWork.kt")
    public void testInjectedFunctionParametersWithLambdaDefaultsWork() {
      runTest("compiler-tests/src/test/data/box/inject/InjectedFunctionParametersWithLambdaDefaultsWork.kt");
    }

    @Nested
    @TestMetadata("compiler-tests/src/test/data/box/inject/assisted")
    @TestDataPath("$PROJECT_ROOT")
    public class Assisted {
      @Test
      public void testAllFilesPresentInAssisted() {
        KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("compiler-tests/src/test/data/box/inject/assisted"), Pattern.compile("^(.+)\\.kt$"), null, TargetBackend.JVM_IR, true);
      }

      @Test
      @TestMetadata("AssistedTypesCanBeExplicitlyProvided.kt")
      public void testAssistedTypesCanBeExplicitlyProvided() {
        runTest("compiler-tests/src/test/data/box/inject/assisted/AssistedTypesCanBeExplicitlyProvided.kt");
      }

      @Test
      @TestMetadata("GenericAssistedParams.kt")
      public void testGenericAssistedParams() {
        runTest("compiler-tests/src/test/data/box/inject/assisted/GenericAssistedParams.kt");
      }
    }

    @Nested
    @TestMetadata("compiler-tests/src/test/data/box/inject/member")
    @TestDataPath("$PROJECT_ROOT")
    public class Member {
      @Test
      public void testAllFilesPresentInMember() {
        KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("compiler-tests/src/test/data/box/inject/member"), Pattern.compile("^(.+)\\.kt$"), null, TargetBackend.JVM_IR, true);
      }

      @Test
      @TestMetadata("CustomMembersInjectorInstancesCanSatisfyBindings.kt")
      public void testCustomMembersInjectorInstancesCanSatisfyBindings() {
        runTest("compiler-tests/src/test/data/box/inject/member/CustomMembersInjectorInstancesCanSatisfyBindings.kt");
      }

      @Test
      @TestMetadata("CustomMembersInjectorInstancesIntoMap.kt")
      public void testCustomMembersInjectorInstancesIntoMap() {
        runTest("compiler-tests/src/test/data/box/inject/member/CustomMembersInjectorInstancesIntoMap.kt");
      }

      @Test
      @TestMetadata("GenericMemberInjection.kt")
      public void testGenericMemberInjection() {
        runTest("compiler-tests/src/test/data/box/inject/member/GenericMemberInjection.kt");
      }

      @Test
      @TestMetadata("GenericMemberInjectionFromGraph.kt")
      public void testGenericMemberInjectionFromGraph() {
        runTest("compiler-tests/src/test/data/box/inject/member/GenericMemberInjectionFromGraph.kt");
      }

      @Test
      @TestMetadata("GenericMemberInjectionWithDeepAncesors.kt")
      public void testGenericMemberInjectionWithDeepAncesors() {
        runTest("compiler-tests/src/test/data/box/inject/member/GenericMemberInjectionWithDeepAncesors.kt");
      }

      @Test
      @TestMetadata("GenericMemberInjectorRequest.kt")
      public void testGenericMemberInjectorRequest() {
        runTest("compiler-tests/src/test/data/box/inject/member/GenericMemberInjectorRequest.kt");
      }

      @Test
      @TestMetadata("InjectingSubclassWithNoMembersButParentsDo.kt")
      public void testInjectingSubclassWithNoMembersButParentsDo() {
        runTest("compiler-tests/src/test/data/box/inject/member/InjectingSubclassWithNoMembersButParentsDo.kt");
      }

      @Test
      @TestMetadata("MemberInjectorRequest.kt")
      public void testMemberInjectorRequest() {
        runTest("compiler-tests/src/test/data/box/inject/member/MemberInjectorRequest.kt");
      }

      @Test
      @TestMetadata("MemberInjectorRequestAsConstructorParam.kt")
      public void testMemberInjectorRequestAsConstructorParam() {
        runTest("compiler-tests/src/test/data/box/inject/member/MemberInjectorRequestAsConstructorParam.kt");
      }

      @Test
      @TestMetadata("MemberInjectorRequestAsProvidesParam.kt")
      public void testMemberInjectorRequestAsProvidesParam() {
        runTest("compiler-tests/src/test/data/box/inject/member/MemberInjectorRequestAsProvidesParam.kt");
      }

      @Test
      @TestMetadata("MemberInjectorRequestMultipleLocations.kt")
      public void testMemberInjectorRequestMultipleLocations() {
        runTest("compiler-tests/src/test/data/box/inject/member/MemberInjectorRequestMultipleLocations.kt");
      }

      @Test
      @TestMetadata("MemberInjectsInMultibinding.kt")
      public void testMemberInjectsInMultibinding() {
        runTest("compiler-tests/src/test/data/box/inject/member/MemberInjectsInMultibinding.kt");
      }

      @Test
      @TestMetadata("MultiInheritanceMemberInject.kt")
      public void testMultiInheritanceMemberInject() {
        runTest("compiler-tests/src/test/data/box/inject/member/MultiInheritanceMemberInject.kt");
      }
    }
  }

  @Nested
  @TestMetadata("compiler-tests/src/test/data/box/interop")
  @TestDataPath("$PROJECT_ROOT")
  public class Interop {
    @Test
    public void testAllFilesPresentInInterop() {
      KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("compiler-tests/src/test/data/box/interop"), Pattern.compile("^(.+)\\.kt$"), null, TargetBackend.JVM_IR, true);
    }

    @Nested
    @TestMetadata("compiler-tests/src/test/data/box/interop/dagger")
    @TestDataPath("$PROJECT_ROOT")
    public class Dagger {
      @Test
      public void testAllFilesPresentInDagger() {
        KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("compiler-tests/src/test/data/box/interop/dagger"), Pattern.compile("^(.+)\\.kt$"), null, TargetBackend.JVM_IR, true);
      }

      @Test
      @TestMetadata("AssistedDaggerFactoryClass.kt")
      public void testAssistedDaggerFactoryClass() {
        runTest("compiler-tests/src/test/data/box/interop/dagger/AssistedDaggerFactoryClass.kt");
      }

      @Test
      @TestMetadata("AssistedDaggerFactoryClassWithDifferentInputs.kt")
      public void testAssistedDaggerFactoryClassWithDifferentInputs() {
        runTest("compiler-tests/src/test/data/box/interop/dagger/AssistedDaggerFactoryClassWithDifferentInputs.kt");
      }

      @Test
      @TestMetadata("DaggerComponentModulesAnnotationInterop.kt")
      public void testDaggerComponentModulesAnnotationInterop() {
        runTest("compiler-tests/src/test/data/box/interop/dagger/DaggerComponentModulesAnnotationInterop.kt");
      }

      @Test
      @TestMetadata("DaggerFactoryClassCanBeLoaded.kt")
      public void testDaggerFactoryClassCanBeLoaded() {
        runTest("compiler-tests/src/test/data/box/interop/dagger/DaggerFactoryClassCanBeLoaded.kt");
      }

      @Test
      @TestMetadata("DaggerFactoryClassCanBeLoadedJakarta.kt")
      public void testDaggerFactoryClassCanBeLoadedJakarta() {
        runTest("compiler-tests/src/test/data/box/interop/dagger/DaggerFactoryClassCanBeLoadedJakarta.kt");
      }

      @Test
      @TestMetadata("DaggerFactoryClassWithDifferentInputs.kt")
      public void testDaggerFactoryClassWithDifferentInputs() {
        runTest("compiler-tests/src/test/data/box/interop/dagger/DaggerFactoryClassWithDifferentInputs.kt");
      }

      @Test
      @TestMetadata("DaggerModulesAnnotationInterop.kt")
      public void testDaggerModulesAnnotationInterop() {
        runTest("compiler-tests/src/test/data/box/interop/dagger/DaggerModulesAnnotationInterop.kt");
      }

      @Test
      @TestMetadata("DaggerMultibindsAllowEmptyByDefault.kt")
      public void testDaggerMultibindsAllowEmptyByDefault() {
        runTest("compiler-tests/src/test/data/box/interop/dagger/DaggerMultibindsAllowEmptyByDefault.kt");
      }

      @Test
      @TestMetadata("GenericDaggerFactoryClassCanBeLoaded.kt")
      public void testGenericDaggerFactoryClassCanBeLoaded() {
        runTest("compiler-tests/src/test/data/box/interop/dagger/GenericDaggerFactoryClassCanBeLoaded.kt");
      }

      @Test
      @TestMetadata("InjectedDaggerLazyInteropWorks.kt")
      public void testInjectedDaggerLazyInteropWorks() {
        runTest("compiler-tests/src/test/data/box/interop/dagger/InjectedDaggerLazyInteropWorks.kt");
      }

      @Test
      @TestMetadata("InjectedJavaxProviderInteropWorks.kt")
      public void testInjectedJavaxProviderInteropWorks() {
        runTest("compiler-tests/src/test/data/box/interop/dagger/InjectedJavaxProviderInteropWorks.kt");
      }

      @Test
      @TestMetadata("JavaxProviderShouldWorkInMap.kt")
      public void testJavaxProviderShouldWorkInMap() {
        runTest("compiler-tests/src/test/data/box/interop/dagger/JavaxProviderShouldWorkInMap.kt");
      }

      @Test
      @TestMetadata("JavaxProviderShouldWorkInSet.kt")
      public void testJavaxProviderShouldWorkInSet() {
        runTest("compiler-tests/src/test/data/box/interop/dagger/JavaxProviderShouldWorkInSet.kt");
      }

      @Test
      @TestMetadata("KotlinDaggerFactoryClassCanBeLoaded.kt")
      public void testKotlinDaggerFactoryClassCanBeLoaded() {
        runTest("compiler-tests/src/test/data/box/interop/dagger/KotlinDaggerFactoryClassCanBeLoaded.kt");
      }

      @Test
      @TestMetadata("KotlinDaggerObjectFactoryClassCanBeLoaded.kt")
      public void testKotlinDaggerObjectFactoryClassCanBeLoaded() {
        runTest("compiler-tests/src/test/data/box/interop/dagger/KotlinDaggerObjectFactoryClassCanBeLoaded.kt");
      }

      @Nested
      @TestMetadata("compiler-tests/src/test/data/box/interop/dagger/anvil")
      @TestDataPath("$PROJECT_ROOT")
      public class Anvil {
        @Test
        public void testAllFilesPresentInAnvil() {
          KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("compiler-tests/src/test/data/box/interop/dagger/anvil"), Pattern.compile("^(.+)\\.kt$"), null, TargetBackend.JVM_IR, true);
        }

        @Test
        @TestMetadata("DaggerMergeComponentModulesAnnotationInterop.kt")
        public void testDaggerMergeComponentModulesAnnotationInterop() {
          runTest("compiler-tests/src/test/data/box/interop/dagger/anvil/DaggerMergeComponentModulesAnnotationInterop.kt");
        }
      }
    }
  }

  @Nested
  @TestMetadata("compiler-tests/src/test/data/box/member")
  @TestDataPath("$PROJECT_ROOT")
  public class Member {
    @Test
    public void testAllFilesPresentInMember() {
      KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("compiler-tests/src/test/data/box/member"), Pattern.compile("^(.+)\\.kt$"), null, TargetBackend.JVM_IR, true);
    }

    @Test
    @TestMetadata("FieldInjectFactory.kt")
    public void testFieldInjectFactory() {
      runTest("compiler-tests/src/test/data/box/member/FieldInjectFactory.kt");
    }
  }

  @Nested
  @TestMetadata("compiler-tests/src/test/data/box/multibindings")
  @TestDataPath("$PROJECT_ROOT")
  public class Multibindings {
    @Test
    public void testAllFilesPresentInMultibindings() {
      KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("compiler-tests/src/test/data/box/multibindings"), Pattern.compile("^(.+)\\.kt$"), null, TargetBackend.JVM_IR, true);
    }

    @Test
    @TestMetadata("ElementsIntoSetProperty.kt")
    public void testElementsIntoSetProperty() {
      runTest("compiler-tests/src/test/data/box/multibindings/ElementsIntoSetProperty.kt");
    }

    @Test
    @TestMetadata("MultibindingGraphWithWithScopedMapProviderDeps.kt")
    public void testMultibindingGraphWithWithScopedMapProviderDeps() {
      runTest("compiler-tests/src/test/data/box/multibindings/MultibindingGraphWithWithScopedMapProviderDeps.kt");
    }
  }

  @Nested
  @TestMetadata("compiler-tests/src/test/data/box/provides")
  @TestDataPath("$PROJECT_ROOT")
  public class Provides {
    @Test
    public void testAllFilesPresentInProvides() {
      KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("compiler-tests/src/test/data/box/provides"), Pattern.compile("^(.+)\\.kt$"), null, TargetBackend.JVM_IR, true);
    }

    @Test
    @TestMetadata("CapitalizedProvides.kt")
    public void testCapitalizedProvides() {
      runTest("compiler-tests/src/test/data/box/provides/CapitalizedProvides.kt");
    }

    @Test
    @TestMetadata("ExplicitlyPrivateProviderAnnotationsArePropagated.kt")
    public void testExplicitlyPrivateProviderAnnotationsArePropagated() {
      runTest("compiler-tests/src/test/data/box/provides/ExplicitlyPrivateProviderAnnotationsArePropagated.kt");
    }

    @Test
    @TestMetadata("ProvidesParametersCanHaveDefaults.kt")
    public void testProvidesParametersCanHaveDefaults() {
      runTest("compiler-tests/src/test/data/box/provides/ProvidesParametersCanHaveDefaults.kt");
    }

    @Test
    @TestMetadata("QualifiersOnDifferentAnnotationSites.kt")
    public void testQualifiersOnDifferentAnnotationSites() {
      runTest("compiler-tests/src/test/data/box/provides/QualifiersOnDifferentAnnotationSites.kt");
    }

    @Test
    @TestMetadata("SimpleFunctionProvider.kt")
    public void testSimpleFunctionProvider() {
      runTest("compiler-tests/src/test/data/box/provides/SimpleFunctionProvider.kt");
    }

    @Test
    @TestMetadata("StatusTransformedPrivateProviderAnnotationsArePropagated.kt")
    public void testStatusTransformedPrivateProviderAnnotationsArePropagated() {
      runTest("compiler-tests/src/test/data/box/provides/StatusTransformedPrivateProviderAnnotationsArePropagated.kt");
    }

    @Test
    @TestMetadata("TransitiveSuccessorScope.kt")
    public void testTransitiveSuccessorScope() {
      runTest("compiler-tests/src/test/data/box/provides/TransitiveSuccessorScope.kt");
    }
  }
}
