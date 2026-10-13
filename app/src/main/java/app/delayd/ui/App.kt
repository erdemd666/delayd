package app.delayd.ui

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import app.delayd.ClaimStatus
import app.delayd.TripStore
import java.time.YearMonth

/** A delay notification target; [claim] = open the claim straight away. */
data class DeepLink(val tripId: String, val claim: Boolean)

/**
 * Routes are plain strings in one saved back stack: "onboarding", "home", "settings", "trip/<id>", "claim/<id>",
 * "month/<yyyy-MM>". Five destinations don't need a navigation library, and BackHandler pops correctly.
 */
@SuppressLint("MutableCollectionMutableState") // ArrayList is what rememberSaveable can put in a Bundle; every change swaps in a copy
@Composable
fun DelaydApp(prefs: Prefs, deepLink: DeepLink?, onDeepLinkHandled: () -> Unit) {
    val trips by TripStore.trips.collectAsState()
    var stack by rememberSaveable { mutableStateOf(arrayListOf(if (prefs.onboarded) "home" else "onboarding")) }
    fun push(r: String) { stack = ArrayList(stack + r) }
    fun pop() { if (stack.size > 1) stack = ArrayList(stack.dropLast(1)) }
    fun reset(vararg r: String) { stack = arrayListOf(*r) }

    // A delay notification opens the trip on top of Home (its "Claim now" action goes on into the claim); before onboarding is done it waits.
    LaunchedEffect(deepLink, prefs.onboarded) {
        if (deepLink != null && prefs.onboarded) {
            val trip = trips.firstOrNull { it.id == deepLink.tripId }
            val routes = if (deepLink.claim && trip?.status == ClaimStatus.OPEN) listOf("trip/${trip.id}", claimRoute(trip, prefs.dTicket)) else listOf("trip/${deepLink.tripId}")
            // Pushed onto whatever the user is looking at, so Back returns there; routes already on top are not repeated.
            routes.drop(routes.indexOf(stack.last()) + 1).forEach(::push)
            onDeepLinkHandled()
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
                onClaim = { push(trips.firstOrNull { it.id == arg }?.let { claimRoute(it, prefs.dTicket) } ?: "month/${YearMonth.now(ZONE)}") })
            route.startsWith("claim/") -> ClaimScreen(arg, trips, prefs, onBack = ::pop, onDone = ::pop)
            route.startsWith("month/") -> MonthClaimScreen(YearMonth.parse(arg), trips, onBack = ::pop, onDone = ::pop)
            else -> HomeScreen(prefs, trips,
                onTrip = { push("trip/${it.id}") }, onSettings = { push("settings") },
                onClaimMonth = { push("month/$it") }, onAdd = { push("add") })
        }
    }
}
