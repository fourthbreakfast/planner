package dev.fourthbreakfast.planner.sync

import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceIdTest {
    // the certificate of a real device, whose id Syncthing itself worked out
    private val certificate = javaClass.getResourceAsStream("/device.pem")!!.use {
        CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
    }

    @Test
    fun theIdIsReadFromTheCertificate() {
        assertEquals("QIHIWFW-Q5Z7EB2-PUQYLWA-SEDPXH3-RQ43BKF-K4WMLAC-R2SUW42-UZQQ7QU", deviceId(certificate))
    }

    @Test
    fun aCodeCarriesAddressesOrNot() {
        val code = parsePairCode("QIHIWFW-Q5Z7EB2?addr=tcp://192.168.1.43:22101,tcp://10.0.0.2:22101")
        assertEquals("QIHIWFW-Q5Z7EB2", code.id)
        assertEquals(listOf("tcp://192.168.1.43:22101", "tcp://10.0.0.2:22101"), code.addresses)
        assertEquals(PairCode("QIHIWFW", emptyList()), parsePairCode(" qihiwfw "))
        assertEquals(code, parsePairCode(pairText(code.id, code.addresses)))
    }
}
