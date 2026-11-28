package ru.krapiva.app.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ClubDestination(val label: String, val icon: ClubIcon) {
    HOME("Главная", ClubIcon.HOME), CATALOG("Поиск", ClubIcon.SEARCH),
    COLLECTION("Моё пиво", ClubIcon.BOTTLE), FEED("Лента", ClubIcon.FEED), PETS("Питомец", ClubIcon.LEAF), PROFILE("Профиль", ClubIcon.PERSON)
}
enum class ClubIcon { HOME, CATALOG, BOTTLE, FEED, PERSON, PEOPLE, GLASS, LEAF, PLUS, HEART, COMMENT, REFRESH, CHEVRON, SETTINGS, SEARCH, SCAN, BOOKMARK, CHECK, MORE }

private val settingsIcon = PathParser().parsePathString(
    "M19.43,12.98c.04,-.32 .07,-.64 .07,-.98s-.03,-.66 -.08,-.98l2.11,-1.65c.19,-.15 .24,-.42 .12,-.64l-2,-3.46c-.12,-.22 -.39,-.31 -.61,-.22l-2.49,1c-.52,-.4 -1.07,-.73 -1.68,-.98l-.38,-2.65C14.46,2.18 14.25,2 14,2h-4c-.25,0 -.45,.18 -.49,.42l-.38,2.65c-.61,.25 -1.17,.59 -1.68,.98l-2.49,-1c-.23,-.09 -.49,0 -.61,.22l-2,3.46c-.13,.22 -.07,.49 .12,.64l2.11,1.65c-.05,.32 -.09,.65 -.09,.98s.04,.66 .09,.98l-2.11,1.65c-.19,.15 -.24,.42 -.12,.64l2,3.46c.12,.22 .39,.31 .61,.22l2.49,-1c.52,.4 1.07,.73 1.68,.98l.38,2.65c.04,.24 .24,.42 .49,.42h4c.25,0 .46,-.18 .49,-.42l.38,-2.65c.61,-.25 1.17,-.58 1.68,-.98l2.49,1c.23,.09 .49,0 .61,-.22l2,-3.46c.12,-.22 .07,-.49 -.12,-.64zM12,15.5a3.5,3.5 0,1 1,0 -7,3.5 3.5,0 0,1 0,7z"
).toPath().apply { fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd }

