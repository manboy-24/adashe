package com.tontine;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    // ── Base de données H2 ──────────────────────────────────────────────────
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    // ── JWT ─────────────────────────────────────────────────────────────────
    "jwt.secret=dGVzdFNlY3JldEtleUZvclRlc3RpbmdPbmx5MTIzNDU2Nzg=",
    "jwt.expiration=86400000",
    "jwt.refresh-expiration=604800000",
    // ── OTP / PIN ────────────────────────────────────────────────────────────
    "otp.expiration-minutes=5",
    "pin.max-tentatives=5",
    "pin.blocage-minutes=15",
    // ── Firebase ─────────────────────────────────────────────────────────────
    "firebase.credentials-json=",
    "firebase.credentials-path=classpath:firebase-service-account.json",
    // ── SMS ──────────────────────────────────────────────────────────────────
    "sms.provider=console",
    // ── Monetbil ─────────────────────────────────────────────────────────────
    "monetbil.service-key=test-key",
    "monetbil.service-secret=test-secret",
    "monetbil.api-url=http://localhost/test",
    "monetbil.notify-url=http://localhost/test/notify",
    "monetbil.return-url=http://localhost/test/return",
    // ── Mobile Money ─────────────────────────────────────────────────────────
    "mobile-money.mtn.base-url=http://localhost",
    "mobile-money.mtn.subscription-key=test",
    "mobile-money.mtn.api-user=test",
    "mobile-money.mtn.api-key=test",
    "mobile-money.mtn.environment=sandbox",
    "mobile-money.mtn.currency=XAF",
    "mobile-money.mtn.callback-url=http://localhost/callback",
    "mobile-money.orange.base-url=http://localhost",
    "mobile-money.orange.client-id=test",
    "mobile-money.orange.client-secret=test",
    "mobile-money.orange.merchant-key=test",
    "mobile-money.orange.currency=XAF",
    "mobile-money.orange.return-url=http://localhost/return",
    "mobile-money.orange.cancel-url=http://localhost/cancel",
    "mobile-money.orange.notif-url=http://localhost/notif",
    // ── Mail ─────────────────────────────────────────────────────────────────
    "spring.mail.host=localhost",
    "spring.mail.port=25",
    "spring.mail.username=test@test.com",
    "spring.mail.password=test"
})
class TontineApplicationTests {

    @Test
    void contextLoads() {
        // Vérifie que le contexte Spring démarre sans erreur
    }
}
