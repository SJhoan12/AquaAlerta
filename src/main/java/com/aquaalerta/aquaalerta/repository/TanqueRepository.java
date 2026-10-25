package com.aquaalerta.aquaalerta.repository;

import com.aquaalerta.aquaalerta.Tanque;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TanqueRepository extends MongoRepository<Tanque, String> {
}