// Copyright (C) 2024 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.compiler.fir

import dev.zacsweers.metro.compiler.Symbols
import dev.zacsweers.metro.compiler.expectAsOrNull
import dev.zacsweers.metro.compiler.fir.generators.collectAbstractFunctions
import dev.zacsweers.metro.compiler.mapToArray
import dev.zacsweers.metro.compiler.memoized
import java.util.Objects
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.descriptors.annotations.KotlinTarget
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaredMemberScope
import org.jetbrains.kotlin.fir.analysis.checkers.getAllowedAnnotationTargets
import org.jetbrains.kotlin.fir.analysis.checkers.getContainingClassSymbol
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.FirTypeParameterRef
import org.jetbrains.kotlin.fir.declarations.constructors
import org.jetbrains.kotlin.fir.declarations.evaluateAs
import org.jetbrains.kotlin.fir.declarations.findArgumentByName
import org.jetbrains.kotlin.fir.declarations.getBooleanArgument
import org.jetbrains.kotlin.fir.declarations.getDeprecationsProvider
import org.jetbrains.kotlin.fir.declarations.getTargetType
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.origin
import org.jetbrains.kotlin.fir.declarations.primaryConstructorIfAny
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.declarations.utils.isAbstract
import org.jetbrains.kotlin.fir.declarations.utils.isLocal
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.deserialization.toQualifiedPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirArrayLiteral
import org.jetbrains.kotlin.fir.expressions.FirClassReferenceExpression
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirResolvedQualifier
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.expressions.builder.buildEnumEntryDeserializedAccessExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildLiteralExpression
import org.jetbrains.kotlin.fir.expressions.unexpandedClassId
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension.TypeResolveService
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.extensions.QualifierPartBuilder
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.references.impl.FirSimpleNamedReference
import org.jetbrains.kotlin.fir.references.toResolvedPropertySymbol
import org.jetbrains.kotlin.fir.render
import org.jetbrains.kotlin.fir.renderer.ConeIdRendererForDiagnostics
import org.jetbrains.kotlin.fir.renderer.ConeIdShortRenderer
import org.jetbrains.kotlin.fir.renderer.ConeTypeRendererForReadability
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.getSuperTypes
import org.jetbrains.kotlin.fir.resolve.lookupSuperTypes
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.resolve.substitution.substitutorByMap
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.scopes.impl.toConeType
import org.jetbrains.kotlin.fir.scopes.processAllCallables
import org.jetbrains.kotlin.fir.scopes.processAllClassifiers
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirBackingFieldSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertyAccessorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.toFirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeKotlinTypeProjection
import org.jetbrains.kotlin.fir.types.ConeTypeProjection
import org.jetbrains.kotlin.fir.types.FirTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildUserTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.fir.types.constructType
import org.jetbrains.kotlin.fir.types.isResolved
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.types.ConstantValueKind

internal fun FirBasedSymbol<*>.isAnnotatedInject(session: FirSession): Boolean {
  return isAnnotatedWithAny(session, session.classIds.injectAnnotations)
}

internal fun FirBasedSymbol<*>.isBinds(session: FirSession): Boolean {
  return isAnnotatedWithAny(session, session.classIds.bindsAnnotations)
}

internal fun FirBasedSymbol<*>.isDependencyGraph(session: FirSession): Boolean {
  return isAnnotatedWithAny(session, session.classIds.dependencyGraphAnnotations)
}

internal fun FirBasedSymbol<*>.isGraphFactory(session: FirSession): Boolean {
  return isAnnotatedWithAny(session, session.classIds.dependencyGraphFactoryAnnotations)
}

internal fun FirAnnotationContainer.isAnnotatedWithAny(
  session: FirSession,
  names: Collection<ClassId>,
): Boolean {
  return names.any { hasAnnotation(it, session) }
}

internal fun FirAnnotationContainer.annotationsIn(
  session: FirSession,
  names: Set<ClassId>,
): Sequence<FirAnnotation> {
  return annotations.annotationsIn(session, names)
}

internal fun List<FirAnnotation>.annotationsIn(
  session: FirSession,
  names: Set<ClassId>,
): Sequence<FirAnnotation> {
  return asSequence().filter { it.toAnnotationClassIdSafe(session) in names }
}

internal fun FirBasedSymbol<*>.annotationsIn(
  session: FirSession,
  names: Set<ClassId>,
): Sequence<FirAnnotation> {
  return resolvedCompilerAnnotationsWithClassIds
    .asSequence()
    .filter { it.isResolved }
    .flatMap {
      val classId =
        it.toAnnotationClassIdSafe(session) ?: return@flatMap emptySequence<FirAnnotation>()
      if (classId in names) {
        if (classId in session.classIds.allRepeatableContributesAnnotationsContainers) {
          it.flattenRepeatedAnnotations()
        } else {
          sequenceOf(it)
        }
      } else {
        emptySequence()
      }
    }
}

/** @see [dev.zacsweers.metro.compiler.ClassIds.allRepeatableContributesAnnotationsContainers] */
internal fun FirAnnotation.flattenRepeatedAnnotations(): Sequence<FirAnnotation> {
  return argumentAsOrNull<FirArrayLiteral>(StandardNames.DEFAULT_VALUE_PARAMETER, 0)
    ?.arguments
    ?.asSequence()
    ?.filterIsInstance<FirAnnotation>()
    .orEmpty()
}

