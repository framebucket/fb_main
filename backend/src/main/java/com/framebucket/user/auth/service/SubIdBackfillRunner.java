package com.framebucket.user.auth.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SubIdBackfillRunner implements ApplicationRunner {
    private final SubIdService subIdService;

    public SubIdBackfillRunner(SubIdService subIdService) {
        this.subIdService = subIdService;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        subIdService.backfillMissingSubIds();
    }
}
