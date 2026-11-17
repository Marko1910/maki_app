package pe.ruma.app.ui.screens.generator

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import pe.ruma.app.R
import pe.ruma.app.data.RumaRepository
import pe.ruma.app.data.ReferralInviteDto
import pe.ruma.app.data.ReferralSummaryDto
import pe.ruma.app.data.SupabaseConfig
import pe.ruma.app.ui.components.DetailHeader
import pe.ruma.app.ui.components.HeroCard
import pe.ruma.app.ui.components.RumaEmpty
import pe.ruma.app.ui.components.RumaError
import pe.ruma.app.ui.components.RumaInputField
import pe.ruma.app.ui.components.RumaList
import pe.ruma.app.ui.components.RumaLoading
import pe.ruma.app.ui.components.RumaRow
import pe.ruma.app.ui.components.PrimaryButton
import pe.ruma.app.ui.components.SecondaryButton
import pe.ruma.app.ui.components.SectionLabel
import pe.ruma.app.ui.components.SoftCard
import pe.ruma.app.ui.components.StatusChip
import pe.ruma.app.ui.components.StepDots
import pe.ruma.app.ui.components.TextAction
import pe.ruma.app.ui.components.rumaCard
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType
import kotlinx.coroutines.launch

/** "Invita y gana": everything shown comes from rpc/my_referral_summary; the app never decides a payout. */
class InviteViewModel : ViewModel() {
    var summary by mutableStateOf<ReferralSummaryDto?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var code by mutableStateOf("")
        private set
    var busy by mutableStateOf(false)
        private set
    /** Result of "Usar código": the database's own words when it refuses. */
    var applyError by mutableStateOf<String?>(null)
        private set

    fun load(force: Boolean = false) {
        if (loading || (summary != null && !force)) return
        loading = true
        error = null
        viewModelScope.launch {
            try { summary = RumaRepository.loadReferralSummary() }
            catch (e: Exception) { error = plain(e, "No pudimos cargar tus invitaciones.") }
            finally { loading = false }
        }
    }

    fun onCode(v: String) { code = v.uppercase().filter { it.isLetterOrDigit() }.take(6); applyError = null }

    fun apply() {
        if (busy || code.length < 6) return
        busy = true
        applyError = null
        viewModelScope.launch {
            try {
                RumaRepository.applyReferralCode(code)
                summary = RumaRepository.loadReferralSummary()
            } catch (e: Exception) { applyError = plain(e, "No pudimos usar ese código.") }
            finally { busy = false }
        }
    }

    // RumaApi prefixes "Error (400): "; the database already speaks Spanish.
    private fun plain(e: Exception, fallback: String) = e.message?.substringAfter("): ")?.takeIf { it.isNotBlank() } ?: fallback
}

/** The home card for a happy moment, at most once a week per account. */
object InvitePrompt {
    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    fun isDue(lastShownMs: Long, nowMs: Long): Boolean = nowMs - lastShownMs >= WEEK_MS

    /** True (and remembers today) when the card may show now. */
    fun claim(context: Context, userId: String?, nowMs: Long = System.currentTimeMillis()): Boolean {
        val prefs = context.getSharedPreferences("ruma_invite", Context.MODE_PRIVATE)
        val key = "shown_${userId.orEmpty()}"
        if (!isDue(prefs.getLong(key, 0L), nowMs)) return false
        prefs.edit().putLong(key, nowMs).apply()
        return true
    }
}

/** Staging builds share the staging PWA; a production build shares the public one. */
internal fun inviteLink(code: String): String =
    (if ("mbbdrmicjobydprvbpsj" in SupabaseConfig.URL) "https://ruma-staging.vercel.app" else "https://ruma-recicla.vercel.app") + "/?ref=$code"

internal fun inviteMessage(code: String, welcomePoints: Int): String =
    "¡Hola! Reciclo con Ruma: un Eco-Rider pasa por mi casa a recoger las botellas y el cartón, y gano puntos. " +
        "Regístrate con mi código $code y te regalan ${groupInt(welcomePoints)} pts de bienvenida cuando pesen tu primer reciclaje. " +
        inviteLink(code)

