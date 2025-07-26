package com.example.K1;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DbConnectionTest implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public DbConnectionTest(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            System.out.println("✅ Database connection successful! Result: " + result);

            // Fetch and log EmployeeInfo table
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM EmployeeInfo");
            System.out.println("=== EmployeeInfo Table ===");
            for (Map<String, Object> row : rows) {
                System.out.println(row);
            }
            System.out.println("=========================");
        } catch (Exception e) {
            System.err.println("❌ Database connection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
