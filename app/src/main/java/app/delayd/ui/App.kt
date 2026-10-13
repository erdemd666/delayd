package app.delayd.ui

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import app.delayd.TripStore
import java.time.YearMonth

/**
 * Routes are plain strings in one saved back stack: "onboarding", "home", "settings", "trip/<id>", "claim/<id>",
 * "month/<yyyy-MM>". Five destinations don't need a navigation library, and BackHandler pops correctly.
 */
@SuppressLint("MutableCollectionMutableState") // ArrayList is what rememberSaveable can put in a Bundle; every change swaps in a copy
@Composable
fun DelaydApp(prefs: Prefs, deepLinkTripId: String?, onDeepLinkHandled: () -> Unit) {
    val trips by TripStore.trips.collectAsState()
    var stack by rememberSaveable { mutableStateOf(arrayListOf(if (prefs.onboarded) "home" else "onboarding")) }
    fun push(r: String) { stack = ArrayList(stack + r) }
    fun pop() { if (stack.size > 1) stack = ArrayList(stack.dropLast(1)) }
    fun reset(vararg r: String) { stack = arrayListOf(*r) }

    // A delay notification opens the trip on top of Home; before onboarding is done it waits.
    LaunchedEffect(deepLinkTripId, prefs.onboarded) {
        if (deepLinkTripId != null && prefs.onboarded) {
            reset("home", "trip/$deepLinkTripId"); onDeepLinkHandled()
        }
    }
    BackHandler(stack.size > 1) { pop() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val route = stack.last()
        val arg = route.substringAfter('/', "")
        when {
            route == "onboarding" -> OnboardingScreen(prefs) { prefs.finishOnboarding(); reset("home") }
            route == "settings" -> SettingsScreen(prefs, trips, onBack = ::pop)
            route == "add" -> AddRideScreen(onBack = ::pop, onOpenTrip = { id -> stack = ArrayList(stack.dropLast(1) + "trip/$id") })
            route.startsWith("trip/") -> TripDetailScreen(arg, trips, prefs.dTicket, ::pop,
                onClaim = { push(if (prefs.dTicket) "month/${trips.firstOrNull { it.id == arg }?.month() ?: YearMonth.now(ZONE)}" else "claim/$arg") })
            route.startsWith("claim/") -> ClaimScreen(arg, trips, onBack = ::pop, onDone = ::pop)
            route.startsWith("month/") -> MonthClaimScreen(YearMonth.parse(arg), trips, onBack = ::pop, onDone = ::pop)
            else -> HomeScreen(prefs, trips,
                onTrip = { push("trip/${it.id}") }, onSettings = { push("settings") },
                onClaimMonth = { push("month/$it") }, onAdd = { push("add") })
        }
    }
}