internal fun FirBasedSymbol<*>.isAnnotatedWithAny(
  session: FirSession,
  names: Set<ClassId>,
): Boolean {
  return resolvedCompilerAnnotationsWithClassIds
    .filter { it.isResolved }
    .any { it.toAnnotationClassIdSafe(session) in names }
}

internal fun FirBasedSymbol<*>.findAnnotation(
  session: FirSession,
  names: Set<ClassId>,
): FirAnnotation? {
  return resolvedCompilerAnnotationsWithClassIds
    .filter { it.isResolved }
    .find { it.toAnnotationClassIdSafe(session) in names }
}

internal fun List<FirAnnotation>.isAnnotatedWithAny(
  session: FirSession,
  names: Set<ClassId>,
): Boolean {
  return annotationsIn(session, names).any()
}

internal inline fun FirMemberDeclaration.checkVisibility(
  allowProtected: Boolean = false,
  onError: (source: KtSourceElement?, allowedVisibilities: String) -> Nothing,
) {
  visibility.checkVisibility(source, allowProtected, onError)
}

internal inline fun FirCallableSymbol<*>.checkVisibility(
  allowProtected: Boolean = false,
  onError: (source: KtSourceElement?, allowedVisibilities: String) -> Nothing,
) {
  visibility.checkVisibility(source, allowProtected, onError)
}

internal inline fun Visibility.checkVisibility(
  source: KtSourceElement?,
  allowProtected: Boolean = false,
  onError: (source: KtSourceElement?, allowedVisibilities: String) -> Nothing,
) {
  // TODO what about expect/actual/protected
  when (this) {
    Visibilities.Public,
    Visibilities.Internal -> {
      // These are fine
      // TODO what about across modules? Is internal really ok? Or PublishedApi?
    }
    Visibilities.Protected -> {
      if (!allowProtected) {
        onError(source, "public or internal")
      }
    }
    else -> {
      onError(source, if (allowProtected) "public, internal or protected" else "public or internal")
    }
  }
}

@OptIn(DirectDeclarationsAccess::class)
internal fun FirClassSymbol<*>.callableDeclarations(
  session: FirSession,
  includeSelf: Boolean,
  includeAncestors: Boolean,
  yieldAncestorsFirst: Boolean = true,
): Sequence<FirCallableSymbol<*>> {
  return sequence {
    val declaredMembers =
      if (includeSelf) {
        declarationSymbols.asSequence().filterIsInstance<FirCallableSymbol<*>>().filterNot {
          it is FirConstructorSymbol
        }
      } else {
        emptySequence()
      }

    if (includeSelf && !yieldAncestorsFirst) {
      yieldAll(declaredMembers)
    }
    if (includeAncestors) {
      val superTypes = getSuperTypes(session)
      val superTypesToCheck = if (yieldAncestorsFirst) superTypes.asReversed() else superTypes
      for (superType in superTypesToCheck.mapNotNull { it.toClassSymbol(session) }) {
        yieldAll(
          // If we're recursing up, we no longer want to include ancestors because we're handling
          // that here
          superType.callableDeclarations(
            session = session,
            includeSelf = true,
            includeAncestors = false,
            yieldAncestorsFirst = yieldAncestorsFirst,
          )
        )
      }
    }
    if (includeSelf && yieldAncestorsFirst) {
      yieldAll(declaredMembers)
    }
  }
}

context(context: CheckerContext, diagnosticReporter: DiagnosticReporter)
internal inline fun FirClass.singleAbstractFunction(
  session: FirSession,
  reporter: DiagnosticReporter,
  type: String,
  allowProtected: Boolean = false,
  onError: () -> Nothing,
): FirNamedFunctionSymbol {
  val abstractFunctions = symbol.collectAbstractFunctions(session).orEmpty()
  if (abstractFunctions.size != 1) {
    if (abstractFunctions.isEmpty()) {
      reporter.reportOn(
        source,
        FirMetroErrors.FACTORY_MUST_HAVE_ONE_ABSTRACT_FUNCTION,
        type,
        "none",
      )
    } else {
      // Report each function
      for (abstractFunction in abstractFunctions) {
        reporter.reportOn(
          abstractFunction.source,
          FirMetroErrors.FACTORY_MUST_HAVE_ONE_ABSTRACT_FUNCTION,
          type,
          abstractFunctions.size.toString(),
        )
      }
    }
    onError()
  }

  val function = abstractFunctions.single()
  function.checkVisibility(allowProtected) { source, allowedVisibilities ->
    reporter.reportOn(
      source,
      FirMetroErrors.METRO_DECLARATION_VISIBILITY_ERROR,
      "$type classes' single abstract functions",
      allowedVisibilities,
    )
    onError()
  }
  return function
}

/**
 * Computes a hash key for this annotation instance composed of its underlying type and value
 * arguments.
 */
