package com.bKash.customerapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : AppCompatActivity() {

    // FIX: Android 13 (API 33)+ এ নোটিফিকেশন দেখাতে হলে শুধু ম্যানিফেস্টে
    // POST_NOTIFICATIONS ঘোষণা করলেই যথেষ্ট না — রানটাইমেও ইউজারের কাছ থেকে
    // এটা চাইতে হয়। DoctorAlertService ব্যাকগ্রাউন্ডে ডাক্তার/এডমিনের মেসেজের
    // জন্য নোটিফিকেশন দেখায়, তাই অ্যাপ চালু হওয়ার শুরুতেই (স্প্ল্যাশ স্ক্রিনেই)
    // এই পারমিশনটা চাওয়া হচ্ছে। এটা UI বা বিদ্যমান কোনো ফিচারে কোনো পরিবর্তন
    // আনে না — শুধু একটা সিস্টেম পারমিশন ডায়ালগ (যদি আগে থেকে অনুমতি না থাকে)
    // এক মুহূর্তের জন্য দেখাতে পারে।
    private val notificationPermissionLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { /* গ্রান্ট করুক বা না করুক, অ্যাপের স্বাভাবিক ফ্লো একই থাকে */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()

        // === ফন্ট লোডিং লজিক ===
        // ফন্ট ফাইলটা সরাসরি প্রজেক্টের res/font/solaimanlipi.ttf-এ বান্ডেল করা আছে, তাই
        // কোনো নেটওয়ার্ক কল বা ব্যাকগ্রাউন্ড থ্রেড ছাড়াই লোড হয়।
        // Compose UI নিজে FontFamily(Font(R.font.solaimanlipi)) ব্যবহার করে (নিচে দেখুন);
        // আর MyApp-কে জানিয়ে দেওয়া হয় ফন্ট রেডি — বাকি (View-ভিত্তিক) Activity-গুলোর
        // Auto Font System এর জন্য।
        loadFontAndApply()

        setContent {
            SplashScreen()
        }

        // Auto navigate
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (SupabaseClient.isLoggedIn(this)) {
                startActivity(Intent(this, HomeActivity::class.java))
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }
            finish()
        }, 2600)
    }

    // FIX: ডাক্তার/এডমিন থেকে মেসেজ এলে ব্যাকগ্রাউন্ডেও নোটিফিকেশন দেখানোর জন্য
    // (DoctorAlertService) Android 13+ এ এই রানটাইম পারমিশনটা লাগবেই। আগে থেকে
    // অনুমতি থাকলে বা Android 13 এর নিচে হলে কিছুই করা হয় না (চুপচাপ স্কিপ)।
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                try {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * MyApp-কে ফন্ট লোড করতে বলে (res/font/solaimanlipi.ttf থেকে, লোকাল রিসোর্স —
     * কোনো নেটওয়ার্ক কল/ব্যাকগ্রাউন্ড থ্রেড লাগে না)।
     */
    private fun loadFontAndApply() {
        try {
            // MyApp কে জানিয়ে দাও ফন্ট রেডি - Auto System এর জন্য
            (application as MyApp).loadFont()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Colors & Font
// ─────────────────────────────────────────────────────────────

private val GradientTop = Color(0xFF0F6C61)
private val GradientBottom = Color(0xFF0A4A42)
private val AccentOrange = Color(0xFFF59E0B)
private val ShieldCheckColor = Color(0xFF0A4A42)

private val SolaimanLipi = FontFamily(Font(R.font.solaimanlipi))

// আগের কোডের মতোই: বাংলা অক্ষর (Unicode 2432..2559) থাকলে তবেই SolaimanLipi, নইলে সিস্টেম ফন্ট
private fun hasBangla(text: String): Boolean = text.any { it.code in 2432..2559 }

private fun fontFor(text: String): FontFamily =
    if (hasBangla(text)) SolaimanLipi else FontFamily.Default

// ValueAnimator এর ডিফল্ট AccelerateDecelerateInterpolator এর হুবহু সমতুল্য
private val AccelerateDecelerateEasing = Easing { f ->
    (cos((f + 1) * PI) / 2.0 + 0.5).toFloat()
}

// ─────────────────────────────────────────────────────────────
// Splash screen
// ─────────────────────────────────────────────────────────────

@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GradientTop, GradientBottom)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ===== App icon (unchanged, as requested) =====
            SunnyCareIcon(Modifier.size(100.dp))

            Spacer(Modifier.height(16.dp))

            SplashText(
                text = "SunnyCare ☀",
                sizeSp = 30f,
                bold = true,
                color = Color.White,
                letterSpacingEm = 0.02f
            )

            DoctorCard()
            BmdcBadge()
            LoadingDots()
        }

        SplashText(
            text = "আপনার সুস্থতাই আমাদের অঙ্গীকার",
            sizeSp = 9.5f,
            color = Color(255, 255, 255, 120),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 22.dp)
        )
    }
}

