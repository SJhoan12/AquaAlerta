package com.aquaalerta.aquaalerta;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "aquaalerta.bluetooth.enabled=false")
class AquaAlertaApplicationTests {

    @Test
    void contextLoads() {
    }
}