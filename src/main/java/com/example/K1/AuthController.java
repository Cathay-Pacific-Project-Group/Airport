package com.example.K1;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//Comment--git pull origin main
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api")
@CrossOrigin(
        origins = {"http://localhost:3000", "http://localhost:5173"},
        allowCredentials = "true"
)
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String origin = httpRequest.getHeader("Origin");
        logger.info("Login button clicked in frontend. Origin: [{}], Attempting login for Employee_ID [{}]", origin, request.getEmployeeID());
        if (request.getEmployeeID() == null || request.getPassword() == null ||
            request.getEmployeeID().isEmpty() || request.getPassword().isEmpty()) {
            logger.warn("Login attempt with missing Employee ID or password.");
            return ResponseEntity.badRequest().body("Please enter both Employee ID and password.");
        }
        boolean authenticated = authService.authenticate(request.getEmployeeID(), request.getPassword());
        if (authenticated) {
            logger.info("Frontend login successful for Employee_ID [{}]", request.getEmployeeID());
            return ResponseEntity.ok("Login successful!");
        } else {
            logger.warn("Frontend login failed for Employee_ID [{}]", request.getEmployeeID());
            return ResponseEntity.status(401).body("Invalid Employee ID or password.");
        }
    }

    static class LoginRequest {
        private String employeeID;
        private String password;

        // Getters and Setters
        public String getEmployeeID() { return employeeID; }
        public void setEmployeeID(String employeeID) { this.employeeID = employeeID; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