/** WhatsApp is what Peru uses; without it, the phone's own share sheet. */
private fun shareOnWhatsApp(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    try {
        context.startActivity(Intent(send).setPackage("com.whatsapp"))
    } catch (e: ActivityNotFoundException) {
        runCatching { context.startActivity(Intent.createChooser(send, "Compartir tu código")) }
    }
}

private fun copyCode(context: Context, code: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
        ?.setPrimaryClip(ClipData.newPlainText("Código Ruma", code))
}

/** What each invite's line says, in points only. */
internal fun inviteLine(i: ReferralInviteDto, cap: Int): Pair<String, String> = when {
    i.status == "pagado" -> "PAGADO" to "¡Te pagamos ${groupInt(i.points)} pts!"
    i.status == "rechazado" -> "NO CALIFICÓ" to when (i.reason) {
        "mismo_telefono" -> "Usa el mismo teléfono que tú"
        "misma_direccion" -> "Vive en tu misma dirección"
        "rider_vinculado" -> "Su recojo lo hizo una cuenta vinculada"
        else -> "No cumplió las reglas del programa"
    }
    i.reason == "tope_mensual" -> "EL PRÓXIMO MES" to "Llegaste a $cap este mes: se paga en su siguiente pesaje"
    else -> "PENDIENTE" to "Esperando su primer recojo"
}

@Composable
fun GeneratorInviteScreen(vm: InviteViewModel, onBack: () -> Unit) {
    InviteContent(
        summary = vm.summary, loading = vm.loading, error = vm.error,
        code = vm.code, busy = vm.busy, applyError = vm.applyError,
        onBack = onBack, onRetry = { vm.load(force = true) }, onCode = vm::onCode, onApply = vm::apply,
    )
}

