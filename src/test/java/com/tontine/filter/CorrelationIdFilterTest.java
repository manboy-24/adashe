package com.tontine.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {

    @Mock private HttpServletRequest  request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain         filterChain;

    @InjectMocks
    private CorrelationIdFilter filter;

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void en_absence_de_header_genere_un_uuid_et_le_met_dans_mdc() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn(null);

        // Capture l'ID injecté dans le MDC pendant l'exécution de la chaîne
        final String[] capturedId = new String[1];
        doAnswer(inv -> { capturedId[0] = MDC.get(CorrelationIdFilter.MDC_KEY); return null; })
                .when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(capturedId[0]).isNotNull().matches("[0-9a-f-]{36}");
    }

    @Test
    void header_client_present_utilise_la_valeur_fournie() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn("my-client-id-123");

        final String[] capturedId = new String[1];
        doAnswer(inv -> { capturedId[0] = MDC.get(CorrelationIdFilter.MDC_KEY); return null; })
                .when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(capturedId[0]).isEqualTo("my-client-id-123");
    }

    @Test
    void correlation_id_est_ecrit_dans_la_reponse() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn("resp-id-456");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(eq(CorrelationIdFilter.HEADER), eq("resp-id-456"));
    }

    @Test
    void mdc_nettoye_apres_la_chaine() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn("id-cleanup");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void mdc_nettoye_meme_si_la_chaine_leve_une_exception() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn("id-error");
        doThrow(new RuntimeException("Downstream error")).when(filterChain).doFilter(any(), any());

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
                .isInstanceOf(RuntimeException.class);

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void header_vide_genere_un_nouvel_uuid() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn("   ");

        final String[] capturedId = new String[1];
        doAnswer(inv -> { capturedId[0] = MDC.get(CorrelationIdFilter.MDC_KEY); return null; })
                .when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(capturedId[0]).isNotBlank().doesNotContain(" ");
    }

    @Test
    void filter_chain_est_toujours_appele() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
