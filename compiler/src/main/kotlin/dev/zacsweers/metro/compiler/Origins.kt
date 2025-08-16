// Copyright (C) 2024 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.compiler

import dev.zacsweers.metro.compiler.fir.Keys
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin

internal object Origins {
  val Default: IrDeclarationOrigin = IrDeclarationOrigin.GeneratedByPlugin(Keys.Default)
  val InstanceParameter: IrDeclarationOrigin =
    IrDeclarationOrigin.GeneratedByPlugin(Keys.InstanceParameter)
  val ReceiverParameter: IrDeclarationOrigin =
    IrDeclarationOrigin.GeneratedByPlugin(Keys.ReceiverParameter)
  val RegularParameter: IrDeclarationOrigin =
    IrDeclarationOrigin.GeneratedByPlugin(Keys.RegularParameter)
  val AssistedFactoryImplClassDeclaration: IrDeclarationOrigin =
    IrDeclarationOrigin.GeneratedByPlugin(Keys.AssistedFactoryImplClassDeclaration)
  val AssistedFactoryImplCreatorFunctionDeclaration: IrDeclarationOrigin =
    IrDeclarationOrigin.GeneratedByPlugin(Keys.AssistedFactoryImplCreatorFunctionDeclaration)
  val MetroGraphCreatorsObjectInvokeDeclaration: IrDeclarationOrigin =
    IrDeclarationOrigin.GeneratedByPlugin(Keys.MetroGraphCreatorsObjectInvokeDeclaration)
  val MetroGraphDeclaration: IrDeclarationOrigin =
    IrDeclarationOrigin.GeneratedByPlugin(Keys.MetroGraphDeclaration)
  val MetroGraphAccessorCallableOverride: IrDeclarationOrigin =
    IrDeclarationOrigin.GeneratedByPlugin(Keys.MetroGraphAccessorCallableOverride)
  val MetroGraphInjectorCallableOverride: IrDeclarationOrigin =
    IrDeclarationOrigin.GeneratedByPlugin(Keys.MetroGraphInjectorCallableOverride)
  val MetroGraphFactoryCompanionGetter: IrDeclarationOrigin =
    IrDeclarationOrigin.GeneratedByPlugin(Keys.MetroGraphFactoryCompanionGetter)
  val MetroContributionClassDeclaration: IrDeclarationOrigin =
    IrDeclarationOrigin.GeneratedByPlugin(Keys.MetroContributionClassDeclaration)
  val MetroContributionCallableDeclaration: IrDeclarationOrigin =
    IrDeclarationOrigin.GeneratedByPlugin(Keys.MetroContributionCallableDeclaration)
  val InjectConstructorFactoryClassDeclaration: IrDeclarationOrigin =
    IrDeclarationOrigin.GeneratedByPlugin(Keys.InjectConstructorFactoryClassDeclaration)
  val MembersInjectorClassDeclaration: IrDeclarationOrigin =
    IrDeclarationOrigin.GeneratedByPlugin(Keys.MembersInjectorClassDeclaration)
  val FactoryCreateFunction: IrDeclarationOrigin =
    IrDeclarationOrigin.GeneratedByPlugin(Keys.FactoryCreateFunction)
  val FactoryNewInstanceFunction: IrDeclarationOrigin =
    IrDeclarationOrigin.GeneratedByPlugin(Keys.FactoryNewInstanceFunction)
  val ProviderFactoryClassDeclaration: IrDeclarationOrigin =
    IrDeclarationOrigin.GeneratedByPlugin(Keys.ProviderFactoryClassDeclaration)
  val TopLevelInjectFunctionClassFunction: IrDeclarationOrigin =
    IrDeclarationOrigin.GeneratedByPlugin(Keys.TopLevelInjectFunctionClassFunction)
  val ExtendableGraphAccessor: IrDeclarationOrigin =
    IrDeclarationOrigin.GeneratedByPlugin(Keys.ExtendableGraphAccessor)
  val GeneratedGraphExtension: IrDeclarationOrigin =
    IrDeclarationOrigin.GeneratedByPlugin(Keys.GeneratedGraphExtension)
  val BindingMirrorClassDeclaration: IrDeclarationOrigin =
    IrDeclarationOrigin.GeneratedByPlugin(Keys.BindingMirrorClassDeclaration)
}
