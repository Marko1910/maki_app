package com.example.maki.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.maki.ui.screens.auth.AuthViewModel
import com.example.maki.ui.screens.auth.LoginScreen
import com.example.maki.ui.screens.auth.OnboardingScreen
import com.example.maki.ui.screens.auth.RegisterScreen
import com.example.maki.ui.screens.auth.RoleSelectScreen
import com.example.maki.ui.screens.auth.SplashScreen

/**
 * Top-level graph: splash → onboarding → login → generator/rider app.
 *
 * Onboarding lands on **login**, not on the role picker: choosing an actor is a
 * sign-up decision, so "Bienvenido a Maki" hangs off "Regístrate" alone
 * (login → role → register).
 */
@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val auth: AuthViewModel = viewModel()
    val context = LocalContext.current
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }

    NavHost(navController = nav, startDestination = AppRoutes.SPLASH) {
        composable(AppRoutes.SPLASH) {
            LaunchedEffect(Unit) {
                auth.decideStart { dest ->
                    nav.navigate(dest) { popUpTo(AppRoutes.SPLASH) { inclusive = true } }
                }
            }
            SplashScreen()
        }
        composable(AppRoutes.ONBOARDING) {
            OnboardingScreen(onFinish = { address, lat, lng ->
                auth.finishOnboarding(address, lat, lng)
                nav.navigate(AppRoutes.LOGIN) { popUpTo(AppRoutes.ONBOARDING) { inclusive = true } }
            })
        }
        composable(AppRoutes.ROLE) {
            RoleSelectScreen(
                onGenerator = { auth.selectRole("generador", forSignUp = true); nav.navigate(AppRoutes.REGISTER) },
                onRider = { auth.selectRole("eco_rider", forSignUp = true); nav.navigate(AppRoutes.REGISTER) },
                onLogin = { nav.popBackStack(AppRoutes.LOGIN, inclusive = false) },
                onInfo = toast,
            )
        }
        composable(AppRoutes.REGISTER) {
            RegisterScreen(
                vm = auth,
                onSuccess = { nav.navigate(auth.homeRoute()) { popUpTo(nav.graph.id) { inclusive = true } } },
                onBack = { nav.popBackStack() },
                onLogin = { nav.popBackStack(AppRoutes.LOGIN, inclusive = false) },
            )
        }
        composable(AppRoutes.LOGIN) {
            LoginScreen(
                vm = auth,
                onSuccess = { nav.navigate(auth.homeRoute()) { popUpTo(nav.graph.id) { inclusive = true } } },
                onRegister = { nav.navigate(AppRoutes.ROLE) { launchSingleTop = true } },
                onInfo = toast,
            )
        }
        composable(AppRoutes.GENERATOR) {
            GeneratorRoot(
                onLogout = {
                    nav.navigate(AppRoutes.LOGIN) { popUpTo(nav.graph.id) { inclusive = true } }
                },
            )
        }
        composable(AppRoutes.RIDER) {
            RiderRoot(
                onLogout = {
                    nav.navigate(AppRoutes.LOGIN) { popUpTo(nav.graph.id) { inclusive = true } }
                },
            )
        }
    }
}
