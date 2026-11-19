package app.katib.ui

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.viewModels
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.katib.R
import app.katib.data.Prefs

class MainActivity : FragmentActivity() {
    private val vm: AppVm by viewModels()
    private val locked = mutableStateOf(true)
    private var backgroundedAt = 0L
    private val lockAfterMs = 60_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Hide content from the app switcher and block screenshots.
        // Debug builds only: a marker file (created via `adb shell run-as`) allows screenshots for UI review.
        val allowScreens = app.katib.BuildConfig.DEBUG && java.io.File(filesDir, "test/allow_screens").exists()
        if (!allowScreens) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        locked.value = Prefs(this).lockEnabled
        // Seal each finished recording with the user's biometric-gated hardware key.
        lifecycleScopeLaunchSeals()
        setContent {
            KatibTheme {
                Surface(Modifier.fillMaxSize(), color = E.c.paper) {
                    if (locked.value) LockScreen { authenticate() } else KatibNav()
                }
            }
        }
    }

    private fun lifecycleScopeLaunchSeals() {
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                app.katib.audio.RecorderService.sealRequests.collect { id ->
                    app.katib.audio.RecorderService.sealRequests.resetReplayCache()
                    // Wait briefly for the recorder to persist the audio hash.
                    val manual = id < 0          // negative id = user tapped "Seal now"/"Sign note"
                    val mid = kotlin.math.abs(id)
                    if (!manual && vm.isNote(mid)) return@collect   // personal notes: signing is optional, no prompt
                    var payload: String? = null
                    repeat(20) { if (payload == null) { payload = vm.capturePayload(mid); if (payload == null) kotlinx.coroutines.delay(250) } }
                    payload?.let { sealWithBiometric(mid, it) }
                }
            }
        }
    }

    private fun sealWithBiometric(meetingId: Long, payload: String) {
        val sig = try { app.katib.security.Provenance.userSignature(this) } catch (t: Throwable) {
            android.util.Log.e("KatibProvenance", "user key unavailable", t); return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val s = result.cryptoObject?.signature ?: return
                    s.update(payload.toByteArray())
                    vm.storeCaptureSeal(meetingId, payload, s.sign())
                    android.widget.Toast.makeText(this@MainActivity, getString(R.string.sealed_toast), android.widget.Toast.LENGTH_SHORT).show()
                }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.seal_title))
            .setSubtitle(getString(R.string.seal_subtitle))
            .apply {
                if (android.os.Build.VERSION.SDK_INT >= 30) setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                else { setAllowedAuthenticators(BIOMETRIC_STRONG); setNegativeButtonText(getString(R.string.cancel)) }
            }.build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(sig))
    }

    override fun onStart() {
        super.onStart()
        if (backgroundedAt > 0 && SystemClock.elapsedRealtime() - backgroundedAt > lockAfterMs && Prefs(this).lockEnabled)
            locked.value = true
        if (locked.value) authenticate()
    }

    override fun onStop() { super.onStop(); backgroundedAt = SystemClock.elapsedRealtime() }

    private var prompting = false
    private fun authenticate() {
        if (prompting) return
        prompting = true
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false; locked.value = false
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    prompting = false
                    // No lock screen configured on device: don't brick the app.
                    if (errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL ||
                        errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT) locked.value = false
                }
            })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.unlock))
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build())
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(Space.xl), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        EngravedLogo(Modifier.size(168.dp), animate = true)
        Spacer(Modifier.height(Space.l))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(Space.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(14.dp), tint = E.c.muted)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.locked).uppercase(), style = labelStyle)
        }
        Spacer(Modifier.height(Space.xl))
        KButton(onClick = onUnlock) { Text(stringResource(R.string.unlock)) }
    }
}

private data class Tab(val route: String, val label: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private val tabs = listOf(
    Tab("record", R.string.tab_record, Icons.Outlined.Mic),
    Tab("library", R.string.tab_library, Icons.Outlined.LibraryBooks),
    Tab("search", R.string.tab_search, Icons.Outlined.Search),
    Tab("people", R.string.tab_people, Icons.Outlined.People),
)

@Composable
fun KatibNav() {
    val vm: AppVm = viewModel()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(
        containerColor = E.c.paper,
        bottomBar = {
            if (tabs.any { it.route == route }) {
                Column {
                    HorizontalDivider(color = E.c.line)
                    NavigationBar(containerColor = E.c.paper, tonalElevation = 0.dp) {
                        tabs.forEach { t ->
                            NavigationBarItem(
                                selected = route == t.route,
                                onClick = {
                                    nav.navigate(t.route) {
                                        popUpTo("record") { saveState = true }; launchSingleTop = true; restoreState = true
                                    }
                                },
                                icon = {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(t.icon, null)
                                        Spacer(Modifier.height(Space.xs))
                                        // Selected tab: a short ink rule under the icon instead of Material's pill.
                                        Box(Modifier.width(if (route == t.route) 16.dp else 0.dp).height(2.dp).background(E.c.ink))
                                    }
                                },
                                label = { Text(stringResource(t.label).uppercase(), style = labelStyle.copy(color = androidx.compose.ui.graphics.Color.Unspecified, fontSize = 10.sp)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = E.c.ink, indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                    selectedTextColor = E.c.ink, unselectedIconColor = E.c.muted, unselectedTextColor = E.c.muted,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { pad ->
        NavHost(nav, "record", Modifier.padding(pad).background(E.c.paper)) {
            composable("record") { RecordScreen(vm, onSettings = { nav.navigate("settings") }, onOpen = { nav.navigate("meeting/$it") }) }
            composable("library") { LibraryScreen(vm, onOpen = { nav.navigate("meeting/$it") }, onSettings = { nav.navigate("settings") }) }
            composable("search") { SearchScreen(vm, onOpen = { id, t -> nav.navigate("meeting/$id?t=$t") }) }
            composable("people") { PeopleScreen(vm) }
            composable("settings") { SettingsScreen(vm, onBack = { nav.popBackStack() }) }
            composable(
                "meeting/{id}?t={t}",
                arguments = listOf(navArgument("id") { type = NavType.LongType },
                    navArgument("t") { type = NavType.FloatType; defaultValue = -1f }),
            ) { e ->
                MeetingScreen(vm, e.arguments!!.getLong("id"), e.arguments!!.getFloat("t"), onBack = { nav.popBackStack() })
            }
        }
    }
}
