package me.huizengek.autopickup.service

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.huizengek.autopickup.R
import me.huizengek.autopickup.preferences.PhonePreferences
import me.huizengek.autopickup.preferences.TilePreferences
import me.huizengek.autopickup.util.isAtLeastAndroid10
import me.huizengek.autopickup.util.isAtLeastAndroid11
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class StatusTileService : TileService() {
    private val flow = PhonePreferences.enabledProperty.stateFlow.asSharedFlow()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    @get:Synchronized
    @set:Synchronized
    private var job: Job? = null

    companion object {
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        suspend fun requestAddService(context: Context) = with(context) {
            getSystemService<StatusBarManager>()?.requestAddTileService<StatusTileService>(
                label = R.string.enable,
                icon = R.drawable.baseline_speaker_phone_24
            )
        }
    }

    private fun update(enabled: Boolean) = qsTile.apply {
        state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE

        val stateDesc = getString(if (enabled) R.string.on else R.string.off)
        if (isAtLeastAndroid10) subtitle = stateDesc
        if (isAtLeastAndroid11) stateDescription = stateDesc
    }.updateTile()


    override fun onStartListening() {
        super.onStartListening()

        update(PhonePreferences.enabled)
        coroutineScope.launch {
            flow.collectLatest {
                update(it)
            }
        }
    }

    override fun onClick() {
        super.onClick()

        job?.cancel()
        job = coroutineScope.launch {
            if (TilePreferences.shouldUnlock) awaitUnlockOrSuspend()
            PhonePreferences.enabled = !PhonePreferences.enabled
        }
    }
}

suspend fun TileService.awaitUnlockOrSuspend() = suspendCancellableCoroutine { continuation ->
    var shouldResume = true

    unlockAndRun {
        if (!shouldResume) return@unlockAndRun

        shouldResume = false
        continuation.resume(Unit)
    }

    continuation.invokeOnCancellation {
        shouldResume = false
    }
}

@PublishedApi
internal val pool = Executors.newCachedThreadPool()

@PublishedApi
internal val coroutineScope = CoroutineScope(
    pool.asCoroutineDispatcher() + CoroutineName("System status bar util")
)

@Suppress("MemberVisibilityCanBePrivate")
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@JvmInline
value class AddTileServiceResult private constructor(private val code: Int) {
    companion object {
        val NotAdded =
            AddTileServiceResult(StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED)
        val AlreadyAdded =
            AddTileServiceResult(StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED)
        val Added =
            AddTileServiceResult(StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED)
        val MismatchedPackage =
            AddTileServiceResult(StatusBarManager.TILE_ADD_REQUEST_ERROR_MISMATCHED_PACKAGE)
        val InProgress =
            AddTileServiceResult(StatusBarManager.TILE_ADD_REQUEST_ERROR_REQUEST_IN_PROGRESS)
        val BadComponent =
            AddTileServiceResult(StatusBarManager.TILE_ADD_REQUEST_ERROR_BAD_COMPONENT)
        val NotCurrentUser =
            AddTileServiceResult(StatusBarManager.TILE_ADD_REQUEST_ERROR_NOT_CURRENT_USER)
        val AppNotInForeground =
            AddTileServiceResult(StatusBarManager.TILE_ADD_REQUEST_ERROR_APP_NOT_IN_FOREGROUND)
        val NoStatusBarService =
            AddTileServiceResult(StatusBarManager.TILE_ADD_REQUEST_ERROR_NO_STATUS_BAR_SERVICE)

        fun from(code: Int) = when (code) {
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> NotAdded
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> AlreadyAdded
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> Added
            StatusBarManager.TILE_ADD_REQUEST_ERROR_MISMATCHED_PACKAGE -> MismatchedPackage
            StatusBarManager.TILE_ADD_REQUEST_ERROR_REQUEST_IN_PROGRESS -> InProgress
            StatusBarManager.TILE_ADD_REQUEST_ERROR_BAD_COMPONENT -> BadComponent
            StatusBarManager.TILE_ADD_REQUEST_ERROR_NOT_CURRENT_USER -> NotCurrentUser
            StatusBarManager.TILE_ADD_REQUEST_ERROR_APP_NOT_IN_FOREGROUND -> AppNotInForeground
            StatusBarManager.TILE_ADD_REQUEST_ERROR_NO_STATUS_BAR_SERVICE -> NoStatusBarService

            else -> null
        }
    }

    val isAdded get() = this == Added || this == AlreadyAdded
}

context(Context)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
suspend inline fun <reified T : Any> StatusBarManager.requestAddTileService(
    @StringRes label: Int,
    @DrawableRes icon: Int
) = withContext(coroutineScope.coroutineContext) {
    suspendCoroutine<AddTileServiceResult?> { continuation ->
        runCatching {
            requestAddTileService(
                ComponentName(this@Context, T::class.java),
                getString(label),
                Icon.createWithResource(this@Context, icon),
                pool
            ) { result ->
                val ret = AddTileServiceResult.from(result)
                if (ret != AddTileServiceResult.InProgress) continuation.resume(ret)
            }
        }.onFailure {
            continuation.resumeWithException(it)
        }
    }
}
