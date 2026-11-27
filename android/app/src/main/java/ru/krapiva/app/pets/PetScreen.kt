package ru.krapiva.app.pets

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.account.ApiFailure
import ru.krapiva.app.designsystem.ClubGlyph
import ru.krapiva.app.designsystem.ClubIcon
import java.io.File
import java.security.MessageDigest
import java.util.UUID

private const val Api = "v1/pets/"
private val Slots = mapOf(
    "head" to "Головной убор", "face" to "Очки", "torso_base" to "Базовая одежда", "torso_outer" to "Верхняя одежда",
    "neck" to "Шея", "wrist" to "Запястье", "accessory" to "Аксессуар", "effect" to "Эффект",
    "shelf_slot_01" to "Полка 1", "shelf_slot_02" to "Полка 2", "shelf_slot_03" to "Полка 3", "shelf_slot_04" to "Полка 4",
    "wall_left" to "Стена слева", "wall_center" to "Стена в центре", "wall_right" to "Стена справа",
    "table_top" to "Столешница", "floor_left" to "Пол слева", "floor_right" to "Пол справа",
    "lighting" to "Свет", "wallpaper" to "Обои", "flooring" to "Покрытие пола"
)
private enum class PetPage(val title: String) {
    ROOM("KraRoom"), WARDROBE("Гардероб"), SHOP("KraShop"), FOOD("Лакомства"),
    DECOR("Интерьер"), COLLECTION("Коллекция"), EVENTS("События"), WALLET("Кошелёк")
}