// ===== Doctor details card =====
@Composable
private fun DoctorCard() {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .padding(top = 22.dp)
            .width(300.dp)
            .background(Color(255, 255, 255, 26), shape)
            .border(1.dp, Color(255, 255, 255, 55), shape)
            .padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SplashText(
            text = "ডা. মাসুম বিল্লাহ সানি",
            sizeSp = 19f,
            bold = true,
            color = Color.White
        )

        // সোনালি ডিভাইডার
        Box(
            modifier = Modifier
                .padding(top = 8.dp, bottom = 2.dp)
                .size(width = 46.dp, height = 3.dp)
                .background(AccentOrange, RoundedCornerShape(2.dp))
        )

        IconTextRow(
            iconType = IconType.CAP,
            text = "এম.বি.বি.এস (সিইউ), ডি.এম.ইউ (আল্ট্রা),\nপিজিটি (পিএমসি), এম.সি.জি.পি (মেডিসিন ও শিশু),\nসি.সি.ডি (ডায়াবেটিস-বারডেম, ঢাকা)",
            sizeSp = 10.5f,
            color = Color(255, 255, 255, 235)
        )

        IconTextRow(
            iconType = IconType.HOSPITAL,
            text = "প্রাক্তন মেডিকেল অফিসার:\nপার্কভিউ মেডিকেল কলেজ হাসপাতাল, সিলেট",
            sizeSp = 10f,
            color = Color(255, 255, 255, 195)
        )

        IconTextRow(
            iconType = IconType.HEART,
            text = "মেডিসিন-শিশু, ডায়াবেটিস, উচ্চ রক্তচাপ, নাক-কান-গলা, বাত ব্যাথা, এলার্জি, শ্বাসকষ্ট, চর্ম ও যৌনরোগে অভিজ্ঞ।",
            sizeSp = 10f,
            color = Color(255, 255, 255, 205)
        )

        IconTextRow(
            iconType = IconType.PIN,
            text = "ঢাকা ডায়গনস্টিক সেন্টার, মাধবপুর পশ্চিম বাজার (সোনালী ব্যাংকের নিচ তলা), কাজী আক্তার ম্যানশন, মাধবপুর, হবিগঞ্জ।",
            sizeSp = 9.5f,
            color = Color(255, 255, 255, 180)
        )
    }
}

// ভেক্টর আইকন + টেক্সট নিয়ে একটি হরাইজন্টাল সারি
@Composable
private fun IconTextRow(
    iconType: IconType,
    text: String,
    sizeSp: Float,
    color: Color,
    iconSize: Dp = 18.dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 9.dp),
        verticalAlignment = Alignment.Top
    ) {
        VectorIcon(
            iconType = iconType,
            modifier = Modifier
                .padding(top = 2.dp, end = 9.dp)
                .size(iconSize)
        )
        SplashText(
            text = text,
            sizeSp = sizeSp,
            color = color,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f)
        )
    }
}

