package ru.krapiva.app.menu

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import ru.krapiva.app.diary.objects
import ru.krapiva.app.offline.AdvisorMode
import ru.krapiva.app.offline.LocalDiscovery
import java.io.File
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URI
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime

/** Real loopback Fastify/PostgreSQL and production Kotlin decoders/ranking; no Android UI required. */
class SmartBarJourneyTest {
    @Test(timeout = 90_000) fun twoMenusThroughCheckinAndAdvisorRefresh() {
        val backend = System.getenv("KRAPIVA_SMART_BAR_TEST_BACKEND")
        assumeTrue("Requires the built isolated backend", !backend.isNullOrBlank())
        val server = ProcessBuilder("node", "dist/test/smart-bar-stand.js").directory(File(backend!!))
            .redirectError(ProcessBuilder.Redirect.INHERIT).start()
        try {
            val client = Client(checkNotNull(server.inputStream.bufferedReader().readLine()) { "Test stand failed to start" })
            runJourney(client)
        } finally {
            server.outputStream.close()
            assertTrue("Stand must close its synthetic database", server.waitFor(15, TimeUnit.SECONDS))
            assertEquals(0, server.exitValue())
        }
    }

    private class Client(val base: String) {
        var token: String? = null
        var requests = 0
        fun call(path: String, body: JSONObject? = null, expected: Int = 200): JSONObject {
            val url = URI(base).resolve(path)
            require(url.host == "127.0.0.1" && url.scheme == "http")
            val connection = url.toURL().openConnection(Proxy.NO_PROXY) as HttpURLConnection
            return try {
                requests++
                connection.connectTimeout = 5_000
                connection.readTimeout = 10_000
                token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    if (path == "/v1/checkins") connection.setRequestProperty("Idempotency-Key", UUID.randomUUID().toString())
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                assertEquals(path, expected, connection.responseCode)
                JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
            }
        }
        fun menu(venue: String) = requireNotNull(BarMenu.read(call("/v1/venues/$venue/menu")))
        fun history() = readAdvisorCheckins(call("/v1/me/advisor-history"))
    }

    private fun runJourney(client: Client) {
        client.token = client.call("/v1/auth/login", JSONObject().put("email", "beta@example.invalid")
            .put("password", "Isolated Smart Bar QA!")).getString("accessToken")
        val map = client.call("/v1/venues/map?bbox=37.5,55.6,37.8,55.9").getJSONArray("items").objects()
            .associate { it.getString("name") to it.getString("id") }
        val first = map.getValue("A")
        val second = map.getValue("B")
        assertEquals(first, client.call("/v1/venues/$first").getString("id"))
        val baseline = client.requests
        val start = System.nanoTime()
        val menu = client.menu(first)
        val readMenu = System.nanoTime()
        val history = client.history()
        println("Loopback HTTP + decode: menu=${(readMenu - start) / 1_000} us, history=${(System.nanoTime() - readMenu) / 1_000} us")
        val taste = LocalDiscovery.advisorProfile(history)
        assertEquals(100, taste.historySize)
        assertEquals(250, menu.items.size)
        val themes = themePresets.associate { it.name to filterMenu(menu.items, it.filter) }
        assertEquals(mapOf("Тихая суббота" to 51, "Хмелевой маршрут" to 97, "Тёмная сторона" to 51,
            "Кислая экспедиция" to 51, "Охота за новым" to 150), themes.mapValues { it.value.size })
        assertEquals(100, filterMenu(menu.items, MenuFilter(tried = Tried.YES)).size)

        val rule = MenuFilter(styles = setOf("ipa", "apa"), tried = Tried.NO, abv = MenuRange("4", "8"),
            volumeMl = MenuRange("250", "330"), sort = MenuSort.RATING)
        val preset = MenuPresetBook().save("qa-ipa", "Мои IPA", rule, Instant.EPOCH).use("qa-ipa")
        val restored = MenuFilter.decode(JSONObject(preset.filter("qa-ipa")!!.encode().toString()))
        assertEquals(rule, restored)
        val oldFormat = rule.encode().apply { remove("volumeMin"); remove("volumeMax") }
        assertEquals(rule.copy(volumeMl = MenuRange()), MenuFilter.decode(oldFormat))
        val matching = filterMenu(menu.items, restored)
        assertEquals(48, matching.size)
        val selected = matching.take(4).map(MenuPosition::positionId)
        for (count in 2..4) {
            val compared = comparisonPositions(selected.take(count), menu.items)
            assertEquals(selected.take(count), compared.map(MenuPosition::positionId))
            assertTrue(comparisonFields(compared).all { it.values.size == count })
        }
        val candidates = menu.items.filter(MenuPosition::available).map(MenuPosition::advisorCandidate)
        val recommendations = LocalDiscovery.advise(candidates, taste, AdvisorMode.NEW)
        val picked = recommendations.first()
        assertTrue(picked.explanation.isNotEmpty())
        assertFalse(picked.tried)
        assertEquals(150, recommendations.size)
        assertEquals(2, client.requests - baseline)
        val other = client.menu(second)
        assertEquals(5, filterMenu(other.items, restored).size)
        assertTrue(comparisonPositions(selected, other.items).isEmpty())
        val id = picked.candidate.card.id
        assertEquals(id, client.call("/v1/positions/$id").getString("canonicalId"))
        val written = client.call("/v1/checkins", JSONObject().put("positionId", id).put("venueId", first)
            .put("visibility", "private").put("body", "").put("ratingQuarters", 17)
            .put("occurredAt", Instant.ofEpochMilli(System.currentTimeMillis()).toString()), 201)
        assertEquals(first, written.getString("venueId"))
        val updated = client.menu(first)
        assertTrue(updated.items.single { it.positionId == id }.tried)
        val fresh = LocalDiscovery.advisorProfile(client.history())
        assertEquals(100, fresh.historySize)
        assertTrue(id in fresh.triedIds)
        val newChoices = LocalDiscovery.advise(updated.items.map(MenuPosition::advisorCandidate), fresh, AdvisorMode.NEW)
        assertEquals(149, newChoices.size)
        assertFalse(newChoices.any { it.candidate.card.id == id })

        fun timings(action: () -> Unit): String {
            repeat(5) { action() }
            val samples = List(40) { measureNanoTime(action) / 1_000 }.sorted()
            return "median=${samples[19]} us, p95=${samples[37]} us"
        }
        println("250 offers: filter " + timings { filterMenu(menu.items, restored) })
        println("250 offers / 100 checkins: profile + ranking " + timings {
            LocalDiscovery.advise(candidates, LocalDiscovery.advisorProfile(history), AdvisorMode.MY_CHOICE)
        })
    }
}
