package com.kryptos.vault.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import com.kryptos.vault.KryptosApp
import com.kryptos.vault.security.AuthManager
import com.kryptos.vault.security.BiometricAuth
import com.kryptos.vault.ui.account.AccountSheet
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.drawscope.scale

@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as? FragmentActivity
    val auth = remember { (ctx.applicationContext as KryptosApp).authManager }
    val scope = rememberCoroutineScope()

    var attempts by remember { mutableStateOf(0) }
    var account by remember { mutableStateOf<AuthManager.Account?>(auth.currentAccount) }
    var signingIn by remember { mutableStateOf(false) }
    var signInError by remember { mutableStateOf<String?>(null) }
    var showAccount by remember { mutableStateOf(false) }

    fun authenticate() {
        activity?.let {
            BiometricAuth.prompt(
                activity = it,
                title = "Unlock Kryptos",
                subtitle = "Authenticate to access your vault",
                onSuccess = onUnlocked,
                onFailure = { attempts++ },
            )
        }
    }

    fun signIn() {
        scope.launch {
            signingIn = true
            signInError = null
            auth.signIn(ctx).onSuccess { account = it }
                .onFailure { signInError = it.localizedMessage ?: "Sign-in failed" }
            signingIn = false
        }
    }

    LaunchedEffect(Unit) {
        if (account != null) authenticate()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header Area
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(112.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(com.fmz.kryptos.R.drawable.ic_launcher_monochrome),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
                Text(
                    text = "Kryptos",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Your private vault.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(64.dp))

            // Middle Area (Account or Onboarding)
            Box(
                modifier = Modifier.weight(1f, fill = false),
                contentAlignment = Alignment.Center
            ) {
                val current = account
                if (current != null) {
                    AccountBadge(current)
                } else {
                    Text(
                        text = "Securely back up your data with encrypted cloud storage. Your data never leaves your device unencrypted.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            Spacer(Modifier.height(64.dp))

            // Footer Area (Actions)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val current = account
                if (current == null) {
                    Button(
                        onClick = ::signIn,
                        enabled = !signingIn,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF1F1F1F)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        if (signingIn) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            GoogleIcon(Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = if (signingIn) "Signing in…" else "Sign in with Google",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    signInError?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                } else {
                    Button(
                        onClick = ::authenticate,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = if (attempts == 0) "Unlock Vault" else "Try Again",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showAccount = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Restore")
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    auth.signOut()
                                    account = null
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Switch")
                        }
                    }
                }
            }
        }

        if (showAccount) {
            AccountSheet(
                onDismiss = { showAccount = false },
                onSignOut = {
                    account = null
                    showAccount = false
                }
            )
        }
    }
}

@Composable
private fun GoogleIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val scale = size.width / 24f
        scale(scale, scale, Offset.Zero) {
            val p = Path()
            // Blue
            p.moveTo(22.56f, 12.25f)
            p.cubicTo(22.56f, 11.47f, 22.49f, 10.72f, 22.36f, 10f)
            p.lineTo(12f, 10f)
            p.lineTo(12f, 14.26f)
            p.lineTo(17.92f, 14.26f)
            p.cubicTo(17.66f, 15.63f, 16.88f, 16.79f, 15.71f, 17.57f)
            p.lineTo(15.71f, 20.34f)
            p.lineTo(19.28f, 20.34f)
            p.cubicTo(21.36f, 18.42f, 22.56f, 15.6f, 22.56f, 12.25f)
            drawPath(p, Color(0xFF4285F4))

            // Green
            p.reset()
            p.moveTo(12f, 23f)
            p.cubicTo(15.24f, 23f, 17.95f, 21.92f, 19.93f, 20.09f)
            p.lineTo(16.36f, 17.32f)
            p.cubicTo(15.37f, 17.98f, 14.11f, 18.38f, 12.01f, 18.38f)
            p.cubicTo(8.66f, 18.38f, 5.82f, 16.11f, 4.81f, 13.06f)
            p.lineTo(1.23f, 13.06f)
            p.lineTo(1.23f, 15.83f)
            p.cubicTo(3.21f, 19.68f, 7.31f, 23f, 12f, 23f)
            drawPath(p, Color(0xFF34A853))

            // Yellow
            p.reset()
            p.moveTo(4.8f, 13.06f)
            p.cubicTo(4.54f, 12.29f, 4.4f, 11.46f, 4.4f, 10.6f)
            p.cubicTo(4.4f, 9.74f, 4.54f, 8.91f, 4.8f, 8.14f)
            p.lineTo(4.8f, 5.37f)
            p.lineTo(1.23f, 5.37f)
            p.cubicTo(0.44f, 6.95f, 0f, 8.72f, 0f, 10.6f)
            p.cubicTo(0f, 12.48f, 0.44f, 14.25f, 1.23f, 15.83f)
            p.lineTo(4.8f, 13.06f)
            drawPath(p, Color(0xFFFBBC05))

            // Red
            p.reset()
            p.moveTo(12f, 4.61f)
            p.cubicTo(13.76f, 4.61f, 15.34f, 5.21f, 16.58f, 6.4f)
            p.lineTo(20.01f, 2.97f)
            p.cubicTo(17.95f, 1.08f, 15.24f, 0f, 12f, 0f)
            p.cubicTo(7.31f, 0f, 3.21f, 3.32f, 1.23f, 7.83f)
            p.lineTo(4.8f, 10.6f)
            p.cubicTo(5.81f, 7.55f, 8.65f, 5.28f, 12f, 5.28f)
            drawPath(p, Color(0xFFEA4335))
        }
    }
}

@Composable
private fun AccountBadge(account: AuthManager.Account) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(16.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(112.dp)
        ) {
            if (!account.photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = account.photoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    onError = { state ->
                        android.util.Log.e("LockScreen", "Coil Error: ${state.result.throwable}")
                    },
                    onSuccess = {
                        android.util.Log.d("LockScreen", "Coil Success")
                    }
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = account.displayName ?: account.email ?: "Signed In",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            account.email?.takeIf { it != account.displayName }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