internal fun FirAnnotationCall.computeAnnotationHash(
  session: FirSession,
  typeResolver: TypeResolveService? = null,
): Int {
  return Objects.hash(
    toAnnotationClassIdSafe(session),
    arguments
      .map { arg ->
        when (arg) {
          is FirLiteralExpression -> arg.value
          is FirGetClassCall -> {
            typeResolver?.let { arg.resolvedArgumentConeKotlinType(it)?.classId }
              ?: run {
                val argument = arg.argument
                if (argument is FirResolvedQualifier) {
                  argument.classId
                } else {
                  argument.resolvedType.classId
                }
              }
          }
          // Enum entry reference
          is FirPropertyAccessExpression -> {
            arg.calleeReference
              .toResolvedPropertySymbol()
              ?.resolvedReceiverTypeRef
              ?.coneType
              ?.classId
          }
          else -> {
            error("Unexpected annotation argument type: ${arg::class.java} - ${arg.render()}")
          }
        }
      }
      .toTypedArray()
      .contentDeepHashCode(),
  )
}

context(context: CheckerContext, reporter: DiagnosticReporter)
internal inline fun FirClassSymbol<*>.findInjectConstructor(
  session: FirSession,
  checkClass: Boolean,
  onError: () -> Nothing,
): FirConstructorSymbol? {
  val constructorInjections = findInjectConstructors(session, checkClass = checkClass)
  return when (constructorInjections.size) {
    0 -> null
    1 -> {
      constructorInjections[0].also {
        val warnOnInjectAnnotationPlacement =
          session.metroFirBuiltIns.options.warnOnInjectAnnotationPlacement
        if (warnOnInjectAnnotationPlacement && constructors(session).size == 1) {
          val isAssisted =
            it.resolvedCompilerAnnotationsWithClassIds.isAnnotatedWithAny(
              session,
              session.classIds.assistedAnnotations,
            )
          val inject =
            it.resolvedCompilerAnnotationsWithClassIds
              .annotationsIn(session, session.classIds.injectAnnotations)
              .singleOrNull()
          if (
            !isAssisted &&
              inject != null &&
              KotlinTarget.CLASS in inject.getAllowedAnnotationTargets(session)
          ) {
            reporter.reportOn(inject.source, FirMetroErrors.SUGGEST_CLASS_INJECTION)
          }
        }
      }
    }
    else -> {
      reporter.reportOn(
        constructorInjections[0]
          .resolvedCompilerAnnotationsWithClassIds
          .annotationsIn(session, session.classIds.injectAnnotations)
          .single()
          .source,
        FirMetroErrors.CANNOT_HAVE_MULTIPLE_INJECTED_CONSTRUCTORS,
      )
      onError()
    }
  }
}

@OptIn(DirectDeclarationsAccess::class)
internal fun FirClassSymbol<*>.findInjectConstructors(
  session: FirSession,
  checkClass: Boolean = true,
): List<FirConstructorSymbol> {
  if (classKind != ClassKind.CLASS) return emptyList()
  rawStatus.modality?.let { if (it != Modality.FINAL && it != Modality.OPEN) return emptyList() }
  return if (checkClass && isAnnotatedInject(session)) {
    declarationSymbols.filterIsInstance<FirConstructorSymbol>().filter { it.isPrimary }
  } else {
    declarationSymbols.filterIsInstance<FirConstructorSymbol>().filter {
      it.resolvedCompilerAnnotationsWithClassIds.isAnnotatedWithAny(
        session,
        session.classIds.injectAnnotations,
      )
    }
  }
}

internal inline fun FirClass.validateInjectedClass(
  context: CheckerContext,
  reporter: DiagnosticReporter,
  onError: () -> Nothing,
) {
  if (isLocal) {
    reporter.reportOn(source, FirMetroErrors.LOCAL_CLASSES_CANNOT_BE_INJECTED, context)
    onError()
  }

  when (classKind) {
    ClassKind.CLASS -> {
      when (modality) {
        Modality.FINAL,
        Modality.OPEN -> {
          // final/open This is fine
        }
        else -> {
          // sealed/abstract
          reporter.reportOn(
            source,
            FirMetroErrors.ONLY_FINAL_AND_OPEN_CLASSES_CAN_BE_INJECTED,
            context,
          )
          onError()
        }
      }
    }
    else -> {
      reporter.reportOn(source, FirMetroErrors.ONLY_CLASSES_CAN_BE_INJECTED, context)
      onError()
    }
  }

  checkVisibility { source, allowedVisibilities ->
    reporter.reportOn(
      source,
      FirMetroErrors.INJECTED_CLASSES_MUST_BE_VISIBLE,
      allowedVisibilities,
      context,
    )
    onError()
  }
}

internal fun FirCallableSymbol<*>.allAnnotations(): Sequence<FirAnnotation> {
  return sequence {
    yieldAll(resolvedCompilerAnnotationsWithClassIds)
    if (this@allAnnotations is FirPropertySymbol) {
      yieldAll(backingFieldSymbol?.resolvedCompilerAnnotationsWithClassIds.orEmpty())
      getterSymbol?.resolvedCompilerAnnotationsWithClassIds?.let { yieldAll(it) }
      setterSymbol?.resolvedCompilerAnnotationsWithClassIds?.let { yieldAll(it) }
    }
  }
}

