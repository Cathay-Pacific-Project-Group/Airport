package com.example.K1;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public boolean authenticate(String employeeId, String password) {
        String sql = "SELECT COUNT(*) FROM EmployeeInfo WHERE Employee_ID = ? AND Encry_Pw = ?";
        Integer count = jdbcTemplate.queryForObject(sql, new Object[]{employeeId, password}, Integer.class);
        boolean success = count != null && count > 0;
        if (success) {
            logger.info("User [{}] logged in successfully.", employeeId);
        } else {
            logger.warn("Failed login attempt for Employee_ID [{}].", employeeId);
        }
        return success;
    }
}
