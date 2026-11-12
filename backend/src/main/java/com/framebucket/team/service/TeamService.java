package com.framebucket.team.service;

import com.framebucket.common.phone.IndianMobileNumber;
import com.framebucket.team.api.TeamDtos.CompleteTeamInviteRequest;
import com.framebucket.team.api.TeamDtos.CompleteTeamInviteResponse;
import com.framebucket.team.api.TeamDtos.CreateTeamMemberRequest;
import com.framebucket.team.api.TeamDtos.InviteTeamMemberRequest;
import com.framebucket.team.api.TeamDtos.PublicTeamInviteContext;
import com.framebucket.team.api.TeamDtos.TeamInviteResponse;
import com.framebucket.team.api.TeamDtos.TeamMemberResponse;
import com.framebucket.team.model.Freelancer;
import com.framebucket.team.model.MemberType;
import com.framebucket.team.model.TeamMember;
import com.framebucket.team.model.TeamMemberStatus;
import com.framebucket.team.model.TeamProfile;
import com.framebucket.team.notification.TeamInviteNotice;
import com.framebucket.team.repository.FreelancerRepository;
import com.framebucket.team.repository.TeamContactRepository;
import com.framebucket.team.repository.TeamMemberRepository;
import com.framebucket.user.auth.model.PhotographerPrincipal;
import com.framebucket.user.auth.model.PhotographerUser;
import com.framebucket.user.auth.repository.PhotographerUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class TeamService {
    // Plan features and limits (see com.framebucket.subscription.EntitlementService).
    @org.springframework.beans.factory.annotation.Autowired
    private com.framebucket.subscription.EntitlementService entitlements;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration INVITE_LIFETIME = Duration.ofDays(14);
    private static final Set<String> ALLOWED_LANGUAGES = Set.of(
            "KANNADA", "ENGLISH", "HINDI", "TAMIL", "TELUGU", "MALAYALAM"
    );
    private static final Set<String> ALLOWED_SKILLS = Set.of(
            "PHOTOGRAPHY", "VIDEOGRAPHY", "CANDID_PHOTOGRAPHY", "CINEMATIC_VIDEOGRAPHY",
            "PHOTO_EDITING", "VIDEO_EDITING", "ALBUM_DESIGNING"
    );

    private final TeamMemberRepository teamMemberRepository;
    private final FreelancerRepository freelancerRepository;
    private final TeamContactRepository contactRepository;
    private final PhotographerUserRepository photographerRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final String frontendBaseUrl;

    public TeamService(
            TeamMemberRepository teamMemberRepository,
            FreelancerRepository freelancerRepository,
            TeamContactRepository contactRepository,
            PhotographerUserRepository photographerRepository,
            ApplicationEventPublisher eventPublisher,
            @Value("${app.frontend.base-url}") String frontendBaseUrl
    ) {
        this.teamMemberRepository = teamMemberRepository;
        this.freelancerRepository = freelancerRepository;
        this.contactRepository = contactRepository;
        this.photographerRepository = photographerRepository;
        this.eventPublisher = eventPublisher;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+\\z", "");
    }

    @Transactional(readOnly = true)
    public List<TeamMemberResponse> listForPhotographer(PhotographerPrincipal principal) {
        return Stream.concat(
                        teamMemberRepository.findAllByPhotographer_IdOrderByCreatedAtDesc(principal.getId()).stream(),
                        freelancerRepository.findAllByPhotographer_IdOrderByCreatedAtDesc(principal.getId()).stream()
                )
                .sorted(Comparator.comparing(TeamProfile::getCreatedAt).reversed())
                .map(TeamService::toResponse)
                .toList();
    }

    @Transactional
    public TeamMemberResponse create(PhotographerPrincipal principal, CreateTeamMemberRequest request) {
        entitlements.requireCanAddTeamMember(principal.getId());
        PhotographerUser photographer = findPhotographer(principal.getId());
        String mobile = normalizeMobile(request.mobileNumber());
        claimContact(principal.getId(), mobile);
        List<String> languages = normalizeChoices(request.languages(), ALLOWED_LANGUAGES, "language");
        List<String> skills = normalizeChoices(request.skills(), ALLOWED_SKILLS, "skill");
        TeamProfile profile = saveProfile(request.memberType(), newProfile(
                photographer, request.name(), request.age(), mobile,
                String.join(",", languages), String.join(",", skills),
                TeamMemberStatus.ACTIVE, null, null, request.memberType()
        ));
        return toResponse(profile);
    }

    @Transactional
    public TeamInviteResponse invite(PhotographerPrincipal principal, InviteTeamMemberRequest request) {
        entitlements.requireCanAddTeamMember(principal.getId());
        PhotographerUser photographer = findPhotographer(principal.getId());
        String mobile = normalizeMobile(request.mobileNumber());
        claimContact(principal.getId(), mobile);
        List<String> languages = normalizeChoices(request.languages(), ALLOWED_LANGUAGES, "language");
        List<String> skills = normalizeChoices(request.skills(), ALLOWED_SKILLS, "skill");
        String token = newInviteToken();
        Instant expiresAt = Instant.now().plus(INVITE_LIFETIME);
        TeamProfile profile = saveProfile(request.memberType(), newProfile(
                photographer, request.name(), request.age(), mobile,
                String.join(",", languages), String.join(",", skills),
                TeamMemberStatus.PENDING, hashToken(token), expiresAt, request.memberType()
        ));
        return issueInvitation(profile, token);
    }

    @Transactional
    public TeamInviteResponse resendInvite(PhotographerPrincipal principal, MemberType memberType, UUID memberId) {
        TeamProfile profile = findProfile(memberType, memberId, principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team member not found."));
        if (profile.getStatus() != TeamMemberStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This team member has already completed their profile.");
        }
        String token = newInviteToken();
        profile.issueInvite(hashToken(token), Instant.now().plus(INVITE_LIFETIME));
        return issueInvitation(profile, token);
    }

    @Transactional(readOnly = true)
    public PublicTeamInviteContext inviteContext(String token) {
        TeamProfile profile = findInvite(token);
        ensureInviteIsCurrent(profile);
        return new PublicTeamInviteContext(
                profile.getName(), profile.getAge(), profile.getMobileNumber(), splitChoices(profile.getLanguages()),
                profile.getMemberType(), splitChoices(profile.getSkills()), displayStudioName(profile.getPhotographer()),
                profile.getInviteExpiresAt()
        );
    }

    @Transactional
    public CompleteTeamInviteResponse completeInvite(String token, CompleteTeamInviteRequest request) {
        TeamProfile profile = findInviteForUpdate(token);
        ensureInviteIsCurrent(profile);
        List<String> languages = normalizeChoices(request.languages(), ALLOWED_LANGUAGES, "language");
        List<String> skills = normalizeChoices(request.skills(), ALLOWED_SKILLS, "skill");
        profile.completeProfile(request.name(), request.age(), String.join(",", languages), String.join(",", skills), Instant.now());
        return new CompleteTeamInviteResponse(profile.getName(), displayStudioName(profile.getPhotographer()));
    }

    private TeamInviteResponse issueInvitation(TeamProfile profile, String token) {
        String studioName = displayStudioName(profile.getPhotographer());
        String invitationUrl = frontendBaseUrl + "/team-invite/" + token;
        eventPublisher.publishEvent(new TeamInviteNotice(
                profile.getId(), profile.getName(), profile.getMobileNumber(), studioName,
                profile.getMemberType(), invitationUrl
        ));
        return new TeamInviteResponse(toResponse(profile), invitationUrl);
    }

    private TeamProfile saveProfile(MemberType memberType, TeamProfile profile) {
        if (memberType == MemberType.TEAM_MEMBER) {
            return teamMemberRepository.save((TeamMember) profile);
        }
        return freelancerRepository.save((Freelancer) profile);
    }

    private static TeamProfile newProfile(
            PhotographerUser photographer,
            String name,
            Integer age,
            String mobile,
            String languages,
            String skills,
            TeamMemberStatus status,
            String tokenHash,
            Instant expiresAt,
            MemberType memberType
    ) {
        if (memberType == MemberType.TEAM_MEMBER) {
            return new TeamMember(photographer, name, age, mobile, languages, skills, status, tokenHash, expiresAt);
        }
        return new Freelancer(photographer, name, age, mobile, languages, skills, status, tokenHash, expiresAt);
    }

    private java.util.Optional<TeamProfile> findProfile(MemberType memberType, UUID id, UUID photographerId) {
        if (memberType == MemberType.TEAM_MEMBER) {
            return teamMemberRepository.findByIdAndPhotographer_Id(id, photographerId).map(profile -> profile);
        }
        return freelancerRepository.findByIdAndPhotographer_Id(id, photographerId).map(profile -> profile);
    }

    private TeamProfile findInvite(String token) {
        String tokenHash = hashToken(token);
        return teamMemberRepository.findByInviteTokenHash(tokenHash).map(profile -> (TeamProfile) profile)
                .or(() -> freelancerRepository.findByInviteTokenHash(tokenHash).map(profile -> (TeamProfile) profile))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation link is invalid or already used."));
    }

    private TeamProfile findInviteForUpdate(String token) {
        String tokenHash = hashToken(token);
        return teamMemberRepository.findByInviteTokenHashForUpdate(tokenHash).map(profile -> (TeamProfile) profile)
                .or(() -> freelancerRepository.findByInviteTokenHashForUpdate(tokenHash).map(profile -> (TeamProfile) profile))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation link is invalid or already used."));
    }

    private void claimContact(UUID photographerId, String mobile) {
        int claimed = contactRepository.claimContact(photographerId, mobile);
        if (claimed == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A team member or freelancer with this mobile number already exists.");
        }
    }

    private PhotographerUser findPhotographer(UUID id) {
        return photographerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photographer account not found."));
    }

    private static String normalizeMobile(String mobileNumber) {
        try {
            return IndianMobileNumber.normalize(mobileNumber);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    private static List<String> normalizeChoices(List<String> values, Set<String> allowed, String label) {
        if (values == null) return List.of();
        List<String> normalized = values.stream()
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        if (normalized.stream().anyMatch(value -> !allowed.contains(value))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more selected " + label + "s are not supported.");
        }
        return normalized;
    }

    private static void ensureInviteIsCurrent(TeamProfile profile) {
        if (profile.getStatus() != TeamMemberStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.GONE, "This invitation has already been completed.");
        }
        if (profile.getInviteExpiresAt() == null || !profile.getInviteExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "This invitation link has expired. Ask the photographer to send a new one.");
        }
    }

    private static TeamMemberResponse toResponse(TeamProfile profile) {
        return new TeamMemberResponse(
                profile.getId(), profile.getName(), profile.getAge(), profile.getMobileNumber(),
                splitChoices(profile.getLanguages()), profile.getMemberType(), splitChoices(profile.getSkills()),
                profile.getStatus(), profile.getCreatedAt(), profile.getInviteExpiresAt()
        );
    }

    private static List<String> splitChoices(String values) {
        if (values == null || values.isBlank()) return List.of();
        return List.of(values.split(","));
    }

    private static String displayStudioName(PhotographerUser photographer) {
        return photographer.getStudioName() == null || photographer.getStudioName().isBlank()
                ? photographer.getFullName() : photographer.getStudioName();
    }

    private static String newInviteToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
