// Board operations - Frontend navigation only
// Business logic is handled by Java Spring Boot controllers

/**
 * Open a board in the main editor - Navigate to Java controller
 */
function openBoard(boardId) {
    if (!boardId) {
        alert('Board ID is required');
        return;
    }
    window.location.href = `/boards/open/${boardId}`;
}

/**
 * Open a shared board in the main editor - Navigate to Java controller
 */
function openSharedBoard(boardId) {
    if (!boardId) {
        alert('Shared board ID is required');
        return;
    }
    window.location.href = `/boards/shared/open/${boardId}`;
}

/**
 * Use a template to create a new board - Navigate to Java controller
 */
function useTemplate(templateId) {
    if (!templateId) {
        alert('Template ID is required');
        return;
    }
    window.location.href = `/templates/use/${templateId}`;
}

/**
 * Preview a template without creating a board
 */
function previewTemplate(templateId) {
    if (typeof templateId === 'undefined' || templateId === null) {
        console.error('Template ID is required');
        return;
    }
    
    // If we're already on mainscreen, load the template in preview mode
    if (window.location.pathname === '/mainscreen') {
        loadTemplatePreview(templateId);
    } else {
        // Navigate to mainscreen with preview parameter
        window.location.href = `/mainscreen?preview=${templateId}`;
    }
}

/**
 * Share a board with other users
 */
async function shareBoard(boardId) {
    try {
        const response = await fetch(`/api/boards/share/${boardId}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                // Additional share data can be added here
            })
        });

        const data = await response.json();
        
        if (data.success) {
            // Show share dialog with share URL
            showShareDialog(data.shareUrl);
        } else {
            alert('Error sharing board: ' + data.message);
        }
    } catch (error) {
        console.error('Error sharing board:', error);
        alert('Failed to share board. Please try again.');
    }
}

/**
 * Duplicate a board
 */
async function duplicateBoard(boardId) {
    try {
        const response = await fetch(`/api/boards/duplicate/${boardId}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            }
        });

        const data = await response.json();
        
        if (data.success) {
            alert('Board duplicated successfully!');
            // Refresh the page to show the new board
            location.reload();
        } else {
            alert('Error duplicating board: ' + data.message);
        }
    } catch (error) {
        console.error('Error duplicating board:', error);
        alert('Failed to duplicate board. Please try again.');
    }
}

/**
 * Delete a board
 */
async function deleteBoard(boardId) {
    if (confirm('Are you sure you want to delete this board? This action cannot be undone.')) {
        try {
            const response = await fetch(`/api/boards/delete/${boardId}`, {
                method: 'DELETE',
                headers: {
                    'Content-Type': 'application/json',
                }
            });

            const data = await response.json();
            
            if (data.success) {
                alert('Board deleted successfully!');
                // Refresh the page to remove the deleted board
                location.reload();
            } else {
                alert('Error deleting board: ' + data.message);
            }
        } catch (error) {
            console.error('Error deleting board:', error);
            alert('Failed to delete board. Please try again.');
        }
    }
}

/**
 * Copy a shared board to user's own boards
 */
async function copySharedBoard(boardId) {
    try {
        const response = await fetch(`/api/boards/copy-shared/${boardId}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            }
        });

        const data = await response.json();
        
        if (data.success) {
            alert('Shared board copied successfully!');
            // Redirect to my-content page to show the copied board
            window.location.href = '/my-content';
        } else {
            alert('Error copying shared board: ' + data.message);
        }
    } catch (error) {
        console.error('Error copying shared board:', error);
        alert('Failed to copy shared board. Please try again.');
    }
}

/**
 * Leave a shared board
 */
async function leaveBoard(boardId) {
    if (confirm('Are you sure you want to leave this shared board?')) {
        try {
            const response = await fetch(`/api/boards/leave/${boardId}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                }
            });

            const data = await response.json();
            
            if (data.success) {
                alert('Successfully left the board!');
                // Refresh the page to remove the board from shared list
                location.reload();
            } else {
                alert('Error leaving board: ' + data.message);
            }
        } catch (error) {
            console.error('Error leaving board:', error);
            alert('Failed to leave board. Please try again.');
        }
    }
}

