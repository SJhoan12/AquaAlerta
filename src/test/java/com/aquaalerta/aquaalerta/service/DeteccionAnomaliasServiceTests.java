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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeteccionAnomaliasServiceTests {

    @Test
    void faseCeroDetectaPosibleFugaSinPatronTrasCaidaFisicaSostenida() {
        TestContext context = new TestContext();
        LocalDateTime inicio = LocalDateTime.of(2026, 10, 26, 10, 0);
        PatronConsumo patron = new PatronConsumo();
        when(context.patronRepository.findByTanqueIdAndDiaSemanaAndFranja(
                "tanque-01", "LUNES", "MANANA")).thenReturn(Optional.of(patron));
        when(context.alertaRepository.findByTanqueId("tanque-01")).thenReturn(List.of());

        LecturaNivel historicaAtipica = lectura("historia-invalida", 600, 103,
                inicio.minusDays(10));
        historicaAtipica.setTieneMuestrasAtipicas(true);
        LecturaNivel antesDeTresMinutos = nivelEnCaida(inicio.plusSeconds(150), inicio,
                600.0, 8.5, 600.0, 103.0);
        when(context.lecturaRepository.findByTanqueIdOrderByTimestampDesc("tanque-01"))
                .thenReturn(combinar(historicaAtipica,
                                lecturasFisicas(inicio, 150, 8.5, 600.0, 103.0)),
                        combinar(historicaAtipica,
                                lecturasFisicas(inicio, 180, 8.5, 600.0, 103.0)),
                        combinar(historicaAtipica,
                                lecturasFisicas(inicio, 210, 8.5, 600.0, 103.0)));

        context.service.evaluar(tanque(), antesDeTresMinutos);
        assertEquals(0, patron.getEvaluacionesAnomalasConsecutivas());
        verify(context.alertaRepository, never()).save(any());

        LecturaNivel primeraConfirmacion = nivelEnCaida(inicio.plusSeconds(180), inicio,
                600.0, 8.5, 600.0, 103.0);
        context.service.evaluar(tanque(), primeraConfirmacion);
        assertEquals(1, patron.getEvaluacionesAnomalasConsecutivas());
        verify(context.alertaRepository, never()).save(any());

        LecturaNivel segundaConfirmacion = nivelEnCaida(inicio.plusSeconds(210), inicio,
                600.0, 8.5, 600.0, 103.0);
        context.service.evaluar(tanque(), segundaConfirmacion);

        assertTrue(segundaConfirmacion.isEsAnomalia());
        verify(context.alertaRepository).save(argThat((Alerta alerta) ->
                "ANOMALIA_CONSUMO".equals(alerta.getTipo())
                        && alerta.getDescripcion().contains("posible fuga")
                        && alerta.getDescripcion().contains("8.5 L/min")
                        && alerta.getDescripcion().contains("límite: 8.0 L/min")));
    }

    @Test
    void faseCeroNoDetectaCaidaFisicaPorDebajoDeOchoLitrosPorMinuto() {
        TestContext context = new TestContext();
        LocalDateTime inicio = LocalDateTime.of(2026, 10, 26, 10, 0);
        PatronConsumo patron = new PatronConsumo();
        when(context.patronRepository.findByTanqueIdAndDiaSemanaAndFranja(
                "tanque-01", "LUNES", "MANANA")).thenReturn(Optional.of(patron));
        when(context.lecturaRepository.findByTanqueIdOrderByTimestampDesc("tanque-01"))
                .thenReturn(lecturasFisicas(inicio, 210, 7.5, 600.0, 103.0));

        LecturaNivel lecturaActual = nivelEnCaida(inicio.plusSeconds(210), inicio,
                600.0, 7.5, 600.0, 103.0);
        context.service.evaluar(tanque(), lecturaActual);

        assertEquals(0, patron.getEvaluacionesAnomalasConsecutivas());
        verify(context.alertaRepository, never()).save(any());
    }

    @Test
    void reglaNocturnaPromediaBloquesYConfirmaLaCaida() {
        TestContext context = new TestContext();
        LocalDateTime inicio = LocalDateTime.of(2026, 10, 26, 1, 0);
        PatronConsumo patron = new PatronConsumo();
        when(context.patronRepository.findByTanqueIdAndDiaSemanaAndFranja(
                "tanque-01", "LUNES", "MADRUGADA")).thenReturn(Optional.of(patron));
        when(context.alertaRepository.findByTanqueId("tanque-01")).thenReturn(List.of());

        List<LecturaNivel> primeraLista = lecturasDescendentes(inicio, 22, 100.0, 0.125);
        List<LecturaNivel> segundaLista = lecturasDescendentes(inicio, 23, 100.0, 0.125);
        when(context.lecturaRepository.findByTanqueIdOrderByTimestampDesc("tanque-01"))
                .thenReturn(primeraLista, segundaLista);

        context.service.evaluar(tanque(), ultimaLectura(primeraLista));
        assertEquals(1, patron.getEvaluacionesAnomalasConsecutivas());

        context.service.evaluar(tanque(), ultimaLectura(segundaLista));

        verify(context.alertaRepository).save(argThat((Alerta alerta) ->
                "ANOMALIA_CONSUMO".equals(alerta.getTipo())
                        && "FUGA_NOCTURNA".equals(alerta.getReglaId())
                        && "cm".equals(alerta.getUnidadMedida())
                        && alerta.getDescripcion().contains("01:00-05:00")));
    }

    @Test
    void reglaDeFugaLentaDetectaTendenciaAunqueNoSeaUnaCaidaRapida() {
        TestContext context = new TestContext();
        LocalDateTime inicio = LocalDateTime.of(2026, 10, 26, 10, 0);
        PatronConsumo patron = new PatronConsumo();
        when(context.patronRepository.findByTanqueIdAndDiaSemanaAndFranja(
                "tanque-01", "LUNES", "MANANA")).thenReturn(Optional.of(patron));
        when(context.alertaRepository.findByTanqueId("tanque-01")).thenReturn(List.of());

        List<LecturaNivel> primeraLista = lecturasDescendentes(inicio, 61, 100.0, 0.025);
        List<LecturaNivel> segundaLista = lecturasDescendentes(inicio, 62, 100.0, 0.025);
        when(context.lecturaRepository.findByTanqueIdOrderByTimestampDesc("tanque-01"))
                .thenReturn(primeraLista, segundaLista);

        context.service.evaluar(tanque(), ultimaLectura(primeraLista));
        assertEquals(1, patron.getEvaluacionesAnomalasConsecutivas());

        context.service.evaluar(tanque(), ultimaLectura(segundaLista));

        verify(context.alertaRepository).save(argThat((Alerta alerta) ->
                "FUGA_LENTA".equals(alerta.getReglaId())
                        && alerta.getDescripcion().contains("30 minutos")
                        && "cm".equals(alerta.getUnidadMedida())));
    }

    @Test
    void reglaDeCaidaBruscaExigeTresLecturasYRechazaUnPicoAislado() {
        TestContext context = new TestContext();
        LocalDateTime inicio = LocalDateTime.of(2026, 10, 26, 10, 0);
        PatronConsumo patron = new PatronConsumo();
        when(context.patronRepository.findByTanqueIdAndDiaSemanaAndFranja(
                "tanque-01", "LUNES", "MANANA")).thenReturn(Optional.of(patron));
        when(context.alertaRepository.findByTanqueId("tanque-01")).thenReturn(List.of());

        List<LecturaNivel> primeraLista = List.of(
                lectura("brusca-1", 600.0 * 100.0 / 103.0, 100.0, inicio),
                lectura("brusca-2", 600.0 * 98.4 / 103.0, 98.4, inicio.plusSeconds(30)),
                lectura("brusca-3", 600.0 * 96.8 / 103.0, 96.8, inicio.plusSeconds(60)));
        List<LecturaNivel> segundaLista = new ArrayList<>(primeraLista);
        segundaLista.add(lectura("brusca-4", 600.0 * 95.2 / 103.0,
                95.2, inicio.plusSeconds(90)));
        when(context.lecturaRepository.findByTanqueIdOrderByTimestampDesc("tanque-01"))
                .thenReturn(primeraLista, segundaLista);

        context.service.evaluar(tanque(), ultimaLectura(primeraLista));
        assertEquals(1, patron.getEvaluacionesAnomalasConsecutivas());

        context.service.evaluar(tanque(), ultimaLectura(segundaLista));

        verify(context.alertaRepository).save(argThat((Alerta alerta) ->
                "CAIDA_BRUSCA".equals(alerta.getReglaId())
                        && alerta.getDesviacionPorcentaje() >= 3.0
                        && "cm".equals(alerta.getUnidadMedida())));

        TestContext ruido = new TestContext();
        when(ruido.patronRepository.findByTanqueIdAndDiaSemanaAndFranja(
                "tanque-01", "LUNES", "MANANA")).thenReturn(Optional.of(new PatronConsumo()));
        List<LecturaNivel> spike = List.of(
                lectura("spike-1", 600.0 * 100.0 / 103.0, 100.0, inicio),
                lectura("spike-2", 600.0 * 96.0 / 103.0, 96.0, inicio.plusSeconds(30)),
                lectura("spike-3", 600.0 * 100.0 / 103.0, 100.0, inicio.plusSeconds(60)));
        when(ruido.lecturaRepository.findByTanqueIdOrderByTimestampDesc("tanque-01"))
                .thenReturn(spike);
        ruido.service.evaluar(tanque(), ultimaLectura(spike));
        verify(ruido.alertaRepository, never()).save(any());
    }

    @Test
    void faseUnoComparaRitmoActualConElPromedioDelMismoDia() {
        TestContext context = new TestContext();
        LocalDateTime inicioDia = LocalDateTime.of(2026, 10, 26, 9, 0);
        LocalDateTime inicioConsumoLento = LocalDateTime.of(2026, 10, 26, 9, 50);
        LocalDateTime inicioCaida = LocalDateTime.of(2026, 10, 26, 10, 1);
        PatronConsumo patron = new PatronConsumo();
        when(context.patronRepository.findByTanqueIdAndDiaSemanaAndFranja(
                "tanque-01", "LUNES", "MANANA")).thenReturn(Optional.of(patron));
        when(context.alertaRepository.findByTanqueId("tanque-01")).thenReturn(List.of());

        List<LecturaNivel> primeraLista = combinar(
                lectura("inicio-dia", 600, 103, inicioDia),
                combinar(lectura("inicio-consumo", 580, 103.0 * 580 / 600, inicioConsumoLento),
                        lecturasFisicas(inicioConsumoLento, 360, 0.75, 580, 103.0)),
                lecturasFisicas(inicioCaida, 240, 3.0, 577, 103.0));
        List<LecturaNivel> segundaLista = combinar(
                lectura("inicio-dia", 600, 103, inicioDia),
                combinar(lectura("inicio-consumo", 580, 103.0 * 580 / 600, inicioConsumoLento),
                        lecturasFisicas(inicioConsumoLento, 360, 0.75, 580, 103.0)),
                lecturasFisicas(inicioCaida, 270, 3.0, 577, 103.0));
        when(context.lecturaRepository.findByTanqueIdOrderByTimestampDesc("tanque-01"))
                .thenReturn(primeraLista, segundaLista);

        LecturaNivel primera = ultimaLectura(primeraLista);
        context.service.evaluar(tanque(), primera);
        assertEquals(1, patron.getEvaluacionesAnomalasConsecutivas());
        verify(context.alertaRepository, never()).save(any());

        LecturaNivel segunda = ultimaLectura(segundaLista);
        context.service.evaluar(tanque(), segunda);

        assertTrue(segunda.isEsAnomalia());
        verify(context.alertaRepository).save(argThat((Alerta alerta) ->
                alerta.getDescripcion().contains("Fase 1")
                        && alerta.getDescripcion().contains("veces el ritmo promedio de hoy")));
    }

    @Test
    void faseDosUsaElPatronDesdeLaPrimeraTasaSinEsperarTresMuestras() {
        TestContext context = new TestContext();
        LocalDateTime inicioHistorico = LocalDateTime.of(2026, 10, 24, 8, 0);
        LocalDateTime inicioCaida = LocalDateTime.of(2026, 10, 26, 10, 0);
        LocalDateTime primeraEvaluacion = inicioCaida.plusMinutes(3);
        PatronConsumo patron = new PatronConsumo();
        patron.setTasaReferenciaPctNivelPorMinuto(0.1);
        patron.setMuestrasTasaReferenciaNivel(1);
        when(context.patronRepository.findByTanqueIdAndDiaSemanaAndFranja(
                "tanque-01", "LUNES", "MANANA")).thenReturn(Optional.of(patron));
        when(context.alertaRepository.findByTanqueId("tanque-01")).thenReturn(List.of());

        List<LecturaNivel> primeraLista = combinar(
                lectura("lectura-inicial", 600, 103, inicioHistorico),
                lecturasFisicas(inicioCaida, 180, 1.2, 600, 103));
        List<LecturaNivel> segundaLista = combinar(
                lectura("lectura-inicial", 600, 103, inicioHistorico),
                lecturasFisicas(inicioCaida, 210, 1.2, 600, 103));
        when(context.lecturaRepository.findByTanqueIdOrderByTimestampDesc("tanque-01"))
                .thenReturn(primeraLista, segundaLista);

        LecturaNivel primera = ultimaLectura(primeraLista);
        assertEquals(primeraEvaluacion, primera.getTimestamp());
        context.service.evaluar(tanque(), primera);
        assertEquals(1, patron.getEvaluacionesAnomalasConsecutivas());

        LecturaNivel segunda = ultimaLectura(segundaLista);
        context.service.evaluar(tanque(), segunda);

        assertTrue(segunda.isEsAnomalia());
        verify(context.alertaRepository).save(argThat((Alerta alerta) ->
                alerta.getDescripcion().contains("Fase 2")
                        && alerta.getDescripcion().contains("patrón histórico")));
        assertEquals(1, patron.getMuestrasTasaReferenciaNivel());
    }

    @Test
    void faseDosMantieneElMargenInformativoConfigurable() {
        TestContext context = new TestContext();
        LocalDateTime inicioHistorico = LocalDateTime.of(2026, 10, 24, 8, 0);
        LocalDateTime inicioCaida = LocalDateTime.of(2026, 10, 26, 10, 0);
        PatronConsumo patron = new PatronConsumo();
        patron.setTasaReferenciaPctNivelPorMinuto(0.1);
        patron.setMuestrasTasaReferenciaNivel(1);
        when(context.patronRepository.findByTanqueIdAndDiaSemanaAndFranja(
                "tanque-01", "LUNES", "MANANA")).thenReturn(Optional.of(patron));
        when(context.alertaRepository.findByTanqueId("tanque-01")).thenReturn(List.of());
        List<LecturaNivel> lecturas = combinar(
                lectura("lectura-inicial", 600, 103, inicioHistorico),
                lecturasFisicas(inicioCaida, 240, 0.78, 600, 103));
        when(context.lecturaRepository.findByTanqueIdOrderByTimestampDesc("tanque-01"))
                .thenReturn(lecturas);

        context.service.evaluar(tanque(), ultimaLectura(lecturas));

        verify(context.alertaRepository).save(argThat((Alerta alerta) ->
                "INFORMATIVO".equals(alerta.getTipo())
                        && alerta.getUmbralAplicadoPct() == 20.0));
        verify(context.lecturaRepository, never()).save(any());
    }

    @Test
    void faseDosNoUsaLaReglaFisicaDeNueveLitrosPorMinuto() {
        TestContext context = new TestContext();
        LocalDateTime inicioHistorico = LocalDateTime.of(2026, 10, 24, 8, 0);
        LocalDateTime inicioCaida = LocalDateTime.of(2026, 10, 26, 10, 0);
        PatronConsumo patron = new PatronConsumo();
        patron.setTasaReferenciaPctNivelPorMinuto(2.0);
        patron.setMuestrasTasaReferenciaNivel(1);
        when(context.patronRepository.findByTanqueIdAndDiaSemanaAndFranja(
                "tanque-01", "LUNES", "MANANA")).thenReturn(Optional.of(patron));
        when(context.alertaRepository.findByTanqueId("tanque-01")).thenReturn(List.of());
        List<LecturaNivel> lecturas = combinar(
                lectura("lectura-inicial", 600, 103, inicioHistorico),
                lecturasFisicas(inicioCaida, 240, 12.0, 600, 103));
        when(context.lecturaRepository.findByTanqueIdOrderByTimestampDesc("tanque-01"))
                .thenReturn(lecturas);

        context.service.evaluar(tanque(), ultimaLectura(lecturas));

        verify(context.alertaRepository, never()).save(any());
        assertFalse(ultimaLectura(lecturas).isEsAnomalia());
    }

    @Test
    void ignoraLecturasConMuestrasAtipicas() {
        TestContext context = new TestContext();
        LecturaNivel lectura = lectura("atipica", 500, 85,
                LocalDateTime.of(2026, 10, 26, 10, 0));
        lectura.setTieneMuestrasAtipicas(true);

        context.service.evaluar(tanque(), lectura);

        verify(context.patronRepository, never()).save(any());
        verify(context.lecturaRepository, never())
                .findByTanqueIdOrderByTimestampDesc("tanque-01");
    }

    private static Tanque tanque() {
        Tanque tanque = new Tanque();
        tanque.setId("tanque-01");
        tanque.setVolumenMax(600);
        tanque.setAlturaMaxCm(103);
        tanque.setMargenInformativoPct(20);
        tanque.setUmbralAnomaliaPct(50);
        return tanque;
    }

    private static List<LecturaNivel> lecturasFisicas(LocalDateTime inicio,
                                                       int duracionSegundos,
                                                       double litrosPorMinuto,
                                                       double volumenInicial,
                                                       double alturaMaxima) {
        int intervalos = duracionSegundos / 30;
        List<LecturaNivel> lecturas = new ArrayList<>();
        for (int i = 0; i <= intervalos; i++) {
            LocalDateTime timestamp = inicio.plusSeconds(i * 30L);
            double volumen = volumenInicial - litrosPorMinuto * (i * 0.5);
            double nivel = alturaMaxima * volumen / 600.0;
            lecturas.add(lectura("lectura-" + timestamp, volumen, nivel, timestamp));
        }
        return lecturas;
    }

    private static List<LecturaNivel> lecturasDescendentes(LocalDateTime inicio,
                                                            int cantidad,
                                                            double nivelInicial,
                                                            double caidaCada30Segundos) {
        List<LecturaNivel> lecturas = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            double nivel = nivelInicial - i * caidaCada30Segundos;
            double volumen = 600.0 * nivel / 103.0;
            lecturas.add(lectura("descenso-" + i, volumen, nivel,
                    inicio.plusSeconds(i * 30L)));
        }
        return lecturas;
    }

    private static LecturaNivel nivelEnCaida(LocalDateTime timestamp,
                                              LocalDateTime inicio,
                                              double volumenInicial,
                                              double litrosPorMinuto,
                                              double capacidad,
                                              double alturaMaxima) {
        int intervalos = (int) java.time.Duration.between(inicio, timestamp).getSeconds() / 30;
        double volumen = volumenInicial - litrosPorMinuto * (intervalos * 0.5);
        return lectura("actual-" + timestamp, volumen,
                alturaMaxima * volumen / capacidad, timestamp);
    }

    private static List<LecturaNivel> combinar(LecturaNivel lectura, List<LecturaNivel> resto) {
        List<LecturaNivel> todas = new ArrayList<>(resto);
        todas.add(lectura);
        return todas;
    }

    private static List<LecturaNivel> combinar(LecturaNivel primera,
                                                 List<LecturaNivel> segunda,
                                                 List<LecturaNivel> tercera) {
        List<LecturaNivel> todas = new ArrayList<>(tercera);
        todas.addAll(segunda);
        todas.add(primera);
        return todas;
    }

    private static List<LecturaNivel> combinar(LecturaNivel primera,
                                                 LecturaNivel segunda,
                                                 List<LecturaNivel> resto) {
        List<LecturaNivel> todas = new ArrayList<>(resto);
        todas.add(primera);
        todas.add(segunda);
        return todas;
    }

    private static LecturaNivel lectura(String id,
                                        double volumen,
                                        double nivel,
                                        LocalDateTime timestamp) {
        LecturaNivel lectura = new LecturaNivel();
        lectura.setId(id);
        lectura.setTanqueId("tanque-01");
        lectura.setVolumenLitros(volumen);
        lectura.setNivelCm(nivel);
        lectura.setTimestamp(timestamp);
        return lectura;
    }

    private static LecturaNivel ultimaLectura(List<LecturaNivel> lecturas) {
        return lecturas.stream()
                .max(java.util.Comparator.comparing(LecturaNivel::getTimestamp))
                .orElseThrow();
    }

    private static class TestContext {
        private final LecturaNivelRepository lecturaRepository = mock(LecturaNivelRepository.class);
        private final PatronConsumoRepository patronRepository = mock(PatronConsumoRepository.class);
        private final AlertaRepository alertaRepository = mock(AlertaRepository.class);
        private final DeteccionAnomaliasService service = new DeteccionAnomaliasService(
                lecturaRepository, patronRepository, alertaRepository);
    }
}