context(context: CheckerContext, reporter: DiagnosticReporter)
internal inline fun FirClass.validateApiDeclaration(
  type: String,
  checkConstructor: Boolean,
  onError: () -> Nothing,
) {
  if (isLocal) {
    reporter.reportOn(
      source,
      FirMetroErrors.METRO_DECLARATION_ERROR,
      "$type cannot be local classes.",
    )
    onError()
  }

  when (classKind) {
    ClassKind.INTERFACE -> {
      // This is fine
      when (modality) {
        Modality.SEALED -> {
          reporter.reportOn(
            source,
            FirMetroErrors.METRO_DECLARATION_ERROR,
            "$type should be non-sealed abstract classes or interfaces.",
          )
          onError()
        }
        else -> {
          // This is fine
        }
      }
    }
    ClassKind.CLASS -> {
      when (modality) {
        Modality.ABSTRACT -> {
          // This is fine
        }
        else -> {
          // final/open/sealed
          reporter.reportOn(
            source,
            FirMetroErrors.METRO_DECLARATION_ERROR,
            "$type should be non-sealed abstract classes or interfaces.",
          )
          onError()
        }
      }
    }
    else -> {
      reporter.reportOn(
        source,
        FirMetroErrors.METRO_DECLARATION_ERROR,
        "$type should be non-sealed abstract classes or interfaces.",
      )
      onError()
    }
  }

  checkVisibility { source, allowedVisibilities ->
    reporter.reportOn(
      source,
      FirMetroErrors.METRO_DECLARATION_VISIBILITY_ERROR,
      type,
      allowedVisibilities,
    )
    onError()
  }
  if (checkConstructor && isAbstract && classKind == ClassKind.CLASS) {
    primaryConstructorIfAny(context.session)?.validateVisibility("$type' primary constructor") {
      onError()
    }
  }
}

context(context: CheckerContext, reporter: DiagnosticReporter)
internal inline fun FirConstructorSymbol.validateVisibility(type: String, onError: () -> Nothing) {
  checkVisibility { source, allowedVisibilities ->
    reporter.reportOn(
      source,
      FirMetroErrors.METRO_DECLARATION_VISIBILITY_ERROR,
      type,
      allowedVisibilities,
    )
    onError()
  }
}

internal fun FirBasedSymbol<*>.qualifierAnnotation(
  session: FirSession,
  typeResolver: TypeResolveService? = null,
): MetroFirAnnotation? =
  resolvedCompilerAnnotationsWithClassIds.qualifierAnnotation(session, typeResolver)

internal fun List<FirAnnotation>.qualifierAnnotation(
  session: FirSession,
  typeResolver: TypeResolveService? = null,
): MetroFirAnnotation? =
  asSequence()
    .annotationAnnotatedWithAny(session, session.classIds.qualifierAnnotations, typeResolver)

internal fun FirBasedSymbol<*>.mapKeyAnnotation(session: FirSession): MetroFirAnnotation? =
  resolvedCompilerAnnotationsWithClassIds.mapKeyAnnotation(session)

internal fun List<FirAnnotation>.mapKeyAnnotation(session: FirSession): MetroFirAnnotation? =
  asSequence().annotationAnnotatedWithAny(session, session.classIds.mapKeyAnnotations)

// TODO use FirExpression extensions
//  fun FirExpression.extractClassesFromArgument(session: FirSession): List<FirRegularClassSymbol>
//  fun FirExpression.extractClassFromArgument(session: FirSession): FirRegularClassSymbol?

internal fun List<FirAnnotation>.scopeAnnotations(
  session: FirSession
): Sequence<MetroFirAnnotation> = asSequence().scopeAnnotations(session)

internal fun Sequence<FirAnnotation>.scopeAnnotations(
  session: FirSession
): Sequence<MetroFirAnnotation> =
  annotationsAnnotatedWithAny(session, session.classIds.scopeAnnotations)

// TODO add a single = true|false param? How would we propagate errors
internal fun Sequence<FirAnnotation>.annotationAnnotatedWithAny(
  session: FirSession,
  names: Set<ClassId>,
  typeResolver: TypeResolveService? = null,
): MetroFirAnnotation? {
  return annotationsAnnotatedWithAny(session, names, typeResolver).firstOrNull()
}

internal fun Sequence<FirAnnotation>.annotationsAnnotatedWithAny(
  session: FirSession,
  names: Set<ClassId>,
  typeResolver: TypeResolveService? = null,
): Sequence<MetroFirAnnotation> {
  return filter { it.isResolved }
    .filterIsInstance<FirAnnotationCall>()
    .filter { annotationCall -> annotationCall.isAnnotatedWithAny(session, names) }
    .map { MetroFirAnnotation(it, session, typeResolver) }
}

internal fun FirAnnotationCall.isAnnotatedWithAny(
  session: FirSession,
  names: Set<ClassId>,
): Boolean {
  val annotationType = resolvedType as? ConeClassLikeType ?: return false
  val annotationClass = annotationType.toClassSymbol(session) ?: return false
  return annotationClass.resolvedCompilerAnnotationsWithClassIds.isAnnotatedWithAny(session, names)
}

