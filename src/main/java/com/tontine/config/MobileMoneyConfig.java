package com.tontine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "mobile-money")
@Data
public class MobileMoneyConfig {

    private Mtn mtn = new Mtn();
    private Orange orange = new Orange();

    @Data
    public static class Mtn {
        private String baseUrl = "https://sandbox.momodeveloper.mtn.com";
        private String subscriptionKey;
        private String apiUser;
        private String apiKey;
        private String environment = "sandbox"; // sandbox | production
        private String currency = "XAF";
        private String callbackUrl;
        private int timeoutSeconds = 120;
    }

    @Data
    public static class Orange {
        private String baseUrl = "https://api.orange.com/orange-money-webpay/cm/v1";
        private String clientId;
        private String clientSecret;
        private String merchantKey;
        private String currency = "XAF";
        private String returnUrl;
        private String cancelUrl;
        private String notifUrl;
        private int timeoutSeconds = 120;
    }
}
