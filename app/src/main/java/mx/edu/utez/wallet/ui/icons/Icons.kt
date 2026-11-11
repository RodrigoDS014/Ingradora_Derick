package mx.edu.utez.wallet.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Only the vectors used by this application, keeping the debug APK lightweight. */
object Icons {
    object Outlined {
        val AccountBalanceWallet = icon("Wallet", "M4,5H19V8H5A2,2 0,0 1,5,4H17", "M4,5V19H21V8H4", "M21,11H16V16H21", "M18,13.5H18.1")
        val ArrowForward = icon("Forward", "M5,12H19M13,6L19,12L13,18")
        val Badge = icon("Badge", "M9,4V2H15V4M4,5H20V21H4Z", "M8,10A2,2 0,1 0,12,10A2,2 0,1 0,8,10", "M6,17Q10,13 14,17M16,10H18M16,14H18")
        val QrCode2 = icon("Qr", "M3,3H9V9H3ZM15,3H21V9H15ZM3,15H9V21H3Z", "M15,15H18V18H21V21H15ZM21,12V15M12,3V6M12,12H15M12,18V21")
        val Add = icon("Add", "M5,12H19M12,5V19")
        val Remove = icon("Remove", "M5,12H19")
        val ShoppingBag = icon("Bag", "M5,7H19L20,21H4ZM9,7V5A3,3 0,0 1,15,5V7")
        val Coffee = icon("Coffee", "M4,7H17V14A5,5 0,0 1,7,14V7M17,8H20V13H17M3,21H20M9,2V4M13,2V4")
        val Restaurant = icon("Restaurant", "M5,3V8Q8,12 11,8V3M8,3V21M17,3Q14,7 17,12H20V3M20,12V21")
        val PersonAdd = icon("PersonAdd", "M6,5A3,3 0,1 0,12,5A3,3 0,1 0,6,5M3,21V18Q9,12 15,18V21M18,8H24M21,5V11")
        val Edit = icon("Edit", "M4,16L15,5L19,9L8,20H4ZM15,5L17,3L21,7L19,9")
        val Verified = icon("Verified", "M12,2L15,5L19,5L19,9L22,12L19,15L19,19L15,19L12,22L9,19L5,19L5,15L2,12L5,9L5,5L9,5Z", "M8,12L11,15L16,9")
        val PhotoCamera = icon("Camera", "M3,7H7L9,4H15L17,7H21V20H3ZM8,13A4,4 0,1 0,16,13A4,4 0,1 0,8,13")
        val Lock = icon("Lock", "M5,10H19V21H5ZM8,10V6A4,4 0,0 1,16,6V10M12,14V17")
        val Home = icon("Home", "M2,11L12,3L22,11M5,9V21H10V14H14V21H19V9")
        val ReceiptLong = icon("Receipt", "M5,2L8,4L12,2L16,4L19,2V22L16,20L12,22L8,20L5,22ZM8,8H16M8,12H16M8,16H13")
        val Person = icon("Person", "M8,6A4,4 0,1 0,16,6A4,4 0,1 0,8,6M4,21V18Q12,11 20,18V21")
        val People = icon("People", "M5,6A3,3 0,1 0,11,6A3,3 0,1 0,5,6M14,5A3,3 0,1 1,18,10M2,21V18Q8,12 14,18V21M16,15Q21,15 22,18V21")
        val QrCodeScanner = icon("Scanner", "M2,7V2H7M17,2H22V7M22,17V22H17M7,22H2V17", "M6,6H10V10H6ZM14,6H18V10H14ZM6,14H10V18H6ZM14,14H18V18H14Z")
        val Refresh = icon("Refresh", "M20,8A8,8 0,1 0,20,16M20,3V8H15")
        val CheckCircle = icon("CheckCircle", "M3,12A9,9 0,1 0,21,12A9,9 0,1 0,3,12M7,12L11,16L17,8")
    }
    private fun icon(name: String, vararg paths: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { data -> addPath(pathData = addPathNodes(data), fill = null, stroke = SolidColor(Color.White),
            strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) }
    }.build()
}