@Composable
internal fun PetScreen(
    api: AccountApi,
    owner: String,
    expired: () -> Unit,
    rewardCamera: PetRewardCamera = { result, failure, accept -> DevicePetRewardCamera(result, failure, accept) },
    bottomBar: @Composable () -> Unit,
) {
    val scope=rememberCoroutineScope()
    val context=LocalContext.current
    var snapshot by remember(owner) { mutableStateOf<JSONObject?>(null) }
    var definitions by remember(owner) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var items by remember(owner) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var history by remember(owner) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var historyCursor by remember(owner) { mutableStateOf<String?>(null) }
    var offline by remember(owner) { mutableStateOf(false) }
    var busy by remember(owner) { mutableStateOf(false) }
    var error by remember(owner) { mutableStateOf<String?>(null) }
    var page by rememberSaveable(owner) { mutableStateOf(PetPage.ROOM) }
    var selected by rememberSaveable(owner) { mutableStateOf("starter_hop") }
    var name by rememberSaveable(owner) { mutableStateOf("") }
    var code by rememberSaveable(owner) { mutableStateOf("") }
    var storeFilter by rememberSaveable(owner) { mutableStateOf("food") }
    var pageLimit by rememberSaveable(owner) { mutableIntStateOf(12) }
    var serving by remember(owner) { mutableStateOf<JSONObject?>(null) }
    var wardrobeOpen by remember(owner) { mutableStateOf(false) }
    var fitting by remember(owner) { mutableStateOf<JSONObject?>(null) }
    var fittingSlot by remember(owner) { mutableStateOf<String?>(null) }
    var gesture by remember(owner) { mutableStateOf(PetGesture("greet")) }
    var lastGesture by remember(owner) { mutableLongStateOf(0) }
    var tapped by remember(owner) { mutableStateOf(false) }
    val pet=snapshot?.optJSONObject("pet")
    val inventory=snapshot?.optJSONArray("inventory")?.objects().orEmpty()
    val byDefinition=items.associateBy { it.getString("id") }
    val byInstance=inventory.associateBy { it.getString("id") }
    val petId=pet?.getString("definitionId") ?: selected

    val rig by produceState<PetRig?>(null,petId) {
        value=null
        try { value=PetRig.load(context.assets,petId,true) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { value=null }
    }
    fun act(clip: String, priority: Int=1, followUp: String?=null) {
        val now=SystemClock.elapsedRealtime()
        val gestureCanEnd=rig?.holds(gesture.clip)!=true
        val duration=rig?.duration(gesture.clip) ?: 1200f
        if(priority>=gesture.priority || (gestureCanEnd && now-lastGesture>duration)) {
            lastGesture=now
            gesture=PetGesture(clip,priority,gesture.sequence+1,followUp)
        }
    }
    LaunchedEffect(gesture, page, rig) {
        val next=gesture.followUp ?: return@LaunchedEffect
        if(page!=PetPage.ROOM) return@LaunchedEffect
        if(ValueAnimator.areAnimatorsEnabled()) {
            delay(rig?.duration(gesture.clip)?.toLong() ?: 600L)
        }
        act(next,gesture.priority)
    }
    fun drill(next: PetPage) {
        fitting=null
        fittingSlot=null
        error=null
        page=next
        wardrobeOpen=next==PetPage.WARDROBE
        if(next==PetPage.FOOD) act("sit",3)
        else if(gesture.clip=="sit") gesture=PetGesture("idle",sequence=gesture.sequence+1)
    }
    fun show(confirmed: JSONObject, starterList: JSONObject, shopList: JSONObject, cached: Boolean) {
        snapshot=confirmed
        definitions=starterList.getJSONArray("items").objects()
        items=shopList.getJSONArray("items").objects()
        offline=cached
        if(confirmed.optJSONObject("pet")==null && definitions.none { it.optString("id")==selected })
            selected=definitions.firstOrNull()?.getString("id") ?: selected
    }
    suspend fun load()=coroutineScope {
        val state=async { api.catalogGet(Api+"state") }
        val starter=async { api.catalogGet(Api+"definitions") }
        val catalog=async { api.catalogGet(Api+"catalog") }
        val (confirmed,cachedState)=state.await()
        val (starters,cachedDefinitions)=starter.await()
        val (shop,cachedShop)=catalog.await()
        show(confirmed,starters,shop,cachedState || cachedDefinitions || cachedShop)
    }
    fun run(action: suspend () -> Unit) {
        if(busy) return
        busy=true
        error=null
        scope.launch {
            try { action() }
            catch (cancel: CancellationException) { throw cancel }
            catch (failure: ApiFailure) { if(failure.status==401) expired() else error=failure.message }
            catch (_: Exception) { error="Не удалось связаться с KraPet. Повторите попытку." }
            finally { busy=false }
        }
    }
    fun mutate(path: String, body: JSONObject, method: String="POST", clip: String?=null, onDone: ()->Unit={}) {
        run {
            val action=when(path) {
                "purchase" -> "purchase:"+body.getString("definitionId")
                "consume" -> "consume:"+body.getString("itemId")
                else -> null
            }
            if(action!=null) {
                val requests=api.editLocalDocument("krapet_requests",owner) {
                    if(!it.has(action)) it.put(action,UUID.randomUUID().toString())
                }
                body.put("requestId",requests.getString(action))
            }
            val result=api.json(Api+path,method,body)
            load()
            // Keep the request ID until the confirmed state can replace the checkout UI.
            if(action!=null) api.editLocalDocument("krapet_requests",owner) { it.remove(action) }
            if(path=="redeem" && result.optBoolean("replayed")) {
                error="Эта награда уже в коллекции"
            } else if(clip!=null) {
                if(clip=="eat" && gesture.clip=="sit" && ValueAnimator.areAnimatorsEnabled()) {
                    val remaining=(rig?.transitionDuration("sit")?.toLong() ?: 0L) -
                        (SystemClock.elapsedRealtime()-lastGesture)
                    delay(remaining.coerceAtLeast(0L))
                }
                if(clip=="celebrate" && ValueAnimator.areAnimatorsEnabled()) {
                    act("gift",3,followUp=clip)
                } else act(clip,3)
            }
            onDone()
        }
    }
    LaunchedEffect(owner) {
        run {
            val state=api.cachedCatalog(Api+"state",owner)
            val starters=api.cachedCatalog(Api+"definitions",owner)
            val shop=api.cachedCatalog(Api+"catalog",owner)
            if(state!=null && starters!=null && shop!=null) show(state,starters,shop,true)
            load()
            act(if(state?.optJSONObject("pet")!=null) "return" else "greet",2)
        }
    }
    LaunchedEffect(pet?.optString("id"),page,offline) {
        if(pet!=null && !offline && page in setOf(PetPage.ROOM,PetPage.WARDROBE)) {
            val view=if(page==PetPage.ROOM) "room" else "wardrobe"
            try { api.json(Api+"visit","POST",JSONObject().put("view",view)) }
            catch(cancel:CancellationException) { throw cancel }
            catch(_:Exception) { /* Analytics does not interrupt the room. */ }
        }
    }
    LaunchedEffect(serving) {
        if(serving!=null) {
            delay(2400)
            serving=null
        }
    }
    BackHandler(page!=PetPage.ROOM) { drill(PetPage.ROOM) }

    val routes=listOf(PetPage.WARDROBE,PetPage.SHOP,PetPage.FOOD,PetPage.DECOR,PetPage.COLLECTION,PetPage.EVENTS)
    val adornments=mutableMapOf<String,JSONObject>()
    snapshot?.optJSONObject("equipped")?.let { equipped ->
        equipped.keys().forEach { slot -> byInstance[equipped.optString(slot)]?.let { instance ->
            byDefinition[instance.optString("definitionId")]?.let { adornments[slot]=it }
        } }
    }
    if(fitting?.optString("kind")=="wear") fittingSlot?.let { adornments[it]=fitting!! }
    val decorations=mutableMapOf<String,JSONObject>()
    snapshot?.optJSONObject("decor")?.let { decor ->
        decor.keys().forEach { slot -> byInstance[decor.optString(slot)]?.let { instance ->
            byDefinition[instance.optString("definitionId")]?.let { decorations[slot]=it }
        } }
    }
    if(fitting?.optString("kind")=="decor") fittingSlot?.let { decorations[it]=fitting!! }

    val scroll=rememberScrollState()
    LaunchedEffect(page) { scroll.scrollTo(0) }
    Scaffold(bottomBar={ Box(Modifier.navigationBarsPadding()) { bottomBar() } }) { padding ->
        Column(Modifier.padding(padding).safeDrawingPadding().fillMaxSize().verticalScroll(scroll)
            .padding(horizontal=14.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("pet-error")) }
            if(offline) Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Сохранённые данные · офлайн",Modifier.weight(1f))
                TextButton({run {load()}},enabled=!busy){Text("Обновить")}
            }
            if(snapshot==null) {
                Text("Питомец",style=MaterialTheme.typography.headlineSmall)
                if(!busy) Button({run {load()}}){Text("Загрузить")}
            } else if(pet==null) {
                Text("Знакомьтесь, KraPet",style=MaterialTheme.typography.headlineSmall)
                Text("Заведите питомца бесплатно и обустройте его мини-бар. Вернуться к выбору можно в любое время.")
                Box(Modifier.fillMaxWidth().height(205.dp).clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center) {
                    rig?.let { AnimatedPet(it,gesture,Modifier.fillMaxSize().testTag("pet-preview-$petId").clickable { act("greet",2) }) }
                }
                Row(Modifier.testTag("pet-candidates").horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    definitions.forEach { item ->
                        FilterChip(selected==item.getString("id"),{
                            selected=item.getString("id")
                            gesture=PetGesture("greet",2,gesture.sequence+1)
                        },{Text(item.getString("name"))})
                    }
                }
                definitions.find { it.optString("id")==selected }?.let {
                    Text(it.getString("personality"),style=MaterialTheme.typography.titleMedium)
                }
                OutlinedTextField(name,{name=it.take(32)},Modifier.fillMaxWidth().testTag("pet-name"),label={Text("Имя питомца")},singleLine=true)
                Button({mutate("adopt",JSONObject().put("definitionId",selected).put("name",name.trim()),clip="appear")},
                    enabled=!busy && !offline && name.isNotBlank() && definitions.any { it.optString("id")==selected },
                    modifier=Modifier.fillMaxWidth().testTag("pet-adopt")) { Text("Выбрать питомца") }
            } else {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(pet.getString("name"),style=MaterialTheme.typography.headlineSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(page.title,style=MaterialTheme.typography.labelLarge)
                    }
                    TextButton({
                        drill(PetPage.WALLET)
                        run {
                            val entries=api.json(Api+"wallet")
                            history=entries.getJSONArray("items").objects()
                            historyCursor=entries.optString("next").takeUnless { it.isBlank() || it=="null" }
                        }
                    },enabled=!busy && !offline) {
                        KrapiCoinMark()
                        Text(" ${snapshot?.optInt("balance")} KC",modifier=Modifier.testTag("pet-balance"))
                    }
                    if(page!=PetPage.SHOP) TextButton({drill(PetPage.SHOP)}) { Text("Магазин") }
                }
                if(page!=PetPage.ROOM) TextButton({drill(PetPage.ROOM)}) { Text("В комнату") }
                if(page in setOf(PetPage.ROOM,PetPage.WARDROBE,PetPage.FOOD) || fitting!=null) {
                    KraRoom(api,rig,gesture,adornments,decorations,offline,wardrobeOpen,serving,
                        petClick={tapped=!tapped;act(if(tapped)"react" else "alternate",2)},
                        open={drill(it)})
                }
                if(page==PetPage.ROOM) {
                    Row(Modifier.testTag("pet-room-actions").horizontalScroll(rememberScrollState()),
                        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        routes.forEach { route -> OutlinedButton({drill(route)},Modifier.testTag("pet-page-${route.name}")) { Text(route.title) } }
                    }
                    snapshot?.optJSONArray("pets")?.objects()?.takeIf { it.size>1 }?.let { pets ->
                        pets.forEach { own ->
                            TextButton({mutate("active",JSONObject().put("petId",own.getString("id")),"PUT","appear")},
                                enabled=!busy && !offline && own.optString("id")!=pet.optString("id")) {Text(own.optString("name"))}
                        }
                    }
                }
                val preview:(JSONObject)->Unit={ item ->
                    fitting=item
                    fittingSlot=item.getJSONArray("slots").optString(0).takeIf { it.isNotBlank() }
                }
                when(page) {
                    PetPage.ROOM -> Unit
                    PetPage.SHOP -> {
                        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            listOf("food" to "Лакомства","wear" to "Одежда","decor" to "Интерьер").forEach { (key,label) ->
                                FilterChip(storeFilter==key,{storeFilter=key;pageLimit=12;fitting=null;fittingSlot=null},{Text(label)})
                            }
                        }
                        val listed=items.filter { it.optBoolean("forSale") && it.optString("kind")==storeFilter }.filter { item ->
                            val compatible=item.getJSONArray("compatiblePets")
                            (compatible.length()==0 || (0 until compatible.length()).any { compatible.getString(it)==petId }) &&
                                (item.optString("kind")=="food" || inventory.none { it.optString("definitionId")==item.optString("id") })
                        }
                        fitting?.let { item ->
                            val price=item.optInt("price")
                            fittingSlot?.let { Text(Slots[it] ?: it) }
                            Button({mutate("purchase",JSONObject().put("definitionId",item.getString("id")),clip="purchase"){
                                fitting=null;fittingSlot=null;page=PetPage.ROOM
                            }},enabled=!busy && !offline && item.optBoolean("forSale") && (snapshot?.optInt("balance") ?: 0)>=price) {
                                Text("Купить «${item.getString("name")}» за $price KC")
                            }
                            TextButton({fitting=null;fittingSlot=null}){Text("Закрыть примерку")}
                        }
                        if(listed.isEmpty()) Text("Сейчас нет предложений.")
                        listed.take(pageLimit).forEach { item ->
                            PetItemRow(api,item,offline,if(storeFilter=="food")"Выбрать" else "Примерить") {preview(item)}
                        }
                        if(listed.size>pageLimit) TextButton({pageLimit+=12}) {Text("Ещё предметы")}
                    }
                    PetPage.WARDROBE,PetPage.DECOR,PetPage.COLLECTION,PetPage.FOOD -> {
                        val kind=when(page){PetPage.WARDROBE->"wear";PetPage.DECOR->"decor";PetPage.FOOD->"food";else->null}
                        val relevant=inventory.mapNotNull { instance -> byDefinition[instance.optString("definitionId")]?.let { instance to it } }
                            .filter { kind==null || it.second.optString("kind")==kind }
                        if(relevant.isEmpty()) {
                            Text("Здесь появятся ваши предметы.")
                            TextButton({drill(PetPage.SHOP)}){Text("Открыть магазин")}
                        }
                        relevant.forEach { (instance,item) ->
                            val target=instance.getString("id")
                            val slotMap=snapshot?.optJSONObject(if(kind=="decor")"decor" else "equipped")
                            val current=slotMap?.keys()?.asSequence()?.firstOrNull { slotMap.optString(it)==target }
                            val action=when {
                                kind=="food" -> "Угостить"
                                current!=null -> "Снять"
                                kind!=null -> "Примерить"
                                else -> null
                            }
                            PetItemRow(api,item,offline,action) {
                                when {
                                    kind=="food" -> mutate("consume",JSONObject().put("itemId",target),clip="eat"){
                                        serving=item
                                        page=PetPage.ROOM
                                    }
                                    current!=null -> mutate("${if(kind=="decor")"decor" else "wear"}/$current",JSONObject().put("itemId",JSONObject.NULL),"PUT")
                                    else -> preview(item)
                                }
                            }
                        }
                        fitting?.takeIf { kind in setOf("wear","decor") }?.let { item ->
                            val own=relevant.firstOrNull { it.second.optString("id")==item.optString("id") }?.first
                            fittingSlot?.let { slot ->
                                val slots=item.getJSONArray("slots")
                                if(slots.length()>1) Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                    (0 until slots.length()).map { slots.getString(it) }.forEach { newSlot ->
                                        FilterChip(newSlot==slot,{fittingSlot=newSlot},{Text(Slots[newSlot] ?: newSlot)})
                                    }
                                }
                                Button({
                                    val reaction=when { kind=="decor"->"inspect";slot=="head"->"adjust_hat";slot=="face"->"adjust_glasses";else->"adjust_clothes" }
                                    mutate("$kind/$slot",JSONObject().put("itemId",own?.getString("id")),"PUT",reaction) {
                                        page=PetPage.ROOM;fitting=null;fittingSlot=null
                                    }
                                },enabled=own!=null && !busy && !offline) { Text("Применить · ${Slots[slot] ?: slot}") }
                            }
                            TextButton({fitting=null;fittingSlot=null}){Text("Закрыть примерку")}
                        }
                    }
                    PetPage.EVENTS -> {
                        Text("Партнёрская награда",style=MaterialTheme.typography.titleMedium)
                        Text("Введите код события или отсканируйте QR.")
                        PetRewardScanner(!busy && !offline,rewardCamera) { code=it }
                        OutlinedTextField(code,{code=it.take(195)},Modifier.fillMaxWidth(),label={Text("Код события")},singleLine=true)
                        Button({mutate("redeem",JSONObject().put("code",code.trim()),clip="celebrate"){code="";page=PetPage.ROOM}},
                            enabled=!offline && !busy && isPetRewardCode(code.trim())) {Text("Получить награду")}
                    }
                    PetPage.WALLET -> {
                        Text("История KrapiCoin",style=MaterialTheme.typography.titleMedium)
                        history.forEach { item ->
                            val note=when(item.optString("reason")) {
                                "checkin"->"Публикация чекина";"starter"->"Открытие кошелька";"purchase"->"Покупка"
                                "checkin_removal"->"Корректировка чекина";else->"Корректировка"
                            }
                            Text("$note · ${item.optInt("amount")} KC")
                        }
                        historyCursor?.let { cursor -> TextButton({run {
                            val entries=api.json(Api+"wallet?before=$cursor")
                            history=history+entries.getJSONArray("items").objects()
                            historyCursor=entries.optString("next").takeUnless { it.isBlank() || it=="null" }
                        }},enabled=!busy){Text("Ещё операции")} }
                    }
                }
            }
        }
    }
}

