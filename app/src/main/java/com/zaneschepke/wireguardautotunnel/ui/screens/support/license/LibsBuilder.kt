package com.zaneschepke.wireguardautotunnel.ui.screens.support.license

import android.content.Context
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Developer
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.entity.License
import com.mikepenz.aboutlibraries.entity.Scm
import com.mikepenz.aboutlibraries.util.withContext

fun buildLibsWithAdditionalLibraries(context: Context): Libs {
    val baseLibs = Libs.Builder().withContext(context).build()

    val cleanedBaseLibs =
        baseLibs.libraries.filterNot { library ->
            library.uniqueId.contains("com.github.topjohnwu.libsu", ignoreCase = true) ||
                library.uniqueId.contains("com.github.T8RIN.QuickieExtended", ignoreCase = true)
        }

    val nativeLibraries =
        listOf(
            Library(
                uniqueId = "github.com.wgtunnel:amneziawg-go",
                artifactVersion = "v0.0.0-20260618075902-e1b699b2104b",
                name = "AmneziaWG Go (Fork)",
                description = "WireGuard implementation with Amnezia obfuscation",
                website = "https://wgtunnel.com",
                developers =
                    listOf(
                        Developer(
                            name = "Zane Schepke (Fork Maintainer)",
                            organisationUrl = "https://wgtunnel.com",
                        ),
                        Developer(
                            name = "Jason A. Donenfeld (Original WireGuard)",
                            organisationUrl = "https://www.wireguard.com/",
                        ),
                        Developer(
                            name = "Amnezia VPN Team",
                            organisationUrl = "https://amnezia.org/",
                        ),
                    ),
                organization = null,
                scm = Scm(null, null, "https://github.com/wgtunnel/amneziawg-go"),
                licenses =
                    setOf(
                        License(
                            name = "MIT License",
                            url = "https://opensource.org/licenses/MIT",
                            spdxId = "MIT",
                            hash = "mit-license-amneziawg-fork",
                        )
                    ),
                funding = emptySet(),
                tag = "native",
            ),
            Library(
                uniqueId = "github.com.wgtunnel:wireproxy-awg",
                artifactVersion = "v0.0.0-20260309043206-ff4200f20ff2",
                name = "Wireproxy AWG (Fork)",
                description = "WireGuard proxy with Amnezia support",
                website = "https://wgtunnel.com",
                developers =
                    listOf(
                        Developer(
                            name = "Zane Schepke (Fork Maintainer)",
                            organisationUrl = "https://wgtunnel.com",
                        ),
                        Developer(name = "Artem Russkikh (Original)", organisationUrl = null),
                    ),
                organization = null,
                scm = Scm(null, null, "https://github.com/wgtunnel/wireproxy-awg"),
                licenses =
                    setOf(
                        License(
                            name = "MIT License",
                            url = "https://opensource.org/licenses/MIT",
                            spdxId = "MIT",
                            hash = "mit-license-wireproxy-fork",
                        )
                    ),
                funding = emptySet(),
                tag = "native",
            ),
            Library(
                uniqueId = "github.com.wgtunnel:go-socks5",
                artifactVersion = "v0.0.0-20260307052555-86f8d93b9534",
                name = "go-socks5 (Fork)",
                description = "SOCKS5 proxy server implementation",
                website = "https://wgtunnel.com",
                developers =
                    listOf(
                        Developer(
                            name = "Zane Schepke (Fork Maintainer)",
                            organisationUrl = "https://wgtunnel.com",
                        ),
                        Developer(name = "Things-go Team (Original)", organisationUrl = null),
                    ),
                organization = null,
                scm = Scm(null, null, "https://github.com/wgtunnel/go-socks5"),
                licenses =
                    setOf(
                        License(
                            name = "MIT License",
                            url = "https://opensource.org/licenses/MIT",
                            spdxId = "MIT",
                            hash = "mit-license-go-socks5-fork",
                        )
                    ),
                funding = emptySet(),
                tag = "native",
            ),
            Library(
                uniqueId = "github.com.miekg:dns",
                artifactVersion = "v1.1.69",
                name = "miekg/dns",
                description = "DNS library for Go",
                website = "https://github.com/miekg/dns",
                developers = listOf(Developer(name = "Miek Gieben", organisationUrl = null)),
                organization = null,
                scm = Scm(null, null, "https://github.com/miekg/dns"),
                licenses =
                    setOf(
                        License(
                            name = "BSD 3-Clause \"New\" or \"Revised\" License",
                            url = "https://opensource.org/licenses/BSD-3-Clause",
                            spdxId = "BSD-3-Clause",
                            hash = "bsd3-miekg-dns",
                        )
                    ),
                funding = emptySet(),
                tag = "go",
            ),
            Library(
                uniqueId = "github.com.heiher:hev-socks5-tunnel",
                artifactVersion = "2.15.0",
                name = "hev-socks5-tunnel",
                description = "High performance SOCKS5 tunnel",
                website = "https://github.com/heiher/hev-socks5-tunnel",
                developers = listOf(Developer(name = "heiher", organisationUrl = null)),
                organization = null,
                scm = Scm(null, null, "https://github.com/heiher/hev-socks5-tunnel"),
                licenses =
                    setOf(
                        License(
                            name = "MIT License",
                            url = "https://opensource.org/licenses/MIT",
                            spdxId = "MIT",
                            hash = "mit-license-hev",
                        )
                    ),
                funding = emptySet(),
                tag = "native",
            ),
        )

    val additionalLibraries =
        listOf(
            Library(
                uniqueId = "com.github.T8RIN.QuickieExtended:quickie-foss",
                artifactVersion = "1.18.1",
                name = "QuickieFoss",
                description = "Camera QR code scanner",
                website = "https://github.com/T8RIN/QuickieExtended",
                developers = listOf(Developer(name = "T8RIN", null)),
                organization = null,
                scm = Scm(null, null, "https://github.com/T8RIN/QuickieExtended"),
                licenses =
                    setOf(
                        License(
                            name = "Apache License 2.0",
                            url = "https://www.apache.org/licenses/LICENSE-2.0",
                            spdxId = "Apache-2.0",
                            hash = "apache-2-quickie",
                        )
                    ),
                funding = emptySet(),
                tag = "ui",
            ),
            Library(
                uniqueId = "com.github.topjohnwu.libsu:core",
                artifactVersion = "6.0.0",
                name = "libsu",
                description = "Root shell library for Android",
                website = "https://github.com/topjohnwu/libsu",
                developers = listOf(Developer(name = "topjohnwu", null)),
                organization = null,
                scm = Scm(null, null, "https://github.com/topjohnwu/libsu"),
                licenses =
                    setOf(
                        License(
                            name = "Apache License 2.0",
                            url = "https://www.apache.org/licenses/LICENSE-2.0",
                            spdxId = "Apache-2.0",
                            hash = "apache-2-libsu",
                        )
                    ),
                funding = emptySet(),
                tag = "system",
            ),
        )

    return Libs(
        libraries =
            (cleanedBaseLibs + nativeLibraries + additionalLibraries).sortedBy {
                it.name.lowercase()
            },
        licenses =
            baseLibs.licenses +
                nativeLibraries.flatMap { it.licenses } +
                additionalLibraries.flatMap { it.licenses },
    )
}
