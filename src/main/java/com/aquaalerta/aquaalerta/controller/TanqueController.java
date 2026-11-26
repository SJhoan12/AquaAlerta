package com.aquaalerta.aquaalerta.controller;

import com.aquaalerta.aquaalerta.Tanque;
import com.aquaalerta.aquaalerta.repository.AlertaRepository;
import com.aquaalerta.aquaalerta.service.TanqueService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/tanques")
public class TanqueController {

    private final TanqueService tanqueService;
    private final AlertaRepository alertaRepository;

    public TanqueController(TanqueService tanqueService,
                            AlertaRepository alertaRepository) {
        this.tanqueService = tanqueService;
        this.alertaRepository = alertaRepository;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("tanques", tanqueService.listarTodos());
        Object tanqueModel = model.asMap().get("tanque");
        Tanque tanque = tanqueModel instanceof Tanque
                ? (Tanque) tanqueModel
                : new Tanque();
        model.addAttribute("tanque", prepararTanque(tanque));
        model.addAttribute("numAlertas", alertaRepository.findByEstado("ACTIVA").size());
        return "tanques";
    }

    @PostMapping("/guardar")
    public String guardar(@ModelAttribute Tanque tanque, RedirectAttributes redirectAttributes) {
        try {
            tanqueService.guardar(tanque);
            redirectAttributes.addFlashAttribute("mensajeTanque", "Se guardó la configuración del tanque.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorTanque", exception.getMessage());
            redirectAttributes.addFlashAttribute("tanque", tanque);
        }
        return "redirect:/tanques";
    }

    @PostMapping("/{id}/registrar-lleno")
    public String registrarLleno(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            tanqueService.registrarLleno(id);
            redirectAttributes.addFlashAttribute("mensajeTanque",
                    "Listo. Se guardó la lectura actual como referencia de tanque lleno.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("errorTanque", exception.getMessage());
        }
        return "redirect:/tanques";
    }

    @GetMapping("/editar/{id}")
    public String editar(@PathVariable String id, Model model) {
        model.addAttribute("tanques", tanqueService.listarTodos());
        Tanque tanque = tanqueService.buscarPorId(id).orElseGet(Tanque::new);
        model.addAttribute("tanque", prepararTanque(tanque));
        return "tanques";
    }

    private Tanque prepararTanque(Tanque tanque) {
        tanque.setNumeroPersonas(tanque.getNumeroPersonas());
        tanque.setModoVigilancia(tanque.isModoVigilancia());
        return tanque;
    }

    @PostMapping("/eliminar/{id}")
    public String eliminar(@PathVariable String id) {
        tanqueService.eliminarPorId(id);
        return "redirect:/tanques";
    }

    @PostMapping("/{id}/modo-vigilancia/toggle")
    public String alternarModoVigilancia(@PathVariable String id, @RequestParam String origen) {
        if (!"dashboard".equals(origen) && !"tanques".equals(origen)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Origen de redirección inválido.");
        }
        try {
            tanqueService.alternarModoVigilancia(id);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
        return "dashboard".equals(origen)
                ? "redirect:/?tanqueId=" + id
                : "redirect:/tanques";
    }
}
