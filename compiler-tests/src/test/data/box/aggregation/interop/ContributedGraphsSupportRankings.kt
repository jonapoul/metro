// WITH_ANVIL

import com.squareup.anvil.annotations.ContributesBinding

interface ContributedInterface

@ContributesBinding(AppScope::class, rank = 50)
object LowRankImpl : ContributedInterface

@ContributesBinding(AppScope::class, rank = 100)
object HighRankImpl : ContributedInterface

@ContributesGraphExtension(AppScope::class)
interface ExampleGraphExtension {
  val contributedInterface: ContributedInterface

  @ContributesGraphExtension.Factory(Unit::class)
  interface Factory {
    fun createExampleGraphExtension(): ExampleGraphExtension
  }
}

@DependencyGraph(Unit::class, isExtendable = true)
interface UnitGraph

fun box(): String {
  val graph = createGraph<UnitGraph>().createExampleGraphExtension()
  assertTrue(graph.contributedInterface == HighRankImpl)
  return "OK"
}