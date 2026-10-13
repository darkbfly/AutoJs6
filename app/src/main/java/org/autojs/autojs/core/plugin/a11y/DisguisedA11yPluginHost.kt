package org.autojs.autojs.core.plugin.a11y

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.os.SystemClock.uptimeMillis
import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.autojs.plugin.disguised.a11y.api.IDisguisedA11yPlugin
import org.autojs.plugin.disguised.a11y.api.NodeSnapshot
import org.autojs.plugin.disguised.a11y.api.PluginInfo
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Host binder for the disguised accessibility plugin (layout-node scheme).
 *
 * Signature-level [org.autojs.permission.PLUGIN] already gates bind; user must
 * enable the plugin AccessibilityService in system settings manually.
 */
object DisguisedA11yPluginHost {

    private const val TAG = "DisguisedA11yHost"

    const val ACTION_DISGUISED_A11Y = "org.autojs.plugin.DISGUISED_A11Y"

    private const val DEFAULT_BIND_TIMEOUT_MS = 15_000L
    private const val DEFAULT_CALL_TIMEOUT_MS = 15_000L
    private const val IDLE_UNBIND_MS = 30_000L

    private val externalServiceFlag =
        runCatching { ServiceInfo::class.java.getField("FLAG_EXTERNAL_SERVICE").getInt(null) }.getOrNull() ?: 0
    private val bindExternalServiceFlag =
        runCatching { Context::class.java.getField("BIND_EXTERNAL_SERVICE").getInt(null) }.getOrNull() ?: 0
    private val poolHandler = Handler(Looper.getMainLooper())
    private val poolLock = Any()
    private val connectionPool = LinkedHashMap<ComponentName, PooledConnection>()

    data class Discovered(
        val serviceInfo: ServiceInfo,
        val pluginInfo: PluginInfo?,
        val error: Throwable?,
    )

    fun hasInstalledPlugin(context: Context): Boolean = queryServices(context).isNotEmpty()

    fun captureActiveWindowSync(context: Context): NodeSnapshot? = runBlocking {
        captureActiveWindow(context)
    }

    suspend fun captureActiveWindow(context: Context): NodeSnapshot? {
        // Capture path skips discover/getInfo; bind the first matching service directly.
        val serviceInfo = queryServices(context).firstOrNull()
            ?: error("No disguised a11y plugin installed")
        val start = uptimeMillis()
        return withService(context, serviceInfo, DEFAULT_BIND_TIMEOUT_MS) { proxy ->
            val remain = DEFAULT_CALL_TIMEOUT_MS - (uptimeMillis() - start)
            if (remain <= 0) error("AIDL call timeout")
            if (!proxy.isServiceConnected) {
                error("Disguised accessibility service is not enabled in system settings")
            }
            proxy.captureActiveWindow()
        }
    }

    suspend fun select(context: Context): Discovered? {
        val list = discover(context).filter { it.pluginInfo != null || it.error == null }
        if (list.isEmpty()) return null
        return list.firstOrNull { it.pluginInfo != null } ?: list.firstOrNull()
    }

    suspend fun discover(context: Context): List<Discovered> {
        val services = queryServices(context)
        Log.i(TAG, "discover: services=${services.size}")
        return services.map { svc ->
            val result = runCatching { withService(context, svc, DEFAULT_BIND_TIMEOUT_MS) { it.getInfo() } }
            Discovered(svc, result.getOrNull(), result.exceptionOrNull())
        }
    }