internal fun createDeprecatedHiddenAnnotation(session: FirSession): FirAnnotation =
  buildAnnotation {
    val deprecatedAnno =
      session.symbolProvider.getClassLikeSymbolByClassId(StandardClassIds.Annotations.Deprecated)
        as FirRegularClassSymbol

    annotationTypeRef = deprecatedAnno.defaultType().toFirResolvedTypeRef()

    argumentMapping = buildAnnotationArgumentMapping {
      mapping[Name.identifier("message")] =
        buildLiteralExpression(
          null,
          ConstantValueKind.String,
          "This synthesized declaration should not be used directly",
          setType = true,
        )

      // It has nothing to do with enums deserialization, but it is simply easier to build it this
      // way.
      mapping[Name.identifier("level")] =
        buildEnumEntryDeserializedAccessExpression {
            enumClassId = StandardClassIds.DeprecationLevel
            enumEntryName = Name.identifier("HIDDEN")
          }
          .toQualifiedPropertyAccessExpression(session)
    }
  }

internal fun FirClassLikeDeclaration.markAsDeprecatedHidden(session: FirSession) {
  replaceAnnotations(annotations + listOf(createDeprecatedHiddenAnnotation(session)))
  replaceDeprecationsProvider(this.getDeprecationsProvider(session))
}

internal fun ConeTypeProjection.wrapInProviderIfNecessary(
  session: FirSession,
  providerClassId: ClassId,
): ConeClassLikeType {
  val type = this.type
  if (type is ConeClassLikeType) {
    val classId = type.lookupTag.classId
    if (classId in session.classIds.providerTypes) {
      // Already a provider
      return type
    }
  }
  return providerClassId.constructClassLikeType(arrayOf(this))
}

internal fun ConeTypeProjection.wrapInLazyIfNecessary(
  session: FirSession,
  lazyClassId: ClassId,
): ConeClassLikeType {
  val type = this.type
  if (type is ConeClassLikeType) {
    val classId = type.lookupTag.classId
    if (classId in session.classIds.lazyTypes) {
      // Already a lazy
      return type
    }
  }
  return lazyClassId.constructClassLikeType(arrayOf(this))
}

internal fun FirClassSymbol<*>.constructType(
  typeParameterRefs: List<FirTypeParameterRef>
): ConeClassLikeType {
  return constructType(typeParameterRefs.mapToArray { it.symbol.toConeType() })
}

// Annoyingly, FirDeclarationOrigin.Plugin does not implement equals()
// TODO this still doesn't seem to work in 2.2
internal fun FirBasedSymbol<*>.hasOrigin(vararg keys: GeneratedDeclarationKey): Boolean {
  for (key in keys) {
    if (hasOrigin(key.origin)) return true
  }
  return false
}

internal fun FirBasedSymbol<*>.hasOrigin(o: FirDeclarationOrigin): Boolean {
  val thisOrigin = origin

  if (thisOrigin == o) return true
  if (thisOrigin is FirDeclarationOrigin.Plugin && o is FirDeclarationOrigin.Plugin) {
    return thisOrigin.key == o.key
  }
  return false
}

/** Properties can store annotations in SO many places */
internal fun FirCallableSymbol<*>.findAnnotation(
  session: FirSession,
  findAnnotation: FirBasedSymbol<*>.(FirSession) -> MetroFirAnnotation?,
  callingAccessor: FirCallableSymbol<*>? = null,
): MetroFirAnnotation? {
  findAnnotation(session)?.let {
    return it
  }
  when (this) {
    is FirPropertySymbol -> {
      getterSymbol
        ?.takeUnless { it == callingAccessor }
        ?.findAnnotation(session)
        ?.let {
          return it
        }
      setterSymbol
        ?.takeUnless { it == callingAccessor }
        ?.findAnnotation(session)
        ?.let {
          return it
        }
      backingFieldSymbol
        ?.takeUnless { it == callingAccessor }
        ?.findAnnotation(session)
        ?.let {
          return it
        }
    }
    is FirPropertyAccessorSymbol -> {
      return propertySymbol.findAnnotation(session, findAnnotation, this)
    }
    is FirBackingFieldSymbol -> {
      return propertySymbol.findAnnotation(session, findAnnotation, this)
    }
  // else it's a function, covered by the above
  }
  return null
}

internal fun FirBasedSymbol<*>.requireContainingClassSymbol(): FirClassLikeSymbol<*> =
  getContainingClassSymbol() ?: error("No containing class symbol found for $this")

private val FirPropertyAccessExpression.qualifierName: Name?
  get() = (calleeReference as? FirSimpleNamedReference)?.name

internal fun FirAnnotation.scopeArgument() = classArgument(Symbols.Names.scope, index = 0)

internal fun FirAnnotation.additionalScopesArgument() =
  argumentAsOrNull<FirArrayLiteral>(Symbols.Names.additionalScopes, index = 1)

internal fun FirAnnotation.bindingContainersArgument() =
  argumentAsOrNull<FirArrayLiteral>(Symbols.Names.bindingContainers, index = 4)

internal fun FirAnnotation.includesArgument() =
  argumentAsOrNull<FirArrayLiteral>(Symbols.Names.includes, index = 0)

internal fun FirAnnotation.allScopeClassIds(): Set<ClassId> =
  buildSet {
      resolvedScopeClassId()?.let(::add)
      resolvedAdditionalScopesClassIds()?.let(::addAll)
    }
    .filterNotTo(mutableSetOf()) { it == StandardClassIds.Nothing }