/**
 * Show share dialog with share URL
 */
function showShareDialog(shareUrl) {
    // Create modal dialog
    const modal = document.createElement('div');
    modal.style.cssText = `
        position: fixed;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: rgba(0,0,0,0.5);
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 10000;
    `;

    const dialog = document.createElement('div');
    dialog.style.cssText = `
        background: white;
        padding: 20px;
        border-radius: 8px;
        max-width: 500px;
        width: 90%;
    `;

    dialog.innerHTML = `
        <h3>Share Board</h3>
        <p>Copy this link to share your board:</p>
        <input type="text" value="${shareUrl}" readonly style="width: 100%; padding: 8px; margin: 10px 0; border: 1px solid #ccc; border-radius: 4px;">
        <div style="text-align: right; margin-top: 15px;">
            <button onclick="copyToClipboard('${shareUrl}')" style="background: #007bff; color: white; border: none; padding: 8px 16px; border-radius: 4px; margin-right: 10px; cursor: pointer;">Copy Link</button>
            <button onclick="this.closest('.modal').remove()" style="background: #6c757d; color: white; border: none; padding: 8px 16px; border-radius: 4px; cursor: pointer;">Close</button>
        </div>
    `;

    modal.className = 'modal';
    modal.appendChild(dialog);
    document.body.appendChild(modal);

    // Close modal when clicking outside
    modal.addEventListener('click', (e) => {
        if (e.target === modal) {
            modal.remove();
        }
    });
}

/**
 * Copy text to clipboard
 */
function copyToClipboard(text) {
    navigator.clipboard.writeText(text).then(() => {
        alert('Link copied to clipboard!');
    }).catch(err => {
        console.error('Failed to copy: ', err);
        // Fallback for older browsers
        const textArea = document.createElement('textarea');
        textArea.value = text;
        document.body.appendChild(textArea);
        textArea.select();
        document.execCommand('copy');
        document.body.removeChild(textArea);
        alert('Link copied to clipboard!');
    });
}

/**
 * Show import dialog
 */
function showImportDialog() {
    // Open mainscreen with import parameter; mainscreen will prompt for file and handle saving
    window.location.href = '/mainscreen?import=1';
}

/**
 * Show join dialog
 */
function showJoinDialog() {
    alert('Join board functionality coming soon!');
}

/**
 * Navigate to home page from whiteboard
 */
function goHome() {
    window.location.href = '/home';
}

/**
 * Load board data into the whiteboard
 */
async function loadBoardData(boardId) {
    try {
        const response = await fetch(`/api/boards/open/${boardId}`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            }
        });
        
        if (response.ok) {
            const receivedBoardData = await response.json();
            console.log('Loading board data:', receivedBoardData);
            
            // Clear current board state
            clearBoard();
            
            // Update global board data (assuming it exists from whiteboard.js)
            if (typeof boardData !== 'undefined') {
                Object.assign(boardData, receivedBoardData);
            }
            
            // Load board elements
            if (receivedBoardData.elements) {
                const container = document.getElementById('canvasElements');
                if (container) {
                    container.innerHTML = receivedBoardData.elements;
                    // Re-setup interactions for loaded elements
                    document.querySelectorAll('.canvas-element').forEach(element => {
                        if (typeof setupElementInteraction === 'function') {
                            setupElementInteraction(element);
                        }
                    });
                }
            }
            
            // Update board name
            if (receivedBoardData.name) {
                const boardNameInput = document.getElementById('boardName');
                if (boardNameInput) {
                    boardNameInput.value = receivedBoardData.name;
                    boardNameInput.disabled = true; // lock editing for server-backed boards
                }
            }
            
            // Apply board settings if available
            if (receivedBoardData.settings) {
                applyBoardSettings(receivedBoardData.settings);
            }

            // Load persisted content snapshot if available
            try {
                const contentRes = await fetch(`/api/boards/${boardId}/content`, { headers: { 'Content-Type': 'application/json' } });
                if (contentRes.ok) {
                    const content = await contentRes.json();
                    if (content && typeof content.elements !== 'undefined') {
                        const container = document.getElementById('canvasElements');
                        if (container) {
                            container.innerHTML = content.elements || '';
                            document.querySelectorAll('.canvas-element').forEach(element => {
                                if (typeof setupElementInteraction === 'function') {
                                    setupElementInteraction(element);
                                }
                            });
                        }
                        if (content.settings) {
                            applyBoardSettings(content.settings);
                        }
                    }
                }
            } catch (_) { /* ignore missing snapshot */ }
            
        } else {
            console.error('Failed to load board:', response.statusText);
            alert('Failed to load board. Please try again.');
        }
    } catch (error) {
        console.error('Error loading board:', error);
        alert('Error loading board. Please check your connection.');
    }
}

