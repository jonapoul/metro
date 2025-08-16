// Copyright (C) 2024 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.compiler.ir

import dev.zacsweers.metro.compiler.BitField
import dev.zacsweers.metro.compiler.Origins
import dev.zacsweers.metro.compiler.ir.parameters.Parameters
import dev.zacsweers.metro.compiler.mapToSet
import dev.zacsweers.metro.compiler.proto.DependencyGraphProto
import dev.zacsweers.metro.compiler.unsafeLazy
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.fileOrNull
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.name.ClassId

// Represents an object graph's structure and relationships
internal data class DependencyGraphNode(
  val sourceGraph: IrClass,
  val supertypes: List<IrType>,
  val includedGraphNodes: Map<IrTypeKey, DependencyGraphNode>,
  val graphExtensions: List<Pair<IrTypeKey, MetroSimpleFunction>>,
  val scopes: Set<IrAnnotation>,
  val aggregationScopes: Set<ClassId>,
  val providerFactories: List<Pair<IrTypeKey, ProviderFactory>>,
  // Types accessible via this graph (includes inherited)
  // Dagger calls these "provision methods", but that's a bit vague IMO
  val accessors: List<Pair<MetroSimpleFunction, IrContextualTypeKey>>,
  val bindsCallables: Set<BindsCallable>,
  val multibindsCallables: Set<MultibindsCallable>,
  /** Binding containers that need a managed instance. */
  val bindingContainers: Set<IrClass>,
  /** Fake overrides of binds functions that need stubbing. */
  val bindsFunctions: List<MetroSimpleFunction>,
  // TypeKey key is the injected type wrapped in MembersInjector
  val injectors: List<Pair<MetroSimpleFunction, IrContextualTypeKey>>,
  val isExternal: Boolean,
  val creator: Creator?,
  val extendedGraphNodes: Map<IrTypeKey, DependencyGraphNode>,
  val typeKey: IrTypeKey = IrTypeKey(sourceGraph.typeWith()),
  // TODO not ideal that this is mutable/lateinit but welp
  //  maybe we track these protos separately somewhere?
  var proto: DependencyGraphProto? = null,
) {
  val hasExtensions = graphExtensions.isNotEmpty()

  val metroGraph by unsafeLazy { sourceGraph.metroGraphOrNull }

  val metroGraphOrFail by unsafeLazy { metroGraph ?: error("No generated MetroGraph found: ${sourceGraph.kotlinFqName}") }

  /** [IrTypeKey] of the contributed graph extension, if any. */
  val contributedGraphTypeKey: IrTypeKey? by unsafeLazy {
    if (sourceGraph.origin == Origins.GeneratedGraphExtension) {
      IrTypeKey(sourceGraph.superTypes.first())
    } else {
      null
    }
  }

  /** [contributedGraphTypeKey] if not null, otherwise [typeKey]. */
  val originalTypeKey
    get() = contributedGraphTypeKey ?: typeKey

  val publicAccessors by unsafeLazy { accessors.mapToSet { (_, contextKey) -> contextKey.typeKey } }

  val reportableSourceGraphDeclaration by unsafeLazy {
    generateSequence(sourceGraph) { it.parentAsClass }
      .firstOrNull { it.origin != Origins.GeneratedGraphExtension && it.fileOrNull != null }
      ?: error(
        "Could not find a reportable source graph declaration for ${sourceGraph.kotlinFqName}"
      )
  }

  val multibindingAccessors by unsafeLazy {
    proto
      ?.let {
        val bitfield = BitField(it.multibinding_accessor_indices)
        val multibindingCallableIds =
          it.accessor_callable_names.filterIndexedTo(mutableSetOf()) { index, _ ->
            bitfield.isSet(index)
          }
        accessors
          .filter { it.first.ir.name.asString() in multibindingCallableIds }
          .mapToSet { it.first }
      }
      .orEmpty()
  }

  val allIncludedNodes by lazy { buildMap { recurseIncludedNodes(this) }.values.toSet() }

  val allExtendedNodes by lazy { buildMap { recurseParents(this) } }

  override fun toString(): String = typeKey.render(short = true)

  sealed class Creator {
    abstract val type: IrClass
    abstract val function: IrFunction
    abstract val parameters: Parameters
    abstract val bindingContainersParameterIndices: BitField

    val typeKey by unsafeLazy { IrTypeKey(type.typeWith()) }

    data class Constructor(
      override val type: IrClass,
      override val function: IrConstructor,
      override val parameters: Parameters,
      override val bindingContainersParameterIndices: BitField,
    ) : Creator()

    data class Factory(
      override val type: IrClass,
      override val function: IrSimpleFunction,
      override val parameters: Parameters,
      override val bindingContainersParameterIndices: BitField,
    ) : Creator()
  }
}

private fun DependencyGraphNode.recurseIncludedNodes(
  builder: MutableMap<IrTypeKey, DependencyGraphNode>
) {
  for ((key, node) in includedGraphNodes) {
    if (key !in builder) {
      builder[key] = node
      node.recurseIncludedNodes(builder)
    }
  }
  // Propagate included nodes from parent graphs
  for (node in extendedGraphNodes.values) {
    for (includedFromParent in node.allIncludedNodes) {
      builder[includedFromParent.typeKey] = includedFromParent
    }
  }
}

private fun DependencyGraphNode.recurseParents(
  builder: MutableMap<IrTypeKey, DependencyGraphNode>
) {
  for ((key, value) in extendedGraphNodes) {
    builder[key] = value
    value.recurseParents(builder)
  }
}
