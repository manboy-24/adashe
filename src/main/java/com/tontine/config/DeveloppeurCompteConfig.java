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

    /** Numéro MTN MoMo du développeur — modifiable via DEVELOPPEUR_MTN_MOMO ou application.yml */
    private String mtnMomo = "681951580";

    /** Numéro Orange Money du développeur — modifiable via DEVELOPPEUR_ORANGE_MONEY ou application.yml */
    private String orangeMoney = "692966294";
}
