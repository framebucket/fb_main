package com.framebucket.crew;

import com.framebucket.crew.CrewDtos.AvailabilityCheck;
import com.framebucket.crew.CrewDtos.Balance;
import com.framebucket.crew.CrewDtos.CostRequest;
import com.framebucket.crew.CrewDtos.CrewRequest;
import com.framebucket.crew.CrewDtos.CrewView;
import com.framebucket.crew.CrewDtos.EventCosts;
import com.framebucket.crew.CrewDtos.EventGear;
import com.framebucket.crew.CrewDtos.EventGearRequest;
import com.framebucket.crew.CrewDtos.EventGearUpdate;
import com.framebucket.crew.CrewDtos.GearItem;
import com.framebucket.crew.CrewDtos.GearRequest;
import com.framebucket.crew.CrewDtos.Insights;
import com.framebucket.crew.CrewDtos.Member;
import com.framebucket.crew.CrewDtos.PayoutRequest;
import com.framebucket.crew.CrewDtos.PayoutView;
import com.framebucket.crew.CrewDtos.UnavailableRequest;
import com.framebucket.crew.CrewDtos.UnavailableView;
import com.framebucket.user.auth.model.PhotographerPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class CrewController {
    private final CrewService crew;
    private final GearService gear;
    private final InsightsService insights;

    public CrewController(CrewService crew, GearService gear, InsightsService insights) {
        this.crew = crew;
        this.gear = gear;
        this.insights = insights;
    }

    // ---- crew per event ----
    @GetMapping("/api/events/{eventId}/crew")
    public List<CrewView> crew(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID eventId) {
        return crew.eventCrew(p.getId(), eventId);
    }

    @GetMapping("/api/events/{eventId}/crew/check")
    public AvailabilityCheck check(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID eventId,
                                   @RequestParam String memberType, @RequestParam UUID memberId) {
        return crew.check(p.getId(), eventId, memberType, memberId);
    }

    @PostMapping("/api/events/{eventId}/crew")
    public List<CrewView> addCrew(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID eventId,
                                  @Valid @RequestBody CrewRequest request) {
        return crew.add(p.getId(), eventId, request);
    }

    @PutMapping("/api/events/{eventId}/crew/{crewId}")
    public List<CrewView> updateCrew(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID eventId,
                                     @PathVariable UUID crewId, @Valid @RequestBody CrewRequest request) {
        return crew.update(p.getId(), eventId, crewId, request);
    }

    @DeleteMapping("/api/events/{eventId}/crew/{crewId}")
    public List<CrewView> removeCrew(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID eventId, @PathVariable UUID crewId) {
        return crew.remove(p.getId(), eventId, crewId);
    }

    // ---- crew directory, availability, payouts ----
    @GetMapping("/api/studio/crew/members")
    public List<Member> members(@AuthenticationPrincipal PhotographerPrincipal p) {
        return crew.members(p.getId());
    }

    @GetMapping("/api/studio/crew/balances")
    public List<Balance> balances(@AuthenticationPrincipal PhotographerPrincipal p) {
        return crew.balances(p.getId());
    }

    @GetMapping("/api/studio/crew/unavailable")
    public List<UnavailableView> unavailable(@AuthenticationPrincipal PhotographerPrincipal p) {
        return crew.unavailable(p.getId());
    }

    @PostMapping("/api/studio/crew/unavailable")
    public List<UnavailableView> addUnavailable(@AuthenticationPrincipal PhotographerPrincipal p, @Valid @RequestBody UnavailableRequest request) {
        return crew.addUnavailable(p.getId(), request);
    }

    @DeleteMapping("/api/studio/crew/unavailable/{id}")
    public List<UnavailableView> deleteUnavailable(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID id) {
        return crew.deleteUnavailable(p.getId(), id);
    }

    @GetMapping("/api/studio/crew/payouts")
    public List<PayoutView> payouts(@AuthenticationPrincipal PhotographerPrincipal p) {
        return crew.payouts(p.getId());
    }

    @PostMapping("/api/studio/crew/payouts")
    public List<PayoutView> addPayout(@AuthenticationPrincipal PhotographerPrincipal p, @Valid @RequestBody PayoutRequest request) {
        return crew.addPayout(p.getId(), request);
    }

    @DeleteMapping("/api/studio/crew/payouts/{payoutId}")
    public List<PayoutView> deletePayout(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID payoutId) {
        return crew.deletePayout(p.getId(), payoutId);
    }

    // ---- gear ----
    @GetMapping("/api/studio/gear")
    public List<GearItem> gear(@AuthenticationPrincipal PhotographerPrincipal p) {
        return gear.items(p.getId());
    }

    @PostMapping("/api/studio/gear")
    public List<GearItem> addGear(@AuthenticationPrincipal PhotographerPrincipal p, @Valid @RequestBody GearRequest request) {
        return gear.saveItem(p.getId(), null, request);
    }

    @PutMapping("/api/studio/gear/{gearId}")
    public List<GearItem> updateGear(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID gearId,
                                     @Valid @RequestBody GearRequest request) {
        return gear.saveItem(p.getId(), gearId, request);
    }

    @DeleteMapping("/api/studio/gear/{gearId}")
    public List<GearItem> deleteGear(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID gearId) {
        return gear.deleteItem(p.getId(), gearId);
    }

    @GetMapping("/api/events/{eventId}/gear")
    public List<EventGear> eventGear(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID eventId) {
        return gear.eventGear(p.getId(), eventId);
    }

    @PostMapping("/api/events/{eventId}/gear")
    public List<EventGear> addEventGear(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID eventId,
                                        @Valid @RequestBody EventGearRequest request) {
        return gear.addToEvent(p.getId(), eventId, request);
    }

    @PutMapping("/api/events/{eventId}/gear/{gearId}")
    public List<EventGear> updateEventGear(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID eventId,
                                           @PathVariable UUID gearId, @RequestBody EventGearUpdate update) {
        return gear.updateEventGear(p.getId(), eventId, gearId, update);
    }

    @DeleteMapping("/api/events/{eventId}/gear/{gearId}")
    public List<EventGear> removeEventGear(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID eventId,
                                           @PathVariable UUID gearId) {
        return gear.removeFromEvent(p.getId(), eventId, gearId);
    }

    // ---- costs & insights ----
    @GetMapping("/api/events/{eventId}/costs")
    public EventCosts costs(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID eventId) {
        return gear.costs(p.getId(), eventId);
    }

    @PostMapping("/api/events/{eventId}/costs")
    public EventCosts addCost(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID eventId,
                              @Valid @RequestBody CostRequest request) {
        return gear.addCost(p.getId(), eventId, request);
    }

    @DeleteMapping("/api/events/{eventId}/costs/{expenseId}")
    public EventCosts deleteCost(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID eventId,
                                 @PathVariable UUID expenseId) {
        return gear.deleteCost(p.getId(), eventId, expenseId);
    }

    @GetMapping("/api/studio/insights")
    public Insights insights(@AuthenticationPrincipal PhotographerPrincipal p) {
        return insights.insights(p.getId());
    }
}
