// Copyright (C) 2024 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.compiler.ir

import dev.zacsweers.metro.compiler.MetroAnnotations
import dev.zacsweers.metro.compiler.Origins
import dev.zacsweers.metro.compiler.exitProcessing
import dev.zacsweers.metro.compiler.expectAs
import dev.zacsweers.metro.compiler.graph.MutableBindingGraph
import dev.zacsweers.metro.compiler.ir.parameters.wrapInProvider
import dev.zacsweers.metro.compiler.tracing.Tracer
import dev.zacsweers.metro.compiler.tracing.traceNested
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.isMarkedNullable
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.removeAnnotations
import org.jetbrains.kotlin.ir.types.typeOrFail
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.fileOrNull
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.isSubtypeOf
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.parentClassOrNull

internal class IrBindingGraph(
  private val metroContext: IrMetroContext,
  private val node: DependencyGraphNode,
  newBindingStack: () -> IrBindingStack,
  private val bindingLookup: BindingLookup,
) {
  private val realGraph =
    MutableBindingGraph(
      newBindingStack = newBindingStack,
      newBindingStackEntry = { contextKey, callingBinding, roots ->
        if (callingBinding == null) {
          roots.getValue(contextKey)
        } else {
          bindingStackEntryForDependency(callingBinding, contextKey, contextKey.typeKey)
        }
      },
      absentBinding = { key -> IrBinding.Absent(key) },
      computeBindings = { contextKey, currentBindings, stack ->
        bindingLookup.lookup(contextKey, currentBindings, stack)
      },
      onError = ::onError,
      findSimilarBindings = { key -> findSimilarBindings(key).mapValues { it.value.toString() } },
    )

  // TODO hoist accessors up and visit in seal?
  private val accessors = mutableMapOf<IrContextualTypeKey, IrBindingStack.Entry>()
  private val injectors = mutableMapOf<IrContextualTypeKey, IrBindingStack.Entry>()
  private val extraKeeps = mutableMapOf<IrContextualTypeKey, IrBindingStack.Entry>()

  // Thin immutable view over the internal bindings
  fun bindingsSnapshot(): Map<IrTypeKey, IrBinding> = realGraph.bindings

  fun addAccessor(key: IrContextualTypeKey, entry: IrBindingStack.Entry) {
    accessors[key] = entry
  }

  fun addInjector(key: IrContextualTypeKey, entry: IrBindingStack.Entry) {
    injectors[key] = entry
  }

  fun addBinding(key: IrTypeKey, binding: IrBinding, bindingStack: IrBindingStack) {
    realGraph.tryPut(binding, bindingStack, key)
  }

  fun keep(key: IrContextualTypeKey, entry: IrBindingStack.Entry) {
    extraKeeps[key] = entry
  }

  fun findBinding(key: IrTypeKey): IrBinding? = realGraph[key]

  // For bindings we expect to already be cached
  fun requireBinding(key: IrTypeKey, stack: IrBindingStack): IrBinding {
    return requireBinding(IrContextualTypeKey.create(key), stack)
  }

  fun requireBinding(contextKey: IrContextualTypeKey, stack: IrBindingStack): IrBinding {
    return realGraph[contextKey.typeKey]
      ?: run {
        if (contextKey.hasDefault) return IrBinding.Absent(contextKey.typeKey)
        realGraph.reportMissingBinding(contextKey.typeKey, stack) {
          if (metroContext.debug) {
            appendLine(dumpGraph(stack.graphFqName.asString(), short = false))
          }
        }
      }
  }

  context(context: IrMetroContext)
  fun getOrCreateMultibinding(
    annotations: MetroAnnotations<IrAnnotation>,
    contextKey: IrContextualTypeKey,
    declaration: IrSimpleFunction,
    originalQualifier: IrAnnotation?,
    bindingStack: IrBindingStack,
  ): IrBinding.Multibinding {
    val multibindingTypeKey =
      when {
        annotations.isIntoSet -> {
          val setType = metroContext.irBuiltIns.setClass.typeWith(contextKey.typeKey.type)
          contextKey.typeKey.copy(type = setType, qualifier = originalQualifier)
        }

        annotations.isElementsIntoSet -> {
          val elementType =
            contextKey.typeKey.type.expectAs<IrSimpleType>().arguments.single().typeOrFail
          val setType = metroContext.irBuiltIns.setClass.typeWith(elementType)
          contextKey.typeKey.copy(type = setType, qualifier = originalQualifier)
        }
        annotations.isIntoMap -> {
          val mapKey =
            annotations.mapKeys.firstOrNull()
              ?: run {
                // Hard error because the FIR checker should catch these, so this implies broken
                // FIR code gen
                error("Missing @MapKey for @IntoMap function: ${declaration.locationOrNull()}")
              }
          val keyType = mapKeyType(mapKey)
          val mapType =
            metroContext.irBuiltIns.mapClass.typeWith(
              // MapKey is the key type
              keyType,
              // Return type is the value type
              contextKey.typeKey.type.removeAnnotations(),
            )
          contextKey.typeKey.copy(type = mapType, qualifier = originalQualifier)
        }

        else -> {
          error(
            "Unrecognized provider: ${declaration.locationOrNull() ?: ("\n" + declaration.dumpKotlinLike())}"
          )
        }
      }

    var binding = realGraph[multibindingTypeKey]

    if (binding == null) {
      binding =
        context(metroContext) { IrBinding.Multibinding.fromContributor(multibindingTypeKey) }
      realGraph.tryPut(binding, bindingStack)
      // If it's a map, expose a binding for Map<KeyType, Provider<ValueType>>
      if (binding.isMap) {
        val keyType = (binding.typeKey.type as IrSimpleType).arguments[0].typeOrNull!!
        val valueType =
          binding.typeKey.type.arguments[1]
            .typeOrNull!!
            .wrapInProvider(this@IrBindingGraph.metroContext.symbols.metroProvider)
        val providerTypeKey =
          binding.typeKey.copy(type = context.irBuiltIns.mapClass.typeWith(keyType, valueType))
        realGraph.tryPut(binding, bindingStack, providerTypeKey)
      }
    }

    return binding as? IrBinding.Multibinding
      ?: error(
        """
        Expected a multibinding but got $binding.
      """
          .trimIndent()
      )
  }

  operator fun contains(key: IrTypeKey): Boolean = key in realGraph

  data class BindingGraphResult(
    val sortedKeys: List<IrTypeKey>,
    val deferredTypes: List<IrTypeKey>,
    val reachableKeys: Set<IrTypeKey>,
  )

  data class GraphError(val declaration: IrDeclaration?, val message: String)

  fun seal(parentTracer: Tracer, onError: (List<GraphError>) -> Nothing): BindingGraphResult =
    context(metroContext) {
      val (sortedKeys, deferredTypes, reachableKeys) =
        parentTracer.traceNested("seal graph") { tracer ->
          val roots = buildMap {
            putAll(accessors)
            putAll(injectors)
          }

          realGraph.seal(
            roots = roots,
            keep = extraKeeps,
            shrinkUnusedBindings = metroContext.options.shrinkUnusedBindings,
            tracer = tracer,
            onPopulated = {
              writeDiagnostic("keys-populated-${parentTracer.tag}.txt") {
                realGraph.bindings.keys.sorted().joinToString("\n")
              }
            },
            onSortedCycle = { elementsInCycle ->
              writeDiagnostic(
                "cycle-${parentTracer.tag}-${elementsInCycle[0].render(short = true, includeQualifier = false)}.txt"
              ) {
                elementsInCycle.plus(elementsInCycle[0]).joinToString("\n")
              }
            },
            validateBindings = ::validateBindings,
          )
        }

      writeDiagnostic("keys-validated-${parentTracer.tag}.txt") {
        sortedKeys.joinToString(separator = "\n")
      }

      writeDiagnostic("keys-deferred-${parentTracer.tag}.txt") {
        deferredTypes.joinToString(separator = "\n")
      }

      val unused = bindingsSnapshot().keys - reachableKeys
      if (unused.isNotEmpty()) {
        // TODO option to warn or fail? What about extensions that implicitly have many unused
        writeDiagnostic("keys-unused-${parentTracer.tag}.txt") {
          unused.joinToString(separator = "\n")
        }
      }

      parentTracer.traceNested("check empty multibindings") { checkEmptyMultibindings(onError) }
      parentTracer.traceNested("check for absent bindings") {
        check(realGraph.bindings.values.none { it is IrBinding.Absent }) {
          "Found absent bindings in the binding graph: ${dumpGraph("Absent bindings", short = true)}"
        }
      }
      return BindingGraphResult(sortedKeys, deferredTypes, reachableKeys)
    }

  fun reportDuplicateBinding(
    key: IrTypeKey,
    existing: IrBinding,
    duplicate: IrBinding,
    bindingStack: IrBindingStack,
  ) {
    realGraph.reportDuplicateBinding(key, existing, duplicate, bindingStack)
  }

  private fun checkEmptyMultibindings(onError: (List<GraphError>) -> Nothing) {
    val multibindings = realGraph.bindings.values.filterIsInstance<IrBinding.Multibinding>()
    val errors = mutableListOf<GraphError>()
    for (multibinding in multibindings) {
      if (!multibinding.allowEmpty && multibinding.sourceBindings.isEmpty()) {
        val message = buildString {
          append("[Metro/EmptyMultibinding] Multibinding '")
          append(multibinding.typeKey)
          appendLine("' was unexpectedly empty.")

          appendLine()
          appendLine(
            "If you expect this multibinding to possibly be empty, annotate its declaration with `@Multibinds(allowEmpty = true)`."
          )

          val similarBindings = findSimilarMultibindings(multibinding, multibindings).toList()
          if (similarBindings.isNotEmpty()) {
            appendLine()
            appendLine("Similar multibindings:")
            val reported = mutableSetOf<IrTypeKey>()
            for (key in similarBindings) {
              if (key in reported) continue
              appendLine("- ${key.render(short = true)}")
              reported += key
            }
          }
        }
        val declarationToReport =
          if (multibinding.declaration?.isFakeOverride == true) {
            multibinding.declaration!!
              .overriddenSymbolsSequence()
              .firstOrNull { !it.owner.isFakeOverride }
              ?.owner
          } else {
            multibinding.declaration
          }
        errors += GraphError(declarationToReport, message)
      }
    }
    if (errors.isNotEmpty()) {
      onError(errors)
    }
  }

  private fun findSimilarMultibindings(
    multibinding: IrBinding.Multibinding,
    multibindings: List<IrBinding.Multibinding>,
  ): Sequence<IrTypeKey> = sequence {
    if (multibinding.isMap) {
      val keyType = multibinding.typeKey.requireMapKeyType()
      val valueType = multibinding.typeKey.requireMapValueType()
      val similarKeys =
        multibindings
          .filter { it.isMap && it != multibinding && it.typeKey.requireMapKeyType() == keyType }
          .map { it.typeKey }

      yieldAll(similarKeys)

      val similarValues =
        multibindings
          .filter {
            if (!it.isMap) return@filter false
            if (it == multibinding) return@filter false
            val otherValueType = it.typeKey.requireMapValueType()
            if (valueType == otherValueType) return@filter true
            if (valueType.isSubtypeOf(otherValueType, metroContext.irTypeSystemContext))
              return@filter true
            if (otherValueType.isSubtypeOf(valueType, metroContext.irTypeSystemContext))
              return@filter true
            false
          }
          .map { it.typeKey }

      yieldAll(similarValues)
    } else {
      // Set binding
      val elementType = multibinding.typeKey.requireSetElementType()

      val similar =
        multibindings
          .filter {
            if (!it.isSet) return@filter false
            if (it == multibinding) return@filter false
            val otherElementType = it.typeKey.requireSetElementType()
            if (elementType == otherElementType) return@filter true
            if (elementType.isSubtypeOf(otherElementType, metroContext.irTypeSystemContext))
              return@filter true
            if (otherElementType.isSubtypeOf(elementType, metroContext.irTypeSystemContext))
              return@filter true
            false
          }
          .map { it.typeKey }

      yieldAll(similar)
    }
  }

  private fun findSimilarBindings(key: IrTypeKey): Map<IrTypeKey, SimilarBinding> {
    // Use a map to avoid reporting duplicates
    val similarBindings = mutableMapOf<IrTypeKey, SimilarBinding>()

    // Same type with different qualifier
    if (key.qualifier != null) {
      findBinding(key.copy(qualifier = null))?.let {
        similarBindings.putIfAbsent(it.typeKey, SimilarBinding(it, "Different qualifier"))
      }
    }

    // Check for nullable/non-nullable equivalent
    val isNullable = key.type.isMarkedNullable()
    val equivalentType =
      if (isNullable) {
        key.type.makeNotNull()
      } else {
        key.type.makeNullable()
      }
    val equivalentKey = key.copy(type = equivalentType)
    findBinding(equivalentKey)?.let {
      val nullabilityDescription =
        if (isNullable) "Non-nullable equivalent" else "Nullable equivalent"
      similarBindings.putIfAbsent(it.typeKey, SimilarBinding(it, nullabilityDescription))
    }

    // Merge graph bindings and cached bindings from BindingLookup
    val allBindings = buildMap {
      putAll(realGraph.bindings)
      // Add cached bindings that aren't already in the graph
      bindingLookup.getAvailableStaticBindings().forEach { (bindingKey, binding) ->
        putIfAbsent(bindingKey, binding)
      }
    }

    // Iterate through all bindings to find similar ones
    allBindings.forEach { (bindingKey, binding) ->
      when {
        bindingKey.type == key.type && key.qualifier != bindingKey.qualifier -> {
          similarBindings.putIfAbsent(bindingKey, SimilarBinding(binding, "Different qualifier"))
        }
        binding is IrBinding.Multibinding -> {
          val valueType =
            if (binding.isSet) {
              (bindingKey.type.type as IrSimpleType).arguments[0].typeOrFail
            } else {
              // Map binding
              (bindingKey.type.type as IrSimpleType).arguments[1].typeOrFail
            }
          if (valueType == key.type) {
            similarBindings.putIfAbsent(bindingKey, SimilarBinding(binding, "Multibinding"))
          }
        }
        bindingKey.type == key.type -> {
          // Already covered above but here to avoid falling through to the subtype checks
          // below as they would always return true for this
        }
        bindingKey.type.isSubtypeOf(key.type, metroContext.irTypeSystemContext) -> {
          similarBindings.putIfAbsent(bindingKey, SimilarBinding(binding, "Subtype"))
        }
        key.type.type.isSubtypeOf(bindingKey.type, metroContext.irTypeSystemContext) -> {
          similarBindings.putIfAbsent(bindingKey, SimilarBinding(binding, "Supertype"))
        }
      }
    }

    return similarBindings.filterNot {
      (it.value.binding as? IrBinding.BindingWithAnnotations)?.annotations?.isIntoMultibinding ==
        true
    }
  }

  // TODO iterate on this more!
  internal fun dumpGraph(name: String, short: Boolean): String {
    if (realGraph.bindings.isEmpty()) return "Empty binding graph"

    return buildString {
      append("Binding Graph: ")
      appendLine(name)
      // Sort by type key for consistent output
      realGraph.bindings.entries
        .sortedBy { it.key.toString() }
        .forEach { (_, binding) ->
          appendLine("─".repeat(50))
          appendBinding(binding, short, isNested = false)
        }
    }
  }

  /**
   * We always want to report the original declaration for overridable nodes, as fake overrides
   * won't necessarily have source that is reportable.
   */
  @Suppress("UNCHECKED_CAST")
  private fun <T : IrDeclaration> T.originalDeclarationIfOverride(): T {
    return when (this) {
      is IrValueParameter -> {
        val index = indexInParameters
        // Need to check if the parent is a fakeOverride function or property setter
        val parent = parent.expectAs<IrFunction>()
        val originalParent = parent.originalDeclarationIfOverride()
        return originalParent.parameters[index] as T
      }
      is IrSimpleFunction if isFakeOverride -> {
        overriddenSymbolsSequence().last().owner as T
      }
      is IrProperty if isFakeOverride -> {
        overriddenSymbolsSequence().last().owner as T
      }
      else -> this
    }
  }

  private fun onError(message: String, stack: IrBindingStack): Nothing {
    val declaration =
      stack.lastEntryOrGraph?.originalDeclarationIfOverride()
        ?: node.reportableSourceGraphDeclaration
    if (declaration.fileOrNull == null) {
      // TODO move to diagnostic reporter in 2.2.20 https://youtrack.jetbrains.com/issue/KT-78280
      metroContext.logVerbose(
        "File-less declaration for ${declaration.dumpKotlinLike()} in ${declaration.parentClassOrNull?.dumpKotlinLike()}"
      )
      metroContext.messageCollector.report(
        CompilerMessageSeverity.ERROR,
        message,
        declaration.locationOrNull(),
      )
    } else {
      metroContext.diagnosticReporter.at(declaration).report(MetroIrErrors.METRO_ERROR, message)
    }
    exitProcessing()
  }

  private fun validateBindings(
    bindings: Map<IrTypeKey, IrBinding>,
    stack: IrBindingStack,
    roots: Map<IrContextualTypeKey, IrBindingStack.Entry>,
    adjacency: Map<IrTypeKey, Set<IrTypeKey>>,
  ) {
    val reverseAdjacency = buildReverseAdjacency(adjacency)
    val rootsByTypeKey = roots.mapKeys { it.key.typeKey }
    for (binding in bindings.values) {
      checkScope(binding, stack, roots, adjacency)
      validateAssistedInjection(binding, bindings, rootsByTypeKey, reverseAdjacency)
    }
  }

  private fun buildReverseAdjacency(
    adjacency: Map<IrTypeKey, Set<IrTypeKey>>
  ): Map<IrTypeKey, Set<IrTypeKey>> {
    val reverse = mutableMapOf<IrTypeKey, MutableSet<IrTypeKey>>()
    for ((from, tos) in adjacency) {
      for (to in tos) {
        reverse.getOrPut(to) { mutableSetOf() }.add(from)
      }
    }
    return reverse
  }

  // Check scoping compatibility
  // TODO FIR error?
  private fun checkScope(
    binding: IrBinding,
    stack: IrBindingStack,
    roots: Map<IrContextualTypeKey, IrBindingStack.Entry>,
    adjacency: Map<IrTypeKey, Set<IrTypeKey>>,
  ) {
    val bindingScope = binding.scope
    if (bindingScope != null) {
      if (node.scopes.isEmpty() || bindingScope !in node.scopes) {
        val isUnscoped = node.scopes.isEmpty()
        // Error if there are mismatched scopes
        val declarationToReport =
          if (node.sourceGraph.origin == Origins.GeneratedGraphExtension) {
            node.sourceGraph.parentAsClass
          } else {
            node.sourceGraph
          }
        val backTrace = buildRouteToRoot(binding.typeKey, roots, adjacency)
        for (entry in backTrace) {
          stack.push(entry)
        }
        stack.push(
          IrBindingStack.Entry.simpleTypeRef(
            binding.contextualTypeKey,
            usage = "(scoped to '$bindingScope')",
          )
        )
        val message = buildString {
          append("[Metro/IncompatiblyScopedBindings] ")
          append(node.sourceGraph.kotlinFqName)
          if (isUnscoped) {
            // Unscoped graph but scoped binding
            append(" (unscoped) may not reference scoped bindings:")
          } else {
            // Scope mismatch
            append(
              " (scopes ${node.scopes.joinToString { "'$it'" }}) may not reference bindings from different scopes:"
            )
          }
          appendLine()
          appendBindingStack(stack, short = false)

          if (node.sourceGraph.origin == Origins.GeneratedGraphExtension) {
            appendLine()
            appendLine()
            appendLine("(Hint)")
            append(
              "${node.sourceGraph.name} is contributed by '${node.sourceGraph.sourceGraphIfMetroGraph.kotlinFqName}' to '${declarationToReport.sourceGraphIfMetroGraph.kotlinFqName}'."
            )
          }
        }
        // TODO remove messagecollector in 2.2.20
        if (declarationToReport.origin == Origins.GeneratedGraphExtension) {
          metroContext.messageCollector.report(
            CompilerMessageSeverity.ERROR,
            message,
            declarationToReport.location(),
          )
        } else {
          metroContext.diagnosticReporter
            .at(declarationToReport)
            .report(MetroIrErrors.METRO_ERROR, message)
        }
      }
    }
  }

  private fun validateAssistedInjection(
    binding: IrBinding,
    bindings: Map<IrTypeKey, IrBinding>,
    roots: Map<IrTypeKey, IrBindingStack.Entry>,
    reverseAdjacency: Map<IrTypeKey, Set<IrTypeKey>>,
  ) {
    if (binding !is IrBinding.ConstructorInjected || !binding.isAssisted) return

    fun reportInvalidBinding(declaration: IrDeclarationWithName?) {
      // Look up the assisted factory as a hint
      val assistedFactory =
        bindings.values.find { it is IrBinding.Assisted && it.target.typeKey == binding.typeKey }
      // Report an error for anything that isn't an assisted binding depending on this
      val message = buildString {
        append("[Metro/InvalidBinding] ")
        append(
          "'${binding.typeKey}' uses assisted injection and cannot be injected directly into '${declaration?.fqNameWhenAvailable}'. You must inject a corresponding @AssistedFactory type instead."
        )
        if (assistedFactory != null) {
          appendLine()
          appendLine()
          appendLine("(Hint)")
          appendLine(
            "It looks like the @AssistedFactory for '${binding.typeKey}' is '${assistedFactory.typeKey}'."
          )
        }
      }
      val toReport = declaration ?: node.sourceGraph
      if (toReport.fileOrNull == null) {
        metroContext.messageCollector.report(
          CompilerMessageSeverity.ERROR,
          message,
          toReport.locationOrNull(),
        )
      } else {
        metroContext.diagnosticReporter
          .at(declaration ?: node.sourceGraph)
          .report(MetroIrErrors.METRO_ERROR, message)
      }
    }

    reverseAdjacency[binding.typeKey]?.let { dependents ->
      for (dependentKey in dependents) {
        val dependentBinding = bindings[dependentKey] ?: continue
        if (dependentBinding !is IrBinding.Assisted) {
          reportInvalidBinding(
            dependentBinding.parametersByKey[binding.typeKey]?.ir?.takeIf {
              val location = it.locationOrNull() ?: return@takeIf false
              location.line != 0 || location.column != 0
            } ?: dependentBinding.reportableDeclaration
          )
        }
      }
    }
    roots[binding.typeKey]?.let { reportInvalidBinding(it.declaration) }
  }

  /**
   * Builds a route from this binding back to one of the root bindings. Useful for error messaging
   * to show a trace back to an entry point.
   */
  private fun buildRouteToRoot(
    key: IrTypeKey,
    roots: Map<IrContextualTypeKey, IrBindingStack.Entry>,
    adjacency: Map<IrTypeKey, Set<IrTypeKey>>,
  ): List<IrBindingStack.Entry> {
    // Build who depends on what
    val dependents = mutableMapOf<IrTypeKey, MutableSet<IrTypeKey>>()
    for ((key, deps) in adjacency) {
      for (dep in deps) {
        dependents.getOrPut(dep) { mutableSetOf() }.add(key)
      }
    }

    // Walk backwards from this binding to find a root
    val visited = mutableSetOf<IrTypeKey>()

    fun walkToRoot(current: IrTypeKey, path: List<IrTypeKey>): List<IrTypeKey>? {
      if (current in visited) return null // Cycle

      // Is this a root?
      if (roots.any { it.key.typeKey == current }) {
        return path + current
      }

      visited.add(current)

      // Try walking through each dependent
      for (dependent in dependents[current].orEmpty()) {
        walkToRoot(dependent, path + current)?.let {
          return it
        }
      }

      visited.remove(current)
      return null
    }

    val path = walkToRoot(key, emptyList()) ?: return emptyList()

    // Convert to stack entries - just create a simple stack and build it up
    val result = mutableListOf<IrBindingStack.Entry>()

    for (i in path.indices.reversed()) {
      val typeKey = path[i]

      if (i == path.lastIndex) {
        // This is the root
        val rootEntry = roots.entries.first { it.key.typeKey == typeKey }.value
        result.add(0, rootEntry)
      } else {
        // Create an entry for this step
        val callingBinding = realGraph.bindings.getValue(path[i + 1])
        val contextKey = callingBinding.dependencies.first { it.typeKey == typeKey }
        val entry = bindingStackEntryForDependency(callingBinding, contextKey, contextKey.typeKey)
        result.add(0, entry)
      }
    }

    // Reverse the route as these will push onto the top of the stack
    return result.asReversed()
  }

  private fun Appendable.appendBinding(binding: IrBinding, short: Boolean, isNested: Boolean) {
    appendLine("Type: ${binding.typeKey.render(short)}")
    appendLine("├─ Binding: ${binding::class.simpleName}")
    appendLine("├─ Contextual Type: ${binding.contextualTypeKey.render(short)}")

    binding.scope?.let { scope -> appendLine("├─ Scope: $scope") }

    if (binding is IrBinding.Alias) {
      appendLine("├─ Aliased type: ${binding.aliasedType.render(short)}")
    }

    if (binding.parametersByKey.isNotEmpty()) {
      appendLine("├─ Dependencies:")
      binding.parametersByKey.forEach { (depKey, param) ->
        appendLine("│  ├─ ${depKey.render(short)}")
        appendLine("│  │  └─ Parameter: ${param.name} (${param.contextualTypeKey.render(short)})")
      }
    }

    if (binding.parameters.allParameters.isNotEmpty()) {
      appendLine("├─ Parameters:")
      binding.parameters.allParameters.forEach { param ->
        appendLine("│  └─ ${param.name}: ${param.contextualTypeKey.render(short)}")
      }
    }

    if (!isNested && binding is IrBinding.Multibinding && binding.sourceBindings.isNotEmpty()) {
      appendLine("├─ Source bindings:")
      binding.sourceBindings.forEach { sourceBindingKey ->
        val sourceBinding = requireBinding(sourceBindingKey, IrBindingStack.empty())
        val nested = buildString { appendBinding(sourceBinding, short, isNested = true) }
        append("│  ├─ ")
        appendLine(nested.lines().first())
        appendLine(nested.lines().drop(1).joinToString("\n").prependIndent("│  │  "))
      }
    }

    binding.reportableDeclaration?.locationOrNull()?.let { location ->
      appendLine("└─ Location: ${location.render()}")
    }
  }

  data class SimilarBinding(val binding: IrBinding, val description: String) {
    override fun toString(): String {
      return buildString {
        append(binding.typeKey.render(short = true))
        append(" (")
        append(description)
        append("). Type: ")
        append(binding.javaClass.simpleName)
        append('.')
        binding.reportableDeclaration?.locationOrNull()?.render()?.let {
          append(" Source: ")
          append(it)
        }
      }
    }
  }
}
