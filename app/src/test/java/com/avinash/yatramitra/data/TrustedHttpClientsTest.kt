package com.avinash.yatramitra.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Proves the actual bug fix, not just that the code compiles: this is the exact certificate chain
 * captured live from router.project-osrm.org (also served by overpass-api.de) that causes a
 * persistent SSLHandshakeException ("Handshake failed") on any Android version before 14, since
 * ISRG Root X2 isn't in their system trust store. If TrustedHttpClients's trust manager doesn't
 * accept this chain, the underlying real-world bug is not actually fixed.
 */
class TrustedHttpClientsTest {

    private val leafCert = """-----BEGIN CERTIFICATE-----
MIIDyTCCA0+gAwIBAgISBQmTVp+EbPqfMYV4IY833g85MAoGCCqGSM49BAMDMDMx
CzAJBgNVBAYTAlVTMRYwFAYDVQQKEw1MZXQncyBFbmNyeXB0MQwwCgYDVQQDEwNZ
RTIwHhcNMjYwNzIxMDgzNTUwWhcNMjYxMDE5MDgzNTQ5WjAhMR8wHQYDVQQDExZn
b29kZS5vcGVuc3RyZWV0bWFwLmRlMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE
/goLgkNxzc+Ix0ZPr+8XVfe8viHZKwxYOZaONI6mlEO0wVa/RnDw1zaewGfyZhjB
WDak/rIsC3q96KTkpCY1eaOCAlMwggJPMA4GA1UdDwEB/wQEAwIHgDATBgNVHSUE
DDAKBggrBgEFBQcDATAMBgNVHRMBAf8EAjAAMB0GA1UdDgQWBBTXL5m+7N52yWSj
gyDtI9PllJfeATAfBgNVHSMEGDAWgBS5WfKOzyLwhtM3SP92FBi6gthVhzAzBggr
BgEFBQcBAQQnMCUwIwYIKwYBBQUHMAKGF2h0dHA6Ly95ZTIuaS5sZW5jci5vcmcv
MFAGA1UdEQRJMEeCFmdvb2RlLm9wZW5zdHJlZXRtYXAuZGWCFG1hcC5wcm9qZWN0
LW9zcm0ub3Jnghdyb3V0ZXIucHJvamVjdC1vc3JtLm9yZzATBgNVHSAEDDAKMAgG
BmeBDAECATAuBgNVHR8EJzAlMCOgIaAfhh1odHRwOi8veWUyLmMubGVuY3Iub3Jn
LzU4LmNybDCCAQwGCisGAQQB1nkCBAIEgf0EgfoA+AB2ANdtfRDRp/V3wsfpX9cA
v/mCyTNaZeHQswFzF8DIxWl3AAABn4QGt14AAAQDAEcwRQIhAPDySKEDmizoop7F
F7d+dRMbrUFS6+gSpV4ejDiz+vb1AiB6gk8dAGANYysQa9wjRAWF3IHPXglDsptu
/YlDzeBnYgB+AKgmy+MKxjUSRlM/4GXxTxnZbhkIE8Qd2W15ALMSPFUnAAABn4QG
umcACAAABQAVTUYeBAMARzBFAiBIGqfV8R/6OejtVpBmZaP3kMpH+Z/JgAovy2Vd
PTS6zQIhAOQ0QBp2oyuEXZMW20QCrpljg4PiU98yMyFz9GT3d1khMAoGCCqGSM49
BAMDA2gAMGUCMQD4jLywNtBNCpReekAIuxbldt0WhODHYZ4/OHHZF2D2UicgozxE
VOWrnO55kpwfBiICMG3bcI147mDzVshl4m9CCEqXVtWZep0ddifZEFsNOpMUtRbh
OjW7iUAqgUiJ/EElkg==
-----END CERTIFICATE-----"""

    private val intermediateYe2 = """-----BEGIN CERTIFICATE-----
MIICjDCCAhGgAwIBAgIQTfOxXdbAeExQfNN7WObxFTAKBggqhkjOPQQDAzAuMQsw
CQYDVQQGEwJVUzENMAsGA1UEChMESVNSRzEQMA4GA1UEAxMHUm9vdCBZRTAeFw0y
NTA5MDMwMDAwMDBaFw0yODA5MDIyMzU5NTlaMDMxCzAJBgNVBAYTAlVTMRYwFAYD
VQQKEw1MZXQncyBFbmNyeXB0MQwwCgYDVQQDEwNZRTIwdjAQBgcqhkjOPQIBBgUr
gQQAIgNiAARxmrQzkdbEEL3MqXt3dJQttYc47axkdDTHud5TPqM2z5uSD5cmk0Wr
HlWXvnlvqBLqiB34kluxIbmMyAiq3/YD6e80/vV259K8XQIdjFXloYOa0mIU71f7
HQ09PvYDlw+jge4wgeswDgYDVR0PAQH/BAQDAgGGMBMGA1UdJQQMMAoGCCsGAQUF
BwMBMBIGA1UdEwEB/wQIMAYBAf8CAQAwHQYDVR0OBBYEFLlZ8o7PIvCG0zdI/3YU
GLqC2FWHMB8GA1UdIwQYMBaAFKPIJlqOoUzQNWP8myPIOq5W809WMDIGCCsGAQUF
BwEBBCYwJDAiBggrBgEFBQcwAoYWaHR0cDovL3llLmkubGVuY3Iub3JnLzATBgNV
HSAEDDAKMAgGBmeBDAECATAnBgNVHR8EIDAeMBygGqAYhhZodHRwOi8veWUuYy5s
ZW5jci5vcmcvMAoGCCqGSM49BAMDA2kAMGYCMQDIcnw5dcZLN9ffynXnnkLD/itS
JEycJPb3sRkzeqBowup7vOsAwaqoCnNn/jh9wycCMQCJM6CPlaOC4pQYYbJtVPYb
DKrIb2EKk5NpOpE6/XttQYZV/3gilB9l+Cc/DOVwmyg=
-----END CERTIFICATE-----"""