// ===== BMDC verified badge (with shield vector icon) =====
@Composable
private fun BmdcBadge() {
    Row(
        modifier = Modifier
            .padding(top = 18.dp)
            .background(Color(255, 255, 255, 45), RoundedCornerShape(50))
            .padding(start = 14.dp, top = 7.dp, end = 16.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VectorIcon(
            iconType = IconType.SHIELD,
            modifier = Modifier
                .padding(end = 8.dp)
                .size(15.dp)
        )
        SplashText(
            text = "বি.এম.ডি.সি নং – এ-১১৭৪৬৩  •  Verified",
            sizeSp = 10f,
            bold = true,
            color = Color.White
        )
    }
}

// ===== Loading dots =====
@Composable
private fun LoadingDots() {
    val transition = rememberInfiniteTransition(label = "loadingDots")
    val v by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = AccelerateDecelerateEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotsAlpha"
    )

    Row(
        modifier = Modifier.padding(top = 26.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(8.dp)
                    .alpha(v * (1f - index * 0.15f))
                    .background(Color.White, CircleShape)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Text helper (আগের createText / createRowText এর সমতুল্য)
// ─────────────────────────────────────────────────────────────

@Composable
private fun SplashText(
    text: String,
    sizeSp: Float,
    color: Color,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    textAlign: TextAlign = TextAlign.Center,
    letterSpacingEm: Float? = null
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = sizeSp.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontFamily = fontFor(text),
        textAlign = textAlign,
        letterSpacing = if (letterSpacingEm != null) letterSpacingEm.em else androidx.compose.ui.unit.TextUnit.Unspecified
    )
}

// ─────────────────────────────────────────────────────────────
// Main circular sun/medical-cross logo (kept exactly as before)
// ─────────────────────────────────────────────────────────────

@Composable
private fun SunnyCareIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val cx = size.width / 2
        val cy = size.height / 2

        drawCircle(Color.White, radius = w / 2, center = Offset(cx, cy))
        drawCircle(AccentOrange, radius = w * 0.22f, center = Offset(cx, cy))

        for (i in 0 until 8) {
            val angle = Math.toRadians((i * 45).toDouble())
            val r1 = w * 0.30f
            val r2 = w * 0.38f
            val sx = cx + r1 * cos(angle).toFloat()
            val sy = cy + r1 * sin(angle).toFloat()
            val ex = cx + r2 * cos(angle).toFloat()
            val ey = cy + r2 * sin(angle).toFloat()
            drawLine(
                color = AccentOrange,
                start = Offset(sx, sy),
                end = Offset(ex, ey),
                strokeWidth = 7f,
                cap = StrokeCap.Round
            )
        }

        val crossW = w * 0.24f
        val crossH = w * 0.08f
        drawRect(
            Color.White,
            topLeft = Offset(cx - crossW / 2, cy - crossH / 2),
            size = Size(crossW, crossH)
        )
        drawRect(
            Color.White,
            topLeft = Offset(cx - crossH / 2, cy - crossW / 2),
            size = Size(crossH, crossW)
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Small vector icons used inside the doctor details card
// ─────────────────────────────────────────────────────────────

private enum class IconType { CAP, HOSPITAL, HEART, PIN, SHIELD }

private val IconStroke = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)

@Composable
private fun VectorIcon(iconType: IconType, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        when (iconType) {
            IconType.CAP -> drawCap(w, h)
            IconType.HOSPITAL -> drawHospital(w, h)
            IconType.HEART -> drawHeart(w, h)
            IconType.PIN -> drawPin(w, h)
            IconType.SHIELD -> drawShield(w, h)
        }
    }
}

// গ্র্যাজুয়েশন ক্যাপ — শিক্ষাগত যোগ্যতা (ডিগ্রি) বোঝাতে
private fun DrawScope.drawCap(w: Float, h: Float) {
    val path = Path().apply {
        moveTo(w * 0.5f, h * 0.12f)
        lineTo(w * 0.95f, h * 0.40f)
        lineTo(w * 0.5f, h * 0.66f)
        lineTo(w * 0.05f, h * 0.40f)
        close()
    }
    drawPath(path, Color.White)
    drawRoundRect(
        color = AccentOrange,
        topLeft = Offset(w * 0.27f, h * 0.54f),
        size = Size(w * 0.73f - w * 0.27f, h * 0.76f - h * 0.54f),
        cornerRadius = CornerRadius(w * 0.05f, w * 0.05f)
    )
    drawLine(
        color = Color.White,
        start = Offset(w * 0.87f, h * 0.42f),
        end = Offset(w * 0.87f, h * 0.82f),
        strokeWidth = 5f,
        cap = StrokeCap.Round
    )
    drawCircle(AccentOrange, radius = w * 0.05f, center = Offset(w * 0.87f, h * 0.86f))
}

// হাসপাতাল ভবন + ক্রস — কর্মস্থল/মেডিকেল অফিসার বোঝাতে
private fun DrawScope.drawHospital(w: Float, h: Float) {
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(w * 0.12f, h * 0.14f),
        size = Size(w * 0.88f - w * 0.12f, h * 0.90f - h * 0.14f),
        cornerRadius = CornerRadius(w * 0.10f, w * 0.10f)
    )
    val crossW = w * 0.14f
    val crossH = w * 0.36f
    val cx = w / 2
    val cy = h * 0.52f
    drawRect(
        AccentOrange,
        topLeft = Offset(cx - crossW / 2, cy - crossH / 2),
        size = Size(crossW, crossH)
    )
    drawRect(
        AccentOrange,
        topLeft = Offset(cx - crossH / 2, cy - crossW / 2),
        size = Size(crossH, crossW)
    )
}

