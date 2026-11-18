package com.example.xtreamtv
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
internal val SkyLikeFamily = FontFamily(
    Font(R.font.shark_tv_sans_regular, FontWeight.Normal),
    Font(R.font.shark_tv_sans_bold, FontWeight.Bold)
)
internal fun skyLikeTypography(): Typography {
    val t = Typography()
    return Typography(
        displayLarge=t.displayLarge.copy(fontFamily=SkyLikeFamily), displayMedium=t.displayMedium.copy(fontFamily=SkyLikeFamily), displaySmall=t.displaySmall.copy(fontFamily=SkyLikeFamily),
        headlineLarge=t.headlineLarge.copy(fontFamily=SkyLikeFamily), headlineMedium=t.headlineMedium.copy(fontFamily=SkyLikeFamily), headlineSmall=t.headlineSmall.copy(fontFamily=SkyLikeFamily),
        titleLarge=t.titleLarge.copy(fontFamily=SkyLikeFamily,fontWeight=FontWeight.Medium), titleMedium=t.titleMedium.copy(fontFamily=SkyLikeFamily,fontWeight=FontWeight.Medium), titleSmall=t.titleSmall.copy(fontFamily=SkyLikeFamily,fontWeight=FontWeight.Medium),
        bodyLarge=t.bodyLarge.copy(fontFamily=SkyLikeFamily,letterSpacing=0.08.sp), bodyMedium=t.bodyMedium.copy(fontFamily=SkyLikeFamily,letterSpacing=0.08.sp), bodySmall=t.bodySmall.copy(fontFamily=SkyLikeFamily,letterSpacing=0.08.sp),
        labelLarge=t.labelLarge.copy(fontFamily=SkyLikeFamily,fontWeight=FontWeight.Normal), labelMedium=t.labelMedium.copy(fontFamily=SkyLikeFamily,fontWeight=FontWeight.Normal), labelSmall=t.labelSmall.copy(fontFamily=SkyLikeFamily,fontWeight=FontWeight.Normal))
}
