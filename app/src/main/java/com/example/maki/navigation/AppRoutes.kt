package com.example.maki.navigation

/** Top-level destinations: the pre-app auth flow plus the role apps. */
object AppRoutes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    /** "Bienvenido a Maki" — picking the actor. Only reached from "Regístrate". */
    const val ROLE = "role"
    const val REGISTER = "register"
    const val LOGIN = "login"
    const val GENERATOR = "generator"
    const val RIDER = "rider"
}
