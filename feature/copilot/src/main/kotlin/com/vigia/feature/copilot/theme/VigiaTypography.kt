package com.vigia.feature.copilot.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vigia.feature.copilot.R

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage   = "com.google.android.gms",
    certificates      = R.array.com_google_android_gms_fonts_certs,
)

private val Inter = GoogleFont("Inter")
private val Lora  = GoogleFont("Lora")

private val InterFamily = FontFamily(
    Font(googleFont = Inter, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = Inter, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = Inter, fontProvider = provider, weight = FontWeight.SemiBold),
)

// Serif voice for greetings and hero copy — the "human" register of the UI.
private val LoraFamily = FontFamily(
    Font(googleFont = Lora, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = Lora, fontProvider = provider, weight = FontWeight.Medium),
)

val VigiaTypography = Typography(
    // Serif display — greeting header, hero statements
    displayLarge   = TextStyle(fontFamily = LoraFamily, fontSize = 40.sp, fontWeight = FontWeight.Medium, lineHeight = 46.sp, letterSpacing = (-0.01).em),
    displayMedium  = TextStyle(fontFamily = LoraFamily, fontSize = 32.sp, fontWeight = FontWeight.Medium, lineHeight = 40.sp, letterSpacing = (-0.01).em),
    displaySmall   = TextStyle(fontFamily = LoraFamily, fontSize = 24.sp, fontWeight = FontWeight.Medium, lineHeight = 32.sp),
    // Section headings
    headlineLarge  = TextStyle(fontFamily = InterFamily, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontFamily = InterFamily, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    headlineSmall  = TextStyle(fontFamily = InterFamily, fontSize = 18.sp, fontWeight = FontWeight.Medium,   lineHeight = 24.sp),
    // Card titles / list rows
    titleLarge     = TextStyle(fontFamily = InterFamily, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    titleMedium    = TextStyle(fontFamily = InterFamily, fontSize = 16.sp, fontWeight = FontWeight.Medium,   lineHeight = 22.sp),
    titleSmall     = TextStyle(fontFamily = InterFamily, fontSize = 14.sp, fontWeight = FontWeight.Medium,   lineHeight = 20.sp),
    // Body
    bodyLarge      = TextStyle(fontFamily = InterFamily, fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 25.sp),
    bodyMedium     = TextStyle(fontFamily = InterFamily, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp),
    bodySmall      = TextStyle(fontFamily = InterFamily, fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 17.sp),
    // Labels / chips / tags
    labelLarge  = TextStyle(fontFamily = InterFamily, fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.01.em, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = InterFamily, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.04.em, lineHeight = 16.sp),
    labelSmall  = TextStyle(fontFamily = InterFamily, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.08.em, lineHeight = 14.sp),
)

// Editorial serif for the copilot's answers — relaxed leading, reads like an article.
internal val AnswerBodyStyle = TextStyle(
    fontFamily = LoraFamily, fontSize = 17.sp, fontWeight = FontWeight.Normal, lineHeight = 27.sp,
)