@Composable
private fun PetItemRow(api:AccountApi,item:JSONObject,offline:Boolean,action:String?,select:()->Unit) {
    val image=rememberItemArt(api,item,offline)
    Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        image?.let { Image(it.asImageBitmap(),null,Modifier.size(44.dp)) }
        Column(Modifier.weight(1f)) {
            Text(item.getString("name"),style=MaterialTheme.typography.titleSmall)
            if(item.optBoolean("forSale")) Text("${item.optInt("price")} KC",style=MaterialTheme.typography.labelSmall)
        }
        if(action!=null) TextButton(select,Modifier.testTag("pet-item-${item.optString("id")}"),enabled=!offline || action=="Примерить") {Text(action)}
    }
}

private suspend fun loadArt(api:AccountApi,file:File,hash:String,offline:Boolean):Bitmap? = withContext(Dispatchers.IO) {
    if(!hash.matches(Regex("[0-9a-f]{64}"))) return@withContext null
    if(file.isFile) return@withContext BitmapFactory.decodeFile(file.path)
    if(offline) return@withContext null
    try {
        val bytes=api.bytes(Api+"art/$hash")
        val actual=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        if(actual!=hash) return@withContext null
        val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size) ?: return@withContext null
        val temp=File.createTempFile("kra-", ".webp",file.parentFile)
        try { temp.writeBytes(bytes);temp.renameTo(file) } finally { temp.delete() }
        bitmap
    } catch(cancel:CancellationException) { throw cancel }
      catch(_:Exception) { null }
}
@Composable
private fun rememberItemArt(api:AccountApi,item:JSONObject,offline:Boolean):Bitmap? {
    val context=LocalContext.current
    val hash=item.optString("assetHash")
    val art by produceState<Bitmap?>(null,hash,offline) {
        value=null
        val directory=File(context.cacheDir,"krapet-assets").apply { mkdirs() }
        if(hash.matches(Regex("[0-9a-f]{64}"))) value=loadArt(api,File(directory,hash+".webp"),hash,offline)
    }
    return art
}