internal fun FirAnnotation.excludesArgument() =
  argumentAsOrNull<FirArrayLiteral>(Symbols.Names.excludes, index = 2)

internal fun FirAnnotation.replacesArgument() =
  argumentAsOrNull<FirArrayLiteral>(Symbols.Names.replaces, index = 2)

internal fun FirAnnotation.rankValue(): Long {
  // Although the parameter is defined as an Int, the value we receive here may end up being
  // an Int or a Long so we need to handle both
  return rankArgument()?.value?.let { it as? Long ?: (it as? Int)?.toLong() } ?: Long.MIN_VALUE
}

private fun FirAnnotation.rankArgument() =
  argumentAsOrNull<FirLiteralExpression>(Symbols.Names.rank, index = 5)

internal fun FirAnnotation.bindingArgument() = annotationArgument(Symbols.Names.binding, index = 1)

internal fun FirAnnotation.resolvedBindingArgument(
  session: FirSession,
  typeResolver: TypeResolveService? = null,
): FirTypeRef? {
  // Return a binding defined using Metro's API
  bindingArgument()?.let { binding ->
    return binding.typeArguments[0].expectAsOrNull<FirTypeProjectionWithVariance>()?.typeRef
  }
  // Anvil interop - try a boundType defined using anvil KClass
  return anvilKClassBoundTypeArgument(session, typeResolver)
}

internal fun FirAnnotation.anvilKClassBoundTypeArgument(
  session: FirSession,
  typeResolver: TypeResolveService? = null,
): FirTypeRef? {
  return getAnnotationKClassArgument(Symbols.Names.boundType, session, typeResolver)
    ?.toFirResolvedTypeRef()
}

internal fun FirAnnotation.anvilIgnoreQualifier(session: FirSession): Boolean {
  return getBooleanArgument(Symbols.Names.ignoreQualifier, session) ?: false
}

internal fun FirAnnotation.getAnnotationKClassArgument(
  name: Name,
  session: FirSession,
  typeResolver: TypeResolveService? = null,
): ConeKotlinType? {
  val argument = findArgumentByNameSafe(name) ?: return null
  return argument.evaluateAs<FirGetClassCall>(session)?.getTargetType()
    ?: typeResolver?.let { (argument as FirGetClassCall).resolvedArgumentConeKotlinType(it) }
}

internal fun FirAnnotation.resolvedScopeClassId() = scopeArgument()?.resolvedClassId()

internal fun FirAnnotation.resolvedScopeClassId(typeResolver: TypeResolveService): ClassId? {
  val scopeArgument = scopeArgument() ?: return null
  // Try to resolve it normally first. If this fails,
  // try to resolve within the enclosing scope
  return scopeArgument.resolvedClassId()
    ?: scopeArgument.resolvedArgumentConeKotlinType(typeResolver)?.classId
}

internal fun FirAnnotation.resolvedAdditionalScopesClassIds() =
  additionalScopesArgument()?.argumentList?.arguments?.mapNotNull {
    it.expectAsOrNull<FirGetClassCall>()?.resolvedClassId()
  }

internal fun FirAnnotation.resolvedBindingContainersClassIds() =
  bindingContainersArgument()?.argumentList?.arguments?.mapNotNull {
    it.expectAsOrNull<FirGetClassCall>()
  }

internal fun FirAnnotation.resolvedIncludesClassIds() =
  includesArgument()?.argumentList?.arguments?.mapNotNull { it.expectAsOrNull<FirGetClassCall>() }

internal fun FirAnnotation.resolvedAdditionalScopesClassIds(
  typeResolver: TypeResolveService
): List<ClassId> {
  val additionalScopes =
    additionalScopesArgument()?.argumentList?.arguments?.mapNotNull {
      it.expectAsOrNull<FirGetClassCall>()
    } ?: return emptyList()
  // Try to resolve it normally first. If this fails,
  // try to resolve within the enclosing scope
  return additionalScopes.mapNotNull { it.resolvedClassId() }.takeUnless { it.isEmpty() }
    ?: additionalScopes.mapNotNull { it.resolvedArgumentConeKotlinType(typeResolver)?.classId }
}

internal fun FirAnnotation.resolvedExcludedClassIds(
  typeResolver: TypeResolveService
): Set<ClassId> {
  val excludesArgument =
    excludesArgument()?.argumentList?.arguments?.mapNotNull { it.expectAsOrNull<FirGetClassCall>() }
      ?: return emptySet()
  // Try to resolve it normally first. If this fails, try to resolve within the enclosing scope
  val excluded =
    excludesArgument.mapNotNull { it.resolvedClassId() }.takeUnless { it.isEmpty() }
      ?: excludesArgument.mapNotNull { it.resolvedArgumentConeKotlinType(typeResolver)?.classId }
  return excluded.toSet()
}

