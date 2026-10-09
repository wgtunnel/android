package com.zaneschepke.wireguardautotunnel.ui.screens.support.donate.crypto

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.zaneschepke.wireguardautotunnel.R

data class Address(
    @StringRes val name: Int,
    @StringRes val address: Int,
    @DrawableRes val icon: Int,
) {
    companion object {
        val allAddresses =
            listOf(
                Address(
                    name = R.string.bitcoin,
                    address = R.string.bitcoin_address,
                    icon = R.drawable.btc,
                ),
                Address(
                    name = R.string.bitcoin_lightning,
                    address = R.string.bitcoin_lightning_address,
                    icon = R.drawable.btc_lightning,
                ),
                Address(
                    name = R.string.monero,
                    address = R.string.monero_address,
                    icon = R.drawable.xmr,
                ),
                Address(
                    name = R.string.ethereum,
                    address = R.string.ethereum_address,
                    icon = R.drawable.eth,
                ),
                Address(
                    name = R.string.zcash,
                    address = R.string.zcash_address,
                    icon = R.drawable.zcash,
                ),
                Address(
                    name = R.string.litecoin,
                    address = R.string.litecoin_address,
                    icon = R.drawable.ltc,
                ),
                Address(
                    name = R.string.ecash,
                    address = R.string.ecash_address,
                    icon = R.drawable.ecash,
                ),
                Address(
                    name = R.string.polygon,
                    address = R.string.polygon_address,
                    icon = R.drawable.polygon,
                ),
                Address(
                    name = R.string.avalanche,
                    address = R.string.avalanche_address,
                    icon = R.drawable.avalanche,
                ),
                Address(
                    name = R.string.solana,
                    address = R.string.solana_address,
                    icon = R.drawable.solana,
                ),
                Address(
                    name = R.string.stellar,
                    address = R.string.stellar_address,
                    icon = R.drawable.stellar,
                ),
                Address(
                    name = R.string.tron,
                    address = R.string.tron_address,
                    icon = R.drawable.tron,
                ),
                Address(
                    name = R.string.bitcoin_cash,
                    address = R.string.bitcoin_cash_address,
                    icon = R.drawable.bitcoin_cash,
                ),
            )
    }
}