    private fun queryServices(context: Context, packageName: String? = null): List<ServiceInfo> {
        val pm = context.packageManager
        val intent = Intent(ACTION_DISGUISED_A11Y).apply {
            if (!packageName.isNullOrBlank()) setPackage(packageName)
        }
        val resolveList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentServices(intent, 0)
        }
        return resolveList.mapNotNull { it.serviceInfo }
    }

    private fun buildBindFlags(serviceInfo: ServiceInfo): Int {
        var flags = Context.BIND_AUTO_CREATE
        if (externalServiceFlag != 0 && bindExternalServiceFlag != 0 && (serviceInfo.flags and externalServiceFlag) != 0) {
            flags = flags or bindExternalServiceFlag
        }
        return flags
    }

    private suspend fun <T> withService(
        context: Context,
        serviceInfo: ServiceInfo,
        bindTimeoutMs: Long,
        block: suspend (IDisguisedA11yPlugin) -> T,
    ): T {
        val appCtx = context.applicationContext
        val entry = getOrCreateEntry(appCtx, serviceInfo)
        val proxy = awaitProxy(entry, bindTimeoutMs)
        return try {
            block(proxy)
        } catch (e: RemoteException) {
            invalidateEntry(entry, e)
            val retry = awaitProxy(entry, bindTimeoutMs)
            block(retry)
        } finally {
            touch(entry)
        }
    }

    private fun touch(entry: PooledConnection) {
        synchronized(entry.lock) {
            entry.lastUsed = uptimeMillis()
        }
        scheduleIdleUnbind(entry)
    }

    private suspend fun awaitProxy(entry: PooledConnection, bindTimeoutMs: Long): IDisguisedA11yPlugin =
        suspendCancellableCoroutine { cont ->
            var shouldBind = false
            synchronized(entry.lock) {
                entry.lastUsed = uptimeMillis()
                cancelIdleUnbindLocked(entry)
                val existing = entry.proxy
                if (existing != null && entry.bound) {
                    cont.resume(existing)
                    return@suspendCancellableCoroutine
                }
                entry.waiters.add(cont)
                if (!entry.connecting) {
                    entry.connecting = true
                    shouldBind = true
                }
            }
            if (shouldBind) {
                startBind(entry, bindTimeoutMs)
            }
            cont.invokeOnCancellation {
                synchronized(entry.lock) {
                    entry.waiters.remove(cont)
                }
            }
        }

    private fun startBind(entry: PooledConnection, bindTimeoutMs: Long) {
        val intent = Intent().setComponent(entry.cn)
        val bindFlags = buildBindFlags(entry.serviceInfo)
        var ok = try {
            Log.i(TAG, "bindService: ${entry.cn}")
            entry.appCtx.bindService(intent, entry.connection, bindFlags)
        } catch (se: SecurityException) {
            failWaiters(
                entry,
                IllegalStateException(
                    "bindService SecurityException: ${entry.cn}. Plugin must declare org.autojs.permission.PLUGIN and share the host signing key.",
                    se,
                ),
            )
            return
        }
        if (!ok && bindFlags != Context.BIND_AUTO_CREATE) {
            ok = try {
                entry.appCtx.bindService(intent, entry.connection, Context.BIND_AUTO_CREATE)
            } catch (se: SecurityException) {
                failWaiters(entry, IllegalStateException("bindService SecurityException: ${entry.cn}", se))
                return
            }
        }
        if (!ok) {
            val appInfo = entry.serviceInfo.applicationInfo
            val stopped = (appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0
            failWaiters(
                entry,
                IllegalStateException(
                    "bindService failed: ${entry.cn} exported=${entry.serviceInfo.exported} stopped=$stopped",
                ),
            )
            return
        }

        val timeout = Runnable {
            val pending = synchronized(entry.lock) {
                entry.connecting = false
                val list = entry.waiters.toList()
                entry.waiters.clear()
                entry.bindTimeoutRunnable = null
                list
            }
            if (pending.isNotEmpty()) {
                pending.forEach { waiter ->
                    if (!waiter.isCompleted) {
                        waiter.resumeWithException(IllegalStateException("bindService timeout: ${entry.cn}"))
                    }
                }
                runCatching { entry.appCtx.unbindService(entry.connection) }
            }
        }
        synchronized(entry.lock) {
            entry.bindTimeoutRunnable?.let { poolHandler.removeCallbacks(it) }
            entry.bindTimeoutRunnable = timeout
        }
        poolHandler.postDelayed(timeout, bindTimeoutMs)
    }

    private fun invalidateEntry(entry: PooledConnection, error: Throwable) {
        synchronized(entry.lock) {
            entry.proxy = null
            entry.bound = false
            entry.connecting = false
            entry.bindTimeoutRunnable?.let { poolHandler.removeCallbacks(it) }
            entry.bindTimeoutRunnable = null
            entry.idleRunnable?.let { poolHandler.removeCallbacks(it) }
            entry.idleRunnable = null
        }
        poolHandler.post {
            runCatching { entry.appCtx.unbindService(entry.connection) }
            Log.w(TAG, "invalidateEntry: ${entry.cn} | ${error.message}")
        }
    }

    private fun failWaiters(entry: PooledConnection, error: Throwable) {
        val pending = synchronized(entry.lock) {
            entry.connecting = false
            entry.bindTimeoutRunnable?.let { poolHandler.removeCallbacks(it) }
            entry.bindTimeoutRunnable = null
            val list = entry.waiters.toList()
            entry.waiters.clear()
            list
        }
        pending.forEach { waiter ->
            if (!waiter.isCompleted) waiter.resumeWithException(error)
        }
    }

    private fun scheduleIdleUnbind(entry: PooledConnection) {
        val runnable = Runnable {
            val shouldUnbind = synchronized(entry.lock) {
                uptimeMillis() - entry.lastUsed >= IDLE_UNBIND_MS &&
                    entry.bound && entry.proxy != null && !entry.connecting
            }
            if (shouldUnbind) {
                runCatching { entry.appCtx.unbindService(entry.connection) }
                synchronized(entry.lock) {
                    entry.proxy = null
                    entry.bound = false
                }
            }
        }
        synchronized(entry.lock) {
            entry.idleRunnable?.let { poolHandler.removeCallbacks(it) }
            entry.idleRunnable = runnable
        }
        poolHandler.postDelayed(runnable, IDLE_UNBIND_MS)
    }

    private fun cancelIdleUnbindLocked(entry: PooledConnection) {
        entry.idleRunnable?.let { poolHandler.removeCallbacks(it) }
        entry.idleRunnable = null
    }

    private fun getOrCreateEntry(appCtx: Context, serviceInfo: ServiceInfo): PooledConnection {
        val cn = ComponentName(serviceInfo.packageName, serviceInfo.name)
        synchronized(poolLock) {
            return connectionPool.getOrPut(cn) { PooledConnection(appCtx, serviceInfo) }
        }
    }

    private class PooledConnection(
        val appCtx: Context,
        val serviceInfo: ServiceInfo,
    ) {
        val cn: ComponentName = ComponentName(serviceInfo.packageName, serviceInfo.name)
        val lock = Any()
        val waiters = ArrayList<CancellableContinuation<IDisguisedA11yPlugin>>()
        var proxy: IDisguisedA11yPlugin? = null
        var bound: Boolean = false
        var connecting: Boolean = false
        var lastUsed: Long = 0L
        var idleRunnable: Runnable? = null
        var bindTimeoutRunnable: Runnable? = null

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                Log.i(TAG, "onServiceConnected: $name")
                val proxy = IDisguisedA11yPlugin.Stub.asInterface(binder)
                val pending = synchronized(lock) {
                    this@PooledConnection.proxy = proxy
                    bound = true
                    connecting = false
                    lastUsed = uptimeMillis()
                    bindTimeoutRunnable?.let { poolHandler.removeCallbacks(it) }
                    bindTimeoutRunnable = null
                    val list = waiters.toList()
                    waiters.clear()
                    list
                }
                pending.forEach { waiter ->
                    if (!waiter.isCompleted) waiter.resume(proxy)
                }
            }

            override fun onServiceDisconnected(name: ComponentName) = handleDisconnect(name, "onServiceDisconnected")
            override fun onBindingDied(name: ComponentName) = handleDisconnect(name, "onBindingDied")
            override fun onNullBinding(name: ComponentName) = handleDisconnect(name, "onNullBinding")
        }

        private fun handleDisconnect(name: ComponentName, reason: String) {
            val pending = synchronized(lock) {
                proxy = null
                bound = false
                connecting = false
                bindTimeoutRunnable?.let { poolHandler.removeCallbacks(it) }
                bindTimeoutRunnable = null
                val list = waiters.toList()
                waiters.clear()
                list
            }
            if (pending.isNotEmpty()) {
                val error = IllegalStateException("$reason: $name")
                pending.forEach { waiter ->
                    if (!waiter.isCompleted) waiter.resumeWithException(error)
                }
            }
        }
    }
}