@Composable
private fun KraRoom(api: AccountApi,rig:PetRig?,gesture:PetGesture,adornments:Map<String,JSONObject>,decor:Map<String,JSONObject>,
    offline:Boolean,wardrobeOpen:Boolean,serving:JSONObject?,petClick:()->Unit,open:(PetPage)->Unit) {
    val fits=adornments.mapNotNull { (slot,item) -> key(slot,item.optString("id")) {
        rememberItemArt(api,item,offline)?.let { PetFit(slot,it,item.getJSONObject("render")) }
    } }
    val wallpaper=decor["wallpaper"]?.let { rememberItemArt(api,it,offline) }?.asImageBitmap()
    val flooring=decor["flooring"]?.let { rememberItemArt(api,it,offline) }?.asImageBitmap()
    val dark=MaterialTheme.colorScheme.surface.luminance()<.22f
    val wall=if(dark) Color(0xff283c32) else Color(0xff58654a)
    val wood=if(dark) Color(0xff372b26) else Color(0xff805544)
    BoxWithConstraints(Modifier.fillMaxWidth().height(315.dp).clip(RoundedCornerShape(20.dp))
        .background(Brush.verticalGradient(listOf(wall,wall,wood))).testTag("kra-room")) {
        val w=maxWidth
        val h=maxHeight
        Canvas(Modifier.fillMaxSize()) {
            fun rect(color:Color,x:Float,y:Float,a:Float,b:Float,r:Float=0f) =
                drawRoundRect(color,Offset(x*size.width,y*size.height),Size(a*size.width,b*size.height),CornerRadius(r))
            wallpaper?.let { tile ->
                for (y in 0..3) for (x in 0..5) drawImage(tile,
                    dstOffset=IntOffset((x*size.width/6).toInt(),(y*size.height*.185f).toInt()),
                    dstSize=IntSize((size.width/6+1).toInt(),(size.height*.185f+1).toInt()),alpha=.24f)
            }
            drawRect(Brush.radialGradient(listOf(Color(0x70ffc46d),Color.Transparent),Offset(.5f*size.width,.23f*size.height),size.width*.63f))
            rect(wood,0f,.75f,1f,.25f)
            flooring?.let { tile ->
                for (x in 0..4) drawImage(tile,dstOffset=IntOffset((x*size.width/5).toInt(),(.75f*size.height).toInt()),
                    dstSize=IntSize((size.width/5+1).toInt(),(.25f*size.height).toInt()),alpha=.8f)
            }
            rect(Color(0xff796147),0f,.71f,1f,.028f)
            rect(Color(0xff584335),0f,.734f,1f,.018f)
            drawLine(Color(0xff907049),Offset(.50f*size.width,0f),Offset(.50f*size.width,.115f*size.height),2.2f)
            if("lighting" !in decor) {
                rect(Color(0xff886d47),.446f,.111f,.108f,.029f,5f)
                rect(Color(0xffd0a668),.46f,.136f,.080f,.015f,8f)
            }
            repeat(5) { row ->
                val y = size.height * (0.75f + row * 0.061f)
                drawLine(Color(0x44311e13), Offset(0f, y), Offset(size.width, y), 1.2f)
            }
            rect(Color(0xff604431),.26f,.37f,.46f,.037f,4f)
            rect(Color(0xffb88351),.26f,.37f,.46f,.013f,4f)
            rect(Color(0xff5b3825),.04f,.35f,.20f,.42f,8f)
            rect(Color(0xff956548),.052f,.37f,.176f,.37f,5f)
            if(wardrobeOpen) {
                rect(Color(0xff472f22),.068f,.392f,.147f,.32f,4f)
                rect(Color(0xff876346),.076f,.51f,.13f,.018f,4f)
                rect(Color(0xff876346),.076f,.65f,.13f,.018f,4f)
                rect(Color(0xff78886b),.105f,.46f,.07f,.04f,6f)
                rect(Color(0xffb46f5f),.094f,.60f,.09f,.04f,6f)
            } else {
                rect(Color(0xff69432d),.136f,.38f,.007f,.34f,3f)
                rect(Color(0xffdba964),.105f,.54f,.013f,.035f,3f)
                rect(Color(0xffdba964),.157f,.54f,.013f,.035f,3f)
            }
            rect(Color(0xff4b3026),.82f,.60f,.027f,.21f,4f)
            rect(Color(0xff4b3026),.92f,.60f,.027f,.21f,4f)
            rect(Color(0xffbd8754),.77f,.58f,.21f,.035f,6f)
            rect(Color(0xff573a2d),.73f,.72f,.02f,.12f,4f)
            rect(Color(0xff573a2d),.79f,.72f,.02f,.12f,4f)
            rect(Color(0xffa86e45),.70f,.70f,.13f,.032f,7f)
            drawOval(Color(0x490b0703),Offset(.37f*size.width,.84f*size.height),Size(.28f*size.width,.045f*size.height))
        }
        for((slot,item) in decor.filterKeys { it !in setOf("wallpaper","flooring") }) key(slot,item.optString("id")) {
            val image=rememberItemArt(api,item,offline)
            val spot=when(slot) {
                "shelf_slot_01"->.29f to .25f;"shelf_slot_02"->.395f to .25f
                "shelf_slot_03"->.50f to .25f;"shelf_slot_04"->.605f to .25f
                "wall_left"->.12f to .12f;"wall_center"->.43f to .10f;"wall_right"->.76f to .12f
                "table_top"->.82f to .47f;"floor_left"->.13f to .74f;"floor_right"->.84f to .74f
                "lighting"->.44f to .02f;"wallpaper"->.03f to .02f;else->.04f to .90f
            }
            image?.let { Image(it.asImageBitmap(),item.optString("name"),Modifier.offset(w*spot.first,h*spot.second)
                .size(w*.105f,h*.115f)) }
        }
        serving?.let { item -> rememberItemArt(api,item,offline)?.let { image ->
            Image(image.asImageBitmap(),item.optString("name"),Modifier.offset(w*.84f,h*.487f).size(w*.09f,h*.095f))
        } }
        rig?.let { AnimatedPet(it,gesture,Modifier.align(Alignment.BottomCenter).padding(bottom=30.dp).width(w*.63f).height(h*.66f)
            .testTag("pet-gesture-${gesture.clip}")
            .semantics {
                contentDescription="Питомец"
                if(fits.isNotEmpty()) stateDescription="Предметов надето: ${fits.size}"
            }.clickable(onClick=petClick),fits) }
        Box(Modifier.offset(w*.04f,h*.36f).width(w*.20f).height(h*.41f).semantics { contentDescription="Открыть шкаф" }
            .clickable(role=Role.Button){open(PetPage.WARDROBE)})
        Box(Modifier.offset(w*.27f,h*.24f).width(w*.43f).height(h*.17f).semantics { contentDescription="Осмотреть полку" }
            .clickable(role=Role.Button){open(PetPage.DECOR)})
        Box(Modifier.offset(w*.77f,h*.51f).width(w*.21f).height(h*.32f).semantics { contentDescription="Подойти к столу" }
            .clickable(role=Role.Button){open(PetPage.FOOD)})
    }
}


@Composable
private fun KrapiCoinMark() {
    Box(Modifier.size(20.dp),contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0xff93622b))
            drawCircle(Color(0xffe9b45d),size.minDimension*.44f)
            drawCircle(Color(0xfff6cf82),size.minDimension*.35f)
        }
        ClubGlyph(ClubIcon.LEAF,Modifier.size(12.dp),Color(0xff5a462d))
    }
}
