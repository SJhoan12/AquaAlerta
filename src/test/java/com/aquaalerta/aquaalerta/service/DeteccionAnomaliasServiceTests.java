package com.aquaalerta.aquaalerta.service;

import com.aquaalerta.aquaalerta.Alerta;
import com.aquaalerta.aquaalerta.LecturaNivel;
import com.aquaalerta.aquaalerta.PatronConsumo;
import com.aquaalerta.aquaalerta.Tanque;
import com.aquaalerta.aquaalerta.repository.AlertaRepository;
import com.aquaalerta.aquaalerta.repository.LecturaNivelRepository;
import com.aquaalerta.aquaalerta.repository.PatronConsumoRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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

        Tanque tanque = tanque();
        LecturaNivel lectura = lectura("lectura-01", 20, LocalDateTime.of(2026, 10, 26, 10, 0));
        lectura.setTieneMuestrasAtipicas(true);

        service.evaluar(tanque, lectura);

        verify(patronRepository, never()).save(any());
    }

    @Test
    void generaAnomaliaSoloTrasDosEvaluacionesAltasConsecutivas() {
        LecturaNivelRepository lecturaRepository = mock(LecturaNivelRepository.class);
        PatronConsumoRepository patronRepository = mock(PatronConsumoRepository.class);
        AlertaRepository alertaRepository = mock(AlertaRepository.class);
        DeteccionAnomaliasService service = new DeteccionAnomaliasService(
                lecturaRepository, patronRepository, alertaRepository);

        LocalDateTime primeraEvaluacion = LocalDateTime.of(2026, 10, 26, 10, 2);
        LocalDateTime segundaEvaluacion = primeraEvaluacion.plusSeconds(40);
        LecturaNivel inicial = lectura("lectura-00", 20, primeraEvaluacion.minusMinutes(2));
        LecturaNivel anterior = lectura("lectura-01", 19.8, primeraEvaluacion.minusMinutes(1));
        LecturaNivel primera = lectura("lectura-02", 19.5, primeraEvaluacion);
        LecturaNivel segunda = lectura("lectura-03", 19.3, segundaEvaluacion);
        Tanque tanque = tanque();

        PatronConsumo patron = new PatronConsumo();
        patron.setTasaReferenciaPctNivelPorMinuto(0.1);
        patron.setMuestrasTasaReferenciaNivel(1);
        patron.setUltimaEvaluacionTasa(primeraEvaluacion.minusSeconds(40));
        when(patronRepository.findByTanqueIdAndDiaSemanaAndFranja(
                "tanque-01", "LUNES", "MANANA"))
                .thenReturn(Optional.of(patron));
        when(lecturaRepository.findByTanqueIdOrderByTimestampDesc("tanque-01"))
                .thenReturn(List.of(primera, anterior, inicial), List.of(segunda, primera, anterior, inicial));
        when(alertaRepository.findByTanqueId("tanque-01")).thenReturn(List.of());

        service.evaluar(tanque, primera);

        assertEquals(1, patron.getEvaluacionesAnomalasConsecutivas());
        verify(alertaRepository, never()).save(any());

        service.evaluar(tanque, segunda);

        verify(alertaRepository).save(argThat((Alerta alerta) ->
                "ANOMALIA_CONSUMO".equals(alerta.getTipo())
                        && alerta.getDescripcion().contains("% de la altura por minuto")));
        verify(lecturaRepository).save(segunda);
    }

    @Test
    void usaLaPrimeraTasaValidaComoReferenciaSinEsperarTresMuestras() {
        LecturaNivelRepository lecturaRepository = mock(LecturaNivelRepository.class);
        PatronConsumoRepository patronRepository = mock(PatronConsumoRepository.class);
        AlertaRepository alertaRepository = mock(AlertaRepository.class);
        DeteccionAnomaliasService service = new DeteccionAnomaliasService(
                lecturaRepository, patronRepository, alertaRepository);

        LocalDateTime timestamp = LocalDateTime.of(2026, 10, 26, 10, 2);
        LecturaNivel anterior = lectura("lectura-01", 20, timestamp.minusMinutes(1));
        LecturaNivel actual = lectura("lectura-02", 19.5, timestamp);
        Tanque tanque = tanque();
        PatronConsumo patron = new PatronConsumo();
        when(patronRepository.findByTanqueIdAndDiaSemanaAndFranja(
                "tanque-01", "LUNES", "MANANA"))
                .thenReturn(Optional.of(patron));
        when(lecturaRepository.findByTanqueIdOrderByTimestampDesc("tanque-01"))
                .thenReturn(List.of(actual, anterior));

        service.evaluar(tanque, actual);

        assertEquals(0.5, patron.getTasaReferenciaPctNivelPorMinuto(), 0.0001);
        assertEquals(1, patron.getMuestrasTasaReferenciaNivel());
        verify(alertaRepository, never()).save(any());
        verify(patronRepository).save(patron);
    }

    @Test
    void noAlertaPorCaidaMenorAlMinimoDeConsumo() {
        LecturaNivelRepository lecturaRepository = mock(LecturaNivelRepository.class);
        PatronConsumoRepository patronRepository = mock(PatronConsumoRepository.class);
        AlertaRepository alertaRepository = mock(AlertaRepository.class);
        DeteccionAnomaliasService service = new DeteccionAnomaliasService(
                lecturaRepository, patronRepository, alertaRepository);

        LocalDateTime timestamp = LocalDateTime.of(2026, 10, 26, 10, 2);
        LecturaNivel anterior = lectura("lectura-01", 20, timestamp.minusMinutes(1));
        LecturaNivel actual = lectura("lectura-02", 19.95, timestamp);
        Tanque tanque = tanque();

        PatronConsumo patron = new PatronConsumo();
        patron.setTasaReferenciaPctNivelPorMinuto(0.1);
        patron.setMuestrasTasaReferenciaNivel(1);
        when(patronRepository.findByTanqueIdAndDiaSemanaAndFranja(
                "tanque-01", "LUNES", "MANANA"))
                .thenReturn(Optional.of(patron));
        when(lecturaRepository.findByTanqueIdOrderByTimestampDesc("tanque-01"))
                .thenReturn(List.of(actual, anterior));

        service.evaluar(tanque, actual);

        assertEquals(0, patron.getEvaluacionesAnomalasConsecutivas());
        verify(alertaRepository, never()).save(any());
    }

    private Tanque tanque() {
        Tanque tanque = new Tanque();
        tanque.setId("tanque-01");
        tanque.setVolumenMax(20);
        tanque.setAlturaMaxCm(100);
        tanque.setMargenInformativoPct(20);
        tanque.setUmbralAnomaliaPct(50);
        return tanque;
    }

    private LecturaNivel lectura(String id, double volumen, LocalDateTime timestamp) {
        LecturaNivel lectura = new LecturaNivel();
        lectura.setId(id);
        lectura.setTanqueId("tanque-01");
        lectura.setVolumenLitros(volumen);
        lectura.setNivelCm(volumen);
        lectura.setTimestamp(timestamp);
        return lectura;
    }
}
