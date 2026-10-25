package com.aquaalerta.aquaalerta.repository;

import com.aquaalerta.aquaalerta.Alerta;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AlertaRepository extends MongoRepository<Alerta, String> {
    List<Alerta> findByEstado(String estado);
    List<Alerta> findByTanqueId(String tanqueId);
}