package com.pararam2006.cmv.utils

import timber.log.Timber

// Имя текущего файла, чтобы отфильтровать его из стек-трейса
private val LOG_HELPER_CLASS = "LogExtensionsKt"

fun Any.logError(message: String, throwable: Throwable? = null) {
    val stackTrace = Thread.currentThread().stackTrace
    val methodName = stackTrace.getOrNull(3)?.methodName ?: "unknown"

    if (throwable != null) {
        Timber.e(throwable, "[$methodName] -> $message")
    } else {
        Timber.e("[$methodName] -> $message")
    }
}

fun Any.logDebug(message: String) {
    val methodName = getDynamicMethodName()
    Timber.d("[$methodName] -> $message")
}

fun Any.logLifecycle(detail: String = "") {
    val message = if (detail.isEmpty()) "LIFECYCLE" else "LIFECYCLE — $detail"
    logDebug(message) // Индексы больше не важны, метод ниже все найдет сам
}

private fun getDynamicMethodName(): String {
    val stackTrace = Thread.currentThread().stackTrace

    // Ищем первый элемент стек-трейса, который не является системным
    // и не принадлежит этому файлу с экстеншенами
    val caller = stackTrace.firstOrNull { element ->
        val className = element.className
        !className.startsWith("java.lang") &&
                !className.startsWith("dalvik.system") &&
                !className.contains(LOG_HELPER_CLASS) &&
                !element.methodName.contains("invokeSuspend") // Игнорируем внутренности корутин
    }

    return caller?.methodName ?: "unknown"
}