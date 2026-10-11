package tn.loukious.superproxyunlocker

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import java.io.ByteArrayOutputStream
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicInteger

/** Install before Flutter initializes, so initial replies and subsequent updates are covered. */
class ModuleMain : XposedModule() {
    private val patchCount = AtomicInteger()
    private var installed = false

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        log(Log.INFO, TAG, "v${BuildConfig.VERSION_NAME} loaded: ${param.processName}")
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != TARGET || !param.isFirstPackage || installed) return
        try {
            System.loadLibrary("dexkit")
            val loader = param.classLoader
            val sources = listOf(param.applicationInfo.sourceDir) +
                param.applicationInfo.splitSourceDirs.orEmpty()
            val candidates = linkedSetOf<Method>()
            val codecClasses = linkedSetOf<Class<*>>()
            for (source in sources) {
                DexKitBridge.create(source).use { bridge ->
                    // Flutter's StandardMessageCodec.writeValue. These literals and the
                    // BAOS/Object -> void shape are independent of R8 class/member names.
                    val hits = bridge.findMethod {
                        matcher {
                            returnType = "void"
                            paramTypes("java.io.ByteArrayOutputStream", "java.lang.Object")
                            addUsingString("Unsupported value: '", StringMatchType.Equals)
                            addUsingString("Unsupported Number type: ", StringMatchType.Equals)
                        }
                    }
                    for (hit in hits) {
                        val method = hit.getMethodInstance(loader)
                        if (!Modifier.isStatic(method.modifiers) &&
                            method.parameterTypes.contentEquals(arrayOf(ByteArrayOutputStream::class.java, Any::class.java))) {
                            candidates += method
                            codecClasses += method.declaringClass
                        }
                    }
                    // ART may have inlined writeValue into the method-envelope encoders.
                    // Discover their classes by two independent Flutter protocol literals.
                    bridge.findClass {
                        matcher {
                            addUsingString("Method call corrupted", StringMatchType.Equals)
                            addUsingString("Envelope corrupted", StringMatchType.Equals)
                        }
                    }.forEach { codecClasses += it.getInstance(loader) }
                }
            }
            if (candidates.size != 1) {
                log(Log.ERROR, TAG, "Discovery rejected: expected one Flutter value writer, found ${candidates.size}; no hooks installed")
                return
            }
            val writer = candidates.single()
            hook(writer).intercept { chain ->
                val value = chain.getArg(1)
                if (!CustomerInfoPatcher.isCustomerInfo(value)) {
                    chain.proceed()
                } else {
                    val patched = try {
                        CustomerInfoPatcher.patch(value as Map<*, *>)
                    } catch (error: Throwable) {
                        log(Log.ERROR, TAG, "Customer-info schema could not be patched", error)
                        null
                    }
                    if (patched == null) {
                        chain.proceed()
                    } else {
                        val count = patchCount.incrementAndGet()
                        if (count <= 3 || count % 50 == 0) {
                            log(Log.INFO, TAG, "CustomerInfo patched #$count; active entitlement IDs=${(patched["entitlements"] as Map<*, *>)["active"].let { (it as Map<*, *>).keys }}")
                        }
                        chain.proceed(arrayOf(chain.getArg(0), patched))
                    }
                }
            }
            installed = true
            var deoptimized = 0
            codecClasses.flatMap { it.declaredMethods.toList() }.forEach { method ->
                if (!Modifier.isAbstract(method.modifiers) && !Modifier.isNative(method.modifiers)) {
                    runCatching { if (deoptimize(method)) deoptimized++ }
                }
            }
            log(Log.INFO, TAG, "Hook installed: ${writer.declaringClass.name}.${writer.name}; codec methods deoptimized=$deoptimized. Awaiting CustomerInfo.")
        } catch (error: Throwable) {
            log(Log.ERROR, TAG, "Discovery/installation failed; restart after checking framework compatibility", error)
        }
    }

    companion object {
        const val TAG = "SuperProxyUnlocker"
        const val TARGET = "com.scheler.superproxy"
    }
}