@Composable
private fun InviteContent(
    summary: ReferralSummaryDto?,
    loading: Boolean,
    error: String?,
    code: String,
    busy: Boolean,
    applyError: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onCode: (String) -> Unit,
    onApply: () -> Unit,
) {
    val ctx = LocalContext.current
    LazyColumn(
        Modifier.fillMaxSize().rumaScreen().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = RumaSpace.gutter, vertical = RumaSpace.lg),
        verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
    ) {
        item { DetailHeader("Invita y gana", onBack, subtitle = "Recicla con tus vecinos") }
        val s = summary
        when {
            s == null && loading -> item { RumaLoading("Cargando tu código…") }
            s == null -> item { RumaError(error ?: "No pudimos cargar tus invitaciones.", onRetry) }
            else -> {
                item {
                    HeroCard(
                        RumaColors.Gen, "TU CÓDIGO", s.code,
                        "Tú ganas ${groupInt(s.referrer_points)} pts y tu vecino ${groupInt(s.referred_points)} pts cuando el acopio pese su primer reciclaje.",
                    ) {
                        Image(
                            painterResource(R.drawable.ruma_5), null,
                            Modifier.size(88.dp).align(Alignment.End), contentScale = ContentScale.Fit,
                        )
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
                        PrimaryButton(
                            "Compartir por WhatsApp", Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Filled.Send,
                            onClick = { shareOnWhatsApp(ctx, inviteMessage(s.code, s.referred_points)) },
                        )
                        SecondaryButton("Copiar código", Modifier.fillMaxWidth(), icon = Icons.Filled.ContentCopy, onClick = { copyCode(ctx, s.code) })
                    }
                }
                item {
                    SoftCard(Modifier.fillMaxWidth(), gap = RumaSpace.md) {
                        SectionLabel("Cómo funciona")
                        StepDots(
                            listOf("Comparte" to "tu código", "Tu vecino recicla" to "y pide su recojo", "Lo pesan" to "y ganan los dos"),
                            current = 0,
                        )
                        Text(
                            "Cuenta desde ${trimKg(s.min_kg)} kg pesados en el acopio. Hasta ${s.monthly_cap} invitados pagados por mes. " +
                                "No vale para tu misma casa ni tu mismo teléfono.",
                            style = RumaType.bodySmall, color = RumaColors.Text2,
                        )
                        Text(
                            "Este mes: ${s.paid_this_month} de ${s.monthly_cap} · Ganaste ${groupInt(s.earned_points)} pts invitando",
                            style = RumaType.rowTitle, color = RumaColors.Text,
                        )
                    }
                }
                s.invited_by?.let { by ->
                    item {
                        SoftCard(Modifier.fillMaxWidth(), gap = RumaSpace.xs) {
                            Text("Te invitó ${by.name ?: "un vecino"}", style = RumaType.rowTitle, color = RumaColors.Text)
                            Text(
                                when (by.status) {
                                    "pagado" -> "Recibiste ${groupInt(by.points)} pts de bienvenida."
                                    "rechazado" -> "Esta invitación no calificó."
                                    else -> "Tus ${groupInt(s.referred_points)} pts de bienvenida llegan cuando pesen tu primer reciclaje."
                                },
                                style = RumaType.bodySmall, color = RumaColors.Text2,
                            )
                        }
                    }
                }
                if (s.can_apply) item {
                    SoftCard(Modifier.fillMaxWidth(), gap = RumaSpace.md) {
                        RumaInputField(
                            "¿TE INVITÓ ALGUIEN? CÓDIGO", Icons.Filled.CardGiftcard, code, onCode,
                            placeholder = "6 letras", imeAction = ImeAction.Done, enabled = !busy,
                            helper = "Solo en tus primeros ${s.attach_days} días y antes de tu primer pesaje.",
                        )
                        applyError?.let { Text(it, style = RumaType.bodySmall, color = RumaColors.Error) }
                        SecondaryButton(if (busy) "Revisando…" else "Usar código", Modifier.fillMaxWidth(), enabled = !busy && code.length == 6, onClick = onApply)
                    }
                }
                item { SectionLabel("Tus invitados") }
                if (s.invites.isEmpty()) item {
                    RumaEmpty(
                        Icons.Outlined.GroupAdd, "Aún no invitas a nadie",
                        "Comparte tu código por WhatsApp. Cuando pesen su primer reciclaje, te pagamos ${groupInt(s.referrer_points)} pts.",
                        accent = RumaColors.Gen,
                    )
                } else item {
                    RumaList(s.invites) { i ->
                        val (chip, line) = inviteLine(i, s.monthly_cap)
                        RumaRow(
                            i.name ?: "Tu invitado", subtitle = line, chevron = false,
                            trailing = {
                                StatusChip(
                                    chip,
                                    when (i.status) { "pagado" -> RumaColors.Success; "rechazado" -> RumaColors.Text2; else -> RumaColors.Money },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun trimKg(kg: Double): String = if (kg % 1.0 == 0.0) kg.toInt().toString() else kg.toString().replace('.', ',')

/** The home card: one calm line at a happy moment, never a pop-up. */
@Composable
fun InvitePromptCard(onOpen: () -> Unit, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().rumaCard()
            .clickable(onClickLabel = "Abrir Invita y gana", role = Role.Button, onClick = onOpen)
            .then(Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RumaSpace.md),
    ) {
        Image(painterResource(R.drawable.ruma_5), null, Modifier.size(64.dp), contentScale = ContentScale.Fit)
        Column(Modifier.weight(1f).padding(vertical = RumaSpace.md), verticalArrangement = Arrangement.spacedBy(RumaSpace.xs)) {
            Text("¿Te gusta Ruma? Invita a un vecino", style = RumaType.rowTitle, color = RumaColors.Text)
            Text("Cuando pesen su primer reciclaje, ganan puntos los dos.", style = RumaType.bodySmall, color = RumaColors.Text2)
            TextAction("Invitar", onClick = onOpen)
        }
        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Ahora no", tint = RumaColors.Text2) }
    }
}

private val SampleInvite = ReferralSummaryDto(
    code = "R4NQUS", referrer_points = 2000, referred_points = 1000, paid_this_month = 1, earned_points = 2000,
    invites = listOf(
        ReferralInviteDto("Fam. Torres", "pagado", points = 2000),
        ReferralInviteDto("Fam. Rojas", "pendiente"),
    ),
)

@Preview(showBackground = true, widthDp = 390, heightDp = 1100)
@Composable
private fun InvitePreview() {
    RumaTheme {
        InviteContent(SampleInvite, false, null, "", false, null, {}, {}, {}, {})
    }
}