internal fun FirAnnotation.resolvedReplacedClassIds(
  typeResolver: MetroFirTypeResolver
): Set<ClassId> {
  val replacesArgument =
    replacesArgument()?.argumentList?.arguments?.mapNotNull { it.expectAsOrNull<FirGetClassCall>() }
      ?: return emptySet()
  val replaced =
    replacesArgument.mapNotNull { getClassCall ->
      getClassCall.resolveClassId(typeResolver)
      // If it's available and resolved, just use it directly!
      getClassCall.coneTypeIfResolved()?.classId?.let {
        return@mapNotNull it
      }
      // Otherwise fall back to trying to parse from the reference
      val reference = getClassCall.resolvedArgumentTypeRef() ?: return@mapNotNull null
      typeResolver.resolveType(reference).classId
    }
  return replaced.toSet()
}

internal fun FirGetClassCall.resolveClassId(
  typeResolver: MetroFirTypeResolver
): ClassId? {
  // If it's available and resolved, just use it directly!
  coneTypeIfResolved()?.classId?.let {
    return it
  }
  // Otherwise fall back to trying to parse from the reference
  val reference = resolvedArgumentTypeRef() ?: return null
  return typeResolver.resolveType(reference).classId
}

internal fun FirGetClassCall.resolvedClassId() = (argument as? FirResolvedQualifier)?.classId

internal fun FirAnnotation.resolvedArgumentConeKotlinType(
  name: Name,
  index: Int,
  typeResolver: TypeResolveService,
): ConeKotlinType? {
  // TODO if the annotation is resolved we can skip ahead
  val getClassCall = argumentAsOrNull<FirGetClassCall>(name, index) ?: return null
  return getClassCall.resolvedArgumentConeKotlinType(typeResolver)
}

internal fun FirGetClassCall.resolvedArgumentConeKotlinType(
  typeResolver: TypeResolveService
): ConeKotlinType? {
  coneTypeIfResolved()?.let {
    return it
  }
  val ref = resolvedArgumentTypeRef() ?: return null
  return typeResolver.resolveUserType(ref).coneType
}

private fun FirGetClassCall.coneTypeIfResolved(): ConeKotlinType? {
  return when (val arg = argument) {
    // I'm not really sure why these sometimes come down as different types but shrug
    is FirClassReferenceExpression if (isResolved) -> arg.classTypeRef.coneTypeOrNull
    is FirResolvedQualifier if (isResolved) -> arg.resolvedType
    else -> null
  }
}

internal fun FirGetClassCall.resolvedArgumentTypeRef(): FirUserTypeRef? {
  val source = source ?: return null

  return typeRefFromQualifierParts(isMarkedNullable = false, source) {
    fun visitQualifiers(expression: FirExpression) {
      if (expression !is FirPropertyAccessExpression) return
      expression.explicitReceiver?.let { visitQualifiers(it) }
      expression.qualifierName?.let { part(it) }
    }
    visitQualifiers(argument)
  }
}

internal fun FirAnnotation.classArgument(name: Name, index: Int) =
  argumentAsOrNull<FirGetClassCall>(name, index)

internal fun FirAnnotation.annotationArgument(name: Name, index: Int) =
  argumentAsOrNull<FirFunctionCall>(name, index)

internal inline fun <reified T> FirAnnotation.argumentAsOrNull(name: Name, index: Int): T? {
  findArgumentByNameSafe(name)?.let {
    return it as? T?
  }
  if (this !is FirAnnotationCall) return null
  // Fall back to the index if necessary
  return arguments.getOrNull(index) as? T?
}

/**
 * In most cases if we're searching for an argument by name, we do not want to default to the first
 * argument. E.g. when looking for 'boundType', if it's not explicitly defined, then receiving the
 * first argument would mean receiving the 'scope' argument and it would still compile fine since
 * those annotation params share the same type.
 *
 * ```
 * Given `@ContributesBinding(scope = AppScope::class)`
 * findArgumentByName("boundType")     returns AppScope::class
 * findArgumentByNameSafe("boundType") returns null
 * ```
 */
internal fun FirAnnotation.findArgumentByNameSafe(name: Name): FirExpression? =
  findArgumentByName(name, returnFirstWhenNotFound = false)

internal fun List<FirElement>.joinToRender(separator: String = ", "): String {
  return joinToString(separator) {
    buildString {
      append(it.render())
      if (it is FirAnnotation) {
        append(" resolved=")
        append(it.isResolved)
        append(" unexpandedClassId=")
        append(it.unexpandedClassId)
      }
    }
  }
}

internal fun buildSimpleAnnotation(symbol: () -> FirRegularClassSymbol): FirAnnotation {
  return buildAnnotation {
    annotationTypeRef = symbol().defaultType().toFirResolvedTypeRef()

    argumentMapping = buildAnnotationArgumentMapping()
  }
}

internal fun FirClass.isOrImplements(supertype: ClassId, session: FirSession): Boolean {
  if (classId == supertype) return true
  return implements(supertype, session)
}

internal fun FirClass.implements(supertype: ClassId, session: FirSession): Boolean {
  return lookupSuperTypes(
      klass = this,
      lookupInterfaces = true,
      deep = true,
      useSiteSession = session,
      substituteTypes = true,
    )
    .any { it.classId?.let { it == supertype } == true }
}

internal fun FirClassSymbol<*>.isOrImplements(supertype: ClassId, session: FirSession): Boolean {
  if (classId == supertype) return true
  return implements(supertype, session)
}

