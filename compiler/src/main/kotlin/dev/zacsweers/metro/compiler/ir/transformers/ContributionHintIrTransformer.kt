// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.compiler.ir.transformers

import dev.zacsweers.metro.compiler.ir.IrMetroContext
import dev.zacsweers.metro.compiler.ir.annotationsIn
import dev.zacsweers.metro.compiler.ir.scopeOrNull
import dev.zacsweers.metro.compiler.mapNotNullToSet
import dev.zacsweers.metro.compiler.scopeHintFunctionName
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.declarations.IrClass

/**
 * A transformer that generates hint marker functions for _downstream_ compilations. This handles
 * both scoped @Inject classes and classes with contributing annotations. See [HintGenerator] for
 * more details about hint specifics.
 */
internal class ContributionHintIrTransformer(
  context: IrMetroContext,
  private val hintGenerator: HintGenerator,
) : IrMetroContext by context {

  fun visitClass(declaration: IrClass) {
    // Don't generate hints for non-public APIs
    // Internal is allowed for friend paths
    if (
      !declaration.visibility.isPublicAPI &&
        declaration.visibility != DescriptorVisibilities.INTERNAL
    ) {
      return
    }

    val contributions =
      declaration.annotationsIn(symbols.classIds.allContributesAnnotations).toList()

    val contributionScopes = contributions.mapNotNullToSet { it.scopeOrNull() }

    for (contributionScope in contributionScopes) {
      hintGenerator.generateHint(
        sourceClass = declaration,
        hintName = contributionScope.scopeHintFunctionName(),
      )
    }
  }
}
