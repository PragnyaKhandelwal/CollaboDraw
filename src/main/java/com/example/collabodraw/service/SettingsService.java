package com.example.collabodraw.service;

import com.example.collabodraw.model.entity.UserSettings;
import com.example.collabodraw.repository.UserSettingsRepository;
import org.springframework.stereotype.Service;

@Service
public class SettingsService {
    private final UserSettingsRepository userSettingsRepository;

    public SettingsService(UserSettingsRepository userSettingsRepository) {
        this.userSettingsRepository = userSettingsRepository;
    }

    public UserSettings getOrInitSettings(Long userId, String defaultDisplayName) {
        UserSettings s = userSettingsRepository.findByUserId(userId);
        if (s == null) {
            userSettingsRepository.insertDefaults(userId, defaultDisplayName);
            s = userSettingsRepository.findByUserId(userId);
        }
        return s;
    }

    public void update(UserSettings settings) {
        userSettingsRepository.update(settings);
    }
}
