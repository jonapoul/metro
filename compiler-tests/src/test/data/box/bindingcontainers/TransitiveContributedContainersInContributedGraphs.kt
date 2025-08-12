@ContributesTo(AppScope::class)
@BindingContainer(includes = [IncludedBindings::class])
interface Bindings1 {
  @Binds val Int.bindNumber: Number
}

@BindingContainer
interface IncludedBindings {
  @Binds val String.bindCharSequence: CharSequence
}

@ContributesGraphExtension(AppScope::class)
interface ContributedGraph {
  val number: Number
  val charSequence: CharSequence

  @Provides fun provideString(): String = "Hello, World!"

  @Provides fun provideInt(): Int = 3

  @ContributesGraphExtension.Factory(Unit::class)
  interface Factory {
    fun createContributedGraph(): ContributedGraph
  }
}

@DependencyGraph(Unit::class, isExtendable = true) interface UnitGraph

fun box(): String {
  val graph = createGraph<UnitGraph>().createContributedGraph()
  assertEquals("Hello, World!", graph.charSequence)
  assertEquals(3, graph.number)
  return "OK"
}
