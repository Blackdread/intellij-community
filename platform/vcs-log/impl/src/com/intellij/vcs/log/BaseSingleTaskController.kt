// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.Consumer
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.vcs.log.data.SingleTaskController
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

abstract class BaseSingleTaskController<Request, Result>(name: String, resultConsumer: (Result) -> Unit, disposable: Disposable) :
  SingleTaskController<Request, Result>(name, Consumer { runInEdt(disposable) { resultConsumer(it) } }, disposable) {
  override fun startNewBackgroundTask(): SingleTask {
    val indicator: ProgressIndicator = createProgressIndicator()
    val future = AppExecutorUtil.getAppExecutorService().submitSafe(LOG) {
      ProgressManager.getInstance().runProcess({ doRun(indicator) }, indicator)
    }
    return SingleTaskImpl(future, indicator)
  }

  private fun doRun(indicator: ProgressIndicator) {
    var result: Result? = null

    while (true) {
      indicator.checkCanceled()

      val requests = popRequests()
      if (requests.isEmpty()) break
      result = process(requests, result)
    }

    taskCompleted(result)
  }

  protected open fun createProgressIndicator() = EmptyProgressIndicator()

  abstract fun process(requests: List<Request>, previousResult: Result?): Result
}

fun runInEdt(disposable: Disposable, action: () -> Unit) {
  val app = ApplicationManager.getApplication()
  if (app.isDispatchThread) {
    action()
  }
  else {
    app.invokeLater(action, { Disposer.isDisposed(disposable) })
  }
}

fun ExecutorService.submitSafe(log: Logger, task: () -> Unit): Future<*> = this.submit {
  try {
    task()
  }
  catch (_: ProcessCanceledException) {
  }
  catch (t: Throwable) {
    log.error(t)
  }
}