// Copyright (C) 2024 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.compiler.fir.checkers

import dev.zacsweers.metro.compiler.fir.FirMetroErrors
import dev.zacsweers.metro.compiler.fir.FirTypeKey
import dev.zacsweers.metro.compiler.fir.allScopeClassIds
import dev.zacsweers.metro.compiler.fir.annotationsIn
import dev.zacsweers.metro.compiler.fir.classIds
import dev.zacsweers.metro.compiler.fir.singleAbstractFunction
import dev.zacsweers.metro.compiler.fir.validateApiDeclaration
import dev.zacsweers.metro.compiler.flatMapToSet
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.classKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.analysis.checkers.getContainingClassSymbol
import org.jetbrains.kotlin.fir.analysis.checkers.toClassLikeSymbol
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.types.isResolved
import org.jetbrains.kotlin.name.ClassId

internal object DependencyGraphCreatorChecker : FirClassChecker(MppCheckerKind.Common) {
  private val PLATFORM_TYPE_PACKAGES =
    setOf("android.", "androidx.", "java.", "javax.", "kotlin.", "kotlinx.", "scala.")

  private val NON_INCLUDES_KINDS = setOf(ClassKind.ENUM_CLASS, ClassKind.ANNOTATION_CLASS)

  context(context: CheckerContext, reporter: DiagnosticReporter)
  override fun check(declaration: FirClass) {
    declaration.source ?: return
    val session = context.session
    val classIds = session.classIds

    val graphFactoryAnnotation =
      declaration.annotationsIn(session, classIds.graphFactoryLikeAnnotations).singleOrNull()
        ?: return

    val annotationClassId = graphFactoryAnnotation.toAnnotationClassId(session) ?: return
    val contributesToAnno =
      declaration.annotationsIn(session, classIds.contributesToAnnotations).toList()
    val isContributedExtensionFactory =
      annotationClassId in classIds.graphExtensionFactoryAnnotations &&
        contributesToAnno.isNotEmpty()

    if (isContributedExtensionFactory) {
      // Must be interfaces
      if (declaration.classKind != ClassKind.INTERFACE) {
        reporter.reportOn(
          declaration.source,
          FirMetroErrors.GRAPH_CREATORS_ERROR,
          "Contributed @${annotationClassId.relativeClassName.asString()} declarations can only be interfaces.",
        )
        return
      }
    }

    declaration.validateApiDeclaration(
      "@${annotationClassId.relativeClassName.asString()} declarations",
      checkConstructor = true,
    ) {
      return
    }

    val createFunction =
      declaration.singleAbstractFunction(
        session,
        reporter,
        "@${annotationClassId.relativeClassName.asString()} declarations",
      ) {
        return
      }

    val targetGraph = createFunction.resolvedReturnType.toClassSymbol(session)
    val targetGraphAnnotation =
      targetGraph
        ?.resolvedCompilerAnnotationsWithClassIds
        ?.annotationsIn(session, classIds.graphLikeAnnotations)
        ?.singleOrNull()
    targetGraph?.let {
      if (targetGraphAnnotation == null) {
        reporter.reportOn(
          createFunction.resolvedReturnTypeRef.source ?: declaration.source,
          FirMetroErrors.GRAPH_CREATORS_ERROR,
          "@${annotationClassId.relativeClassName.asString()} abstract function '${createFunction.name}' must return a dependency graph but found ${it.classId.asSingleFqName()}.",
        )
        return
      }

      if (isContributedExtensionFactory) {
        // Target graph must be an extension
        if (
          targetGraphAnnotation.toAnnotationClassId(session) !in classIds.graphExtensionAnnotations
        ) {
          reporter.reportOn(
            targetGraphAnnotation.source ?: declaration.source,
            FirMetroErrors.GRAPH_CREATORS_ERROR,
            "@${annotationClassId.relativeClassName.asString()} abstract function '${createFunction.name}' must return a graph extension but found ${it.classId.asSingleFqName()}.",
          )
          return
        }
        // Factory must be nested in that class
        if (it.classId != declaration.getContainingClassSymbol()?.classId) {
          reporter.reportOn(
            targetGraphAnnotation.source ?: declaration.source,
            FirMetroErrors.GRAPH_CREATORS_ERROR,
            "@${annotationClassId.relativeClassName.asString()} declarations must be nested within the contributed graph they create but was ${declaration.getContainingClassSymbol()?.classId?.asSingleFqName() ?: "top-level"}.",
          )
          return
        }
      }
    }

    val targetGraphScopes = targetGraphAnnotation?.allScopeClassIds().orEmpty()

    if (isContributedExtensionFactory) {
      val contributedScopes = contributesToAnno.flatMapToSet { it.allScopeClassIds() }
      val overlapping = contributedScopes.intersect(targetGraphScopes)
      // ContributesGraphExtension.Factory must not contribute to the same scope as its containing
      // graph, otherwise it'd be contributing to itself!
      if (overlapping.isNotEmpty()) {
        reporter.reportOn(
          graphFactoryAnnotation.source ?: declaration.source,
          FirMetroErrors.GRAPH_CREATORS_ERROR,
          "${annotationClassId.relativeClassName.asString()} declarations must contribute to a different scope than their contributed graph. However, this factory and its contributed graph both contribute to '${overlapping.map { it.asFqNameString() }.single()}'.",
        )
        return
      }
    }

    val paramTypes = mutableSetOf<FirTypeKey>()

    for (param in createFunction.valueParameterSymbols) {
      val typeKey = FirTypeKey.from(session, param)
      if (!paramTypes.add(typeKey)) {
        reporter.reportOn(
          param.source,
          FirMetroErrors.GRAPH_CREATORS_ERROR,
          "${annotationClassId.relativeClassName.asString()} abstract function parameters must be unique.",
        )
        continue
      }

      var isIncludes = false
      var isProvides = false

      for (annotation in param.resolvedCompilerAnnotationsWithClassIds) {
        if (!annotation.isResolved) continue
        val annotationClassId = annotation.toAnnotationClassIdSafe(session) ?: continue
        when (annotationClassId) {
          in classIds.includes -> {
            isIncludes = true
          }

          in classIds.providesAnnotations -> {
            isProvides = true
          }
        }
      }

      val reportAnnotationCountError = {
        reporter.reportOn(
          param.source,
          FirMetroErrors.GRAPH_CREATORS_ERROR,
          "${annotationClassId.relativeClassName.asString()} abstract function parameters must be annotated with exactly one @Includes or @Provides.",
        )
      }
      if (isIncludes && isProvides) {
        reportAnnotationCountError()
        continue
      }

      val type = param.resolvedReturnTypeRef.toClassLikeSymbol(session) ?: continue

      // Don't allow the target graph as a param
      if (type.classId == targetGraph?.classId) {
        reporter.reportOn(
          param.resolvedReturnTypeRef.source ?: param.source ?: declaration.source,
          FirMetroErrors.GRAPH_CREATORS_ERROR,
          "${annotationClassId.relativeClassName.asString()} declarations cannot have their target graph type as parameters.",
        )
        continue
      }

      when {
        isIncludes -> {
          if (type.classKind in NON_INCLUDES_KINDS || type.classId.isPlatformType()) {
            reporter.reportOn(
              param.source,
              FirMetroErrors.GRAPH_CREATORS_ERROR,
              "@Includes cannot be applied to enums, annotations, or platform types.",
            )
          }
        }
        isProvides -> {
          // TODO anything?
        }
        else -> {
          reportAnnotationCountError()
        }
      }
    }
  }

  private fun ClassId.isPlatformType(): Boolean {
    return packageFqName.asString().let { packageName ->
      PLATFORM_TYPE_PACKAGES.any { packageName.startsWith(it) }
    }
  }
}
