package com.example.collabodraw.service;

import com.example.collabodraw.model.dto.WhiteboardDto;
import com.example.collabodraw.model.entity.Board;
import com.example.collabodraw.model.entity.BoardMembership;
import com.example.collabodraw.model.entity.Element;
import com.example.collabodraw.repository.BoardRepository;
import com.example.collabodraw.repository.SessionRoomRepository;
import com.example.collabodraw.repository.BoardMembershipRepository;
import com.example.collabodraw.repository.ElementRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for Board/Whiteboard-related business logic
 */
@Service
public class WhiteboardService {
    
    private final BoardRepository boardRepository;
    private final BoardMembershipRepository boardMembershipRepository;
    private final ElementRepository elementRepository;
    private final SessionRoomRepository sessionRoomRepository;

    public WhiteboardService(BoardRepository boardRepository, 
                           BoardMembershipRepository boardMembershipRepository,
                           ElementRepository elementRepository,
                           SessionRoomRepository sessionRoomRepository) {
        this.boardRepository = boardRepository;
        this.boardMembershipRepository = boardMembershipRepository;
        this.elementRepository = elementRepository;
        this.sessionRoomRepository = sessionRoomRepository;
    }

    public Board createWhiteboard(WhiteboardDto whiteboardDto) {
        Board board = new Board(whiteboardDto.getOwnerId(), whiteboardDto.getName(), whiteboardDto.getIsPublic());
        Long result = boardRepository.save(board);
        if (result == null || result <= 0) {
            throw new RuntimeException("Failed to create board");
        }
        board.setBoardId(result);
        return board;
    }

    public List<Board> getWhiteboardsByOwner(Long ownerId) {
        return boardRepository.findByOwnerId(ownerId);
    }

    public Board getWhiteboardById(Long id) {
        return boardRepository.findById(id);
    }

    public List<Board> getAllWhiteboards() {
        return boardRepository.findAll();
    }

    public List<Board> getPublicWhiteboards() {
        return boardRepository.findPublicBoards();
    }

    public void addUserToWhiteboard(Long boardId, Long userId, String role) {
        // Check if board exists
        Board board = boardRepository.findById(boardId);
        if (board == null) {
            throw new RuntimeException("Board not found with ID: " + boardId);
        }
        
        // Check if user is already a member
        BoardMembership existingMembership = boardMembershipRepository.findByBoardIdAndUserId(boardId, userId);
        if (existingMembership == null) {
            BoardMembership membership = new BoardMembership(boardId, userId, role);
            boardMembershipRepository.save(membership);
        }
    }

    public String getUserRoleInWhiteboard(Long userId, Long boardId) {
        // Check if user is the owner
        Board board = boardRepository.findById(boardId);
        if (board != null && board.getOwnerId().equals(userId)) {
            return "owner";
        }
        
        // Check membership table
        BoardMembership membership = boardMembershipRepository.findByBoardIdAndUserId(boardId, userId);
        if (membership != null) {
            return membership.getRole();
        }
        
        return null; // User has no access
    }

    public Integer getWhiteboardElementCount(Long boardId) {
        return elementRepository.countByBoardId(boardId);
    }

    public String getBoardSnapshot(Long boardId) {
        return elementRepository.findLatestSnapshotData(boardId);
    }

    public void saveBoardSnapshot(Long boardId, Long userId, String dataJson) {
        elementRepository.replaceSnapshot(boardId, userId, dataJson);
        boardRepository.updateLastModified(boardId);
    }

