package ru.krapiva.app.badges

internal fun badgeCategory(metric:String):String=when(metric) {
    "checkins","notes"->"Мои записи"
    "positions"->"Коллекция"
    "styles"->"Стили"
    "breweries"->"Пивоварни"
    "photos"->"Фотоистории"
    "venues"->"Места"
    "local_attendance"->"Локальные значки"
    else->"Другие достижения"
}
internal fun badgeFraction(progress:Int,target:Int):Float=if(target<=0)0f else (progress.toFloat()/target).coerceIn(0f,1f)
