package com.framebucket.team.api;

import com.framebucket.team.model.MemberType;
import com.framebucket.team.model.TeamMemberStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TeamDtos {
    private TeamDtos() {
    }

    public record CreateTeamMemberRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull @Min(16) @Max(100) Integer age,
            @NotBlank @Pattern(regexp = "(\\+91[0-9]{10}|[0-9]{10})") String mobileNumber,
            @NotEmpty @Size(max = 6) List<@NotBlank @Size(max = 24) String> languages,
            @NotNull MemberType memberType,
            @NotEmpty @Size(max = 7) List<@NotBlank @Size(max = 40) String> skills
    ) {
    }

    public record InviteTeamMemberRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Pattern(regexp = "(\\+91[0-9]{10}|[0-9]{10})") String mobileNumber,
            @NotNull MemberType memberType,
            @Min(16) @Max(100) Integer age,
            @Size(max = 6) List<@NotBlank @Size(max = 24) String> languages,
            @Size(max = 7) List<@NotBlank @Size(max = 40) String> skills
    ) {
    }

    public record CompleteTeamInviteRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull @Min(16) @Max(100) Integer age,
            @NotEmpty @Size(max = 6) List<@NotBlank @Size(max = 24) String> languages,
            @NotEmpty @Size(max = 7) List<@NotBlank @Size(max = 40) String> skills
    ) {
    }

    public record TeamMemberResponse(
            UUID id,
            String name,
            Integer age,
            String mobileNumber,
            List<String> languages,
            MemberType memberType,
            List<String> skills,
            TeamMemberStatus status,
            Instant createdAt,
            Instant inviteExpiresAt
    ) {
    }

    public record TeamInviteResponse(TeamMemberResponse member, String invitationUrl) {
    }

    public record PublicTeamInviteContext(
            String name,
            Integer age,
            String mobileNumber,
            List<String> languages,
            MemberType memberType,
            List<String> skills,
            String studioName,
            Instant expiresAt
    ) {
    }

    public record CompleteTeamInviteResponse(String name, String studioName) {
    }
}
