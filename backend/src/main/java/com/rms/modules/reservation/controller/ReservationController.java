package com.rms.modules.reservation.controller;

import com.rms.modules.reservation.dto.ReservationDtos.Availability;
import com.rms.modules.reservation.dto.ReservationDtos.CreateRequest;
import com.rms.modules.reservation.dto.ReservationDtos.Response;
import com.rms.modules.reservation.service.ReservationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1")
public class ReservationController {

    private final ReservationService reservations;

    public ReservationController(ReservationService reservations) {
        this.reservations = reservations;
    }

    @GetMapping("/tables/availability")
    public List<Availability> availability(
            @RequestParam Instant start,
            @RequestParam Instant end,
            @RequestParam @Min(1) int guestCount) {
        return reservations.availability(start, end, guestCount);
    }

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@Valid @RequestBody CreateRequest request, Authentication authentication) {
        return reservations.create(request, (Long) authentication.getPrincipal());
    }

    @GetMapping("/reservations/my")
    public List<Response> mine(Authentication authentication) {
        return reservations.mine((Long) authentication.getPrincipal());
    }

    @GetMapping("/reservations/{id}")
    public Response get(@PathVariable Long id, Authentication authentication) {
        return reservations.get(id, (Long) authentication.getPrincipal(), isStaff(authentication));
    }

    @PostMapping("/reservations/{id}/cancel")
    public Response cancel(@PathVariable Long id, Authentication authentication) {
        return reservations.cancel(id, (Long) authentication.getPrincipal(), isStaff(authentication));
    }

    private static boolean isStaff(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_STAFF") || a.equals("ROLE_ADMIN"));
    }
}
