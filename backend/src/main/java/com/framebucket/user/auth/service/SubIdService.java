package com.framebucket.user.auth.service;

import com.framebucket.user.auth.model.PhotographerUser;
import com.framebucket.user.auth.repository.PhotographerUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class SubIdService {
    private final SubIdEncryptionService encryptionService;
    private final PhotographerUserRepository userRepository;

    public SubIdService(
            SubIdEncryptionService encryptionService,
            PhotographerUserRepository userRepository
    ) {
        this.encryptionService = encryptionService;
        this.userRepository = userRepository;
    }

    public void initializeSubId(PhotographerUser photographer) {
        if (photographer.getSubId() == null || photographer.getSubId().isBlank()) {
            photographer.setSubId(encryptionService.encrypt(createPlainSubId(
                    photographer.getStudioName(),
                    photographer.getFullName()
            )));
        }
    }

    public String getPlainSubId(PhotographerUser photographer) {
        return encryptionService.decrypt(photographer.getSubId());
    }

    @Transactional
    public void backfillMissingSubIds() {
        List<PhotographerUser> photographers = userRepository.findAllBySubIdIsNull();
        photographers.forEach(this::initializeSubId);
        userRepository.saveAll(photographers);
    }

    private static String createPlainSubId(String studioName, String fullName) {
        String subId = normalize(studioName);
        if (subId.isEmpty()) {
            subId = normalize(fullName);
        }
        return subId.isEmpty() ? "STUDIO" : subId;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }
}
