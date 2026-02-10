package com.openclaw.a11ykernel.action

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class RootInputExecutor {
    fun isRootAvailable(): Boolean {
        val result = exec("id", 1200)
        return result.ok && result.stdout.contains("uid=0")
    }

    fun tap(x: Int, y: Int): ExecResult {
        return exec("input tap $x $y")
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): ExecResult {
        val duration = durationMs.coerceIn(50, 6000)
        return exec("input swipe $x1 $y1 $x2 $y2 $duration")
    }

    fun keyevent(code: Int): ExecResult {
        return exec("input keyevent $code")
    }

    fun inputText(raw: String): ExecResult {
        val escaped = raw
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        return exec("input text \"$escaped\"")
    }

    fun launchApp(packageName: String): ExecResult {
        val safe = packageName.trim()
        if (safe.isEmpty()) return ExecResult(false, "", "empty package")
        val monkey = exec("monkey -p $safe -c android.intent.category.LAUNCHER 1", 2500)
        if (monkey.ok) return monkey
        return exec("cmd package resolve-activity --brief $safe")
    }

    private fun exec(shellCommand: String, timeoutMs: Long = 2000): ExecResult {
        val process = ProcessBuilder("su", "-c", shellCommand)
            .redirectErrorStream(false)
            .start()

        val ok = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!ok) {
            process.destroyForcibly()
            return ExecResult(false, "", "timeout")
        }

        val code = process.exitValue()
        val stdout = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
        val stderr = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
        return ExecResult(code == 0, stdout.trim(), stderr.trim())
    }
}

data class ExecResult(
    val ok: Boolean,
    val stdout: String,
    val stderr: String
)
