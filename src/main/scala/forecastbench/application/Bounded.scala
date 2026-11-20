package forecastbench.application

import java.util.concurrent.{Callable, Executors, Semaphore}

/**
 * Work over many items, each on a virtual thread, at most `parallel` at once, the results in the
 * items' order. A route that walks every question or every run reads this way: one by one, a few
 * thousand outlast the route's timeout (CLAUDE.md, 2026-11-19).
 */
object Bounded:

  def map[A, B](items: Seq[A], parallel: Int)(f: A => B): List[B] =
    val pool    = Executors.newVirtualThreadPerTaskExecutor()
    val permits = Semaphore(parallel)
    try
      items
        .map(a =>
          pool.submit((() =>
            permits.acquire()
            try f(a)
            finally permits.release()
          ): Callable[B])
        )
        .map(_.get())
        .toList
    finally pool.shutdown()
