package com.example.collabodraw.util;

import com.example.collabodraw.model.entity.User;
import com.example.collabodraw.model.entity.Whiteboard;
import com.example.collabodraw.model.entity.Participant;
import com.example.collabodraw.model.dto.UserRegistrationDto;
import com.example.collabodraw.model.dto.UserLoginDto;
import com.example.collabodraw.model.dto.WhiteboardDto;
import com.example.collabodraw.exception.UserAlreadyExistsException;

/**
 * Utility class to help IDE recognize all model classes
 * This class can be deleted once IDE indexing is complete
 */
public class ModelImports {
    
    // This class exists only to help IDE recognize imports
    // All imports are listed above for reference
    
    public static void dummyMethod() {
        // Dummy method to prevent IDE warnings
        User user = new User();
        Whiteboard whiteboard = new Whiteboard();
        Participant participant = new Participant();
        UserRegistrationDto userReg = new UserRegistrationDto();
        UserLoginDto userLogin = new UserLoginDto();
        WhiteboardDto whiteboardDto = new WhiteboardDto();
        UserAlreadyExistsException exception = new UserAlreadyExistsException("test");
        
        // Suppress unused warnings
        if (user == null || whiteboard == null || participant == null || 
            userReg == null || userLogin == null || whiteboardDto == null || 
            exception == null) {
            // This will never execute, but helps with IDE recognition
        }
    }
}
