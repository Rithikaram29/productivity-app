package com.intentcoach.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.intentcoach.app.R
import com.intentcoach.app.data.CompanionMood
import com.intentcoach.app.data.TimeOfDay
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The cozy room and its two residents, as pixel art.
 *
 * One isometric room per part of the day (res/drawable-nodpi/room_*), with the girl and the
 * cat placed on its floor. Which pose each one is in comes from [sceneFor]: time of day sets
 * the default routine, mood and the focus state override it.
 *
 * Each scene has a few places in the room. Every [DWELL_MS] the pair moves on: the girl fades
 * out and back in at her next spot, and the cat walks there (walk frames, mirrored to face the
 * way it is going). On top of that: a breathing loop on both characters, lamp glow that pulses
 * in the evening and at night, and hearts or sleepy dots drifting up.
 *
 * Source art lives in /assets (cozy_room_assets, cozy_girl_sprites, cozy_cat_sprites); only
 * the sprites used here are copied into res. [working] is for the running focus timer.
 */
@Composable
fun CompanionRoom(
    mood: CompanionMood,
    time: TimeOfDay,
    modifier: Modifier = Modifier,
    celebrate: Boolean = false,
    working: Boolean = false,
    height: Dp = 240.dp
) {
    val scene = sceneFor(time, mood, working, celebrate)

    val room = ImageBitmap.imageResource(roomFor(time))
    val girl = ImageBitmap.imageResource(scene.girl)
    val cat = scene.cat?.let { ImageBitmap.imageResource(it) }
    val glow = ImageBitmap.imageResource(R.drawable.fx_glow)
    val walkFrames = if (scene.cat != null) WalkFrames.map { ImageBitmap.imageResource(it) } else emptyList()

    // Idle motion. The transition only runs while this composable is on screen.
    val transition = rememberInfiniteTransition(label = "companion")
    val breath by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath"
    )
    val catBreath by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(3400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "catBreath"
    )
    val flicker by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "flicker"
    )
    val drift by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart), label = "drift"
    )
    val stepClock by transition.animateFloat(
        0f, WALK_CYCLE_MS.toFloat(), infiniteRepeatable(tween(WALK_CYCLE_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "steps"
    )

    // Which place in the room the pair is at. Moves on every DWELL_MS while on screen.
    var placeIndex by remember(scene) { mutableIntStateOf(0) }
    LaunchedEffect(scene) {
        if (scene.places.size < 2) return@LaunchedEffect
        while (true) {
            delay(DWELL_MS)
            placeIndex = (placeIndex + 1) % scene.places.size
        }
    }

    // Girl: fade out at the old spot, fade in at the new one.
    val girlAlpha = remember(scene) { Animatable(1f) }
    var girlShown by remember(scene) { mutableIntStateOf(0) }
    LaunchedEffect(scene, placeIndex) {
        if (girlShown != placeIndex) {
            girlAlpha.animateTo(0f, tween(FADE_MS))
            girlShown = placeIndex
            girlAlpha.animateTo(1f, tween(FADE_MS))
        }
    }

    // Cat: walks from wherever it is now to its next spot.
    val walk = remember(scene) { Animatable(1f) }
    var catFrom by remember(scene) { mutableStateOf(scene.catStartsAt ?: scene.places[0].catAt) }
    var catTo by remember(scene) { mutableStateOf(scene.catStartsAt ?: scene.places[0].catAt) }
    LaunchedEffect(scene, placeIndex) {
        val start = lerp(catFrom, catTo, walk.value)
        val target = scene.places[placeIndex].catAt
        catFrom = start
        catTo = target
        if (start != target) {
            walk.snapTo(0f)
            walk.animateTo(1f, tween(walkMs(start, target), easing = LinearEasing))
        } else {
            walk.snapTo(1f)
        }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Umber, Ink)))
            .semantics { contentDescription = scene.description }
    ) {
        // Fit the room (base 361x347) inside the card, centred, keeping its aspect ratio.
        val k = minOf(size.height / ROOM_H, size.width / ROOM_W)
        val ox = (size.width - ROOM_W * k) / 2f
        val oy = (size.height - ROOM_H * k) / 2f
        fun at(s: Spot) = Offset(ox + s.x * k, oy + s.y * k)

        drawSprite(room, Offset(ox, oy), ROOM_W * k, ROOM_H * k, FilterQuality.None)

        // Warm light: lamps in the evening, lamps + hearth at night.
        if (time == TimeOfDay.EVENING || time == TimeOfDay.NIGHT) {
            val strength = if (time == TimeOfDay.NIGHT) 0.7f else 0.5f
            LampSpots.forEachIndexed { i, s ->
                val pulse = 0.75f + 0.25f * if (i % 2 == 0) flicker else 1f - flicker
                drawGlow(glow, at(s), 46f * k, strength * pulse)
            }
            if (time == TimeOfDay.NIGHT) drawGlow(glow, at(Hearth.copy(y = Hearth.y - 50f)), 70f * k, 0.3f * (0.8f + 0.2f * flicker))
        }

        // Paint back-to-front so whoever stands lower in the room is drawn on top.
        val girlSpot = scene.places[girlShown].girlAt
        val catSpot = lerp(catFrom, catTo, walk.value)
        val drawGirl = {
            drawFeetAnchored(girl, at(girlSpot), GIRL_K * k, breathe = 0.012f * breath, alpha = girlAlpha.value)
        }
        val drawCat = {
            if (cat != null) {
                if (walk.value < 1f) {
                    val frame = walkFrames[(stepClock / WALK_FRAME_MS).toInt() % walkFrames.size]
                    // The walk frames face left; mirror them when heading right.
                    drawFeetAnchored(frame, at(catSpot), CAT_K * k, breathe = 0f, flipX = catTo.x > catFrom.x)
                } else {
                    drawFeetAnchored(cat, at(catSpot), CAT_K * k, breathe = 0.02f * catBreath)
                }
            }
        }
        if (catSpot.y <= girlSpot.y) { drawCat(); drawGirl() } else { drawGirl(); drawCat() }

        // Something drifting up above her.
        val feet = at(girlSpot)
        val head = Offset(feet.x, feet.y - girl.height * GIRL_K * k * 0.85f)
        when {
            celebrate || mood == CompanionMood.THRIVING ->
                drawRising(head, k, drift, Blush, count = if (celebrate) 4 else 2, hearts = true, alpha = girlAlpha.value)
            mood == CompanionMood.SLEEPY ->
                drawRising(head, k, drift, Lavender, count = 3, hearts = false, alpha = girlAlpha.value)
            else -> Unit
        }
    }
}

