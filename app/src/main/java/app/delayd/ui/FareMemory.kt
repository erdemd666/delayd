package app.delayd.ui

/** Last ticket price per route, newest first, at most [MAX] routes, encoded as one string: "from→to\tcents" per line. */
object FareMemory {
    const val MAX = 20

    private fun entries(s: String?) = s.orEmpty().lines().mapNotNull { l ->
        val (route, ct) = l.split('\t').takeIf { it.size == 2 } ?: return@mapNotNull null
        ct.toIntOrNull()?.let { route to it }
    }

    private fun route(from: String, to: String) = "${from.trim()}→${to.trim()}"

    fun lookup(s: String?, from: String, to: String): Int? = entries(s).firstOrNull { it.first == route(from, to) }?.second

    fun remember(s: String?, from: String, to: String, ct: Int): String =
        (listOf(route(from, to) to ct) + entries(s).filter { it.first != route(from, to) }).take(MAX).joinToString("\n") { "${it.first}\t${it.second}" }
}