/**
 * Load shared board data into the whiteboard
 */
async function loadSharedBoardData(boardId) {
    try {
        const response = await fetch(`/api/boards/shared/open/${boardId}`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            }
        });
        
        if (response.ok) {
            const receivedBoardData = await response.json();
            console.log('Loading shared board data:', receivedBoardData);
            
            // Clear current board state
            clearBoard();
            
            // Update global board data
            if (typeof boardData !== 'undefined') {
                Object.assign(boardData, receivedBoardData);
            }
            
            // Load board elements
            if (receivedBoardData.elements) {
                const container = document.getElementById('canvasElements');
                if (container) {
                    container.innerHTML = receivedBoardData.elements;
                    // Re-setup interactions for loaded elements  
                    document.querySelectorAll('.canvas-element').forEach(element => {
                        if (typeof setupElementInteraction === 'function') {
                            setupElementInteraction(element);
                        }
                    });
                }
            }
            
            // Update board name with shared indicator
            if (receivedBoardData.name) {
                const boardNameInput = document.getElementById('boardName');
                if (boardNameInput) {
                    boardNameInput.value = receivedBoardData.name + ' (Shared)';
                    boardNameInput.disabled = true;
                }
            }
            
            // Apply board settings if available
            if (receivedBoardData.settings) {
                applyBoardSettings(receivedBoardData.settings);
            }
            
            // Set read-only mode for shared boards if needed
            if (receivedBoardData.readOnly) {
                setReadOnlyMode(true);
            }

            // Load read-only content snapshot
            try {
                const contentRes = await fetch(`/api/boards/${boardId}/content`, { headers: { 'Content-Type': 'application/json' } });
                if (contentRes.ok) {
                    const content = await contentRes.json();
                    if (content && typeof content.elements !== 'undefined') {
                        const container = document.getElementById('canvasElements');
                        if (container) {
                            container.innerHTML = content.elements || '';
                            document.querySelectorAll('.canvas-element').forEach(element => {
                                if (typeof setupElementInteraction === 'function') {
                                    setupElementInteraction(element);
                                }
                            });
                        }
                        setReadOnlyMode(true);
                    }
                }
            } catch (_) { /* ignore */ }
            
        } else {
            console.error('Failed to load shared board:', response.statusText);
            alert('Failed to load shared board. Please try again.');
        }
    } catch (error) {
        console.error('Error loading shared board:', error);
        alert('Error loading shared board. Please check your connection.');
    }
}

/**
 * Load template data into the whiteboard
 */