/** One icon vocabulary, drawn locally; no remote icon/font dependency. */
@Composable
fun ClubGlyph(icon: ClubIcon, modifier: Modifier = Modifier.size(24.dp), color: Color = MaterialTheme.colorScheme.onSurface, description: String? = null) {
    Canvas(if (description == null) modifier else modifier.semantics { contentDescription = description }) {
        withTransform({ scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) }) {
            val stroke = Stroke(1.7f, cap = StrokeCap.Round)
            fun line(x: Float, y: Float, a: Float, b: Float) = drawLine(color, Offset(x,y), Offset(a,b), 1.7f, StrokeCap.Round)
            fun path(vararg points: Float) { drawPath(Path().apply { moveTo(points[0],points[1]); for(i in 2 until points.size step 2) lineTo(points[i],points[i+1]) },color,style=stroke) }
            when(icon) {
                ClubIcon.SEARCH -> {drawCircle(color,7f,Offset(10f,10f),style=stroke);line(15f,15f,22f,22f)}
                ClubIcon.SCAN -> {path(2f,8f,2f,2f,8f,2f);path(16f,2f,22f,2f,22f,8f);path(2f,16f,2f,22f,8f,22f);path(16f,22f,22f,22f,22f,16f);for(x in listOf(6f,9f,13f,16f,18f))line(x,7f,x,17f)}
                ClubIcon.BOOKMARK -> path(5f,22f,5f,3f,19f,3f,19f,22f,12f,17f,5f,22f)
                ClubIcon.CHECK -> path(4f,12f,10f,18f,21f,5f)
                ClubIcon.MORE -> for(x in listOf(4f,12f,20f))drawCircle(color,1.5f,Offset(x,12f))

                ClubIcon.HOME -> path(3f,10f,12f,3f,21f,10f,21f,21f,15f,21f,15f,15f,9f,15f,9f,21f,3f,21f,3f,10f)
                ClubIcon.CATALOG -> for(x in listOf(3f,14f)) for(y in listOf(3f,14f)) drawRoundRect(color,Offset(x,y),Size(7f,7f),androidx.compose.ui.geometry.CornerRadius(2f),style=stroke)
                ClubIcon.BOTTLE -> path(10f,2f,14f,2f,14f,8f,18f,12f,18f,22f,6f,22f,6f,12f,10f,8f,10f,2f)
                ClubIcon.FEED -> { drawRoundRect(color,Offset(3f,4f),Size(18f,5f),androidx.compose.ui.geometry.CornerRadius(2f),style=stroke);line(3f,14f,17f,14f);line(3f,20f,21f,20f) }
                ClubIcon.PERSON -> { drawCircle(color,4f,Offset(12f,7f),style=stroke);drawRoundRect(color,Offset(4f,15f),Size(16f,7f),androidx.compose.ui.geometry.CornerRadius(3f),style=stroke) }
                ClubIcon.PEOPLE -> { drawCircle(color,3.2f,Offset(9f,6f),style=stroke);drawCircle(color,2.6f,Offset(18f,9f),style=stroke);drawArc(color,180f,180f,false,Offset(2f,13f),Size(14f,15f),style=stroke);drawArc(color,200f,155f,false,Offset(14f,15f),Size(9f,11f),style=stroke) }
                ClubIcon.GLASS -> { path(4f,5f,16f,5f,15f,22f,5f,22f,4f,5f);path(16f,7f,21f,7f,21f,15f,16f,16f);drawPath(Path().apply{moveTo(4f,5f);cubicTo(1f,0f,8f,0f,9f,3f);cubicTo(12f,0f,18f,2f,16f,5f)},color,style=stroke);line(8f,9f,8f,18f);line(12f,9f,12f,18f) }
                ClubIcon.LEAF -> { path(3f,22f,20f,3f);path(5f,18f,4f,10f,7f,13f,8f,5f,11f,9f,15f,3f,20f,3f,19f,10f,14f,12f,17f,13f,10f,17f,12f,18f,5f,20f);line(8f,16f,8f,11f);line(11f,13f,16f,11f) }
                ClubIcon.PLUS -> {line(12f,4f,12f,20f);line(4f,12f,20f,12f)}
                ClubIcon.HEART -> drawPath(Path().apply {moveTo(12f,21f);cubicTo(-7f,10f,5f,-3f,12f,6f);cubicTo(19f,-3f,31f,10f,12f,21f)},color,style=stroke)
                ClubIcon.COMMENT -> drawPath(Path().apply {moveTo(5f,18f);cubicTo(-4f,5f,6f,1f,12f,2f);cubicTo(26f,1f,26f,19f,12f,20f);lineTo(3f,23f);close()},color,style=stroke)
                ClubIcon.REFRESH -> {drawArc(color,40f,285f,false,Offset(4f,4f),Size(16f,16f),style=stroke);path(15f,2f,21f,5f,17f,9f)}
                ClubIcon.CHEVRON -> path(9f,5f,16f,12f,9f,19f)
                ClubIcon.SETTINGS -> drawPath(settingsIcon, color)
            }
        }
    }
}

@Composable
fun ClubNavigation(selected: ClubDestination, enabled: Boolean = true, onSelect: (ClubDestination) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, border = BorderStroke(0.5.dp, KraPivaColors.Border)) {
        Row(Modifier.fillMaxWidth().testTag("club-navigation").padding(vertical = 8.dp)) {
            ClubDestination.entries.forEach { destination ->
                val active = selected == destination
                val color = if(active) KraPivaColors.Amber else KraPivaColors.Cream
                Column(Modifier.weight(1f).heightIn(min = 52.dp).selectable(active,enabled = enabled,role = Role.Tab,onClick = {onSelect(destination)})
                    .testTag("nav-${destination.name}"),horizontalAlignment = Alignment.CenterHorizontally,verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ClubGlyph(destination.icon,Modifier.size(25.dp),color)
                    Text(destination.label,color = color,fontSize = 11.sp,lineHeight = 16.sp,fontWeight = if(active)FontWeight.SemiBold else FontWeight.Normal,maxLines = 2)
                }
            }
        }
    }
}

@Composable
fun ClubCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier,shape = ClubTokens.CardShape,color = KraPivaColors.Surface,border = BorderStroke(1.dp,KraPivaColors.Border)) {
        Column(Modifier.padding(12.dp),verticalArrangement = Arrangement.spacedBy(10.dp),content = content)
    }
}
@Composable
fun AmberAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick,modifier.heightIn(min = 48.dp),enabled = enabled,shape = ClubTokens.ControlShape,
        colors = ButtonDefaults.buttonColors(containerColor = ClubTokens.Amber,contentColor = ClubTokens.OnAmber),contentPadding = PaddingValues(horizontal = 16.dp,vertical = 10.dp)) {
        ClubGlyph(ClubIcon.PLUS,Modifier.size(18.dp),ClubTokens.OnAmber)
        Spacer(Modifier.width(6.dp));Text(label,style = MaterialTheme.typography.labelLarge)
    }
}
@Composable
fun ClubBrand() {
    Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("KraPiva",style = MaterialTheme.typography.displaySmall,fontSize = 32.sp / androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f),maxLines = 1)
        ClubGlyph(ClubIcon.LEAF,Modifier.size(32.dp),KraPivaColors.Amber)
    }
}
