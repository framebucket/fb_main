package com.framebucket.team.api;

import com.framebucket.team.api.TeamDtos.CreateTeamMemberRequest;
import com.framebucket.team.api.TeamDtos.InviteTeamMemberRequest;
import com.framebucket.team.api.TeamDtos.TeamInviteResponse;
import com.framebucket.team.api.TeamDtos.TeamMemberResponse;
import com.framebucket.team.service.TeamService;
import com.framebucket.user.auth.model.PhotographerPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/team")
public class TeamController {
    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    public List<TeamMemberResponse> list(@AuthenticationPrincipal PhotographerPrincipal photographer) {
        return teamService.listForPhotographer(photographer);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeamMemberResponse create(
            @AuthenticationPrincipal PhotographerPrincipal photographer,
            @Valid @RequestBody CreateTeamMemberRequest request
    ) {
        return teamService.create(photographer, request);
    }

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public TeamInviteResponse invite(
            @AuthenticationPrincipal PhotographerPrincipal photographer,
            @Valid @RequestBody InviteTeamMemberRequest request
    ) {
        return teamService.invite(photographer, request);
    }

    @PostMapping("/{memberType}/{memberId}/invitation")
    public TeamInviteResponse resend(
            @AuthenticationPrincipal PhotographerPrincipal photographer,
            @PathVariable com.framebucket.team.model.MemberType memberType,
            @PathVariable UUID memberId
    ) {
        return teamService.resendInvite(photographer, memberType, memberId);
    }
}
