package com.tontine.security;

import com.tontine.entity.Utilisateur;
import com.tontine.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UtilisateurRepository utilisateurRepository;

    @Override
    public UserDetails loadUserByUsername(String telephone) throws UsernameNotFoundException {
        Utilisateur u = utilisateurRepository.findByTelephone(telephone)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé: " + telephone));

        return User.builder()
                .username(u.getTelephone())
                // Mot de passe vide — auth se fait via PIN ou OTP, pas mot de passe
                .password(u.getMotDePasse() != null ? u.getMotDePasse() : "")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())))
                .accountExpired(false)
                .accountLocked(u.estPinBloque())
                .credentialsExpired(false)
                .disabled(!u.getActif())
                .build();
    }
}