internal fun FirClassSymbol<*>.implements(supertype: ClassId, session: FirSession): Boolean {
  return lookupSuperTypes(
      symbols = listOf(this),
      lookupInterfaces = true,
      deep = true,
      useSiteSession = session,
      substituteTypes = true,
    )
    .any { it.classId?.let { it == supertype } == true }
}

internal val FirValueParameterSymbol.containingFunctionSymbol: FirFunctionSymbol<*>?
  get() = containingDeclarationSymbol as? FirFunctionSymbol<*>

internal fun ConeKotlinType.render(short: Boolean): String {
  return buildString { renderType(short, this@render) }
}

// Custom renderer that excludes annotations
internal fun StringBuilder.renderType(short: Boolean, type: ConeKotlinType) {
  val renderer =
    object :
      ConeTypeRendererForReadability(
        this,
        null,
        { if (short) ConeIdShortRenderer() else ConeIdRendererForDiagnostics() },
      ) {
      override fun ConeKotlinType.renderAttributes() {
        // Do nothing, we don't want annotations
      }
    }
  renderer.render(type)
}

context(context: CheckerContext)
internal fun FirClassSymbol<*>.nestedClasses(): List<FirRegularClassSymbol> {
  val collected = mutableListOf<FirRegularClassSymbol>()
  declaredMemberScope(context).processAllClassifiers { symbol ->
    if (symbol is FirRegularClassSymbol) {
      collected += symbol
    }
  }
  return collected
}

internal fun NestedClassGenerationContext.nestedClasses(): List<FirRegularClassSymbol> {
  val collected = mutableListOf<FirRegularClassSymbol>()
  declaredScope?.processAllClassifiers { symbol ->
    if (symbol is FirRegularClassSymbol) {
      collected += symbol
    }
  }
  return collected
}

context(context: CheckerContext)
internal fun FirClassSymbol<*>.directCallableSymbols(): List<FirCallableSymbol<*>> {
  val collected = mutableListOf<FirCallableSymbol<*>>()
  declaredMemberScope(context).processAllCallables { collected += it }
  return collected
}

internal fun MemberGenerationContext.directCallableSymbols(): List<FirCallableSymbol<*>> {
  val collected = mutableListOf<FirCallableSymbol<*>>()
  this.declaredScope?.processAllCallables { collected += it }
  return collected
}

// Build a complete substitution map that includes mappings for ancestor type parameters
internal fun buildShallowSubstitutionMap(
  targetClass: FirClassSymbol<*>,
  directMappings: Map<FirTypeParameterSymbol, ConeKotlinType>,
  session: FirSession,
): Map<FirTypeParameterSymbol, ConeKotlinType> {
  return buildSubstitutionMapInner(targetClass, directMappings, session, full = false)
}

// Build a complete substitution map that includes mappings for ancestor type parameters
internal fun buildFullSubstitutionMap(
  targetClass: FirClassSymbol<*>,
  directMappings: Map<FirTypeParameterSymbol, ConeKotlinType>,
  session: FirSession,
): Map<FirTypeParameterSymbol, ConeKotlinType> {
  return buildSubstitutionMapInner(targetClass, directMappings, session, full = true)
}

private fun buildSubstitutionMapInner(
  targetClass: FirClassSymbol<*>,
  directMappings: Map<FirTypeParameterSymbol, ConeKotlinType>,
  session: FirSession,
  full: Boolean,
): Map<FirTypeParameterSymbol, ConeKotlinType> {
  val result = mutableMapOf<FirTypeParameterSymbol, ConeKotlinType>()

  // Start with the direct mappings for the target class
  result.putAll(directMappings)

  // Walk up the inheritance chain and collect substitutions
  var currentClass: FirClassSymbol<*>? = targetClass
  while (currentClass != null) {
    val superType =
      currentClass.resolvedSuperTypes.firstOrNull {
        it.classId != session.builtinTypes.anyType.coneType.classId
      }

    if (superType is ConeClassLikeType && superType.typeArguments.isNotEmpty()) {
      val superClass = superType.toRegularClassSymbol(session)
      if (superClass != null) {
        // Map ancestor type parameters to their concrete types in the inheritance chain
        superClass.typeParameterSymbols.zip(superType.typeArguments).forEach { (param, arg) ->
          if (arg is ConeKotlinTypeProjection) {
            // Apply existing substitutions to the argument type
            val substitutor = substitutorByMap(result, session)
            val substitutedType = substitutor.substituteOrNull(arg.type) ?: arg.type
            result[param] = substitutedType
          }
        }
      }
      currentClass = superClass
    } else if (!full) {
      // Shallow one-layer
      break
    } else {
      break
    }
  }

  return result
}

internal fun typeRefFromQualifierParts(
  isMarkedNullable: Boolean,
  source: KtSourceElement,
  builder: QualifierPartBuilder.() -> Unit,
): FirUserTypeRef {
  val userTypeRef = buildUserTypeRef {
    this.isMarkedNullable = isMarkedNullable
    this.source = source
    QualifierPartBuilder(qualifier).builder()
  }
  return userTypeRef
}

internal val FirSession.memoizedAllSessionsSequence: Sequence<FirSession>
  get() = sequenceOf(this).plus(moduleData.allDependsOnDependencies.map { it.session }).memoized()
