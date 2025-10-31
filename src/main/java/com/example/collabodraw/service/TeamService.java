package com.example.collabodraw.service;

import com.example.collabodraw.model.entity.Team;
import com.example.collabodraw.model.entity.TeamMember;
import com.example.collabodraw.repository.TeamRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TeamService {
    private final TeamRepository teamRepository;

    public TeamService(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    public Team getOrCreatePersonalTeam(Long ownerId) {
        return teamRepository.findOrCreatePersonalTeam(ownerId);
    }

    public List<TeamMember> members(Long teamId) {
        return teamRepository.findMembers(teamId);
    }

    public void addMember(Long teamId, Long userId, String role) {
        teamRepository.addMember(teamId, userId, role);
    }

    public void removeMember(Long teamId, Long userId) {
        teamRepository.removeMember(teamId, userId);
    }
}
