package com.aquaalerta.aquaalerta.repository;

import com.aquaalerta.aquaalerta.PatronConsumo;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PatronConsumoRepository extends MongoRepository<PatronConsumo, String> {
    Optional<PatronConsumo> findByTanqueIdAndDiaSemanaAndFranja(String tanqueId, String diaSemana, String franja);
}