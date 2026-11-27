package ru.krapiva.app.pets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import ru.krapiva.app.account.AccountApi

private const val SceneSize = 360f

internal data class RoomArea(val x: Float, val y: Float, val width: Float, val height: Float) {
    fun modifier(scope: BoxWithConstraintsScope) = with(scope) {
        Modifier.offset(maxWidth * (x / SceneSize), maxHeight * (y / SceneSize))
            .size(maxWidth * (width / SceneSize), maxHeight * (height / SceneSize))
    }
}

private val shelfArea = RoomArea(70f, 48f, 202f, 113f)
private val wardrobeArea = RoomArea(278f, 116f, 74f, 170f)
private val foodArea = RoomArea(9f, 219f, 97f, 101f)

// Furnishing artwork is contained inside the opening and aligned with its supporting surface.
internal val roomDecorAreas = mapOf(
    "shelf_slot_01" to RoomArea(84f, 59f, 48f, 40f),
    "shelf_slot_02" to RoomArea(164f, 59f, 48f, 40f),
    "shelf_slot_03" to RoomArea(107f, 112f, 48f, 41f),
    "shelf_slot_04" to RoomArea(201f, 112f, 48f, 41f),
    "wall_left" to RoomArea(12f, 103f, 46f, 65f),
    "wall_center" to RoomArea(139f, 3f, 77f, 41f),
    "wall_right" to RoomArea(293f, 56f, 53f, 56f),
    "table_top" to RoomArea(25f, 203f, 36f, 37f),
    "floor_left" to RoomArea(8f, 286f, 55f, 58f),
    "floor_right" to RoomArea(280f, 273f, 69f, 63f),
    "lighting" to RoomArea(161f, -12f, 38f, 56f),
)
internal val roomServingArea = RoomArea(58f, 211f, 31f, 29f)

@Composable
internal fun KraRoom(
    api: AccountApi,
    rig: PetRig?,
    gesture: PetGesture,
    adornments: Map<String, JSONObject>,
    decor: Map<String, JSONObject>,
    offline: Boolean,
    wardrobeOpen: Boolean,
    serving: JSONObject?,
    onPet: () -> Unit,
    onWardrobe: () -> Unit,
    onShelf: () -> Unit,
    onFood: () -> Unit,
) {
    val fits = adornments.mapNotNull { (slot, item) ->
        key(slot, item.optString("id")) {
            rememberItemArt(api, item, offline)?.let { PetFit(slot, it, item.getJSONObject("render")) }
        }
    }
    val artwork = decor.mapNotNull { (slot, item) ->
        key(slot, item.optString("id")) {
            rememberItemArt(api, item, offline)?.let { bitmap ->
                slot to remember(bitmap) { bitmap.asImageBitmap() }
            }
        }
    }.toMap()
    val food = serving?.let { rememberItemArt(api, it, offline) }?.let { bitmap ->
        remember(bitmap) { bitmap.asImageBitmap() }
    }
    val dark = MaterialTheme.colorScheme.surface.luminance() < .22f

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp)).testTag("kra-room"),
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawKraBar(SceneSize, dark, wardrobeOpen, artwork)
            }
            rig?.let {
                AnimatedPet(
                    it, gesture, Modifier.matchParentSize(), fits, room = true,
                )
                RoomTarget(
                    RoomArea(106f, 160f, 155f, 171f).modifier(this)
                        .testTag("pet-gesture-${gesture.clip}"),
                    "Питомец", onPet,
                    state = if (fits.isEmpty()) null else "Предметов надето: ${fits.size}",
                )
            }
            if (food != null || "table_top" in artwork || "floor_left" in artwork || "floor_right" in artwork) {
                Canvas(Modifier.matchParentSize()) {
                    drawKraBarForeground(SceneSize, artwork, food)
                }
            }
            RoomTarget(shelfArea.modifier(this), "Осмотреть полку", onShelf)
            RoomTarget(wardrobeArea.modifier(this), "Открыть шкаф", onWardrobe)
            RoomTarget(foodArea.modifier(this), "Подойти к столу", onFood)
        }
    }
}

@Composable
private fun RoomTarget(modifier: Modifier, label: String, onClick: () -> Unit, state: String? = null) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onClick)
        .semantics {
            contentDescription = label
            state?.let { stateDescription = it }
        })
}

