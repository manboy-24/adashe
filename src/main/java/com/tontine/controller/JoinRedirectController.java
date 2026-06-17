package com.tontine.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoint public : redirige vers le deep link adashecash://join/{code}.
 * URL partagée sur WhatsApp/SMS — doit être HTTPS pour être cliquable.
 * Exemple : https://api.adashcash.com/api/join/ABC123
 */
@RestController
public class JoinRedirectController {

    private static final String PLAY_STORE_URL =
            "https://play.google.com/store/apps/details?id=com.tontineplus.app";

    @GetMapping(value = "/join/{code}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> redirectToApp(@PathVariable String code) {
        String deepLink = "adashecash://join/" + code;
        String html = """
                <!DOCTYPE html>
                <html lang="fr">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Adashe – Rejoindre la tontine</title>
                  <style>
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body { font-family: sans-serif; text-align: center;
                           padding: 48px 24px; background: #0d1b2a; color: #fff;
                           min-height: 100vh; display: flex; flex-direction: column;
                           align-items: center; justify-content: center; gap: 20px; }
                    .logo { font-size: 1.6rem; font-weight: 700; color: #1d9e75;
                            letter-spacing: 1px; }
                    .subtitle { color: rgba(255,255,255,.5); font-size: .85rem; }
                    .code-box { background: rgba(239,159,39,.08);
                                border: 1px solid rgba(239,159,39,.3);
                                border-radius: 14px; padding: 20px 32px; }
                    .code-label { font-size: .75rem; color: rgba(255,255,255,.4);
                                  letter-spacing: 1px; text-transform: uppercase;
                                  margin-bottom: 8px; }
                    .code { font-size: 2rem; font-weight: 700;
                            letter-spacing: 8px; color: #ef9f27; }
                    .btn { display: inline-block; width: 100%; max-width: 320px;
                           padding: 14px 24px; border-radius: 12px; font-size: 1rem;
                           font-weight: 600; text-decoration: none; cursor: pointer;
                           border: none; }
                    .btn-green  { background: #1d9e75; color: #fff; }
                    .btn-store  { background: rgba(255,255,255,.07);
                                  border: 1px solid rgba(255,255,255,.15);
                                  color: rgba(255,255,255,.8); font-size: .9rem; }
                    #store-section { display: none; }
                    .hint { font-size: .78rem; color: rgba(255,255,255,.35);
                            line-height: 1.5; }
                  </style>
                </head>
                <body>
                  <div class="logo">Adashe</div>
                  <p class="subtitle">Tu as reçu une invitation à rejoindre une tontine</p>

                  <div class="code-box">
                    <div class="code-label">Code d'invitation</div>
                    <div class="code">%s</div>
                  </div>

                  <!-- Bouton principal : ouvre l'app si installée -->
                  <a class="btn btn-green" href="%s" id="openBtn"
                     onclick="scheduleStoreFallback()">Ouvrir Adashe</a>

                  <!-- Affiché après 2 s si l'app ne s'est pas ouverte -->
                  <div id="store-section">
                    <p class="hint">Adashe n'est pas encore installé sur cet appareil.</p>
                    <a class="btn btn-store" href="%s">
                      Télécharger sur Google Play
                    </a>
                  </div>

                  <p class="hint">
                    Déjà installé et l'app ne s'ouvre pas ?<br>
                    Lance <strong>Adashe</strong> → <em>Rejoindre</em>
                    et entre le code ci-dessus.
                  </p>

                  <script>
                    // Tente d'ouvrir l'app immédiatement au chargement
                    (function() {
                      var tried = false;
                      function tryOpen() {
                        if (tried) return;
                        tried = true;
                        window.location.href = '%s';
                        scheduleStoreFallback();
                      }
                      // Lancer après 300 ms pour laisser la page se rendre
                      setTimeout(tryOpen, 300);
                    })();

                    function scheduleStoreFallback() {
                      setTimeout(function() {
                        // Si la page est toujours visible, l'app ne s'est pas ouverte
                        if (!document.hidden) {
                          document.getElementById('store-section').style.display = 'flex';
                          document.getElementById('store-section').style.flexDirection = 'column';
                          document.getElementById('store-section').style.gap = '12px';
                          document.getElementById('store-section').style.alignItems = 'center';
                        }
                      }, 2000);
                    }
                  </script>
                </body>
                </html>
                """.formatted(code, deepLink, PLAY_STORE_URL, deepLink);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }
}
