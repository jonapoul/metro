// Copyright (C) 2024 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.compiler.ir.transformers

import dev.zacsweers.metro.compiler.Origins
import dev.zacsweers.metro.compiler.Symbols
import dev.zacsweers.metro.compiler.asName
import dev.zacsweers.metro.compiler.generatedClass
import dev.zacsweers.metro.compiler.ir.ClassFactory
import dev.zacsweers.metro.compiler.ir.IrMetroContext
import dev.zacsweers.metro.compiler.ir.MetroIrErrors
import dev.zacsweers.metro.compiler.ir.assignConstructorParamsToFields
import dev.zacsweers.metro.compiler.ir.createIrBuilder
import dev.zacsweers.metro.compiler.ir.dispatchReceiverFor
import dev.zacsweers.metro.compiler.ir.finalizeFakeOverride
import dev.zacsweers.metro.compiler.ir.findInjectableConstructor
import dev.zacsweers.metro.compiler.ir.irExprBodySafe
import dev.zacsweers.metro.compiler.ir.irInvoke
import dev.zacsweers.metro.compiler.ir.irTemporary
import dev.zacsweers.metro.compiler.ir.isExternalParent
import dev.zacsweers.metro.compiler.ir.locationOrNull
import dev.zacsweers.metro.compiler.ir.metroAnnotationsOf
import dev.zacsweers.metro.compiler.ir.parameters.Parameter
import dev.zacsweers.metro.compiler.ir.parameters.Parameters
import dev.zacsweers.metro.compiler.ir.parameters.parameters
import dev.zacsweers.metro.compiler.ir.parametersAsProviderArguments
import dev.zacsweers.metro.compiler.ir.regularParameters
import dev.zacsweers.metro.compiler.ir.requireSimpleFunction
import dev.zacsweers.metro.compiler.ir.thisReceiverOrFail
import dev.zacsweers.metro.compiler.ir.trackFunctionCall
import dev.zacsweers.metro.compiler.ir.typeAsProviderArgument
import java.util.Optional
import kotlin.jvm.optionals.getOrNull
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.callableId
import org.jetbrains.kotlin.ir.util.classIdOrFail
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.fileOrNull
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.getAnnotationStringValue
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.nestedClasses
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId

