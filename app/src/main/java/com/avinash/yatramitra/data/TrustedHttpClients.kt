package com.avinash.yatramitra.data

import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * The free OSRM (router.project-osrm.org) and Overpass (overpass-api.de) services this app
 * depends on are both hosted under openstreetmap.de and serve an ECDSA certificate chain rooted
 * at "ISRG Root X2" -- confirmed directly via `openssl s_client -showcerts` against both hosts.
 * Android's system trust store only includes that root starting with Android 14 (API 34); every
 * earlier version (most of this app's minSdk=26 install base) cannot complete that handshake at
 * all, failing with a persistent SSLHandshakeException ("Handshake failed") that has nothing to
 * do with the user's network and that Play Services' ProviderInstaller does not fix (it patches
 * crypto algorithm implementations, not the trusted root CA list).
 *
 * This builds a trust manager that trusts everything the system default trust manager already
 * trusts, *plus* this one specific root -- every other HTTPS site the app might ever talk to
 * keeps validating exactly as it did before.
 */
object TrustedHttpClients {

    // Captured live from router.project-osrm.org's TLS handshake (also served by overpass-api.de).
    private const val ISRG_ROOT_X2_PEM = """-----BEGIN CERTIFICATE-----
MIIEcDCCAligAwIBAgIQbI8dxyfHEX97r4U6yYD5zTANBgkqhkiG9w0BAQsFADBP
MQswCQYDVQQGEwJVUzEpMCcGA1UEChMgSW50ZXJuZXQgU2VjdXJpdHkgUmVzZWFy
Y2ggR3JvdXAxFTATBgNVBAMTDElTUkcgUm9vdCBYMTAeFw0yNjA1MTMwMDAwMDBa
Fw0zMjA5MDIyMzU5NTlaME8xCzAJBgNVBAYTAlVTMSkwJwYDVQQKEyBJbnRlcm5l
dCBTZWN1cml0eSBSZXNlYXJjaCBHcm91cDEVMBMGA1UEAxMMSVNSRyBSb290IFgy
MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEzZvVn4CDCuwJSvMWSj5cz3es3mcFDR0H
ttwW+1qLFNvicWDEukWVEYmO6gbf9yoWHKS5xcUy4APgHoIYOIvXRdgKam7mAHf7
AlF9ItgKbppbd9/w+kHsOdx1ymgHDB/qo4H1MIHyMA4GA1UdDwEB/wQEAwIBBjAd
BgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwDwYDVR0TAQH/BAUwAwEB/zAd
BgNVHQ4EFgQUfEKWrt5LSDv6kviejM9ti6lyN5UwHwYDVR0jBBgwFoAUebRZ5nu2
5eQBc4AIiMgaWPbpm24wMgYIKwYBBQUHAQEEJjAkMCIGCCsGAQUFBzAChhZodHRw
Oi8veDEuaS5sZW5jci5vcmcvMBMGA1UdIAQMMAowCAYGZ4EMAQIBMCcGA1UdHwQg
MB4wHKAaoBiGFmh0dHA6Ly94MS5jLmxlbmNyLm9yZy8wDQYJKoZIhvcNAQELBQAD
ggIBAD2/e9frmMxNpCV03qUHegg+MV2wz9644YoXdqtH8RyWYcBO7xfjjGEXdU1e
/o0OkEFiynUCOSIk/vLLo7ttz6CPAeNlWfC0XNkoGeWgK6jjXvozBaGuGH5n0Ufo
shMeWTuURqNN5G00sSXDTBrpp2+mgvdZQjb8K11TYMA25QA+YHNfbIEL0BniAhKS
2gsnJjSzrdZLI+EZ7SEyqdR2rkjd1KutLDU+n3TFyxjniZVGur4YlhMP3mY/dV95
IruAkkjOZier6hGBdEgZXXvaCz9u9iVEadsIE75pAGL8oHV5vxdARDiotRpul1IN
/UZwzAbrfUFcw1HkAcYD/mlZfnQ2ieCF2MS7j3Vhv7JPDKp45fmykmzYNSrumRW0
upFFKDBOoF7hsOb7oLyHS+Uft6jOUfOrogj8YUx38hKb2K20r42OgsSdDdxdeYWc
MS3Sb6mwJeSZEYxJ2gaXnDSPaKhhrNkYwljyVQyr4Nq+MEJytXNTnHqaAcrNwZlV
pcJL1KBnMrMjP7eanvUwL3FYj3cF17jtboLt7gLoi4+2rWZFvn+w54jmd/FIuhhZ
cEaU/wvU6BUNMtcVquVGHp7itQeDth5j+XL3j4WJ2SABwzUl6OeYdgpIt/ITZa+p
TT0mQ/r5XyA4MEAiabn7XJjvCERlF2dcn2wqJw+CreTkkQ2R
-----END CERTIFICATE-----"""

    /** A trust manager built from *only* [extraRootPem] -- no system trust store involved. Split
     *  out from [buildTrustManager] specifically so a test can prove this one bundled root, by
     *  itself, actually validates a real vulnerable chain -- testing the combined trust manager
     *  alone wouldn't prove that, since it could pass merely because the test JVM's own default
     *  trust store already happens to include the root (unlike an old Android device's). */
    internal fun buildExtraOnlyTrustManager(extraRootPem: String = ISRG_ROOT_X2_PEM): X509TrustManager {
        val extraCert = CertificateFactory.getInstance("X.509")
            .generateCertificate(extraRootPem.byteInputStream()) as X509Certificate

        val extraKeyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("isrg-root-x2", extraCert)
        }

        return TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(extraKeyStore)
        }.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    /** A trust manager that accepts anything the platform default trust manager accepts, plus
     *  [extraRootPem]. Exposed so it can be constructed and tested independently of a live
     *  [SSLContext]/[SSLSocketFactory]. */
    fun buildTrustManager(extraRootPem: String = ISRG_ROOT_X2_PEM): X509TrustManager {
        val systemTrustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(null as KeyStore?)
        }.trustManagers.filterIsInstance<X509TrustManager>().first()

        val extraTrustManager = buildExtraOnlyTrustManager(extraRootPem)

        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
                systemTrustManager.checkClientTrusted(chain, authType)
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
                try {
                    systemTrustManager.checkServerTrusted(chain, authType)
                } catch (systemFailure: CertificateException) {
                    // Fall back to the one extra root; if that also fails to validate, surface
                    // the original system failure -- it's the more informative message for
                    // every other (non-openstreetmap.de) host this app might ever talk to.
                    try {
                        extraTrustManager.checkServerTrusted(chain, authType)
                    } catch (extraFailure: CertificateException) {
                        throw systemFailure
                    }
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> =
                systemTrustManager.acceptedIssuers + extraTrustManager.acceptedIssuers
        }
    }

    val sslSocketFactory: SSLSocketFactory by lazy {
        SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }.socketFactory
    }

    val trustManager: X509TrustManager by lazy { buildTrustManager() }
}
