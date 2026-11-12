package com.aquaalerta.aquaalerta.controller;

import com.aquaalerta.aquaalerta.repository.LecturaNivelRepository;
import com.aquaalerta.aquaalerta.repository.TanqueRepository;
import com.aquaalerta.aquaalerta.service.CalculoVolumenService;
import com.aquaalerta.aquaalerta.service.DeteccionAnomaliasService;
import com.aquaalerta.aquaalerta.service.ProcesamientoMuestrasService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class LecturaControllerSecurityTests {

    private final TanqueRepository tanqueRepository = mock(TanqueRepository.class);
    private final LecturaNivelRepository lecturaRepository = mock(LecturaNivelRepository.class);
    private final DeteccionAnomaliasService deteccionService = mock(DeteccionAnomaliasService.class);

    @Test
    void rejectsSensorRequestsWhenApiKeyIsNotConfigured() {
        LecturaController controller = controller("");

        var response = controller.recibirLectura(null, new LecturaController.LecturaRequest());

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        verifyNoInteractions(tanqueRepository, lecturaRepository, deteccionService);
    }

    @Test
    void rejectsSensorRequestsWithMissingOrIncorrectApiKey() {
        LecturaController controller = controller("expected-key");

        var missingKey = controller.recibirLectura(null, new LecturaController.LecturaRequest());
        var wrongKey = controller.recibirLectura("wrong-key", new LecturaController.LecturaRequest());

        assertEquals(HttpStatus.UNAUTHORIZED, missingKey.getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, wrongKey.getStatusCode());
        verifyNoInteractions(tanqueRepository, lecturaRepository, deteccionService);
    }

    @Test
    void acceptsApiKeyBeforeValidatingTheSensorPayload() {
        LecturaController controller = controller("expected-key");

        var response = controller.recibirLectura(
                "expected-key", new LecturaController.LecturaRequest());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    private LecturaController controller(String apiKey) {
        return new LecturaController(
                tanqueRepository,
                lecturaRepository,
                deteccionService,
                new CalculoVolumenService(),
                new ProcesamientoMuestrasService(),
                apiKey);
    }
}
