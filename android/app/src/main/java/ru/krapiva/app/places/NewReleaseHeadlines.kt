package ru.krapiva.app.places

import android.content.Context
import ru.krapiva.app.R
import kotlin.random.Random

internal class NewReleaseHeadlines(context: Context) {
    private val phrases = context.resources.getStringArray(R.array.new_release_headlines).asList()
    private val preferences = context.getSharedPreferences("new_release_headlines", Context.MODE_PRIVATE)

    fun next(): String {
        var seed = preferences.getLong("seed", Random.nextLong())
        var index = preferences.getInt("index", 0)
        var order = phrases.shuffled(Random(seed))
        if (index !in order.indices) {
            val last = order.last()
            do {
                seed = Random.nextLong()
                order = phrases.shuffled(Random(seed))
            } while (order.first() == last)
            index = 0
        }
        preferences.edit().putLong("seed", seed).putInt("index", index + 1).apply()
        return order[index]
    }
}
