package com.framebucket.team.api;

import com.framebucket.team.api.TeamDtos.CompleteTeamInviteRequest;
import com.framebucket.team.api.TeamDtos.CompleteTeamInviteResponse;
import com.framebucket.team.api.TeamDtos.PublicTeamInviteContext;
import com.framebucket.team.service.TeamService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/team-invites")
public class PublicTeamInviteController {
    private final TeamService teamService;

    public PublicTeamInviteController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping("/{token}")
    public PublicTeamInviteContext getContext(@PathVariable String token) {
        return teamService.inviteContext(token);
    }

    @PutMapping("/{token}")
    public CompleteTeamInviteResponse complete(
            @PathVariable String token,
            @Valid @RequestBody CompleteTeamInviteRequest request
    ) {
        return teamService.completeInvite(token, request);
    }
}
