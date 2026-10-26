package com.aquaalerta.aquaalerta.service;

import com.aquaalerta.aquaalerta.LecturaNivel;
import com.aquaalerta.aquaalerta.Tanque;
import com.aquaalerta.aquaalerta.repository.AlertaRepository;
import com.aquaalerta.aquaalerta.repository.LecturaNivelRepository;
import com.aquaalerta.aquaalerta.repository.PatronConsumoRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeteccionAnomaliasServiceTests {

    @Test
    void noActualizaPatronCuandoLaLecturaTieneMuestrasAtipicas() {
        LecturaNivelRepository lecturaRepository = mock(LecturaNivelRepository.class);
        PatronConsumoRepository patronRepository = mock(PatronConsumoRepository.class);
        AlertaRepository alertaRepository = mock(AlertaRepository.class);
        DeteccionAnomaliasService service = new DeteccionAnomaliasService(
                lecturaRepository, patronRepository, alertaRepository);

        Tanque tanque = new Tanque();
        tanque.setId("tanque-01");
        tanque.setMargenInformativoPct(10);
        tanque.setUmbralAnomaliaPct(30);

        LecturaNivel lectura = new LecturaNivel();
        lectura.setId("lectura-01");
        lectura.setTanqueId("tanque-01");
        lectura.setTimestamp(LocalDateTime.of(2026, 10, 26, 10, 0));
        lectura.setVolumenLitros(500);
        lectura.setTieneMuestrasAtipicas(true);

        when(lecturaRepository.findByTanqueIdOrderByTimestampDesc("tanque-01"))
                .thenReturn(List.of(lectura));
        when(patronRepository.findByTanqueIdAndDiaSemanaAndFranja(
                "tanque-01", "LUNES", "MANANA"))
                .thenReturn(Optional.empty());

        service.evaluar(tanque, lectura);

        verify(patronRepository, never()).save(any());
    }
}