    private val intermediateRootYe = """-----BEGIN CERTIFICATE-----
MIICpjCCAiugAwIBAgIRAIchZfw0tuX7qK3Vs3BftTowCgYIKoZIzj0EAwMwTzEL
MAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2VhcmNo
IEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDIwHhcNMjYwNTEzMDAwMDAwWhcN
MzIwOTAyMjM1OTU5WjAuMQswCQYDVQQGEwJVUzENMAsGA1UEChMESVNSRzEQMA4G
A1UEAxMHUm9vdCBZRTB2MBAGByqGSM49AgEGBSuBBAAiA2IABDwS/6vhrcVqcbBo
+wgdI3fwn9x7DNJJOY/lTOti0vkwuRN87RhEhTH17E7XyFjWsPYhIPt/wzOqxTd2
b+4ZJNy9ID04YywF9U5zasDVyGSNErVNtz8uSGh5izW87j77GaOB6zCB6DAOBgNV
HQ8BAf8EBAMCAQYwEwYDVR0lBAwwCgYIKwYBBQUHAwEwDwYDVR0TAQH/BAUwAwEB
/zAdBgNVHQ4EFgQUo8gmWo6hTNA1Y/ybI8g6rlbzT1YwHwYDVR0jBBgwFoAUfEKW
rt5LSDv6kviejM9ti6lyN5UwMgYIKwYBBQUHAQEEJjAkMCIGCCsGAQUFBzAChhZo
dHRwOi8veDIuaS5sZW5jci5vcmcvMBMGA1UdIAQMMAowCAYGZ4EMAQIBMCcGA1Ud
HwQgMB4wHKAaoBiGFmh0dHA6Ly94Mi5jLmxlbmNyLm9yZy8wCgYIKoZIzj0EAwMD
aQAwZgIxAMU19WCtmxVND8UHBZRoma49Z7jPs64Dma0eTu1OChVbB/2J7GV3nvYK
Ax54uk1G9QIxAO0miLVJu8PLNiXXXkiE/gsK3CTRTF/aeo4bMX42Zw40csRU6AC2
6hSW1/IWaas6dg==
-----END CERTIFICATE-----"""

    // The root TrustedHttpClients bundles -- same constant it uses internally.
    private val isrgRootX2 = """-----BEGIN CERTIFICATE-----
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

    private fun parse(pem: String): X509Certificate =
        CertificateFactory.getInstance("X.509").generateCertificate(pem.byteInputStream()) as X509Certificate

    @Test
    fun `the bundled root alone -- no system trust store -- validates the real vulnerable chain`() {
        // Deliberately bypasses buildTrustManager's system-trust-store fallback: this is the part
        // that must work on its own merits, since testing the combined trust manager could pass
        // merely because this JVM's own default trust store happens to already include the root
        // (unlike an old Android device's, which is the actual bug being fixed).
        val extraOnlyTrustManager = TrustedHttpClients.buildExtraOnlyTrustManager()
        val chain = arrayOf(parse(leafCert), parse(intermediateYe2), parse(intermediateRootYe), parse(isrgRootX2))

        // No exception here is the real proof: an old Android device's system trust manager alone
        // throws CertificateException for this exact chain -- that's the real "Handshake failed".
        extraOnlyTrustManager.checkServerTrusted(chain, "ECDHE_ECDSA")
    }

    @Test
    fun `buildTrustManager (the one actually wired into OkHttp) also accepts the real chain`() {
        val trustManager = TrustedHttpClients.buildTrustManager()
        val chain = arrayOf(parse(leafCert), parse(intermediateYe2), parse(intermediateRootYe), parse(isrgRootX2))
        trustManager.checkServerTrusted(chain, "ECDHE_ECDSA")
    }

    @Test
    fun `buildTrustManager getAcceptedIssuers includes the bundled extra root`() {
        val trustManager = TrustedHttpClients.buildTrustManager()
        val issuers = trustManager.acceptedIssuers
        assertTrue("expected at least the one bundled root among accepted issuers", issuers.isNotEmpty())
        assertTrue(issuers.any { it.subjectX500Principal.name.contains("ISRG Root X2") })
    }
}