async function loadTemplateData(templateId) {
    try {
        const response = await fetch(`/api/templates/use/${templateId}`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            }
        });
        
        if (response.ok) {
            const templateData = await response.json();
            console.log('Loading template data:', templateData);
            
            // Clear current board state
            clearBoard();
            
            // Create new board data based on template
            if (typeof boardData !== 'undefined') {
                boardData.id = generateId ? generateId() : 'board_' + Date.now();
                // Prefer name from URL parameter; for blank fallback to 'Untitled Board'
                let preferredName = null;
                try { const p = new URLSearchParams(window.location.search); preferredName = p.get('name'); } catch(_) {}
                const finalName = (preferredName && preferredName.trim())
                  ? preferredName.trim()
                  : ((String(templateId).toLowerCase() === 'blank')
                      ? 'Untitled Board'
                      : (templateData.title ? ('New Board from ' + templateData.title) : 'New Board'));
                boardData.name = finalName;
                boardData.elements = templateData.elements || '';
                boardData.settings = templateData.settings || {};
                boardData.isTemplate = false;
            }
            
            // Load template elements
            if (templateData.elements) {
                const container = document.getElementById('canvasElements');
                if (container) {
                    container.innerHTML = templateData.elements;
                    // Re-setup interactions for loaded elements
                    document.querySelectorAll('.canvas-element').forEach(element => {
                        if (typeof setupElementInteraction === 'function') {
                            setupElementInteraction(element);
                        }
                    });
                }
            }
            
            // Update board name
            const boardNameInput = document.getElementById('boardName');
            if (boardNameInput) {
                let preferredName = null;
                try { const p = new URLSearchParams(window.location.search); preferredName = p.get('name'); } catch(_) {}
                const finalName = (preferredName && preferredName.trim())
                  ? preferredName.trim()
                  : ((String(templateId).toLowerCase() === 'blank')
                      ? 'Untitled Board'
                      : (templateData.title ? ('New Board from ' + templateData.title) : 'New Board'));
                boardNameInput.value = finalName;
            }
            
            // Apply template settings if available
            if (templateData.settings) {
                applyBoardSettings(templateData.settings);
            }

            // If this navigation came from the New Board dialog (has name param),
            // create a server board with that name and immediately persist snapshot
            try {
                const params = new URLSearchParams(window.location.search);
                const desiredName = params.get('name');
                if (desiredName) {
                    if (typeof ensureServerBoardAndSave === 'function') {
                        await ensureServerBoardAndSave(desiredName);
                        const bn = document.getElementById('boardName');
                        if (bn) bn.disabled = true; // lock title editing
                    }
                }
            } catch(_) { /* ignore */ }
            
        } else {
            console.error('Failed to load template:', response.statusText);
            alert('Failed to load template. Please try again.');
        }
    } catch (error) {
        console.error('Error loading template:', error);
        alert('Error loading template. Please check your connection.');
    }
}

/**
 * Load template preview (read-only) into the whiteboard
 */
async function loadTemplatePreview(templateId) {
    try {
        const response = await fetch(`/api/templates/preview/${templateId}`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            }
        });
        
        if (response.ok) {
            const templateData = await response.json();
            console.log('Loading template preview:', templateData);
            
            // Clear current board state
            clearBoard();
            
            // Set up preview mode board data
            if (typeof boardData !== 'undefined') {
                Object.assign(boardData, templateData);
                boardData.readOnly = true;
                boardData.isPreview = true;
            }
            
            // Load template elements
            if (templateData.elements) {
                const container = document.getElementById('canvasElements');
                if (container) {
                    container.innerHTML = templateData.elements;
                    // Re-setup interactions for loaded elements (but in read-only mode)
                    document.querySelectorAll('.canvas-element').forEach(element => {
                        if (typeof setupElementInteraction === 'function') {
                            setupElementInteraction(element);
                        }
                    });
                }
            }
            
            // Update board name with preview indicator
            if (templateData.title) {
                const boardNameInput = document.getElementById('boardName');
                if (boardNameInput) {
                    boardNameInput.value = templateData.title + ' (Preview)';
                    boardNameInput.disabled = true; // Disable editing in preview
                }
            }
            
            // Apply template settings if available
            if (templateData.settings) {
                applyBoardSettings(templateData.settings);
            }
            
            // Set read-only mode for previews
            setReadOnlyMode(true);
            
        } else {
            console.error('Failed to load template preview:', response.statusText);
            alert('Failed to load template preview. Please try again.');
        }
    } catch (error) {
        console.error('Error loading template preview:', error);
        alert('Error loading template preview. Please check your connection.');
    }
}

