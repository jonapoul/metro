// Copyright (C) 2024 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.compiler.ir.parameters

import dev.drewhamilton.poko.Poko
import dev.zacsweers.metro.compiler.NameAllocator
import dev.zacsweers.metro.compiler.Symbols
import dev.zacsweers.metro.compiler.asName
import dev.zacsweers.metro.compiler.ir.IrContextualTypeKey
import dev.zacsweers.metro.compiler.ir.IrMetroContext
import dev.zacsweers.metro.compiler.ir.IrTypeKey
import dev.zacsweers.metro.compiler.ir.NOOP_TYPE_REMAPPER
import dev.zacsweers.metro.compiler.ir.annotationsIn
import dev.zacsweers.metro.compiler.ir.asContextualTypeKey
import dev.zacsweers.metro.compiler.ir.constArgumentOfTypeAt
import dev.zacsweers.metro.compiler.ir.qualifierAnnotation
import dev.zacsweers.metro.compiler.ir.regularParameters
import dev.zacsweers.metro.compiler.unsafeLazy
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.declarations.IrTypeParametersContainer
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.isPropertyAccessor
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.TypeRemapper
import org.jetbrains.kotlin.ir.util.callableId
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.ir.util.propertyIfAccessor
import org.jetbrains.kotlin.ir.util.remapTypeParameters
import org.jetbrains.kotlin.name.Name

@Poko
internal class Parameter
private constructor(
  val kind: IrParameterKind,
  val name: Name,
  val originalName: Name,
  val contextualTypeKey: IrContextualTypeKey,
  val isAssisted: Boolean,
  val assistedIdentifier: String,
  val assistedParameterKey: AssistedParameterKey,
  val isGraphInstance: Boolean,
  val isBindsInstance: Boolean,
  val isIncludes: Boolean,
  val isMember: Boolean,
  ir: IrValueParameter?,
) : Comparable<Parameter> {
  val typeKey: IrTypeKey = contextualTypeKey.typeKey
  val type: IrType = contextualTypeKey.typeKey.type
  val isWrappedInProvider: Boolean = contextualTypeKey.isWrappedInProvider
  val isWrappedInLazy: Boolean = contextualTypeKey.isWrappedInLazy
  val isLazyWrappedInProvider: Boolean = contextualTypeKey.isLazyWrappedInProvider
  val hasDefault: Boolean = contextualTypeKey.hasDefault

  // TODO just make this nullable
  private val _ir = ir
  val ir: IrValueParameter
    get() = _ir ?: error("Parameter $name has no backing IR value parameter!")

  private val cachedToString by unsafeLazy {
    buildString {
      contextualTypeKey.typeKey.qualifier?.let {
        append(it)
        append(' ')
      }
      append(name)
      append(':')
      append(' ')
      append(contextualTypeKey.render(short = true, includeQualifier = false))
    }
  }

  override fun toString(): String = cachedToString

  fun copy(
    kind: IrParameterKind = this.kind,
    name: Name = this.name,
    originalName: Name = this.originalName,
    contextualTypeKey: IrContextualTypeKey = this.contextualTypeKey,
    isAssisted: Boolean = this.isAssisted,
    assistedIdentifier: String = this.assistedIdentifier,
    assistedParameterKey: AssistedParameterKey =
      AssistedParameterKey(contextualTypeKey.typeKey, assistedIdentifier),
    isGraphInstance: Boolean = this.isGraphInstance,
    isBindsInstance: Boolean = this.isBindsInstance,
    isIncludes: Boolean = this.isIncludes,
    isMember: Boolean = this.isMember,
    ir: IrValueParameter? = this._ir,
  ) =
    Parameter(
      kind = kind,
      name = name,
      originalName = originalName,
      contextualTypeKey = contextualTypeKey,
      isAssisted = isAssisted,
      assistedIdentifier = assistedIdentifier,
      assistedParameterKey = assistedParameterKey,
      isGraphInstance = isGraphInstance,
      isBindsInstance = isBindsInstance,
      isIncludes = isIncludes,
      isMember = isMember,
      ir = ir,
    )

  override fun compareTo(other: Parameter): Int = COMPARATOR.compare(this, other)

  // @Assisted parameters are equal if the type and the identifier match. This subclass makes
  // diffing the parameters easier.
  data class AssistedParameterKey(val typeKey: IrTypeKey, val assistedIdentifier: String) {
    companion object {
      fun IrValueParameter.toAssistedParameterKey(
        symbols: Symbols,
        typeKey: IrTypeKey,
      ): AssistedParameterKey {
        return AssistedParameterKey(
          typeKey,
          annotationsIn(symbols.assistedAnnotations)
            .singleOrNull()
            ?.constArgumentOfTypeAt<String>(0)
            .orEmpty(),
        )
      }
    }
  }

  companion object {
    private val COMPARATOR =
      compareBy<Parameter> { it.kind }
        .thenBy { it.name }
        .thenBy { it.originalName }
        .thenBy { it.typeKey }
        .thenBy { it.assistedIdentifier }

    fun regular(
      kind: IrParameterKind,
      name: Name,
      contextualTypeKey: IrContextualTypeKey,
      isAssisted: Boolean,
      isGraphInstance: Boolean,
      isBindsInstance: Boolean,
      isIncludes: Boolean,
      assistedIdentifier: String,
      assistedParameterKey: AssistedParameterKey =
        AssistedParameterKey(contextualTypeKey.typeKey, assistedIdentifier),
      ir: IrValueParameter?,
    ): Parameter {
      return Parameter(
        kind = kind,
        name = name,
        contextualTypeKey = contextualTypeKey,
        isAssisted = isAssisted,
        isGraphInstance = isGraphInstance,
        isBindsInstance = isBindsInstance,
        isIncludes = isIncludes,
        assistedIdentifier = assistedIdentifier,
        assistedParameterKey = assistedParameterKey,
        ir = ir,
        originalName = name,
        isMember = false,
      )
    }

    fun member(
      kind: IrParameterKind,
      name: Name,
      contextualTypeKey: IrContextualTypeKey,
      originalName: Name,
      ir: IrValueParameter,
    ): Parameter {
      return Parameter(
        kind = kind,
        name = name,
        contextualTypeKey = contextualTypeKey,
        originalName = originalName,
        ir = ir,
        isAssisted = false,
        assistedIdentifier = "",
        assistedParameterKey = AssistedParameterKey(contextualTypeKey.typeKey, ""),
        isBindsInstance = false,
        isGraphInstance = false,
        isIncludes = false,
        isMember = true,
      )
    }
  }
}

