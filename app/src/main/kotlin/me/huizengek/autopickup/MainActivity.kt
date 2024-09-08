package me.huizengek.autopickup

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.StrictMode
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import me.huizengek.autopickup.service.ListenerService
import me.huizengek.autopickup.ui.AutopickupTheme
import me.huizengek.autopickup.ui.screens.home.HomeScreen
import me.huizengek.autopickup.util.intent
import me.huizengek.autopickup.util.isAtLeastAndroid12

class AutoPickupApplication : Application() {
    override fun onCreate() {
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .let {
                    if (isAtLeastAndroid12) it.detectUnsafeIntentLaunch() else it
                }
                .penaltyLog()
                .penaltyDeath()
                .build()
        )

        super.onCreate()
        Dependencies.init()
    }
}

object Dependencies {
    internal lateinit var application: Application
        private set

    context(AutoPickupApplication)
    internal fun init() {
        application = this@AutoPickupApplication
    }
}

// ViewModel in order to avoid recreating the entire service (connection)
class MainViewModel : ViewModel() {
    var binder: ListenerService.Binder? by mutableStateOf(null)

    suspend fun awaitBinder(): ListenerService.Binder =
        binder ?: snapshotFlow { binder }.filterNotNull().first()
}

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is ListenerService.Binder) vm.binder = service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            vm.binder = null
            // Try to rebind, otherwise fail
            unbindService(this)
            bind()
        }
    }

    private fun bind() {
        startForegroundService(intent<ListenerService>())
        bindService(intent<ListenerService>(), serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStart() {
        super.onStart()

        bind()
    }

    override fun onStop() {
        unbindService(serviceConnection)

        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent()
    }

    @Composable
    fun AppWrapper(
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) = AutopickupTheme {
        Surface(
            modifier = modifier
                .fillMaxSize()
                .imePadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            content()
        }
    }

    private fun setContent() = setContent {
        AppWrapper {
            CompositionLocalProvider(
                LocalListenerServiceBinder provides vm.binder
            ) {
                HomeScreen()
            }
        }
    }
}

val LocalListenerServiceBinder = staticCompositionLocalOf<ListenerService.Binder?> { null }
