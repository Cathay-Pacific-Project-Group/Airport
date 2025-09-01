package com.example.K1;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(
        origins = {"http://localhost:8080", "http://localhost:5173", "http://localhost:5174"},
        allowCredentials = "true"
)
public class DashboardController {

    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Get permission info for an employee
    @GetMapping("/permission")
    public Map<String, Object> getPermission(@RequestParam String employeeID) {
        logger.info("Dashboard permission request for Employee_ID [{}]", employeeID);
        String sql = "SELECT Permission FROM EmployeeInfo WHERE Employee_ID = ?";
        String permission = jdbcTemplate.queryForObject(sql, new Object[]{employeeID}, String.class);
        Map<String, Object> result = new HashMap<>();
        result.put("permission", permission);
        logger.info("Permission for Employee_ID [{}]: {}", employeeID, permission);
        return result;
    }

    // Get routine(s) for an employee or all if admin
    @GetMapping("/routine")
    public List<Map<String, Object>> getRoutine(@RequestParam(required = false) String employeeID) {
        logger.info("Received GET /api/routine request from employeeID [{}]", employeeID);
        if (employeeID == null || employeeID.isEmpty()) {
            logger.warn("Routine request missing employeeID.");
            return Collections.emptyList();
        }

        // Check permission
        String roleSql = "SELECT Permission FROM EmployeeInfo WHERE Employee_ID = ?";
        String permission = jdbcTemplate.queryForObject(roleSql, new Object[]{employeeID}, String.class);

        String sql;
        Object[] params;
        if ("Admin".equalsIgnoreCase(permission)) {
            logger.info("Dashboard routine request for ALL employees by Admin [{}]", employeeID);
            sql = "SELECT JobID, Ticket_Date, SN, Flight, [From], [To], STA, ETA, ATA, Remarks, StaffInCharge, Supervisor " +
                  "FROM Routine ORDER BY Ticket_Date DESC";
            params = new Object[]{};
        } else {
            logger.info("Dashboard routine request for StaffInCharge [{}] (staff)", employeeID);
            sql = "SELECT JobID, Ticket_Date, SN, Flight, [From], [To], STA, ETA, ATA, Remarks, StaffInCharge, Supervisor " +
                  "FROM Routine WHERE StaffInCharge = ? ORDER BY Ticket_Date DESC";
            params = new Object[]{employeeID};
        }
        List<Map<String, Object>> routines = jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("JobID", rs.getString("JobID"));
            map.put("date", rs.getString("Ticket_Date"));
            map.put("sn", rs.getString("SN"));
            map.put("flight", rs.getString("Flight"));
            map.put("from", rs.getString("From"));
            map.put("to", rs.getString("To"));
            map.put("sta", rs.getString("STA"));
            map.put("eta", rs.getString("ETA"));
            map.put("ata", rs.getString("ATA"));
            map.put("remarks", rs.getString("Remarks"));
            map.put("employeeID", rs.getString("StaffInCharge"));
            map.put("supervisor", rs.getString("Supervisor"));
            return map;
        });
        logger.info("Routine data returned: {} record(s)", routines.size());
        return routines;
    }

    @PutMapping("/routine/{jobId}")
    public Map<String, Object> updateRoutine(
            @PathVariable("jobId") String jobId,
            @RequestBody Map<String, Object> routineData,
            @RequestParam String employeeID
    ) {
        logger.info("Received PUT /api/routine/{} from employeeID [{}]", jobId, employeeID);
        // Check permission
        String roleSql = "SELECT Permission FROM EmployeeInfo WHERE Employee_ID = ?";
        String permission = jdbcTemplate.queryForObject(roleSql, new Object[]{employeeID}, String.class);

        logger.info("Routine update requested for JobID [{}] by [{}] (Permission: {})", jobId, employeeID, permission);

        // Prepare SQL update statement
        String sql = "UPDATE Routine SET " +
                "Ticket_Date = ?, SN = ?, Flight = ?, [From] = ?, [To] = ?, STA = ?, ETA = ?, ATA = ?, Remarks = ?, StaffInCharge = ?, Supervisor = ? " +
                "WHERE JobID = ?";

        int updated = jdbcTemplate.update(sql,
                routineData.get("date"),
                routineData.get("sn"),
                routineData.get("flight"),
                routineData.get("from"),
                routineData.get("to"),
                routineData.get("sta"),
                routineData.get("eta"),
                routineData.get("ata"),
                routineData.get("remarks"),
                routineData.get("employeeID"),
                routineData.get("supervisor"),
                jobId
        );

        Map<String, Object> result = new HashMap<>();
        if (updated > 0) {
            logger.info("Routine for JobID [{}] updated successfully.", jobId);
            result.put("success", true);
            result.put("message", "Routine updated successfully.");
        } else {
            logger.warn("Routine for JobID [{}] update failed.", jobId);
            result.put("success", false);
            result.put("message", "Routine update failed.");
        }
        return result;
    }
}