// ---- Scene table -------------------------------------------------------------------------

/** A point on the room floor in base room pixels (the room art is 361 x 347). */
private data class Spot(val x: Float, val y: Float)

/** Where the girl and the cat stand for one beat of a scene. */
private data class Placement(val girlAt: Spot, val catAt: Spot)

private data class Scene(
    @DrawableRes val girl: Int,
    @DrawableRes val cat: Int?,
    val places: List<Placement>,
    val description: String,
    /** If set, the cat walks in from here when the scene first appears. */
    val catStartsAt: Spot? = null
)

private const val ROOM_W = 361f
private const val ROOM_H = 347f
private const val GIRL_K = 0.40f   // sprite px -> room px
private const val CAT_K = 0.25f
private const val DWELL_MS = 14_000L
private const val FADE_MS = 450
private const val WALK_FRAME_MS = 140
private const val WALK_CYCLE_MS = WALK_FRAME_MS * 6

// Open floor in the room, measured on the art.
private val Front = Spot(150f, 302f)    // between the bed and the sofa, near the front
private val Center = Spot(200f, 207f)   // the open floor behind the sofa, under the window
private val Nook = Spot(146f, 214f)     // by the window desk and its plants
private val DeskSide = Spot(272f, 230f) // between the sofa and the fireplace
private val BedFoot = Spot(112f, 300f)  // the rug at the foot of the bed
private val Rug = Spot(252f, 320f)      // front right, by the ottoman
private val RugFront = Spot(220f, 306f) // the rug in front of the coffee table
private val Hearth = Spot(300f, 292f)   // in front of the fireplace
private val LampSpots = listOf(Spot(277f, 143f), Spot(150f, 160f), Spot(243f, 135f))

