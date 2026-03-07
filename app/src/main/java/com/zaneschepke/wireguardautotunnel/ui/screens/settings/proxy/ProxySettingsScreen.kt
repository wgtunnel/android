import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forward5
import androidx.compose.material.icons.outlined.Http
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.model.ProxySettings
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.button.ThemedSwitch
import com.zaneschepke.wireguardautotunnel.ui.common.dialog.InfoDialog
import com.zaneschepke.wireguardautotunnel.ui.common.label.GroupLabel
import com.zaneschepke.wireguardautotunnel.ui.common.security.SecureScreenFromRecording
import com.zaneschepke.wireguardautotunnel.ui.common.textbox.ConfigurationTextBox
import com.zaneschepke.wireguardautotunnel.ui.sideeffect.LocalSideEffect
import com.zaneschepke.wireguardautotunnel.viewmodel.ProxySettingsViewModel
import com.zaneschepke.wireguardautotunnel.viewmodel.SharedAppViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.viewmodel.koinActivityViewModel
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun ProxySettingsScreen(
    viewModel: ProxySettingsViewModel = koinViewModel(),
    sharedViewModel: SharedAppViewModel = koinActivityViewModel(),
) {
    val uiState by viewModel.container.stateFlow.collectAsStateWithLifecycle()

    if (uiState.isLoading) return

    val locale = Locale.current.platformLocale

    val proxySettings by remember(uiState) { mutableStateOf(uiState.proxySettings) }

    var socks5Enabled by
        remember(proxySettings) { mutableStateOf(uiState.proxySettings.socks5ProxyEnabled) }
    var httpEnabled by
        remember(proxySettings) { mutableStateOf(uiState.proxySettings.httpProxyEnabled) }
    var socksBindAddress by
        remember(proxySettings) {
            mutableStateOf(uiState.proxySettings.socks5ProxyBindAddress ?: "")
        }
    var httpBindAddress by
        remember(proxySettings) { mutableStateOf(uiState.proxySettings.httpProxyBindAddress ?: "") }
    var proxyUsername by
        remember(proxySettings) { mutableStateOf(uiState.proxySettings.proxyUsername ?: "") }
    var proxyPassword by
        remember(proxySettings) { mutableStateOf(uiState.proxySettings.proxyPassword ?: "") }
    var passwordVisible by remember(proxySettings) { mutableStateOf(uiState.passwordVisible) }

    val keyboardController = LocalSoftwareKeyboardController.current

    val keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
    val keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)

    fun saveChanges() {
        viewModel.save(
            ProxySettings(
                socks5ProxyEnabled = socks5Enabled,
                socks5ProxyBindAddress = socksBindAddress,
                httpProxyEnabled = httpEnabled,
                httpProxyBindAddress = httpBindAddress,
                proxyUsername = proxyUsername,
                proxyPassword = proxyPassword,
            )
        )
    }

    sharedViewModel.collectSideEffect { sideEffect ->
        if (sideEffect is LocalSideEffect.SaveChanges) {
            if (uiState.activeTuns.isNotEmpty()) viewModel.setShowSaveModal(true) else saveChanges()
        }
    }

    if (uiState.showSaveModal) {
        InfoDialog(
            onDismiss = { viewModel.setShowSaveModal(false) },
            onAttest = { saveChanges() },
            title = stringResource(R.string.save_changes),
            body = {
                Text(
                    stringResource(
                        R.string.restart_message_template,
                        stringResource(R.string.tunnels).lowercase(locale),
                    )
                )
            },
            confirmText = stringResource(R.string._continue),
        )
    }

    SecureScreenFromRecording()

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Forward5, contentDescription = null) },
                title = stringResource(R.string.socks_5_proxy),
                trailing = {
                    ThemedSwitch(checked = socks5Enabled, onClick = { socks5Enabled = it })
                },
                onClick = { socks5Enabled = !socks5Enabled },
            )
            if (socks5Enabled) {
                ConfigurationTextBox(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    hint =
                        stringResource(
                            R.string.defaults_to_template,
                            ProxySettings.DEFAULT_SOCKS_BIND_ADDRESS,
                        ),
                    label = stringResource(R.string.socks_5_bind_address),
                    value = socksBindAddress,
                    isError = uiState.isSocks5BindAddressError,
                    onValueChange = {
                        if (uiState.isSocks5BindAddressError) viewModel.clearSocks5BindError()
                        socksBindAddress = it
                    },
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SurfaceRow(
                leading = { Icon(Icons.Outlined.Http, contentDescription = null) },
                title = stringResource(R.string.http_proxy),
                trailing = { ThemedSwitch(checked = httpEnabled, onClick = { httpEnabled = it }) },
                onClick = { httpEnabled = !httpEnabled },
            )
            if (httpEnabled) {
                ConfigurationTextBox(
                    hint =
                        stringResource(
                            R.string.defaults_to_template,
                            ProxySettings.DEFAULT_HTTP_BIND_ADDRESS,
                        ),
                    label = stringResource(R.string.http_bind_address),
                    value = httpBindAddress,
                    isError = uiState.isHttpBindAddressError,
                    onValueChange = {
                        if (uiState.isSocks5BindAddressError) viewModel.clearHttpBindError()
                        httpBindAddress = it
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
        if (socks5Enabled || httpEnabled) {
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.Top),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                GroupLabel(
                    stringResource(
                        R.string.recommended_template,
                        stringResource((R.string.proxy_credentials)),
                    )
                )
                ConfigurationTextBox(
                    value = proxyUsername,
                    onValueChange = {
                        if (uiState.isUserNameError) viewModel.clearUsernameError()
                        proxyUsername = it
                    },
                    label = stringResource(R.string.username),
                    isError = uiState.isUserNameError,
                    hint = "",
                    keyboardActions = keyboardActions,
                    keyboardOptions = keyboardOptions,
                )
                ConfigurationTextBox(
                    value = proxyPassword,
                    onValueChange = {
                        if (uiState.isUserNameError) viewModel.clearPasswordError()
                        proxyPassword = it
                    },
                    label = stringResource(R.string.password),
                    isError = uiState.isPasswordError,
                    hint = "",
                    keyboardActions = keyboardActions,
                    keyboardOptions = keyboardOptions,
                    trailing = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                Icons.Outlined.RemoveRedEye,
                                stringResource(R.string.show_password),
                            )
                        }
                    },
                    visualTransformation =
                        if (!passwordVisible) PasswordVisualTransformation()
                        else VisualTransformation.None,
                )
            }
        }
    }
}
