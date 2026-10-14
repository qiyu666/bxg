package com.qiyu.safe.data

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 捕获未处理异常（例如点生物识别导致的闪退），把堆栈写进本地文件，
 * 供下次启动时读取展示，便于定位问题。
 */
object CrashLog {
    private fun crashFile(context: Context): File = File(context.filesDir, "last_crash.txt")

    /** 安装全局未捕获异常处理器（在 MainActivity.onCreate 调一次即可）。 */
    fun install(context: Context) {
        val def = Thread.getDefaultUncaughtExceptionHandler()
        val file = crashFile(context)
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                file.writeText("线程: ${t.name}\n${sw.toString()}\n时间: ${System.currentTimeMillis()}")
            } catch (ignored: Exception) {
            }
            if (def != null) def.uncaughtException(t, e)
        }
    }

    /** 读取最近一次崩溃日志；无则返回 null。 */
    fun last(context: Context): String? {
        val f = crashFile(context)
        return if (f.exists()) f.readText().ifBlank { null } else null
    }

    fun clear(context: Context) {
        crashFile(context).delete()
    }
}
