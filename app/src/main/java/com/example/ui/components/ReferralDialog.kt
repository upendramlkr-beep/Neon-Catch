package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.ReferralRedemption
import com.example.game.RedeemStatus
import com.example.game.ReferralUiState
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.MidnightBgTop
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMint
import com.example.ui.theme.SolarGold
import com.example.ui.theme.SolarGoldBright
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ReferralDialog(
    referralState: ReferralUiState,
    referredFriends: List<ReferralRedemption>,
    redeemStatus: RedeemStatus,
    onRedeemCode: (String) -> Unit,
    onClearRedeemStatus: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copiedJustNow by remember { mutableStateOf(false) }
    var codeInput by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = SurfaceDark,
            border = BorderStroke(1.5.dp, ElectricViolet.copy(alpha = 0.7f)),
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GroupAdd,
                            contentDescription = null,
                            tint = ElectricViolet,
                            modifier = Modifier.size(26.dp)
                        )
                        Text(
                            text = stringResource(R.string.referral_hub_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("close_referral_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_close),
                            tint = TextSecondary
                        )
                    }
                }

                when (referralState) {
                    ReferralUiState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = NeonCyan)
                        }
                    }

                    is ReferralUiState.Error -> {
                        Text(
                            text = referralState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CrimsonDanger
                        )
                    }

                    is ReferralUiState.Ready -> {
                        val profile = referralState.profile

                        // Balance Row (Shields + Star Shards)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RewardStatTile(
                                icon = Icons.Rounded.Shield,
                                label = "BONUS SHIELDS",
                                value = profile.bonusShields.toString(),
                                tint = NeonMint,
                                modifier = Modifier.weight(1f)
                            )
                            RewardStatTile(
                                icon = Icons.Rounded.AutoAwesome,
                                label = "STAR SHARDS",
                                value = profile.starShards.toString(),
                                tint = SolarGold,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Your Referral Code Card
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = SurfaceCard,
                            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.referral_your_code),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonCyan
                                )
                                Text(
                                    text = profile.referralCode,
                                    style = MaterialTheme.typography.displaySmall.copy(
                                        fontFamily = MaterialTheme.typography.labelLarge.fontFamily,
                                        letterSpacing = 3.sp
                                    ),
                                    color = TextPrimary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.testTag("my_referral_code")
                                )
                                Text(
                                    text = stringResource(R.string.referral_rewards_info),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(profile.referralCode))
                                            copiedJustNow = true
                                        },
                                        border = BorderStroke(1.dp, NeonCyan),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("copy_code_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ContentCopy,
                                            contentDescription = null,
                                            tint = NeonCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(
                                                if (copiedJustNow) R.string.referral_copied else R.string.referral_copy
                                            ),
                                            color = NeonCyan
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            val shareText = "Play Neon Catch with me! Use my referral code ${profile.referralCode} to get +1 Bonus Heart Shield and +100 Star Shards!"
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, shareText)
                                            }
                                            context.startActivity(
                                                Intent.createChooser(intent, "Share Neon Catch Invite")
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ElectricViolet,
                                            contentColor = MidnightBgTop
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("share_code_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Share,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.referral_share),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Redeem Friend's Code Section
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = SurfaceCard,
                            border = BorderStroke(1.dp, SurfaceBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.referral_redeem_title),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SolarGold
                                )

                                if (!profile.referredByCode.isNullOrBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = NeonMint,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "${stringResource(R.string.referral_redeemed_prefix)} ${profile.referredByCode}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = NeonMint
                                        )
                                    }
                                } else {
                                    OutlinedTextField(
                                        value = codeInput,
                                        onValueChange = {
                                            codeInput = it.uppercase().take(12)
                                            if (redeemStatus !is RedeemStatus.Idle) onClearRedeemStatus()
                                        },
                                        placeholder = {
                                            Text(
                                                text = stringResource(R.string.referral_redeem_hint),
                                                color = TextMuted
                                            )
                                        },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            capitalization = KeyboardCapitalization.Characters,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = { onRedeemCode(codeInput) }
                                        ),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = NeonCyan,
                                            unfocusedBorderColor = SurfaceBorder,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("referral_code_input")
                                    )

                                    when (redeemStatus) {
                                        is RedeemStatus.Error -> {
                                            Text(
                                                text = redeemStatus.message,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = CrimsonDanger
                                            )
                                        }
                                        is RedeemStatus.Success -> {
                                            Text(
                                                text = redeemStatus.message,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = NeonMint
                                            )
                                        }
                                        else -> Unit
                                    }

                                    Button(
                                        onClick = { onRedeemCode(codeInput) },
                                        enabled = codeInput.trim().length >= 6 && redeemStatus !is RedeemStatus.Submitting,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = SolarGold,
                                            contentColor = MidnightBgTop
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("redeem_code_button")
                                    ) {
                                        if (redeemStatus is RedeemStatus.Submitting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = MidnightBgTop,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Text(
                                                text = stringResource(R.string.referral_redeem_button),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Referred Friends List
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "${stringResource(R.string.referral_friends_title)} (${referredFriends.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            if (referredFriends.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.referral_friends_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted
                                )
                            } else {
                                referredFriends.forEach { friend ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White.copy(alpha = 0.05f))
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = friend.redeemerName.ifBlank { "Neon Player" },
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "+1 Shield • +150",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SolarGoldBright
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RewardStatTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = tint.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
            }
        }
    }
}
