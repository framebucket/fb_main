package com.framebucket.team.notification;

import com.framebucket.team.model.MemberType;

import java.util.UUID;

public record TeamInviteNotice(
        UUID memberId,
        String memberName,
        String mobileNumber,
        String studioName,
        MemberType memberType,
        String invitationUrl
) {
}