private val WalkFrames = listOf(
    R.drawable.cat_walk_1, R.drawable.cat_walk_2, R.drawable.cat_walk_3,
    R.drawable.cat_walk_4, R.drawable.cat_walk_5, R.drawable.cat_walk_6
)

@DrawableRes
private fun roomFor(time: TimeOfDay): Int = when (time) {
    TimeOfDay.MORNING -> R.drawable.room_morning
    TimeOfDay.AFTERNOON -> R.drawable.room_afternoon
    TimeOfDay.EVENING -> R.drawable.room_evening
    TimeOfDay.NIGHT -> R.drawable.room_night
}

/**
 * Who is doing what, and where. Priority: celebration > working > mood > time-of-day routine.
 * Never scolding: "wilted" is her glancing away from the desk and the cat keeping its distance
 * with its ears down.
 */
private fun sceneFor(time: TimeOfDay, mood: CompanionMood, working: Boolean, celebrate: Boolean): Scene = when {
    celebrate -> Scene(R.drawable.girl_h16_playing_with_pet, null,
        listOf(Placement(Front, Rug), Placement(Center, Rug)),
        "Session complete. Your companion is playing with the cat.")
    working -> Scene(R.drawable.girl_h07_typing_on_laptop, R.drawable.cat_01_sleeping_curled_up,
        listOf(Placement(Front, Rug), Placement(Center, Nook)),
        "Your companion is working beside you while the cat naps.")
    mood == CompanionMood.SLEEPY -> Scene(
        R.drawable.girl_h15_sleeping_in_chair,
        if (time == TimeOfDay.NIGHT) R.drawable.cat_extra_06_curled_asleep else R.drawable.cat_03_sleepy_yawning,
        listOf(Placement(Front, Rug), Placement(Center, BedFoot)),
        "The room is quiet. Your companion is dozing and the cat is sleepy.")
    mood == CompanionMood.WILTED -> Scene(R.drawable.girl_h09_looking_away_from_desk,
        R.drawable.cat_12_drooping_ears_wilted,
        listOf(Placement(Front, DeskSide), Placement(Center, BedFoot)),
        "The room is a little quiet. One session will warm it up again.")
    mood == CompanionMood.THRIVING -> Scene(
        if (time == TimeOfDay.AFTERNOON || time == TimeOfDay.EVENING) R.drawable.girl_h10_stretching_at_desk
        else R.drawable.girl_h03_stretching_arms,
        R.drawable.cat_10_perky_ears_alert,
        listOf(Placement(Front, Rug), Placement(Center, DeskSide)),
        "The room feels warm. Your companion is stretching happily and the cat is perky.")
    else -> when (time) {
        TimeOfDay.MORNING -> Scene(R.drawable.girl_h05_watering_plant, R.drawable.cat_07_sleeping_in_sun_patch,
            listOf(Placement(Nook, Center), Placement(Front, RugFront)),
            "Morning. Your companion is watering the plants; the cat naps in the sun.")
        TimeOfDay.AFTERNOON -> Scene(R.drawable.girl_h08_writing_in_notebook, R.drawable.cat_11_relaxed_ears_content,
            listOf(Placement(Front, DeskSide), Placement(Center, BedFoot)),
            "Afternoon. Your companion is writing; the cat sits nearby.")
        TimeOfDay.EVENING -> Scene(R.drawable.girl_h11_making_tea, R.drawable.cat_13_purring_resting,
            listOf(Placement(Front, Hearth), Placement(Center, Hearth)),
            "Evening. Your companion is making tea; the cat settles by the fire.", catStartsAt = Rug)
        TimeOfDay.NIGHT -> Scene(R.drawable.girl_h14_reading_book, R.drawable.cat_extra_06_curled_asleep,
            listOf(Placement(Front, Rug), Placement(Center, Hearth)),
            "Night. Your companion is reading by lamplight; the cat is curled up asleep.")
    }
}

