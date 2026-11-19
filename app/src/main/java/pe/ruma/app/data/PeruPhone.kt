package pe.ruma.app.data

/**
 * A Peruvian mobile number as the rider dials it: "+51" and 9 digits starting with 9.
 * Accepts what people type ("987 654 321", "+51 987654321", "0051-987-654-321").
 * Twin of `pwa/src/lib/phone.ts`.
 */
object PeruPhone {
    fun normalize(raw: String?): String? {
        val digits = raw.orEmpty().filter { it.isDigit() }.removePrefix("00").let {
            if (it.length == 11 && it.startsWith("51")) it.drop(2) else it
        }
        return if (digits.length == 9 && digits.startsWith("9")) "+51$digits" else null
    }

    const val HINT = "Escribe tu celular: 9 dígitos y empieza con 9."
}
