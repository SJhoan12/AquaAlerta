package com.aquaalerta.aquaalerta;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "aquaalerta.bluetooth.enabled=false",
        "spring.mongodb.uri=mongodb://localhost:27017/aqua_alerta",
        "aquaalerta.mongo.indexes.enabled=false"
})
class AquaAlertaApplicationTests {

    @Test
    void contextLoads() {
    }
}