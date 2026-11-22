package ru.krapiva.app.menu

import java.text.Normalizer
import java.util.Locale

internal fun normalized(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")

internal data class MenuChoice(val id: String, val title: String)
internal data class MenuStyle(val id: String, val family: String, val title: String, val aliases: Set<String>) {
    fun matchesSearch(query: String) = aliases.any { normalized(query) in it }
}

internal object MenuStyles {
    const val HOPPY = "hoppy-ales"
    const val BELGIAN = "belgian-ales"
    const val DARK = "dark-ales"
    const val SOUR = "sour-ales"

    val families = listOf(
        MenuChoice(HOPPY, "IPA и родственные хмелевые эли"),
        MenuChoice(BELGIAN, "Бельгийские эли"),
        MenuChoice(DARK, "Стауты и портеры"),
        MenuChoice(SOUR, "Кислые стили"),
    )

    private fun style(id: String, family: String, title: String, vararg aliases: String) =
        MenuStyle(id, family, title, (listOf(title) + aliases).map(::normalized).toSet())

    val styles = listOf(
        style("ipa", HOPPY, "IPA", "India Pale Ale", "Индийский пейл-эль", "ИПА"),
        style("american-ipa", HOPPY, "American IPA", "IPA - American", "Американский IPA"),
        style("english-ipa", HOPPY, "English IPA", "IPA - English", "Английский IPA"),
        style("west-coast-ipa", HOPPY, "West Coast IPA", "IPA - West Coast", "Вест-кост IPA"),
        style("apa", HOPPY, "APA", "American Pale Ale", "Pale Ale - American", "Американский пейл-эль", "АПА"),
        style("neipa", HOPPY, "NEIPA", "New England IPA", "IPA - New England", "IPA - New England / Hazy", "Hazy IPA", "Мутный IPA"),
        style("dipa", HOPPY, "DIPA", "Double IPA", "Imperial IPA", "IPA - Imperial / Double", "Двойной IPA"),
        style("hazy-dipa", HOPPY, "Hazy DIPA", "IPA - Imperial / Double New England / Hazy", "Double NEIPA", "Двойной NEIPA"),
        style("session-ipa", HOPPY, "Session IPA", "IPA - Session", "Сессионный IPA"),
        style("cold-ipa", HOPPY, "Cold IPA", "IPA - Cold", "Колд IPA"),
        style("triple-ipa", HOPPY, "Triple IPA", "IPA - Triple", "Тройной IPA"),
        style("black-ipa", HOPPY, "Black IPA", "IPA - Black / Cascadian Dark Ale", "Чёрный IPA"),
        style("white-ipa", HOPPY, "White IPA", "IPA - White / Wheat", "Белый IPA"),
        style("belgian-ipa", HOPPY, "Belgian IPA", "IPA - Belgian", "Бельгийский IPA"),
        style("belgian-blonde", BELGIAN, "Belgian Blonde", "Belgian Blonde Ale", "Belgian Blond", "Бельгийский блонд"),
        style("dubbel", BELGIAN, "Dubbel", "Belgian Dubbel", "Дуббель", "Дюббель"),
        style("tripel", BELGIAN, "Tripel", "Belgian Tripel", "Трипель"),
        style("quadrupel", BELGIAN, "Quadrupel", "Belgian Quadrupel", "Квадрюпель"),
        style("belgian-pale", BELGIAN, "Belgian Pale Ale", "Pale Ale - Belgian", "Бельгийский пейл-эль"),
        style("belgian-strong", BELGIAN, "Belgian Strong Ale", "Belgian Strong Golden Ale", "Belgian Strong Dark Ale", "Крепкий бельгийский эль"),
        style("saison", BELGIAN, "Saison", "Farmhouse Ale - Saison", "Сезон"),
        style("witbier", BELGIAN, "Witbier", "Belgian Wit", "Витбир"),
        style("stout", DARK, "Stout", "Стаут"),
        style("dry-stout", DARK, "Dry Stout", "Irish Dry Stout", "Stout - Irish Dry", "Сухой стаут"),
        style("milk-stout", DARK, "Milk Stout", "Stout - Milk / Sweet", "Молочный стаут"),
        style("oatmeal-stout", DARK, "Oatmeal Stout", "Stout - Oatmeal", "Овсяный стаут"),
        style("imperial-stout", DARK, "Imperial Stout", "Russian Imperial Stout", "Stout - Imperial / Double", "Имперский стаут"),
        style("pastry-stout", DARK, "Pastry Stout", "Stout - Pastry", "Десертный стаут"),
        style("porter", DARK, "Porter", "Портер"),
        style("english-porter", DARK, "English Porter", "Porter - English", "Английский портер"),
        style("baltic-porter", DARK, "Baltic Porter", "Porter - Baltic", "Балтийский портер"),
        style("american-porter", DARK, "American Porter", "Porter - American", "Американский портер"),
        style("sour", SOUR, "Sour", "Sour Ale", "Кислый эль"),
        style("fruit-sour", SOUR, "Fruit Sour", "Sour - Fruited", "Фруктовый сауэр"),
        style("gose", SOUR, "Gose", "Sour - Gose", "Sour - Traditional Gose", "Гозе"),
        style("lambic", SOUR, "Lambic", "Lambic - Traditional", "Ламбик"),
        style("gueuze", SOUR, "Gueuze", "Lambic - Gueuze", "Гёз"),
        style("kriek", SOUR, "Kriek", "Lambic - Kriek", "Крик"),
        style("berliner-weisse", SOUR, "Berliner Weisse", "Sour - Berliner Weisse", "Берлинер вайссе"),
        style("flanders-red", SOUR, "Flanders Red Ale", "Sour - Flanders Red Ale", "Фламандский красный эль"),
        style("oud-bruin", SOUR, "Oud Bruin", "Sour - Flanders Oud Bruin", "Ауд брюин"),
    )
    private val aliases = styles.flatMap { style -> style.aliases.map { it to style } }.toMap()

    fun classify(name: String?): MenuStyle? = name?.let { aliases[normalized(it)] }
    fun key(position: MenuPosition): String? =
        classify(position.style)?.id ?: position.styleId?.let { "catalog:$it" }
}
