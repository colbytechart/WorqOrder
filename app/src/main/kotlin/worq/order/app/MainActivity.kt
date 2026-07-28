package worq.order.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WorqOrderRoot()
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            try {
                (application as WorqOrderApplication)
                    .container
                    .timerRecoveryCoordinator
                    .recover()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Main retries and presents the recoverable local-data error. Never crash or
                // alter the persisted active interval because foreground recovery failed.
            }
        }
    }
}