/**
 * Clear the current board state
 */
function clearBoard() {
    // Clear canvas elements
    const container = document.getElementById('canvasElements');
    if (container) {
        container.innerHTML = '';
    }
    
    // Reset board name
    const boardNameInput = document.getElementById('boardName');
    if (boardNameInput) {
        boardNameInput.value = 'Untitled Board';
        boardNameInput.disabled = false;
    }
    
    // Reset board data if it exists
    if (typeof boardData !== 'undefined') {
        boardData.elements = '';
        boardData.name = 'Untitled Board';
        boardData.settings = {};
        boardData.readOnly = false;
        boardData.isPreview = false;
    }
    
    // Reset zoom and pan if functions exist
    if (typeof resetViewport === 'function') {
        resetViewport();
    }
    
    // Disable read-only mode
    setReadOnlyMode(false);
}

/**
 * Apply board settings (zoom, pan, etc.)
 */
function applyBoardSettings(settings) {
    if (!settings) return;
    
    // Apply zoom
    if (settings.zoom && typeof zoomLevel !== 'undefined') {
        zoomLevel = settings.zoom;
        if (typeof updateZoom === 'function') {
            updateZoom();
        }
    }
    
    // Apply pan
    if (settings.pan && typeof panX !== 'undefined' && typeof panY !== 'undefined') {
        panX = settings.pan.x || 0;
        panY = settings.pan.y || 0;
    }
    
    // Apply timer
    if (settings.timer && typeof timerSeconds !== 'undefined') {
        timerSeconds = settings.timer;
        if (typeof updateTimerDisplay === 'function') {
            updateTimerDisplay();
        }
    }
    
    // Apply tool selection
    if (settings.tool && typeof selectTool === 'function') {
        selectTool(settings.tool);
    }
    
    // Apply color selection
    if (settings.color && typeof selectColor === 'function') {
        selectColor(settings.color);
    }
}

/**
 * Set read-only mode for the whiteboard
 */
function setReadOnlyMode(isReadOnly) {
    const body = document.body;
    
    if (isReadOnly) {
        body.classList.add('read-only-mode');
        
        // Disable toolbar interactions
        const toolButtons = document.querySelectorAll('.tool-btn');
        toolButtons.forEach(btn => {
            btn.style.pointerEvents = 'none';
            btn.style.opacity = '0.5';
        });
        
        // Disable canvas interactions
        const canvas = document.getElementById('drawingCanvas');
        if (canvas) {
            canvas.style.pointerEvents = 'none';
        }
        
        // Disable element interactions
        const elements = document.querySelectorAll('.canvas-element');
        elements.forEach(element => {
            element.style.pointerEvents = 'none';
        });
        
        console.log('Read-only mode enabled');
    } else {
        body.classList.remove('read-only-mode');
        
        // Re-enable toolbar interactions
        const toolButtons = document.querySelectorAll('.tool-btn');
        toolButtons.forEach(btn => {
            btn.style.pointerEvents = '';
            btn.style.opacity = '';
        });
        
        // Re-enable canvas interactions
        const canvas = document.getElementById('drawingCanvas');
        if (canvas) {
            canvas.style.pointerEvents = '';
        }
        
        // Re-enable element interactions
        const elements = document.querySelectorAll('.canvas-element');
        elements.forEach(element => {
            element.style.pointerEvents = '';
        });
        
        console.log('Read-only mode disabled');
    }
}