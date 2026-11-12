package com.rms.modules.reservation.controller;

import com.rms.modules.reservation.dto.ReservationDtos.Response;
import com.rms.modules.reservation.service.ReservationService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/staff/reservations")
public class StaffReservationController {

    private final ReservationService reservations;

    public StaffReservationController(ReservationService reservations) {
        this.reservations = reservations;
    }

    @GetMapping
    public List<Response> list() {
        return reservations.listAll();
    }

    @PatchMapping("/{id}/confirm")
    public Response confirm(@PathVariable Long id, Authentication authentication) {
        return reservations.transition(id, "confirm", (Long) authentication.getPrincipal(), true);
    }

    @PatchMapping("/{id}/reject")
    public Response reject(@PathVariable Long id, Authentication authentication) {
        return reservations.transition(id, "reject", (Long) authentication.getPrincipal(), true);
    }

    @PostMapping("/{id}/check-in")
    public Response checkIn(@PathVariable Long id, Authentication authentication) {
        return reservations.transition(id, "check-in", (Long) authentication.getPrincipal(), true);
    }

    @PostMapping("/{id}/complete")
    public Response complete(@PathVariable Long id, Authentication authentication) {
        return reservations.transition(id, "complete", (Long) authentication.getPrincipal(), true);
    }

    @PostMapping("/{id}/no-show")
    public Response noShow(@PathVariable Long id, Authentication authentication) {
        return reservations.transition(id, "no-show", (Long) authentication.getPrincipal(), true);
    }
}
