// Copyright (C) 2024 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.compiler.ir

import dev.drewhamilton.poko.Poko
import dev.zacsweers.metro.compiler.Origins
import dev.zacsweers.metro.compiler.asName
import dev.zacsweers.metro.compiler.expectAs
import dev.zacsweers.metro.compiler.graph.BaseTypeKey
import dev.zacsweers.metro.compiler.ir.parameters.wrapInProvider
import dev.zacsweers.metro.compiler.letIf
import dev.zacsweers.metro.compiler.md5base64
import dev.zacsweers.metro.compiler.unsafeLazy
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeOrFail
import org.jetbrains.kotlin.ir.util.TypeRemapper
import org.jetbrains.kotlin.ir.util.createDispatchReceiverParameterWithClassParent
import org.jetbrains.kotlin.ir.util.defaultType

// TODO cache these in DependencyGraphTransformer or shared transformer data
@Poko
internal class IrTypeKey
private constructor(override val type: IrType, override val qualifier: IrAnnotation?) :
  BaseTypeKey<IrType, IrAnnotation, IrTypeKey> {

  private val cachedRender by unsafeLazy { render(short = false, includeQualifier = true) }

  val hasTypeArgs: Boolean
    get() = type is IrSimpleType && type.arguments.isNotEmpty()

  fun remapTypes(typeRemapper: TypeRemapper): IrTypeKey {
    if (type !is IrSimpleType) return this
    return IrTypeKey(typeRemapper.remapType(type), qualifier)
  }

  override fun copy(type: IrType, qualifier: IrAnnotation?): IrTypeKey {
    return IrTypeKey(type, qualifier)
  }

  override fun toString(): String = cachedRender

  override fun compareTo(other: IrTypeKey): Int {
    if (this == other) return 0
    return cachedRender.compareTo(other.cachedRender)
  }

  override fun render(short: Boolean, includeQualifier: Boolean): String = buildString {
    if (includeQualifier) {
      qualifier?.let {
        append(it.render(short))
        append(" ")
      }
    }
    type.renderTo(this, short)
  }

  companion object {
    context(context: IrMetroContext)
    operator fun invoke(clazz: IrClass): IrTypeKey {
      return invoke(clazz.defaultType, with(context) { clazz.qualifierAnnotation() })
    }

    operator fun invoke(type: IrType, qualifier: IrAnnotation? = null): IrTypeKey {
      // Canonicalize on the way through
      return IrTypeKey(type.canonicalize(), qualifier)
    }
  }
}

internal fun IrTypeKey.requireSetElementType(): IrType {
  return type.expectAs<IrSimpleType>().arguments[0].typeOrFail
}

internal fun IrTypeKey.requireMapKeyType(): IrType {
  return type.expectAs<IrSimpleType>().arguments[0].typeOrFail
}

internal fun IrTypeKey.requireMapValueType(): IrType {
  return type.expectAs<IrSimpleType>().arguments[1].typeOrFail
}

internal fun IrTypeKey.metroAccessorName(suffix: String = ""): String {
  return buildString {
    append("accessor_")
    append(md5base64(listOf(this@metroAccessorName.toString())))
    append(suffix)
  }
}

// TODO for contributed graphs we could instead allow this in non-extendable graphs and just reach
//  into properties
context(context: IrMetroContext)
internal fun IrTypeKey.toAccessorFunctionIn(klass: IrClass, wrapInProvider: Boolean): IrSimpleFunction {
  val accessorName = metroAccessorName(
    suffix = if (wrapInProvider) "_provider" else ""
  )
  return context.irFactory
    .buildFun {
      this.name = accessorName.asName()
      this.returnType = this@toAccessorFunctionIn.type.letIf(wrapInProvider) { it.wrapInProvider(context.symbols.metroProvider) }
      this.visibility = DescriptorVisibilities.PUBLIC
      this.origin = Origins.ExtendableGraphAccessor
    }
    .apply {
      parent = klass
      parameters += createDispatchReceiverParameterWithClassParent()
      // Leave body impl to the caller
    }
}
