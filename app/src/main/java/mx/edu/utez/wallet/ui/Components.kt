package mx.edu.utez.wallet.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.EncodeHintType
import mx.edu.utez.wallet.core.*
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale

val Mint = Color(0xFF10B981)
val Slate = Color(0xFF0F172A)
val Panel = Color(0xFF182438)
val Muted = Color(0xFF9AAEC4)
val Warning = Color(0xFFFBBF24)
val Danger = Color(0xFFFB7185)
private val palette = darkColorScheme(primary = Mint, onPrimary = Color(0xFF002D21), secondary = Mint,
    background = Slate, surface = Slate, surfaceVariant = Panel, onSurface = Color(0xFFF3F6FA),
    onSurfaceVariant = Muted, outline = Color(0xFF334155), error = Danger)

@Composable fun WalletTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = palette, content = content)
fun money(value: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX")).apply { maximumFractionDigits = 2 }.format(BigDecimal.valueOf(value, 2))
fun cents(value: String): Long? = runCatching {
    val normalized = value.trim()
    require(normalized.matches(Regex("[0-9]{1,5}(\\.[0-9]{1,2})?")))
    BigDecimal(normalized).movePointRight(2).longValueExact()
}.getOrNull()
fun date(value: Long): String = DateTimeFormatter.ofPattern("dd MMM · HH:mm", Locale.forLanguageTag("es-MX")).format(Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()))

@Composable fun Eyebrow(text: String) { Text(text, color = Mint, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold) }
@Composable fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        subtitle?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodyMedium) }
    }
}
@Composable fun PanelCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}
@Composable fun StatusPill(active: Boolean) {
    Surface(color = (if (active) Mint else Danger).copy(alpha = .12f), shape = RoundedCornerShape(20.dp)) {
        Text(if (active) "● ACTIVO" else "● INACTIVO", Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = if (active) Mint else Danger, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}
@Composable fun InfoBox(text: String, warning: Boolean = false) {
    Surface(color = (if (warning) Warning else Mint).copy(alpha = .08f), shape = RoundedCornerShape(14.dp)) {
        Text(text, Modifier.padding(14.dp), color = if (warning) Warning else Muted, style = MaterialTheme.typography.bodySmall)
    }
}
@Composable fun ActionButton(text: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = enabled, shape = RoundedCornerShape(14.dp)) {
        Icon(icon, contentDescription = null, Modifier.size(19.dp)); Spacer(Modifier.width(10.dp)); Text(text, fontWeight = FontWeight.SemiBold)
    }
}
@Composable fun Field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    password: Boolean = false, numeric: Boolean = false, enabled: Boolean = true, supporting: String? = null) {
    OutlinedTextField(value, onChange, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        enabled = enabled, shape = RoundedCornerShape(13.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = when { password -> KeyboardType.Password; numeric -> KeyboardType.Decimal; else -> KeyboardType.Text }),
        supportingText = supporting?.let { { Text(it) } })
}
@Composable fun Avatar(user: UserProfile, size: Int = 60) {
    val bitmap = remember(user.photoBase64) { runCatching {
        user.photoBase64?.let { Base64.getDecoder().decode(it) }?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }.getOrNull() }
    Box(Modifier.size(size.dp).clip(CircleShape).background(Mint.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap.asImageBitmap(), "Fotografía de ${user.name}", Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        else Text(user.name.split(' ').take(2).mapNotNull { it.firstOrNull() }.joinToString(""), color = Mint, fontWeight = FontWeight.Bold, fontSize = (size / 3).sp)
    }
}
@Composable fun MovementRow(item: Movement) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background((if (item.kind == MovementKind.COMPRA) Muted else Mint).copy(alpha = .12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Text(when(item.kind) { MovementKind.RECARGA -> "↗"; MovementKind.COMPRA -> "↙"; MovementKind.ACCESO -> "→" }, color = if(item.kind == MovementKind.COMPRA) Muted else Mint, fontSize = 22.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(item.title, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
            Text(date(item.timestamp), color = Muted, fontSize = 11.sp)
        }
        Text(if (item.kind == MovementKind.ACCESO) "Acceso" else (if (item.amountCents > 0) "+" else "") + money(item.amountCents),
            color = if (item.amountCents > 0) Mint else Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
@Composable fun QrImage(value: String) {
    val bitmap = remember(value) {
        val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 720, 720,
            mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 3))
        Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888).also { output ->
            output.setPixels(IntArray(720 * 720) { index -> if (matrix[index % 720, index / 720]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }, 0, 720, 0, 0, 720, 720)
        }
    }
    Image(bitmap.asImageBitmap(), "Código QR temporal", Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)))
}
