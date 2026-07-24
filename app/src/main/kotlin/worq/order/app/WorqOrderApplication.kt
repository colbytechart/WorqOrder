package worq.order.app

import android.app.Application

class WorqOrderApplication : Application() {
    val container: ApplicationContainer by lazy {
        DefaultApplicationContainer(this)
    }
}
