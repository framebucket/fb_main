package com.framebucket.team.notification;

import com.framebucket.common.phone.IndianMobileNumber;
import com.framebucket.enquiry.notification.WhatsAppTemplateSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
public class TeamInviteNotificationListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(TeamInviteNotificationListener.class);
    private final WhatsAppTemplateSender whatsAppSender;
    private final String inviteTemplate;

    public TeamInviteNotificationListener(
            WhatsAppTemplateSender whatsAppSender,
            @Value("${app.whatsapp.team-invite-template:framebucket_team_member_invite}") String inviteTemplate
    ) {
        this.whatsAppSender = whatsAppSender;
        this.inviteTemplate = inviteTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendInvite(TeamInviteNotice notice) {
        String typeLabel = notice.memberType() == null ? "team member" : notice.memberType().name().replace('_', ' ').toLowerCase();
        try {
            whatsAppSender.sendTemplate(
                    "91" + IndianMobileNumber.normalize(notice.mobileNumber()),
                    inviteTemplate,
                    List.of(notice.memberName(), notice.studioName(), typeLabel, notice.invitationUrl())
            );
        } catch (RuntimeException exception) {
            LOGGER.error("Could not send a WhatsApp team invitation for member {}.", notice.memberId(), exception);
        }
    }
}
