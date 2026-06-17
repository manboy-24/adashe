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

    @GetMapping(value = "/join/{code}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> redirectToApp(@PathVariable String code) {
        String deepLink = "adashecash://join/" + code;
        String html = """
                <!DOCTYPE html>
                <html lang="fr">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <meta http-equiv="refresh" content="1;url=%s">
                  <title>Adashe – Rejoindre la tontine</title>
                  <style>
                    body { font-family: sans-serif; text-align: center;
                           padding: 40px 20px; background: #0d1b2a; color: #fff; }
                    h2   { color: #1d9e75; }
                    .code { font-size: 2rem; font-weight: bold; letter-spacing: 6px;
                            color: #ef9f27; margin: 24px 0; }
                    p    { color: rgba(255,255,255,.6); font-size: .9rem; }
                    a    { color: #1d9e75; }
                  </style>
                  <script>
                    setTimeout(function(){ window.location.href = '%s'; }, 500);
                  </script>
                </head>
                <body>
                  <h2>Adashe</h2>
                  <p>Ouverture de l'application…</p>
                  <div class="code">%s</div>
                  <p>Si l'app ne s'ouvre pas automatiquement :<br>
                     lancez <strong>Adashe</strong> → bouton <em>Rejoindre</em>
                     et entrez le code ci-dessus.</p>
                  <p><a href="%s">Ouvrir manuellement</a></p>
                </body>
                </html>
                """.formatted(deepLink, deepLink, code, deepLink);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }
}
