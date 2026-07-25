package worq.order.ui.settings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import worq.order.app.resolveDarkTheme
import worq.order.data.ThemeMode
import worq.order.testing.FakeSettingsRepository
import worq.order.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class ApplicationSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun themeFlowChangesWithoutRecreatingViewModel() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeSettingsRepository()
            val viewModel = ApplicationSettingsViewModel(repository)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.themeMode.collect()
            }
            runCurrent()
            assertEquals(ThemeMode.SYSTEM, viewModel.themeMode.value)

            repository.setThemeMode(ThemeMode.LIGHT)
            runCurrent()
            assertEquals(ThemeMode.LIGHT, viewModel.themeMode.value)

            repository.setThemeMode(ThemeMode.DARK)
            runCurrent()
            assertEquals(ThemeMode.DARK, viewModel.themeMode.value)

            repository.setThemeMode(ThemeMode.SYSTEM)
            runCurrent()
            assertEquals(ThemeMode.SYSTEM, viewModel.themeMode.value)
        }

    @Test
    fun systemModeFollowsDeviceWhileExplicitModesOverrideIt() {
        assertEquals(false, resolveDarkTheme(ThemeMode.SYSTEM, false))
        assertEquals(true, resolveDarkTheme(ThemeMode.SYSTEM, true))
        assertEquals(false, resolveDarkTheme(ThemeMode.LIGHT, true))
        assertEquals(true, resolveDarkTheme(ThemeMode.DARK, false))
    }
}
