package com.example.maki.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Minimal Supabase client over the PostgREST + GoTrue REST APIs.
 * Keeps a single signed-in session token in memory. Designed so it can be
 * swapped for the official supabase-kt SDK later without touching the UI.
 */
object MakiApi {
    // Generous read timeout: detect-material/maki-assistant hold the request while
    // the vision/chat model runs (~6-10s server-side); OkHttp's default 10s cut
    // these off mid-flight and surfaced as fake "connection" failures.
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    /** Same, but writes fields that still hold their default — for INSERT bodies
     *  where "the default in Kotlin" is the value we actually want in the row. */
    val jsonFull = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }

    @Volatile
    var session: Session? = null
        private set

    val isSignedIn: Boolean get() = session != null

    /** Email + password sign-in (GoTrue password grant). */
    suspend fun signIn(email: String, password: String): Session = withContext(Dispatchers.IO) {
        val body = json.encodeToString(
            CredentialsDto.serializer(),
            CredentialsDto(email = email, password = password)
        ).toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("${SupabaseConfig.AUTH}/token?grant_type=password")
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .post(body)
            .build()

        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("Sign-in failed (${resp.code}): $text")
            val dto = json.decodeFromString(SessionDto.serializer(), text)
            Session(
                accessToken = dto.access_token,
                refreshToken = dto.refresh_token,
                userId = dto.user?.id ?: error("Missing user id"),
                email = dto.user?.email,
            ).also { session = it }
        }
    }

    /**
     * Creates the account (GoTrue sign-up). When the project has email confirmation
     * on, the response carries no session — the caller must tell the user to confirm
     * before signing in, so a null return is a normal outcome, not a failure.
     */
    suspend fun signUp(
        email: String,
        password: String,
        role: String,
        fullName: String,
        phone: String?,
    ): Session? = withContext(Dispatchers.IO) {
        val body = json.encodeToString(
            SignUpDto.serializer(),
            SignUpDto(
                email = email,
                password = password,
                data = SignUpMetaDto(
                    role = role,
                    full_name = fullName,
                    display_name = fullName,
                    phone = phone?.takeIf { it.isNotBlank() },
                ),
            ),
        ).toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("${SupabaseConfig.AUTH}/signup")
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .post(body)
            .build()

        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException(signUpError(text, resp.code))
            val dto = json.decodeFromString(SessionDto.serializer(), text)
            val uid = dto.user?.id
            if (dto.access_token.isBlank() || uid == null) return@use null
            Session(
                accessToken = dto.access_token,
                refreshToken = dto.refresh_token,
                userId = uid,
                email = dto.user?.email,
            ).also { session = it }
        }
    }

    /** Turns GoTrue's error body into something a person can act on. */
    private fun signUpError(body: String, code: Int): String {
        val lower = body.lowercase()
        return when {
            "already registered" in lower || "already been registered" in lower ->
                "Ese correo ya tiene una cuenta. Inicia sesión."
            "password" in lower && "6" in lower -> "La contraseña debe tener al menos 6 caracteres."
            // Supabase's built-in SMTP allows only a couple of confirmation mails per
            // hour. Turning off "Confirm email" in the project removes both the wait
            // and this ceiling; until then, say what is actually happening.
            "over_email_send_rate_limit" in lower || code == 429 ->
                "Demasiados registros seguidos. Espera unos minutos e intenta otra vez."
            "invalid" in lower && "email" in lower ->
                "Ese correo no es válido. Usa uno real (por ejemplo @gmail.com)."
            else -> "No se pudo crear la cuenta ($code)."
        }
    }

    fun signOut() { session = null }

    /** Restores a previously persisted session into memory (no network call). */
    fun restoreSession(s: Session) { session = s }

    /** Exchanges a refresh token for a fresh session (GoTrue refresh grant). */
    suspend fun refresh(refreshToken: String): Session = withContext(Dispatchers.IO) {
        val body = json.encodeToString(RefreshDto.serializer(), RefreshDto(refresh_token = refreshToken))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${SupabaseConfig.AUTH}/token?grant_type=refresh_token")
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .post(body)
            .build()

        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("Refresh failed (${resp.code}): $text")
            val dto = json.decodeFromString(SessionDto.serializer(), text)
            Session(
                accessToken = dto.access_token,
                refreshToken = dto.refresh_token,
                userId = dto.user?.id ?: error("Missing user id"),
                email = dto.user?.email,
            ).also { session = it }
        }
    }

    /** GET a PostgREST resource and decode the JSON array body as text. */
    suspend fun getRaw(path: String): String = withContext(Dispatchers.IO) {
        val token = session?.accessToken ?: SupabaseConfig.ANON_KEY
        val request = Request.Builder()
            .url("${SupabaseConfig.REST}/$path")
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .get()
            .build()

        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("GET $path failed (${resp.code}): $text")
            text
        }
    }

    /** POST a JSON body to a PostgREST resource; returns the response body text. */
    suspend fun post(path: String, jsonBody: String, prefer: String = "return=representation"): String =
        withContext(Dispatchers.IO) {
            val token = session?.accessToken ?: SupabaseConfig.ANON_KEY
            val request = Request.Builder()
                .url("${SupabaseConfig.REST}/$path")
                .addHeader("apikey", SupabaseConfig.ANON_KEY)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", prefer)
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IOException(humanize(text, resp.code))
                text
            }
        }

    /** PATCH a JSON body to a PostgREST resource (filtered via the path query). */
    suspend fun patch(path: String, jsonBody: String, prefer: String = "return=minimal"): String =
        withContext(Dispatchers.IO) {
            val token = session?.accessToken ?: SupabaseConfig.ANON_KEY
            val request = Request.Builder()
                .url("${SupabaseConfig.REST}/$path")
                .addHeader("apikey", SupabaseConfig.ANON_KEY)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", prefer)
                .patch(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IOException(humanize(text, resp.code))
                text
            }
        }

    /** POSTs a JSON body to a Supabase Edge Function (`functions/v1/<name>`). */
    suspend fun callFunction(name: String, jsonBody: String): String = withContext(Dispatchers.IO) {
        val token = session?.accessToken ?: SupabaseConfig.ANON_KEY
        val request = Request.Builder()
            .url("${SupabaseConfig.URL}/functions/v1/$name")
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException(humanize(text, resp.code))
            text
        }
    }

    /** Turns a PostgREST/Postgres/Edge Function error body into a short, user-readable message. */
    private fun humanize(body: String, code: Int): String {
        val msg = try {
            json.parseToJsonElement(body).let { el ->
                val obj = el as? kotlinx.serialization.json.JsonObject
                // PostgREST uses "message"; the Edge Functions use "error" (+ optional "detail").
                listOfNotNull("message", "detail", "error")
                    .firstNotNullOfOrNull { key ->
                        (obj?.get(key) as? kotlinx.serialization.json.JsonPrimitive)?.content
                    }
            }
        } catch (e: Exception) { null } ?: body
        return when {
            msg.contains("Insufficient points", ignoreCase = true) -> "Saldo insuficiente para este canje."
            msg.contains("must be positive", ignoreCase = true) -> "Monto de canje inválido."
            else -> "Error ($code): $msg"
        }
    }
}

data class Session(
    val accessToken: String,
    val refreshToken: String?,
    val userId: String,
    val email: String?,
)
