package com.tontine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "developpeur.compte")
@Getter
@Setter
public class DeveloppeurCompteConfig {

    /** Numéro MTN MoMo du développeur — défini via la variable d'env DEVELOPPEUR_MTN_MOMO (Railway). */
    private String mtnMomo = "";

    /** Numéro Orange Money du développeur — défini via la variable d'env DEVELOPPEUR_ORANGE_MONEY (Railway). */
    private String orangeMoney = "";
}
