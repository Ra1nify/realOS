package io.github.ra1nify.realos.xposed.loader

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger

/** Counts successful hook registrations through the module wrapper. */
internal object HookCountProbe {

    private val count = AtomicInteger()

    @Volatile
    private var lastModule: XposedModule? = null

    /** Resets the counter and installs the counting proxy for this module generation. */
    @Synchronized
    fun begin(module: XposedModule) {
        count.set(0)
        if (lastModule === module) return
        lastModule = module

        runCatching {
            val field = Class.forName("io.github.libxposed.api.XposedInterfaceWrapper")
                .declaredFields
                .firstOrNull { XposedInterface::class.java.isAssignableFrom(it.type) }
                ?: return@runCatching
            field.isAccessible = true
            val base = field.get(module) as? XposedInterface ?: return@runCatching

            val proxy = Proxy.newProxyInstance(
                XposedInterface::class.java.classLoader,
                arrayOf(XposedInterface::class.java)
            ) { _, method, args ->
                val result = invoke(base, method, args)
                if (method.name == "hook" || method.name == "hookClassInitializer") {
                    (result as? XposedInterface.HookBuilder)?.let { wrapBuilder(it) } ?: result
                } else {
                    result
                }
            } as XposedInterface
            field.set(module, proxy)
        }
    }

    fun count(): Int = count.get()

    private fun wrapBuilder(builder: XposedInterface.HookBuilder): XposedInterface.HookBuilder {
        lateinit var proxy: XposedInterface.HookBuilder
        proxy = Proxy.newProxyInstance(
            XposedInterface.HookBuilder::class.java.classLoader,
            arrayOf(XposedInterface.HookBuilder::class.java)
        ) { _, method, args ->
            val result = invoke(builder, method, args)
            when {
                method.name == "intercept" -> {
                    count.incrementAndGet()
                    result
                }
                method.returnType == XposedInterface.HookBuilder::class.java -> proxy
                else -> result
            }
        } as XposedInterface.HookBuilder
        return proxy
    }

    private fun invoke(target: Any, method: java.lang.reflect.Method, args: Array<out Any?>?): Any? =
        try {
            method.invoke(target, *(args ?: emptyArray()))
        } catch (t: InvocationTargetException) {
            throw t.targetException
        }
}
