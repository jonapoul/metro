# Multiplatform

The runtime and code gen have been implemented to be entirely platform-agnostic so far.

Cross-platform aggregation features are only supported on JVM and Android at the moment but should be available in Kotlin 2.3.30+. Follow [this issue](https://github.com/ZacSweers/metro/issues/460).

When mixing contributions between common and platform-specific source sets, you must define your final `@DependencyGraph` in the platform-specific code. This is because a graph defined in commonMain wouldn’t have full visibility of contributions from platform-specific types. A good pattern for this is to define your canonical graph in commonMain *without* a `@DependencyGraph` annotation and then a `{Platform}{Graph}` type in the platform source set that extends it and does have the `@DependencyGraph`. Metro automatically exposes bindings of the base graph type on the graph for any injections that need it.

```kotlin
// In commonMain
interface AppGraph {
  val httpClient: HttpClient
}

// In jvmMain
@DependencyGraph
interface JvmAppGraph : AppGraph {
  @Provides fun provideHttpClient(): HttpClient = HttpClient(Netty)
}

// In androidMain
@DependencyGraph
interface AndroidAppGraph : AppGraph {
  @Provides fun provideHttpClient(): HttpClient = HttpClient(OkHttp)
}
```
