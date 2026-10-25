package com.aquaalerta.aquaalerta.repository;

import com.aquaalerta.aquaalerta.LecturaNivel;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LecturaNivelRepository extends MongoRepository<LecturaNivel, String> {
    List<LecturaNivel> findByTanqueIdOrderByTimestampDesc(String tanqueId);
}