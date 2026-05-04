package com.tontine.util;

import com.tontine.exception.UnauthorizedException;
import com.tontine.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityUtil {

    private final UtilisateurRepository utilisateurRepository;

    public Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new UnauthorizedException("Non authentifié");
        }
        return utilisateurRepository.findByTelephone(auth.getName())
                .orElseThrow(() -> new UnauthorizedException("Utilisateur non trouvé"))
                .getId();
    }
}