    /**
     * Resolve a collaborative session code to a concrete board.
     * Strategy: use a canonical name "Session <code>" and find the first board with that name.
     * If none exists, create it owned by the current user and ensure membership.
     */
    public Board findOrCreateBoardBySessionCode(String code, Long currentUserId) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Session code is required");
        }
        String normalized = code.trim().toLowerCase();

        // 1) Fast path: mapping table
        Long mappedId = sessionRoomRepository.findBoardIdByCode(normalized);
        if (mappedId != null) {
            Board board = boardRepository.findById(mappedId);
            if (board != null) {
                addUserToWhiteboard(board.getBoardId(), currentUserId, "viewer");
                return board;
            }
            // mapping exists but board missing => fall through to recreate
        }

        // 2) Create a new board with canonical name and try to claim the mapping atomically
        String canonicalName = "Session " + normalized;
        WhiteboardDto dto = new WhiteboardDto(canonicalName, currentUserId, false);
        Board created = createWhiteboard(dto);
        addUserToWhiteboard(created.getBoardId(), currentUserId, "owner");

        boolean createdMapping = sessionRoomRepository.createMapping(normalized, created.getBoardId());
        if (createdMapping) {
            return created;
        }

        // 3) Another concurrent request won the race; read back the mapping and return that board
        Long winnerId = sessionRoomRepository.findBoardIdByCode(normalized);
        if (winnerId != null) {
            Board winner = boardRepository.findById(winnerId);
            if (winner != null) {
                addUserToWhiteboard(winner.getBoardId(), currentUserId, "viewer");
                return winner;
            }
        }

        // Fallback: return the one we created
        return created;
    }

    @Transactional
    public Board duplicateBoard(Long boardId, Long userId) {
        Board originalBoard = boardRepository.findById(boardId);
        ensureBoardExists(originalBoard, boardId);

        if (!originalBoard.getOwnerId().equals(userId)) {
            throw new AccessDeniedException("Only the board owner can duplicate this board");
        }

        return duplicateBoardForUser(originalBoard, userId, " (Copy)", true);
    }

    @Transactional
    public void deleteBoard(Long boardId, Long userId) {
        Board board = boardRepository.findById(boardId);
        ensureBoardExists(board, boardId);

        if (!board.getOwnerId().equals(userId)) {
            throw new AccessDeniedException("Only the board owner can delete this board");
        }

        elementRepository.deleteByBoardId(boardId);
        boardMembershipRepository.deleteByBoardId(boardId);
        boardRepository.delete(boardId);
    }

    @Transactional
    public Board copySharedBoard(Long boardId, Long userId) {
        Board sourceBoard = boardRepository.findById(boardId);
        ensureBoardExists(sourceBoard, boardId);

        boolean hasAccess = sourceBoard.getOwnerId().equals(userId) ||
                boardMembershipRepository.hasAccess(boardId, userId);

        if (!hasAccess) {
            throw new AccessDeniedException("You do not have access to this board");
        }

        // Shared board copies are always private to the new owner
        return duplicateBoardForUser(sourceBoard, userId, " (My Copy)", false);
    }

    @Transactional
    public void leaveBoard(Long boardId, Long userId) {
        Board board = boardRepository.findById(boardId);
        ensureBoardExists(board, boardId);

        if (board.getOwnerId().equals(userId)) {
            throw new IllegalStateException("Board owners cannot leave their own board");
        }

        if (!boardMembershipRepository.hasAccess(boardId, userId)) {
            throw new IllegalArgumentException("You are not a member of this board");
        }

        boardMembershipRepository.delete(boardId, userId);
    }

    private Board duplicateBoardForUser(Board sourceBoard, Long newOwnerId, String nameSuffix, boolean keepPublicFlag) {
        String copyName = buildCopyName(sourceBoard.getBoardName(), nameSuffix);
        Boolean copyPublic = keepPublicFlag ? sourceBoard.getIsPublic() : Boolean.FALSE;

        Board copy = new Board(newOwnerId, copyName, copyPublic);
        Long newId = boardRepository.save(copy);
        if (newId == null || newId <= 0) {
            throw new RuntimeException("Failed to create board copy");
        }
        copy.setBoardId(newId);

        // Ensure the new owner is recorded in membership table as owner
        boardMembershipRepository.save(new BoardMembership(newId, newOwnerId, "owner"));

        // Duplicate drawing elements
        List<Element> elements = elementRepository.findByBoardId(sourceBoard.getBoardId());
        for (Element element : elements) {
            Element duplicatedElement = new Element();
            duplicatedElement.setBoardId(newId);
            duplicatedElement.setCreatorId(newOwnerId);
            duplicatedElement.setType(element.getType());
            duplicatedElement.setZOrder(element.getZOrder());
            duplicatedElement.setData(element.getData());
            elementRepository.save(duplicatedElement);
        }

        return copy;
    }

    private void ensureBoardExists(Board board, Long boardId) {
        if (board == null) {
            throw new IllegalArgumentException("Board not found with ID: " + boardId);
        }
    }

    private String buildCopyName(String originalName, String suffix) {
        String baseName = (originalName == null || originalName.isBlank()) ? "Untitled Board" : originalName.trim();
        if (baseName.endsWith(suffix)) {
            return baseName;
        }
        return baseName + suffix;
    }
}
