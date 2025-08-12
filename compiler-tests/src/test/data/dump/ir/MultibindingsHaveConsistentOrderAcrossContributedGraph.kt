object AppScope

interface Task
@Inject class TaskImpl2 : Task
@Inject class TaskImpl1 : Task
class TaskImpl4 : Task
class TaskImpl3 : Task

@DependencyGraph(AppScope::class, isExtendable = true)
interface ExampleGraph {
  val tasks: Set<Task>
  @IntoSet @Binds val TaskImpl2.bind: Task
  @IntoSet @Binds val TaskImpl1.bind: Task
  @IntoSet @Provides fun provide4(): Task = TaskImpl4()
  @IntoSet @Provides fun provide3(): Task = TaskImpl3()
}

@ContributesGraphExtension(Unit::class)
interface LoggedInGraph {
  val tasksFromParent: Set<Task>

  @ContributesGraphExtension.Factory(AppScope::class)
  interface Factory1 {
    fun createLoggedInGraph(): LoggedInGraph
  }
}