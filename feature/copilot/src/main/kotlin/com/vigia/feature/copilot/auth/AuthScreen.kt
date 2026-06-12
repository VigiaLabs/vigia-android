package com.vigia.feature.copilot.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vigia.feature.copilot.OrbState
import com.vigia.feature.copilot.orb.AiOrb
import com.vigia.feature.copilot.theme.VigiaTheme
import com.vigia.feature.copilot.theme.pressScale
import com.vigia.feature.copilot.theme.vigiaColors

/**
 * Stateless authentication surface — Welcome · Sign in · Sign up · Confirm.
 * Reuses the app's VIGIA theme, orb and glass language so it feels native.
 */
@Composable
internal fun AuthScreen(
    ui: AuthUiState,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onName: (String) -> Unit,
    onCode: (String) -> Unit,
    onGoTo: (AuthStep) -> Unit,
    onSignIn: () -> Unit,
    onSignUp: () -> Unit,
    onConfirm: () -> Unit,
    onResend: () -> Unit,
    onGoogle: (Activity) -> Unit,
) {
    VigiaTheme {
        val activity = LocalContext.current.findActivity()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 20.dp),
            ) {
                AnimatedContent(
                    targetState = ui.step,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
                    label = "auth_step",
                ) { step ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        when (step) {
                            AuthStep.Welcome -> WelcomeContent(onGoTo, onGoogle, activity, ui)
                            AuthStep.SignIn  -> SignInContent(ui, onEmail, onPassword, onSignIn, onGoogle, activity, onGoTo)
                            AuthStep.SignUp  -> SignUpContent(ui, onName, onEmail, onPassword, onSignUp, onGoogle, activity, onGoTo)
                            AuthStep.Confirm -> ConfirmContent(ui, onCode, onConfirm, onResend, onGoTo)
                        }
                    }
                }
            }
        }
    }
}

// ── Steps ───────────────────────────────────────────────────────────────────