context(context: IrMetroContext)
internal fun List<IrValueParameter>.mapToConstructorParameters(
  remapper: TypeRemapper = NOOP_TYPE_REMAPPER
): List<Parameter> {
  return map { valueParameter ->
    valueParameter.toConstructorParameter(IrParameterKind.Regular, remapper)
  }
}

context(context: IrMetroContext)
internal fun IrValueParameter.toConstructorParameter(
  kind: IrParameterKind = IrParameterKind.Regular,
  remapper: TypeRemapper = NOOP_TYPE_REMAPPER,
): Parameter {
  // Remap type parameters in underlying types to the new target container. This is important for
  // type mangling
  val declaredType = remapper.remapType(this@toConstructorParameter.type)

  val contextKey = declaredType.asContextualTypeKey(qualifierAnnotation(), defaultValue != null)

  val assistedAnnotation = annotationsIn(context.symbols.assistedAnnotations).singleOrNull()

  var isProvides = false
  var isIncludes = false
  for (annotation in annotations) {
    val classId = annotation.symbol.owner.parentAsClass.classId
    when (classId) {
      in context.symbols.classIds.providesAnnotations -> {
        isProvides = true
      }
      in context.symbols.classIds.includes -> {
        isIncludes = true
      }

      else -> continue
    }
  }

  val assistedIdentifier = assistedAnnotation?.constArgumentOfTypeAt<String>(0).orEmpty()

  return Parameter.regular(
    kind = kind,
    name = name,
    contextualTypeKey = contextKey,
    isAssisted = assistedAnnotation != null,
    assistedIdentifier = assistedIdentifier,
    isGraphInstance = false,
    isBindsInstance = isProvides,
    isIncludes = isIncludes,
    ir = this,
  )
}