internal class InjectConstructorTransformer(
  context: IrMetroContext,
  private val membersInjectorTransformer: MembersInjectorTransformer,
) : IrMetroContext by context {

  private val generatedFactories = mutableMapOf<ClassId, Optional<ClassFactory>>()

  fun visitClass(declaration: IrClass) {
    val injectableConstructor =
      declaration.findInjectableConstructor(onlyUsePrimaryConstructor = false)
    if (injectableConstructor != null) {
      getOrGenerateFactory(declaration, injectableConstructor, doNotErrorOnMissing = false)
    }
  }

  fun getOrGenerateFactory(
    declaration: IrClass,
    previouslyFoundConstructor: IrConstructor?,
    doNotErrorOnMissing: Boolean,
  ): ClassFactory? {
    val injectedClassId: ClassId = declaration.classIdOrFail
    generatedFactories[injectedClassId]?.getOrNull()?.let {
      return it
    }

    val isExternal = declaration.isExternalParent

    /*
    Implement a simple Factory class that takes all injected values as providers

    // Simple
    class Example_Factory(private val valueProvider: Provider<String>) : Factory<Example_Factory>

    // Generic
    class Example_Factory<T>(private val valueProvider: Provider<T>) : Factory<Example_Factory<T>>
    */
    val factoryCls =
      declaration.nestedClasses.singleOrNull {
        val isMetroFactory = it.name == Symbols.Names.MetroFactory
        // If not external, double check its origin
        if (isMetroFactory && !isExternal) {
          if (it.origin != Origins.InjectConstructorFactoryClassDeclaration) {
            diagnosticReporter
              .at(declaration)
              .report(
                MetroIrErrors.METRO_ERROR,
                "Found a Metro factory declaration in ${declaration.kotlinFqName} but with an unexpected origin ${it.origin}",
              )
            return null
          }
        }
        isMetroFactory
      }

    if (factoryCls == null) {
      if (isExternal) {
        // TODO maybe emit a warning if we do see one even if it's disabled?
        if (options.enableDaggerRuntimeInterop) {
          val targetConstructor =
            previouslyFoundConstructor
              ?: declaration.findInjectableConstructor(onlyUsePrimaryConstructor = false)
              ?: return null
          // Look up where dagger would generate one
          val daggerFactoryClassId = injectedClassId.generatedClass("_Factory")
          val daggerFactoryClass = pluginContext.referenceClass(daggerFactoryClassId)?.owner
          if (daggerFactoryClass != null) {
            val wrapper =
              ClassFactory.DaggerFactory(
                metroContext,
                daggerFactoryClass,
                targetConstructor.parameters(),
              )
            generatedFactories[injectedClassId] = Optional.of(wrapper)
            return wrapper
          }
        } else if (doNotErrorOnMissing) {
          // Store a null here because it's absent
          generatedFactories[injectedClassId] = Optional.empty()
          return null
        }

        val noFactoryMessage =
          "Could not find generated factory for '${declaration.kotlinFqName}' in upstream module where it's defined. Run the Metro compiler over that module too, or Dagger if you're using its interop for Java files."
        if (declaration.fileOrNull == null) {
          // TODO move to diagnostic reporter in 2.2.20
          // https://youtrack.jetbrains.com/issue/KT-78280
          metroContext.messageCollector.report(
            CompilerMessageSeverity.ERROR,
            noFactoryMessage,
            declaration.locationOrNull(),
          )
        } else {
          diagnosticReporter.at(declaration).report(MetroIrErrors.METRO_ERROR, noFactoryMessage)
        }
        return null
      } else if (doNotErrorOnMissing) {
        // Store a null here because it's absent
        generatedFactories[injectedClassId] = Optional.empty()
        return null
      } else {
        error(
          "No expected factory class generated for '${declaration.kotlinFqName}'. Report this bug with a repro case at https://github.com/zacsweers/metro/issues/new"
        )
      }
    }

    // If it's from another module, we're done!
    // TODO this doesn't work as expected in KMP, where things compiled in common are seen as
    //  external but no factory is found?
    if (isExternal) {
      val parameters =
        factoryCls.requireSimpleFunction(Symbols.StringNames.MIRROR_FUNCTION).owner.parameters()
      val wrapper = ClassFactory.MetroFactory(factoryCls, parameters)
      generatedFactories[injectedClassId] = Optional.of(wrapper)
      return wrapper
    }

    val injectors = membersInjectorTransformer.getOrGenerateAllInjectorsFor(declaration)
    val memberInjectParameters = injectors.flatMap { it.requiredParametersByClass.values.flatten() }

    val targetConstructor =
      previouslyFoundConstructor
        ?: declaration.findInjectableConstructor(onlyUsePrimaryConstructor = false)!!
    val constructorParameters = targetConstructor.parameters()
    val allParameters =
      buildList {
          add(constructorParameters)
          addAll(memberInjectParameters)
        }
        .distinct()
    val allValueParameters = allParameters.flatMap { it.regularParameters }
    val nonAssistedParameters = allValueParameters.filterNot { it.isAssisted }

    val ctor = factoryCls.primaryConstructor!!

    val constructorParametersToFields = assignConstructorParamsToFields(ctor, factoryCls)

    // TODO This is ugly. Can we just source all the params directly from the FIR class now?
    val sourceParametersToFields: Map<Parameter, IrField> =
      constructorParametersToFields.entries.withIndex().associate { (index, pair) ->
        val (_, field) = pair
        val sourceParam = nonAssistedParameters[index]
        sourceParam to field
      }

    val newInstanceFunction =
      generateCreators(
        factoryCls,
        ctor.symbol,
        targetConstructor.symbol,
        constructorParameters,
        allParameters,
      )

    /*
    Normal provider - override + implement the Provider.value property

    // Simple
    override fun invoke(): Example = newInstance(valueProvider())

    // Generic
    override fun invoke(): Example<T> = newInstance(valueProvider())

    // Provider
    override fun invoke(): Example<T> = newInstance(valueProvider)

    // Lazy
    override fun invoke(): Example<T> = newInstance(DoubleCheck.lazy(valueProvider))

    // Provider<Lazy<T>>
    override fun invoke(): Example<T> = newInstance(ProviderOfLazy.create(valueProvider))
    */
    val invoke = factoryCls.requireSimpleFunction(Symbols.StringNames.INVOKE)

    implementFactoryInvokeOrGetBody(
      invoke.owner,
      factoryCls.thisReceiverOrFail,
      newInstanceFunction,
      constructorParameters,
      injectors,
      sourceParametersToFields,
    )

    possiblyImplementInvoke(declaration, constructorParameters)

    // Generate a metadata-visible function that matches the signature of the target constructor
    // This is used in downstream compilations to read the constructor's signature
    val mirrorFunction =
      generateMetadataVisibleMirrorFunction(
        factoryClass = factoryCls,
        target = targetConstructor,
        metroAnnotationsOf(targetConstructor),
      )

    factoryCls.dumpToMetroLog()

    val wrapper = ClassFactory.MetroFactory(factoryCls, mirrorFunction.parameters())
    generatedFactories[injectedClassId] = Optional.of(wrapper)
    return wrapper
  }

  private fun implementFactoryInvokeOrGetBody(
    invokeFunction: IrSimpleFunction,
    thisReceiver: IrValueParameter,
    newInstanceFunction: IrSimpleFunction,
    constructorParameters: Parameters,
    injectors: List<MembersInjectorTransformer.MemberInjectClass>,
    parametersToFields: Map<Parameter, IrField>,
  ) {
    if (invokeFunction.isFakeOverride) {
      invokeFunction.finalizeFakeOverride(thisReceiver)
    }
    invokeFunction.body =
      pluginContext.createIrBuilder(invokeFunction.symbol).irBlockBody {
        val constructorParameterNames =
          constructorParameters.regularParameters
            .filterNot { it.isAssisted }
            .associateBy { it.originalName }

        val functionParamsByName =
          invokeFunction.regularParameters.associate { it.name to irGet(it) }

        val args =
          constructorParameters.regularParameters.map { targetParam ->
            when (val parameterName = targetParam.originalName) {
              in constructorParameterNames -> {
                val constructorParam = constructorParameterNames.getValue(parameterName)
                val providerInstance =
                  irGetField(
                    irGet(invokeFunction.dispatchReceiverParameter!!),
                    parametersToFields.getValue(constructorParam),
                  )
                val contextKey = targetParam.contextualTypeKey
                typeAsProviderArgument(
                  contextKey = contextKey,
                  bindingCode = providerInstance,
                  isAssisted = false,
                  isGraphInstance = constructorParam.isGraphInstance,
                )
              }

              in functionParamsByName -> {
                functionParamsByName.getValue(targetParam.originalName)
              }

              else -> error("Unmatched top level injected function param: $targetParam")
            }
          }

        val typeArgs =
          if (newInstanceFunction.typeParameters.isNotEmpty()) {
            listOf(invokeFunction.returnType)
          } else {
            null
          }
        val newInstance =
          irInvoke(
            dispatchReceiver = dispatchReceiverFor(newInstanceFunction),
            callee = newInstanceFunction.symbol,
            typeArgs = typeArgs,
            args = args,
          )

        if (injectors.isNotEmpty()) {
          val instance = irTemporary(newInstance)
          for (injector in injectors) {
            val typeArgs = injector.ir.parentAsClass.typeParameters.map { it.defaultType }
            for ((function, parameters) in injector.declaredInjectFunctions) {
              // Record for IC
              trackFunctionCall(invokeFunction, function)
              +irInvoke(
                dispatchReceiver = irGetObject(function.parentAsClass.symbol),
                callee = function.symbol,
                typeArgs = typeArgs,
                args =
                  buildList {
                    add(irGet(instance))
                    addAll(
                      parametersAsProviderArguments(
                        parameters,
                        invokeFunction.dispatchReceiverParameter!!,
                        parametersToFields,
                      )
                    )
                  },
              )
            }
          }

          +irReturn(irGet(instance))
        } else {
          +irReturn(newInstance)
        }
      }
  }

  private fun possiblyImplementInvoke(declaration: IrClass, constructorParameters: Parameters) {
    val injectedFunctionClass =
      declaration.getAnnotation(Symbols.ClassIds.metroInjectedFunctionClass.asSingleFqName())
    if (injectedFunctionClass != null) {
      val callableName = injectedFunctionClass.getAnnotationStringValue()!!.asName()
      val callableId = CallableId(declaration.packageFqName!!, callableName)
      var targetCallable = pluginContext.referenceFunctions(callableId).single()

      // Assign fields
      val constructorParametersToFields =
        assignConstructorParamsToFields(constructorParameters, declaration)

      val invokeFunction =
        declaration.functions.first { it.origin == Origins.TopLevelInjectFunctionClassFunction }

      // If compose compiler has already run, the looked up function may be the _old_ function
      // and we need to update the reference to the newly transformed one
      val hasComposeCompilerRun =
        invokeFunction.regularParameters.lastOrNull()?.name?.asString() == "\$changed"
      if (hasComposeCompilerRun) {
        val originalParent = targetCallable.owner.file
        targetCallable =
          originalParent.declarations
            .filterIsInstance<IrSimpleFunction>()
            .first { it.callableId == callableId }
            .symbol
      }

      // TODO
      //  copy default values
      invokeFunction.apply {
        val functionReceiver = dispatchReceiverParameter!!
        body =
          pluginContext.createIrBuilder(symbol).run {
            val constructorParameterNames =
              constructorParameters.regularParameters.associateBy { it.originalName }

            val functionParamsByName =
              invokeFunction.regularParameters.associate { it.name to irGet(it) }

            val args =
              targetCallable.owner.parameters().regularParameters.map { targetParam ->
                when (val parameterName = targetParam.originalName) {
                  in constructorParameterNames -> {
                    val constructorParam = constructorParameterNames.getValue(parameterName)
                    val providerInstance =
                      irGetField(
                        irGet(functionReceiver),
                        constructorParametersToFields.getValue(constructorParam),
                      )
                    val contextKey = targetParam.contextualTypeKey
                    typeAsProviderArgument(
                      contextKey = contextKey,
                      bindingCode = providerInstance,
                      isAssisted = false,
                      isGraphInstance = constructorParam.isGraphInstance,
                    )
                  }

                  in functionParamsByName -> {
                    functionParamsByName.getValue(targetParam.originalName)
                  }

                  else -> error("Unmatched top level injected function param: $targetParam")
                }
              }

            val invokeExpression =
              irInvoke(
                callee = targetCallable,
                dispatchReceiver = null,
                extensionReceiver = null,
                typeHint = targetCallable.owner.returnType,
                // TODO type params
                args = args,
              )

            irExprBodySafe(symbol, invokeExpression)
          }
      }

      declaration.dumpToMetroLog()
    }
  }

  private fun generateCreators(
    factoryCls: IrClass,
    factoryConstructor: IrConstructorSymbol,
    targetConstructor: IrConstructorSymbol,
    constructorParameters: Parameters,
    allParameters: List<Parameters>,
  ): IrSimpleFunction {
    // If this is an object, we can generate directly into this object
    val isObject = factoryCls.kind == ClassKind.OBJECT
    val classToGenerateCreatorsIn =
      if (isObject) {
        factoryCls
      } else {
        factoryCls.companionObject()!!
      }

    // TODO
    //  Dagger will de-dupe these by type key to shrink the code. We could do the same but only for
    //  parameters that don't have default values. For those cases, we would need to keep them
    //  as-is. Something for another day.
    val mergedParameters =
      allParameters.reduce { current, next -> current.mergeValueParametersWithUntyped(next) }

    // Generate create()
    generateStaticCreateFunction(
      parentClass = classToGenerateCreatorsIn,
      targetClass = factoryCls,
      targetConstructor = factoryConstructor,
      parameters = mergedParameters,
      providerFunction = null,
    )

    val newInstanceFunction =
      generateStaticNewInstanceFunction(
        parentClass = classToGenerateCreatorsIn,
        sourceParameters = constructorParameters.regularParameters.map { it.ir },
      ) { function ->
        irCallConstructor(
            callee = targetConstructor,
            typeArguments = function.typeParameters.map { it.defaultType },
          )
          .apply {
            // The function may have a dispatch receiver so we need to offset
            val functionParameters = function.nonDispatchParameters
            val indexOffset = if (function.dispatchReceiverParameter == null) 0 else 1
            for (index in constructorParameters.allParameters.indices) {
              val parameter = functionParameters[index]
              arguments[parameter.indexInParameters - indexOffset] = irGet(parameter)
            }
          }
      }
    return newInstanceFunction
  }
}