@Composable
private fun WelcomeContent(
    onGoTo: (AuthStep) -> Unit,
    onGoogle: (Activity) -> Unit,
    activity: Activity?,
    ui: AuthUiState,
) {
    Spacer(Modifier.height(48.dp))
    AiOrb(state = OrbState.Idle, size = 132.dp)
    Spacer(Modifier.height(28.dp))
    Text("vigia", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onBackground)
    Spacer(Modifier.height(8.dp))
    Text(
        "Your AI road copilot. Sign in to unlock VIGIA and connect your Blackbox.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(40.dp))
    GoogleButton(enabled = !ui.isSubmitting) { activity?.let(onGoogle) }
    Spacer(Modifier.height(12.dp))
    PrimaryButton("Sign up with email", loading = false, enabled = !ui.isSubmitting) { onGoTo(AuthStep.SignUp) }
    Spacer(Modifier.height(20.dp))
    LinkRow("Already have an account?", "Sign in") { onGoTo(AuthStep.SignIn) }
    ErrorText(ui.error)
}

@Composable
private fun SignInContent(
    ui: AuthUiState,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSignIn: () -> Unit,
    onGoogle: (Activity) -> Unit,
    activity: Activity?,
    onGoTo: (AuthStep) -> Unit,
) {
    Header("Welcome back", "Sign in to continue", onBack = { onGoTo(AuthStep.Welcome) })
    Spacer(Modifier.height(28.dp))
    VigiaField(ui.email, onEmail, "Email", Icons.Filled.Email, KeyboardType.Email)
    Spacer(Modifier.height(14.dp))
    PasswordField(ui.password, onPassword, imeAction = ImeAction.Done, onDone = onSignIn)
    ErrorText(ui.error)
    Spacer(Modifier.height(22.dp))
    PrimaryButton("Sign in", loading = ui.isSubmitting, enabled = !ui.isSubmitting, onClick = onSignIn)
    Spacer(Modifier.height(16.dp))
    OrDivider()
    Spacer(Modifier.height(16.dp))
    GoogleButton(enabled = !ui.isSubmitting) { activity?.let(onGoogle) }
    Spacer(Modifier.height(22.dp))
    LinkRow("New to VIGIA?", "Create account") { onGoTo(AuthStep.SignUp) }
}

@Composable
private fun SignUpContent(
    ui: AuthUiState,
    onName: (String) -> Unit,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSignUp: () -> Unit,
    onGoogle: (Activity) -> Unit,
    activity: Activity?,
    onGoTo: (AuthStep) -> Unit,
) {
    Header("Create your account", "Join VIGIA in a moment", onBack = { onGoTo(AuthStep.Welcome) })
    Spacer(Modifier.height(24.dp))
    VigiaField(ui.name, onName, "Full name", Icons.Filled.Person, KeyboardType.Text)
    Spacer(Modifier.height(14.dp))
    VigiaField(ui.email, onEmail, "Email", Icons.Filled.Email, KeyboardType.Email)
    Spacer(Modifier.height(14.dp))
    PasswordField(ui.password, onPassword, imeAction = ImeAction.Done, onDone = onSignUp)
    Text(
        "At least 8 characters, with a lowercase letter and a number.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp),
    )
    ErrorText(ui.error)
    Spacer(Modifier.height(20.dp))
    PrimaryButton("Create account", loading = ui.isSubmitting, enabled = !ui.isSubmitting, onClick = onSignUp)
    Spacer(Modifier.height(16.dp))
    OrDivider()
    Spacer(Modifier.height(16.dp))
    GoogleButton(enabled = !ui.isSubmitting) { activity?.let(onGoogle) }
    Spacer(Modifier.height(22.dp))
    LinkRow("Already have an account?", "Sign in") { onGoTo(AuthStep.SignIn) }
}

@Composable
private fun ConfirmContent(
    ui: AuthUiState,
    onCode: (String) -> Unit,
    onConfirm: () -> Unit,
    onResend: () -> Unit,
    onGoTo: (AuthStep) -> Unit,
) {
    Header("Check your email", ui.info ?: "Enter the 6-digit code we sent you", onBack = { onGoTo(AuthStep.SignUp) })
    Spacer(Modifier.height(28.dp))
    VigiaField(ui.code, onCode, "6-digit code", Icons.Filled.Lock, KeyboardType.NumberPassword)
    ErrorText(ui.error)
    Spacer(Modifier.height(22.dp))
    PrimaryButton("Verify", loading = ui.isSubmitting, enabled = ui.code.length == 6 && !ui.isSubmitting, onClick = onConfirm)
    Spacer(Modifier.height(18.dp))
    LinkRow("Didn't get it?", "Resend code", onResend)
}

// ── Shared components ─────────────────────────────────────────────────────────

@Composable
private fun Header(title: String, subtitle: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        val i = remember { MutableInteractionSource() }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .pressScale(i, 0.9f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(i, indication = null) { onBack() },
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.weight(1f))
    }
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun VigiaField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    leading: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType,
    imeAction: ImeAction = ImeAction.Next,
    visual: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        leadingIcon = { Icon(leading, null, modifier = Modifier.size(20.dp)) },
        trailingIcon = trailing,
        visualTransformation = visual,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor   = MaterialTheme.vigiaColors.glassSurface,
            unfocusedContainerColor = MaterialTheme.vigiaColors.glassSurface,
            focusedIndicatorColor   = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
            focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedLabelColor       = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor     = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor             = MaterialTheme.colorScheme.primary,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValue: (String) -> Unit,
    imeAction: ImeAction,
    onDone: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    VigiaField(
        value = value,
        onValue = onValue,
        label = "Password",
        leading = Icons.Filled.Lock,
        keyboardType = KeyboardType.Password,
        imeAction = imeAction,
        visual = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailing = {
            val i = remember { MutableInteractionSource() }
            Icon(
                imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = if (visible) "Hide password" else "Show password",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(i, indication = null) { visible = !visible }
                    .size(20.dp),
            )
        },
    )
}

@Composable
private fun PrimaryButton(text: String, loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val i = remember { MutableInteractionSource() }
    Surface(
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .pressScale(i, 0.97f)
            .clip(RoundedCornerShape(18.dp))
            .clickable(i, indication = null, enabled = enabled && !loading) { onClick() },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            } else {
                Text(
                    text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GoogleButton(enabled: Boolean, onClick: () -> Unit) {
    val i = remember { MutableInteractionSource() }
    Surface(
        color = MaterialTheme.vigiaColors.glassSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .pressScale(i, 0.97f)
            .clip(RoundedCornerShape(18.dp))
            .clickable(i, indication = null, enabled = enabled) { onClick() },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            // Brand "G" badge
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(22.dp).clip(CircleShape).background(Color.White),
            ) {
                Text("G", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF4285F4))
            }
            Spacer(Modifier.width(12.dp))
            Text("Continue with Google", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun OrDivider() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Text("or", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    }
}

@Composable
private fun LinkRow(prompt: String, action: String, onClick: () -> Unit) {
    val i = remember { MutableInteractionSource() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(prompt, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(
            action,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(i, indication = null) { onClick() },
        )
    }
}

@Composable
private fun ErrorText(error: String?) {
    if (error != null) {
        Spacer(Modifier.height(10.dp))
        Text(
            error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
