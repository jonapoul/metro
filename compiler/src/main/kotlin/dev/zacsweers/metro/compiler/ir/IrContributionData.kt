// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.compiler.ir

import dev.zacsweers.metro.compiler.Symbols
import dev.zacsweers.metro.compiler.flatMapToSet
import dev.zacsweers.metro.compiler.mapNotNullToSet
import dev.zacsweers.metro.compiler.mapToSet
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithVisibility
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.moduleDescriptor
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.getPackageFragment
import org.jetbrains.kotlin.ir.util.nestedClasses
import org.jetbrains.kotlin.name.ClassId

private typealias Scope = ClassId

internal class IrContributionData(private val metroContext: IrMetroContext) {

  private val contributions = mutableMapOf<Scope, MutableSet<IrType>>()
  private val externalContributions = mutableMapOf<Scope, Set<IrType>>()

  private val bindingContainerContributions = mutableMapOf<Scope, MutableSet<IrClass>>()
  private val externalBindingContainerContributions = mutableMapOf<Scope, Set<IrClass>>()

  fun addContribution(scope: Scope, contribution: IrType) {
    contributions.getOrPut(scope) { mutableSetOf() }.add(contribution)
  }

  fun getContributions(scope: Scope): Set<IrType> = buildSet {
    contributions[scope]?.let(::addAll)
    addAll(findExternalContributions(scope))
  }

  fun addBindingContainerContribution(scope: Scope, contribution: IrClass) {
    bindingContainerContributions.getOrPut(scope) { mutableSetOf() }.add(contribution)
  }

  fun getBindingContainerContributions(scope: Scope): Set<IrClass> = buildSet {
    bindingContainerContributions[scope]?.let(::addAll)
    addAll(findExternalBindingContainerContributions(scope))
  }

  private fun findVisibleContributionClassesForScopeInHints(scope: Scope): Set<IrClass> {
    val functionsInPackage = metroContext.referenceFunctions(Symbols.CallableIds.scopeHint(scope))
    val contributingClasses =
      functionsInPackage
        .filter {
          if (it.owner.visibility == Visibilities.Internal) {
            it.owner.isVisibleAsInternal(it.owner.file)
          } else {
            true
          }
        }
        .mapToSet { contribution ->
          // This is the single value param
          contribution.owner.regularParameters.single().type.classOrFail.owner
        }
    return contributingClasses
  }

  // TODO this may do multiple lookups of the same origin class if it contributes to multiple scopes
  //  something we could possibly optimize in the future.
  private fun findExternalContributions(scope: Scope): Set<IrType> {
    return externalContributions.getOrPut(scope) {
      val contributingClasses = findVisibleContributionClassesForScopeInHints(scope)
      getScopedContributions(contributingClasses, scope, bindingContainersOnly = false)
    }
  }

  // TODO this may do multiple lookups of the same origin class if it contributes to multiple scopes
  //  something we could possibly optimize in the future.
  private fun findExternalBindingContainerContributions(scope: Scope): Set<IrClass> {
    return externalBindingContainerContributions.getOrPut(scope) {
      val contributingClasses = findVisibleContributionClassesForScopeInHints(scope)
      getScopedContributions(contributingClasses, scope, bindingContainersOnly = true)
        .mapNotNullToSet {
          it.classOrNull?.owner?.takeIf {
            it.isAnnotatedWithAny(metroContext.symbols.classIds.bindingContainerAnnotations)
          }
        }
    }
  }

  private fun getScopedContributions(
    contributingClasses: Collection<IrClass>,
    scope: Scope,
    bindingContainersOnly: Boolean,
  ): Set<IrType> {
    val filteredContributions = contributingClasses.toMutableList()

    // Remove replaced contributions
    contributingClasses
      .flatMap { contributingType ->
        contributingType
          .annotationsIn(metroContext.symbols.classIds.allContributesAnnotations)
          .filter { it.scopeOrNull() == scope }
          .flatMap { annotation -> annotation.replacedClasses() }
      }
      .distinct()
      .forEach { replacedClass ->
        filteredContributions.removeIf { it.symbol == replacedClass.symbol }
      }

    return filteredContributions
      .let { contributions ->
        if (bindingContainersOnly) {
          contributions.filter {
            it.isAnnotatedWithAny(metroContext.symbols.classIds.bindingContainerAnnotations)
          }
        } else {
          contributions.filterNot {
            it.isAnnotatedWithAny(metroContext.symbols.classIds.bindingContainerAnnotations)
          }
        }
      }
      .flatMapToSet {
        if (it.isAnnotatedWithAny(metroContext.symbols.classIds.bindingContainerAnnotations)) {
          setOf(it.defaultType)
        } else {
          it.nestedClasses.mapNotNullToSet { nestedClass ->
            val metroContribution =
              nestedClass.findAnnotations(Symbols.ClassIds.metroContribution).singleOrNull()
                ?: return@mapNotNullToSet null
            val contributionScope =
              metroContribution.scopeOrNull()
                ?: error("No scope found for @MetroContribution annotation")
            if (contributionScope == scope) {
              nestedClass.defaultType
            } else {
              null
            }
          }
        }
      }
  }

  // Copied from CheckerUtils.kt
  private fun IrDeclarationWithVisibility.isVisibleAsInternal(file: IrFile): Boolean {
    val referencedDeclarationPackageFragment = getPackageFragment()
    val module = file.module
    return module.descriptor.shouldSeeInternalsOf(
      referencedDeclarationPackageFragment.moduleDescriptor
    )
  }
}
