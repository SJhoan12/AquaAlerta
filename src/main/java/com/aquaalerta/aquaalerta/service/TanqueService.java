package com.aquaalerta.aquaalerta.service;

import com.aquaalerta.aquaalerta.Tanque;
import com.aquaalerta.aquaalerta.repository.TanqueRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TanqueService {

    private final TanqueRepository tanqueRepository;

    public TanqueService(TanqueRepository tanqueRepository) {
        this.tanqueRepository = tanqueRepository;
    }

    public Tanque guardar(Tanque tanque) {
        if (tanque.getId() == null || tanque.getId().isBlank()) {
            tanque.setId(null);
        }
        if (tanque.getAlturaMaxCm() > 0 && tanque.getDiametroCm() > 0) {
            double radio = tanque.getDiametroCm() / 2.0;
            double volumenMax = Math.PI * radio * radio * tanque.getAlturaMaxCm() / 1000.0;
            tanque.setVolumenMax(volumenMax);
        }
        return tanqueRepository.save(tanque);
    }

    public List<Tanque> listarTodos() {
        return tanqueRepository.findAll();
    }

    public Optional<Tanque> buscarPorId(String id) {
        return tanqueRepository.findById(id);
    }

    public void eliminarPorId(String id) {
        tanqueRepository.deleteById(id);
    }
}