// হার্টবিট/পালস লাইন — চিকিৎসার বিভিন্ন বিভাগ (ডিজিজ স্পেশালিটি) বোঝাতে
private fun DrawScope.drawHeart(w: Float, h: Float) {
    val path = Path().apply {
        moveTo(0f, h * 0.55f)
        lineTo(w * 0.22f, h * 0.55f)
        lineTo(w * 0.34f, h * 0.18f)
        lineTo(w * 0.47f, h * 0.88f)
        lineTo(w * 0.58f, h * 0.38f)
        lineTo(w * 0.68f, h * 0.55f)
        lineTo(w, h * 0.55f)
    }
    drawPath(path, Color.White, style = IconStroke)
    drawCircle(AccentOrange, radius = w * 0.045f, center = Offset(w * 0.47f, h * 0.88f))
}

// ম্যাপ পিন — চেম্বার/ঠিকানা বোঝাতে
private fun DrawScope.drawPin(w: Float, h: Float) {
    val cx = w / 2
    val topCy = h * 0.34f
    val r = w * 0.32f
    drawCircle(Color.White, radius = r, center = Offset(cx, topCy))
    val tri = Path().apply {
        moveTo(cx - r * 0.78f, topCy + r * 0.55f)
        lineTo(cx + r * 0.78f, topCy + r * 0.55f)
        lineTo(cx, h * 0.95f)
        close()
    }
    drawPath(tri, Color.White)
    drawCircle(AccentOrange, radius = r * 0.42f, center = Offset(cx, topCy))
}

// শিল্ড + চেকমার্ক — বি.এম.ডি.সি ভেরিফায়েড বোঝাতে
private fun DrawScope.drawShield(w: Float, h: Float) {
    val path = Path().apply {
        moveTo(w * 0.5f, h * 0.04f)
        lineTo(w * 0.92f, h * 0.20f)
        lineTo(w * 0.92f, h * 0.54f)
        cubicTo(w * 0.92f, h * 0.80f, w * 0.72f, h * 0.95f, w * 0.5f, h * 0.98f)
        cubicTo(w * 0.28f, h * 0.95f, w * 0.08f, h * 0.80f, w * 0.08f, h * 0.54f)
        lineTo(w * 0.08f, h * 0.20f)
        close()
    }
    drawPath(path, Color.White)
    val check = Path().apply {
        moveTo(w * 0.30f, h * 0.50f)
        lineTo(w * 0.45f, h * 0.65f)
        lineTo(w * 0.72f, h * 0.35f)
    }
    drawPath(check, ShieldCheckColor, style = IconStroke)
}
