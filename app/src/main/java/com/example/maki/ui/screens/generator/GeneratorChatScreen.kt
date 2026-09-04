package com.example.maki.ui.screens.generator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.maki.data.ChatConversationDto
import com.example.maki.data.ChatTurnDto
import com.example.maki.data.MakiRepository
import com.example.maki.ui.components.makiShadow
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont
import kotlinx.coroutines.launch

private data class ChatMsg(val fromUser: Boolean, val text: String)

@Composable
fun GeneratorChatScreen(onBack: () -> Unit = {}, onInfo: (String) -> Unit = {}) {
    val messages = remember { mutableStateListOf<ChatMsg>() }
    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    var convId by remember { mutableStateOf<String?>(null) }
    var conversations by remember { mutableStateOf<List<ChatConversationDto>>(emptyList()) }
    var showHistory by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    /** Swaps the visible thread for a stored conversation. */
    suspend fun open(conversation: ChatConversationDto) {
        val history = MakiRepository.loadChatMessages(conversation.id)
        convId = conversation.id
        messages.clear()
        history.forEach { messages.add(ChatMsg(fromUser = it.role == "user", text = it.content)) }
    }

    // Resume the most recent conversation so re-entering the chat keeps its context.
    LaunchedEffect(Unit) {
        runCatching {
            conversations = MakiRepository.loadConversations()
            conversations.firstOrNull()?.let { open(it) }
        }
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || thinking) return
        messages.add(ChatMsg(fromUser = true, text = t))
        input = ""
        thinking = true
        scope.launch {
            // Persistence is best-effort: offline, the rule fallback still answers
            // and the exchange simply lives in memory for this visit.
            val cid = convId
                ?: runCatching { MakiRepository.createConversation(t.take(60)) }.getOrNull()?.also { convId = it }
            if (cid != null) runCatching { MakiRepository.saveChatMessage(cid, "user", t) }
            // Only the recent turns travel — a fresh conversation keeps the prompt light.
            val turns = messages.takeLast(8).map { ChatTurnDto(if (it.fromUser) "user" else "assistant", it.text) }
            val reply = runCatching { MakiRepository.askAssistant(turns) }.getOrElse { makiReply(t) }
            messages.add(ChatMsg(fromUser = false, text = reply))
            if (cid != null) runCatching { MakiRepository.saveChatMessage(cid, "assistant", reply) }
            thinking = false
        }
    }

    // Keep the newest message in view: maxValue changes when content grows, when the
    // stored history loads, and when the keyboard resizes the viewport.
    LaunchedEffect(Unit) {
        snapshotFlow { scroll.maxValue }.collect { scroll.animateScrollTo(it) }
    }

    Column(Modifier.fillMaxSize().background(MakiColors.Bg).statusBarsPadding().imePadding()) {
        ChatHeader(
            onBack = onBack,
            onHistory = {
                showHistory = true
                scope.launch { runCatching { conversations = MakiRepository.loadConversations() } }
            },
            onNewChat = {
                convId = null
                messages.clear()
                onInfo("Nueva conversación lista.")
            },
        )
        Column(
            Modifier.weight(1f).verticalScroll(scroll).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ChatMapCard()
            MakiBubble {
                Text("¡Hola! Soy Maki, tu agente de reciclaje. ¿En qué te ayudo hoy?", color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
                Text("Pregúntame sobre clasificación, puntos de acopio y más.", color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
            }
            messages.forEach { msg ->
                if (msg.fromUser) UserBubble(msg.text)
                else MakiBubble {
                    Text(msg.text, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
            if (thinking) MakiBubble {
                Text("Maki está escribiendo…", color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            }
        }
        QuickReplies(onPick = { send(it) })
        ChatInputBar(value = input, onValueChange = { input = it }, onSend = { send(input) }, onMic = { onInfo("Dictado por voz disponible pronto.") })
    }

    if (showHistory) {
        ChatHistorySheet(
            conversations = conversations,
            onPick = { conversation ->
                showHistory = false
                scope.launch {
                    runCatching { open(conversation) }
                        .onFailure { onInfo("No se pudo cargar la conversación.") }
                }
            },
            onDismiss = { showHistory = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatHistorySheet(
    conversations: List<ChatConversationDto>,
    onPick: (ChatConversationDto) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MakiColors.Surface) {
        Text(
            "Conversaciones",
            color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
        )
        if (conversations.isEmpty()) {
            Text(
                "Aún no tienes conversaciones guardadas.",
                color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                items(conversations, key = { it.id }) { c ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable { onPick(c) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            c.title ?: "Conversación",
                            color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            (c.last_message_at ?: c.created_at).take(10),
                            color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

/** Rule-based educational replies (placeholder for the future Maki agent). */
private fun makiReply(q: String): String {
    val t = q.lowercase()
    return when {
        "tetra" in t -> "El tetra pack es material compuesto: en Perú va al contenedor AMARILLO. Tip: aplástalo para ahorrar espacio."
        "pila" in t || "batería" in t || "bateria" in t -> "Las pilas son residuo peligroso: no van al tacho común. Llévalas a un punto de acopio especial."
        "tapita" in t || "tapa" in t -> "¡Las tapitas sí se reciclan! Son plástico HDPE. Júntalas aparte y entrégalas a campañas de tapitas."
        "vidrio" in t -> "El vidrio va al contenedor VERDE. Enjuágalo y retira las tapas metálicas."
        "color" in t || "contenedor" in t -> "En Perú: amarillo (plásticos/tetra), verde (vidrio), azul (papel/cartón), y puntos especiales para peligrosos."
        "pet" in t || "botella" in t -> "El PET (botellas) va al contenedor amarillo. Vacíalo, enjuágalo y aplástalo."
        "carton" in t || "cartón" in t || "papel" in t -> "Papel y cartón van al contenedor AZUL, secos y sin grasa."
        "hola" in t || "buenas" in t -> "¡Hola! Soy Maki. Pregúntame sobre cómo clasificar tus residuos."
        "gracias" in t -> "¡De nada! Sigue reciclando para sumar Eco-Puntos."
        else -> "Buena pregunta. En general: separa por material (plástico, vidrio, papel, metal) y mantenlos limpios y secos. ¿Sobre qué material quieres saber más?"
    }
}

@Composable
private fun ChatHeader(onBack: () -> Unit, onHistory: () -> Unit = {}, onNewChat: () -> Unit = {}) {
    Row(
        Modifier.makiShadow(0.dp, 6.dp).background(MakiColors.Surface).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(MakiColors.Bg).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = MakiColors.Text, modifier = Modifier.size(20.dp))
        }
        Box(Modifier.size(42.dp).clip(CircleShape).background(MakiColors.Gen), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Eco, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Maki · Agente Educativo", color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(MakiColors.Success))
                Text("En línea", color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }
        Icon(
            Icons.Outlined.History, "Historial de conversaciones",
            tint = MakiColors.Text2,
            modifier = Modifier.size(22.dp).clickable(onClick = onHistory),
        )
        Icon(
            Icons.Filled.Add, "Nueva conversación",
            tint = MakiColors.Gen,
            modifier = Modifier.size(24.dp).clickable(onClick = onNewChat),
        )
    }
}

@Composable
private fun ChatMapCard() {
    Box(Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFDDE6E1))) {
        Box(Modifier.align(Alignment.Center).offset(x = (-90).dp, y = 12.dp).size(30.dp).clip(CircleShape).border(2.dp, Color.White, CircleShape).background(MakiColors.Rider), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.DirectionsBike, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Box(Modifier.align(Alignment.Center).offset(x = 110.dp, y = (-25).dp).size(28.dp).clip(CircleShape).border(2.dp, Color.White, CircleShape).background(MakiColors.Gen), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Place, null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
        Row(
            Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xF2FFFFFF)).padding(start = 8.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Navigation, null, tint = MakiColors.Rider, modifier = Modifier.size(13.dp))
            Text("Rider a 2 cuadras", color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MakiBubble(content: @Composable ColumnScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(MakiColors.Gen), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Eco, null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
        Column(
            Modifier.widthIn(max = 286.dp).makiShadow(16.dp, 3.dp)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp))
                .background(MakiColors.Surface).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), content = content,
        )
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier.widthIn(max = 250.dp).clip(RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 16.dp))
                .background(MakiColors.Gen).padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(text, color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun QuickReplies(onPick: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("¿Qué color va cada cosa?", "¿Dónde reciclo pilas?", "¿Tapitas?").forEach {
            Box(
                Modifier.clip(RoundedCornerShape(20.dp)).background(MakiColors.Surface)
                    .border(1.2.dp, MakiColors.Gen, RoundedCornerShape(20.dp))
                    .clickable { onPick(it) }
                    .padding(horizontal = 13.dp, vertical = 8.dp),
            ) {
                Text(it, color = MakiColors.Gen, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ChatInputBar(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit, onMic: () -> Unit) {
    Row(
        Modifier.makiShadow(0.dp, 8.dp).background(MakiColors.Surface).fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(MakiColors.Bg).border(1.dp, MakiColors.Border, RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text("Escribe tu duda...", color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MakiColors.Gen),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Icon(Icons.Filled.Mic, "Dictar", tint = MakiColors.Text2, modifier = Modifier.size(19.dp).clickable(onClick = onMic))
        }
        Box(
            Modifier.makiShadow(24.dp, 4.dp, Color(0x400F6E56)).size(48.dp).clip(CircleShape).background(MakiColors.Gen).clickable(onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, "Enviar", tint = Color.White, modifier = Modifier.size(21.dp))
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ChatPreview() {
    MAKITheme { GeneratorChatScreen() }
}
