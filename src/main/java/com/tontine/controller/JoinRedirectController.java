package com.tontine.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Endpoint public : redirige vers le deep link adashecash://join/{code}.
 * URL partagée sur WhatsApp/SMS — doit être HTTPS pour être cliquable.
 * Utilise un Intent URI Android (intent://) au lieu de adashecash:// pour
 * contourner le blocage des redirections JS sans geste utilisateur (Chrome 83+).
 */
@RestController
public class JoinRedirectController {

    private static final String PLAY_STORE_URL =
            "https://play.google.com/store/apps/details?id=com.tontineplus.app";

    private static final String LOGO_B64 = "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";

    @GetMapping(value = "/join/{code}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> redirectToApp(@PathVariable String code) {
        // Intent URI Android : intercepté par Chrome sans geste utilisateur requis.
        // S.browser_fallback_url redirige vers le Play Store si l'app n'est pas installée.
        String playStoreEncoded = URLEncoder.encode(PLAY_STORE_URL, StandardCharsets.UTF_8);
        String intentUri = "intent://join/" + code
                + "#Intent;scheme=adashecash;package=com.tontineplus.app"
                + ";S.browser_fallback_url=" + playStoreEncoded + ";end";
        String html = """
                <!DOCTYPE html>
                <html lang="fr">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
                  <title>Adashe – Invitation</title>
                  <style>
                    *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

                    body {
                      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                      background: #0a1628;
                      color: #ffffff;
                      min-height: 100vh;
                      display: flex;
                      flex-direction: column;
                      align-items: center;
                      justify-content: center;
                      padding: 32px 24px;
                      gap: 0;
                    }

                    /* ─── Card ─── */
                    .card {
                      background: #111e33;
                      border: 1px solid rgba(255,255,255,.07);
                      border-radius: 24px;
                      width: 100%%;
                      max-width: 380px;
                      padding: 40px 32px 36px;
                      display: flex;
                      flex-direction: column;
                      align-items: center;
                      gap: 24px;
                      box-shadow: 0 24px 64px rgba(0,0,0,.5);
                    }

                    /* ─── Logo + nom ─── */
                    .brand { display: flex; flex-direction: column; align-items: center; gap: 12px; }
                    .brand img { width: 80px; height: 80px; border-radius: 20px; }
                    .brand-name {
                      font-size: 1.5rem; font-weight: 700; letter-spacing: .5px;
                      background: linear-gradient(135deg, #1fba86, #17d47a);
                      -webkit-background-clip: text; -webkit-text-fill-color: transparent;
                    }

                    /* ─── Invite text ─── */
                    .invite-text {
                      text-align: center;
                      font-size: .95rem;
                      color: rgba(255,255,255,.6);
                      line-height: 1.5;
                    }
                    .invite-text strong { color: rgba(255,255,255,.85); font-weight: 600; }

                    /* ─── Code ─── */
                    .code-block {
                      width: 100%%;
                      background: rgba(239,159,39,.07);
                      border: 1.5px dashed rgba(239,159,39,.35);
                      border-radius: 16px;
                      padding: 18px 24px;
                      text-align: center;
                    }
                    .code-label {
                      font-size: .68rem; font-weight: 600;
                      letter-spacing: 2px; text-transform: uppercase;
                      color: rgba(239,159,39,.6); margin-bottom: 6px;
                    }
                    .code-value {
                      font-size: 2rem; font-weight: 800;
                      letter-spacing: 8px; color: #ef9f27;
                    }

                    /* ─── Divider ─── */
                    .divider {
                      width: 100%%; display: flex; align-items: center; gap: 12px;
                    }
                    .divider span { flex: 1; height: 1px; background: rgba(255,255,255,.08); }
                    .divider p { font-size: .75rem; color: rgba(255,255,255,.25); white-space: nowrap; }

                    /* ─── Buttons ─── */
                    .btn {
                      display: block; width: 100%%; padding: 16px 24px;
                      border-radius: 14px; font-size: 1rem; font-weight: 700;
                      text-align: center; text-decoration: none;
                      border: none; cursor: pointer; transition: opacity .15s;
                    }
                    .btn:active { opacity: .8; }

                    .btn-primary {
                      background: linear-gradient(135deg, #1fba86 0%%, #0d9e6e 100%%);
                      color: #fff;
                      box-shadow: 0 8px 24px rgba(29,158,117,.35);
                    }

                    .btn-store {
                      background: transparent;
                      border: 1.5px solid rgba(255,255,255,.15);
                      color: rgba(255,255,255,.7);
                      font-size: .9rem;
                      font-weight: 600;
                    }
                    .btn-store:hover { border-color: rgba(255,255,255,.3); color: #fff; }

                    /* ─── Store section (caché jusqu'au fallback) ─── */
                    #store-section {
                      display: none; flex-direction: column;
                      align-items: center; gap: 12px; width: 100%%;
                    }
                    #store-section.visible { display: flex; }

                    .store-hint {
                      font-size: .78rem; color: rgba(255,255,255,.35);
                      text-align: center; line-height: 1.5;
                    }

                    /* ─── Footer hint ─── */
                    .footer-hint {
                      font-size: .72rem; color: rgba(255,255,255,.2);
                      text-align: center; line-height: 1.6; margin-top: 4px;
                    }
                    .footer-hint a { color: rgba(29,158,117,.7); text-decoration: none; }
                  </style>
                </head>
                <body>
                  <div class="card">

                    <!-- Logo + nom -->
                    <div class="brand">
                      <img src="data:image/png;base64,%s" alt="Adashe">
                      <span class="brand-name">Adashe</span>
                    </div>

                    <!-- Message d'invitation -->
                    <p class="invite-text">
                      Tu as reçu une invitation à rejoindre<br>
                      <strong>une tontine sur Adashe</strong> 🎉
                    </p>

                    <!-- Code -->
                    <div class="code-block">
                      <div class="code-label">Code d'invitation</div>
                      <div class="code-value">%s</div>
                    </div>

                    <!-- Bouton principal : Intent URI Android → ouvre l'app ou Play Store -->
                    <a class="btn btn-primary" href="%s" id="openBtn">
                      Ouvrir dans l'application
                    </a>

                    <!-- Séparateur + fallback Play Store -->
                    <div id="store-section">
                      <div class="divider">
                        <span></span><p>Application non installée&nbsp;?</p><span></span>
                      </div>
                      <p class="store-hint">
                        Télécharge Adashe gratuitement, puis reviens sur ce lien.
                      </p>
                      <a class="btn btn-store" href="%s">
                        Télécharger sur Google Play
                      </a>
                    </div>

                    <!-- Footer -->
                    <p class="footer-hint">
                      Déjà installé mais ça ne s'ouvre pas&nbsp;?<br>
                      Lance Adashe → <em>Rejoindre</em> et entre le code ci-dessus.
                    </p>

                  </div>

                  <script>
                    (function () {
                      var isAndroid = /Android/i.test(navigator.userAgent);
                      var storeSection = document.getElementById('store-section');

                      if (isAndroid) {
                        // Intent URI : Chrome Android l'intercepte sans geste utilisateur.
                        // Si l'app est installée → ouvre l'app.
                        // Sinon → S.browser_fallback_url redirige vers le Play Store.
                        window.location.replace('%s');
                        // Afficher quand même le Play Store comme secours après 2,5 s
                        setTimeout(function () {
                          if (!document.hidden) storeSection.classList.add('visible');
                        }, 2500);
                      } else {
                        // Desktop / iOS : montrer directement le lien Play Store
                        storeSection.classList.add('visible');
                      }
                    })();
                  </script>
                </body>
                </html>
                """.formatted(LOGO_B64, code, intentUri, PLAY_STORE_URL, intentUri);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }
}