// ---- Drawing helpers ---------------------------------------------------------------------

private fun lerp(a: Spot, b: Spot, t: Float) = Spot(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

/** About 28 ms per room pixel, so short hops are quick and crossing the room is a stroll. */
private fun walkMs(a: Spot, b: Spot): Int = (hypot(b.x - a.x, b.y - a.y) * 28f).roundToInt().coerceIn(1400, 4200)

private fun DrawScope.drawSprite(
    img: ImageBitmap, topLeft: Offset, w: Float, h: Float, quality: FilterQuality, alpha: Float = 1f
) {
    drawImage(
        img,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(img.width, img.height),
        dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
        dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
        alpha = alpha.coerceIn(0f, 1f),
        filterQuality = quality
    )
}

/** Draws [img] scaled by [k] with its bottom-centre on [feet]; [breathe] grows it upward. */
private fun DrawScope.drawFeetAnchored(
    img: ImageBitmap, feet: Offset, k: Float, breathe: Float, flipX: Boolean = false, alpha: Float = 1f
) {
    val w = img.width * k
    val h = img.height * k * (1f + breathe)
    val topLeft = Offset(feet.x - w / 2f, feet.y - h)
    if (flipX) {
        scale(-1f, 1f, pivot = feet) { drawSprite(img, topLeft, w, h, FilterQuality.Medium, alpha) }
    } else {
        drawSprite(img, topLeft, w, h, FilterQuality.Medium, alpha)
    }
}

private fun DrawScope.drawGlow(glow: ImageBitmap, center: Offset, radius: Float, alpha: Float) {
    drawImage(
        glow,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(glow.width, glow.height),
        dstOffset = IntOffset((center.x - radius).roundToInt(), (center.y - radius).roundToInt()),
        dstSize = IntSize((radius * 2).roundToInt(), (radius * 2).roundToInt()),
        alpha = alpha.coerceIn(0f, 1f),
        blendMode = BlendMode.Plus,
        filterQuality = FilterQuality.Medium
    )
}

/** Hearts when celebrating, soft dots when sleepy, rising and fading above [origin]. */
private fun DrawScope.drawRising(
    origin: Offset, k: Float, t: Float, color: Color, count: Int, hearts: Boolean, alpha: Float
) {
    repeat(count) { i ->
        val phase = (t + i / count.toFloat()) % 1f
        val y = origin.y - phase * 36f * k
        val dx = (if (i % 2 == 0) 1 else -1) * (6f + phase * 8f) * k
        val c = color.copy(alpha = (1f - phase) * 0.95f * alpha)
        val r = (2.4f + phase * 1.2f) * k
        if (hearts) drawHeart(Offset(origin.x + dx, y), r, c)
        else drawCircle(c, radius = r * 0.7f, center = Offset(origin.x + dx, y))
    }
}

private fun DrawScope.drawHeart(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y + r)
        cubicTo(c.x - r * 2f, c.y - r * 0.2f, c.x - r * 0.8f, c.y - r * 1.6f, c.x, c.y - r * 0.5f)
        cubicTo(c.x + r * 0.8f, c.y - r * 1.6f, c.x + r * 2f, c.y - r * 0.2f, c.x, c.y + r)
        close()
    }
    drawPath(p, color)
}
