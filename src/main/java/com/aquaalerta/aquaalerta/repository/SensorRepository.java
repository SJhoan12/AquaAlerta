package com.aquaalerta.aquaalerta.repository;

import com.aquaalerta.aquaalerta.Sensor;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SensorRepository extends MongoRepository<Sensor, String> {
}