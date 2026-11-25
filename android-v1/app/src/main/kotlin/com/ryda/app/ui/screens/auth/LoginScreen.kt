package com.ryda.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryda.app.core.auth.AuthRepository
import com.ryda.app.core.auth.AuthResult
import com.ryda.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    authRepository: AuthRepository,
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onSkipOffline: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun doLogin() {
        if (username.isBlank() || password.isBlank()) {
            errorMsg = "Username and password are required"
            return
        }
        isLoading = true
        errorMsg = null
        scope.launch {
            when (val result = authRepository.login(username.trim(), password)) {
                is AuthResult.Success -> {
                    onLoginSuccess()
                }
                is AuthResult.Error -> { errorMsg = result.message; isLoading = false }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AsphaltBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Logo / wordmark
            Text("RYDA", fontSize = 48.sp, fontWeight = FontWeight.Black, color = SafetyAmber)
            Text("Track every ride", fontSize = 14.sp, color = OnSurfaceMuted)

            Spacer(Modifier.height(16.dp))

            // Username
            OutlinedTextField(
                value = username,
                onValueChange = { username = it; errorMsg = null },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                colors = rydaTextFieldColors()
            )

            // Password
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMsg = null },
                label = { Text("Password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); doLogin() }),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = null,
                            tint = OnSurfaceMuted
                        )
                    }
                },
                colors = rydaTextFieldColors()
            )

            // Error
            errorMsg?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            // Login button
            Button(
                onClick = { doLogin() },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SafetyAmber, contentColor = AsphaltBlack)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = AsphaltBlack)
                } else {
                    Text("Log In", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            // Register link
            TextButton(onClick = onNavigateToRegister) {
                Text("New here? Create account", color = ElectricCyan)
            }

            HorizontalDivider(color = SurfaceCard, modifier = Modifier.padding(vertical = 4.dp))

            // Offline skip
            OutlinedButton(
                onClick = onSkipOffline,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurfaceMuted),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCard)
            ) {
                Text("Continue offline (no sync)", fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun rydaTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = OnSurfacePrimary,
    unfocusedTextColor = OnSurfacePrimary,
    focusedBorderColor = SafetyAmber,
    unfocusedBorderColor = SurfaceCard,
    focusedLabelColor = SafetyAmber,
    unfocusedLabelColor = OnSurfaceMuted,
    cursorColor = SafetyAmber
)
