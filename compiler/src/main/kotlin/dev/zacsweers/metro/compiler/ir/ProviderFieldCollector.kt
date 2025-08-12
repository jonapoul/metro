// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.compiler.ir

import dev.zacsweers.metro.compiler.Symbols
import org.jetbrains.kotlin.backend.jvm.codegen.AnnotationCodegen.Companion.annotationClass
import org.jetbrains.kotlin.ir.util.classId

private const val INITIAL_VALUE = 512

/** Computes the set of bindings that must end up in provider fields. */
internal class ProviderFieldCollector(private val graph: IrBindingGraph) {

  private data class Node(val binding: IrBinding, var refCount: Int = 0) {
    val needsField: Boolean
      get() {
        // Scoped, graph, and members injector bindings always need provider fields
        if (binding.scope != null) return true
        if (binding is IrBinding.GraphDependency) return true
        if (binding is IrBinding.MembersInjected && !binding.isFromInjectorFunction) return true
        // Multibindings are always created adhoc
        if (binding is IrBinding.Multibinding) return false
        // Assisted types always need to be a single field to ensure use of the same provider
        if (binding is IrBinding.Assisted) return true
        // TODO what about assisted but no assisted params? These also don't become providers
        //  we would need to track a set of assisted targets somewhere
        if (binding is IrBinding.ConstructorInjected && binding.isAssisted) return true

        if (
          binding.typeKey.qualifier?.ir?.annotationClass?.classId ==
            Symbols.ClassIds.MultibindingElement
        ) {
          return true
        }

        // If it's unscoped but used more than once and not into a multibinding,
        // we can generate a reusable field
        return refCount >= 2
      }

    /** @return true if we've referenced this binding before. */
    fun mark(): Boolean {
      refCount++
      return refCount > 1
    }
  }

  private val nodes = HashMap<IrTypeKey, Node>(INITIAL_VALUE)

  fun collect(): Map<IrTypeKey, IrBinding> {
    // Count references for each dependency
    for ((key, binding) in graph.bindingsSnapshot()) {
      // Ensure each key has a node
      nodes.getOrPut(key) { Node(binding) }
      for (dependency in binding.dependencies) {
        dependency.mark()
      }
    }

    // Decide which bindings actually need provider fields
    return buildMap(nodes.size) {
      for ((key, node) in nodes) {
        val binding = node.binding
        if (node.needsField) {
          put(key, binding)
        }
      }
    }
  }

  private fun IrContextualTypeKey.mark(): Boolean {
    val binding = graph.requireBinding(this, IrBindingStack.empty())
    return binding.mark()
  }

  private fun IrBinding.mark(): Boolean {
    val node = nodes.getOrPut(typeKey) { Node(this) }
    return node.mark()
  }
}