context(context: IrMetroContext)
internal fun List<IrValueParameter>.mapToMemberInjectParameters(
  nameAllocator: NameAllocator,
  typeParameterRemapper: ((IrType) -> IrType)? = null,
): List<Parameter> {
  return map { valueParameter ->
    valueParameter.toMemberInjectParameter(
      uniqueName = nameAllocator.newName(valueParameter.name.asString()).asName(),
      kind = IrParameterKind.Regular,
      typeParameterRemapper = typeParameterRemapper,
    )
  }
}

context(context: IrMetroContext)
internal fun IrProperty.toMemberInjectParameter(
  uniqueName: Name,
  kind: IrParameterKind = IrParameterKind.Regular,
  typeParameterRemapper: ((IrType) -> IrType)? = null,
): Parameter {
  val propertyType =
    getter?.returnType ?: backingField?.type ?: error("No getter or backing field!")

  val setterParam = setter?.regularParameters?.singleOrNull()

  // Remap type parameters in underlying types to the new target container. This is important for
  // type mangling
  val declaredType = typeParameterRemapper?.invoke(propertyType) ?: propertyType

  // TODO warn if it's anything other than null for now?
  // Check lateinit because they will report having a getter/body even though they're not actually
  // implemented for our needs
  val defaultValue =
    if (isLateinit) {
      null
    } else {
      getter?.body ?: backingField?.initializer
    }
  val contextKey =
    declaredType.asContextualTypeKey(with(context) { qualifierAnnotation() }, defaultValue != null)

  return Parameter.member(
    kind = kind,
    name = uniqueName,
    originalName = name,
    contextualTypeKey = contextKey,
    ir = setterParam!!,
  )
}

context(context: IrMetroContext)
internal fun IrValueParameter.toMemberInjectParameter(
  uniqueName: Name,
  kind: IrParameterKind = IrParameterKind.Regular,
  typeParameterRemapper: ((IrType) -> IrType)? = null,
): Parameter {
  // Remap type parameters in underlying types to the new target container. This is important for
  // type mangling
  val declaredType =
    typeParameterRemapper?.invoke(this@toMemberInjectParameter.type)
      ?: this@toMemberInjectParameter.type

  val contextKey =
    declaredType.asContextualTypeKey(with(context) { qualifierAnnotation() }, defaultValue != null)

  return Parameter.member(
    kind = kind,
    name = uniqueName,
    originalName = name,
    contextualTypeKey = contextKey,
    ir = this,
  )
}

context(context: IrMetroContext)
internal fun IrFunction.memberInjectParameters(
  nameAllocator: NameAllocator,
  parentClass: IrClass = parentClassOrNull!!,
  originClass: IrTypeParametersContainer? = null,
): Parameters {
  val mapper =
    if (originClass != null) {
      val typeParameters = parentClass.typeParameters
      val srcToDstParameterMap: Map<IrTypeParameter, IrTypeParameter> =
        originClass.typeParameters.zip(typeParameters).associate { (src, target) -> src to target }
      // Returning this inline breaks kotlinc for some reason
      val innerMapper: ((IrType) -> IrType) = { type ->
        type.remapTypeParameters(originClass, parentClass, srcToDstParameterMap)
      }
      innerMapper
    } else {
      null
    }

  val valueParams =
    if (isPropertyAccessor) {
      val property = propertyIfAccessor as IrProperty
      listOf(
        property.toMemberInjectParameter(
          uniqueName = nameAllocator.newName(property.name.asString()).asName(),
          kind = IrParameterKind.Regular,
          typeParameterRemapper = mapper,
        )
      )
    } else {
      regularParameters.mapToMemberInjectParameters(
        nameAllocator = nameAllocator,
        typeParameterRemapper = mapper,
      )
    }

  return Parameters(
    callableId = callableId,
    instance = null,
    regularParameters = valueParams,
    // TODO not supported for now
    extensionReceiver = null,
    contextParameters = emptyList(),
    ir = this,
  )
}
