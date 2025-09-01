package com.example.K1;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;

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

    @GetMapping("/routine/export")
    public ResponseEntity<byte[]> exportRoutine(@RequestParam String employeeID) {
        logger.info("Routine Excel export requested by [{}]", employeeID);

        // Get routines for user or admin
        String roleSql = "SELECT Permission FROM EmployeeInfo WHERE Employee_ID = ?";
        String permission = jdbcTemplate.queryForObject(roleSql, new Object[]{employeeID}, String.class);

        String sql;
        Object[] params;
        if ("Admin".equalsIgnoreCase(permission)) {
            sql = "SELECT JobID, Ticket_Date, SN, Flight, [From], [To], STA, ETA, ATA, Remarks, StaffInCharge, Supervisor FROM Routine ORDER BY Ticket_Date DESC";
            params = new Object[]{};
        } else {
            sql = "SELECT JobID, Ticket_Date, SN, Flight, [From], [To], STA, ETA, ATA, Remarks, StaffInCharge, Supervisor FROM Routine WHERE StaffInCharge = ? ORDER BY Ticket_Date DESC";
            params = new Object[]{employeeID};
        }
        List<Map<String, Object>> routines = jdbcTemplate.queryForList(sql, params);

        // Create Excel workbook
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Routine");
            Row header = sheet.createRow(0);
            String[] columns = {"JobID", "Date", "SN", "Flight", "From", "To", "STA", "ETA", "ATA", "Remarks", "StaffInCharge", "Supervisor"};
            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
            }
            for (int i = 0; i < routines.size(); i++) {
                Map<String, Object> r = routines.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(String.valueOf(r.get("JobID")));
                row.createCell(1).setCellValue(String.valueOf(r.get("Ticket_Date")));
                row.createCell(2).setCellValue(String.valueOf(r.get("SN")));
                row.createCell(3).setCellValue(String.valueOf(r.get("Flight")));
                row.createCell(4).setCellValue(String.valueOf(r.get("From")));
                row.createCell(5).setCellValue(String.valueOf(r.get("To")));
                row.createCell(6).setCellValue(String.valueOf(r.get("STA")));
                row.createCell(7).setCellValue(String.valueOf(r.get("ETA")));
                row.createCell(8).setCellValue(String.valueOf(r.get("ATA")));
                row.createCell(9).setCellValue(String.valueOf(r.get("Remarks")));
                row.createCell(10).setCellValue(String.valueOf(r.get("StaffInCharge")));
                row.createCell(11).setCellValue(String.valueOf(r.get("Supervisor")));
            }
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }
            // Write to byte array
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            workbook.write(bos);
            byte[] excelBytes = bos.toByteArray();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=routine.xlsx");
            return ResponseEntity.ok().headers(headers).body(excelBytes);
        } catch (Exception e) {
            logger.error("Excel export failed: {}", e.getMessage());
            return ResponseEntity.status(500).body(null);
        }
    }

    @PostMapping("/routine/import")
    public Map<String, Object> importRoutine(@RequestBody Map<String, Object> routineData, @RequestParam String employeeID) {
        String roleSql = "SELECT Permission FROM EmployeeInfo WHERE Employee_ID = ?";
        String permission;
        Map<String, Object> result = new HashMap<>();
        try {
            permission = jdbcTemplate.queryForObject(roleSql, new Object[]{employeeID}, String.class);
        } catch (Exception ex) {
            logger.error("Error checking permission for [{}]: {}", employeeID, ex.getMessage());
            result.put("success", false);
            result.put("message", "Database error: " + ex.getMessage());
            return result;
        }

        if (!"Admin".equalsIgnoreCase(permission)) {
            result.put("success", false);
            result.put("message", "Only admin can import routines.");
            return result;
        }

        // Auto-generate JobID (UUID)
        String jobId = UUID.randomUUID().toString();

        String sql = "INSERT INTO Routine (JobID, Ticket_Date, SN, Flight, [From], [To], STA, ETA, ATA, Remarks, StaffInCharge, Supervisor) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try {
            int inserted = jdbcTemplate.update(sql,
                    jobId,
                    parseDate((String)routineData.get("date")),
                    routineData.get("sn"),
                    routineData.get("flight"),
                    routineData.get("from"),
                    routineData.get("to"),
                    parseTime((String)routineData.get("sta")),
                    parseTime((String)routineData.get("eta")),
                    parseTime((String)routineData.get("ata")),
                    routineData.get("remarks"),
                    routineData.get("employeeID"),
                    routineData.get("supervisor")
            );
            if (inserted > 0) {
                logger.info("Routine imported with JobID [{}] by admin [{}]", jobId, employeeID);
                result.put("success", true);
                result.put("message", "Routine imported successfully.");
                result.put("JobID", jobId);
            } else {
                logger.warn("Routine import failed by admin [{}]", employeeID);
                result.put("success", false);
                result.put("message", "Routine import failed.");
            }
        } catch (Exception ex) {
            logger.error("Exception during routine import: {}", ex.getMessage());
            result.put("success", false);
            result.put("message", "Routine import failed: " + ex.getMessage());
        }
        return result;
    }

    @PostMapping("/routine/import/excel")
    public Map<String, Object> importExcel(@RequestParam("file") MultipartFile file, @RequestParam String employeeID) {
        Map<String, Object> result = new HashMap<>();
        String roleSql = "SELECT Permission FROM EmployeeInfo WHERE Employee_ID = ?";
        String permission;
        try {
            permission = jdbcTemplate.queryForObject(roleSql, new Object[]{employeeID}, String.class);
        } catch (Exception ex) {
            logger.error("Error checking permission for [{}]: {}", employeeID, ex.getMessage());
            result.put("success", false);
            result.put("message", "Database error: " + ex.getMessage());
            return result;
        }
        if (!"Admin".equalsIgnoreCase(permission)) {
            result.put("success", false);
            result.put("message", "Only admin can import routines.");
            return result;
        }

        int successCount = 0, failCount = 0;
        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                try {
                    String jobId = UUID.randomUUID().toString();
                    String sql = "INSERT INTO Routine (JobID, Ticket_Date, SN, Flight, [From], [To], STA, ETA, ATA, Remarks, StaffInCharge, Supervisor) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                    jdbcTemplate.update(sql,
                        jobId,
                        parseDate(getCell(row, 1)), // Ticket_Date as java.sql.Date
                        getCell(row, 2), // SN
                        getCell(row, 3), // Flight
                        getCell(row, 4), // From
                        getCell(row, 5), // To
                        parseTime(getCell(row, 6)), // STA as java.sql.Time
                        parseTime(getCell(row, 7)), // ETA as java.sql.Time
                        parseTime(getCell(row, 8)), // ATA as java.sql.Time
                        getCell(row, 9), // Remarks
                        getCell(row, 10), // StaffInCharge
                        getCell(row, 11)  // Supervisor
                    );
                    logger.info("Excel import: Row {} imported with JobID [{}] by [{}]", i, jobId, employeeID);
                    successCount++;
                } catch (Exception ex) {
                    logger.error("Excel row import failed at row {}: {}", i, ex.getMessage());
                    failCount++;
                }
            }
            result.put("success", true);
            result.put("message", "Excel import finished. Success: " + successCount + ", Failed: " + failCount);
        } catch (Exception ex) {
            logger.error("Excel import failed: {}", ex.getMessage());
            result.put("success", false);
            result.put("message", "Excel import failed: " + ex.getMessage());
        }
        return result;
    }

    private String getCell(Row row, int idx) {
        Cell cell = row.getCell(idx);
        return cell == null ? "" : cell.toString();
    }

    // Normalize time to HH:mm:ss format
    private String normalizeTime(String time) {
        if (time == null || time.isEmpty()) return "";
        // If already in HH:mm:ss, return as is
        if (time.matches("\\d{2}:\\d{2}:\\d{2}")) return time;
        // If in H:mm:ss or HH:mm, pad as needed
        String[] parts = time.split(":");
        if (parts.length == 3) {
            String h = parts[0].length() == 1 ? "0" + parts[0] : parts[0];
            String m = parts[1].length() == 1 ? "0" + parts[1] : parts[1];
            String s = parts[2].length() == 1 ? "0" + parts[2] : parts[2];
            return h + ":" + m + ":" + s;
        }
        if (parts.length == 2) {
            String h = parts[0].length() == 1 ? "0" + parts[0] : parts[0];
            String m = parts[1].length() == 1 ? "0" + parts[1] : parts[1];
            return h + ":" + m + ":00";
        }
        if (parts.length == 1) {
            String h = parts[0].length() == 1 ? "0" + parts[0] : parts[0];
            return h + ":00:00";
        }
        return time;
    }

    private java.sql.Date parseDate(String dateStr) {
        try {
            return java.sql.Date.valueOf(dateStr.trim()); // expects yyyy-MM-dd
        } catch (Exception e) {
            logger.warn("Invalid date format: {}", dateStr);
            return null;
        }
    }

    private java.sql.Time parseTime(String timeStr) {
        try {
            String normalized = normalizeTime(timeStr.trim());
            return java.sql.Time.valueOf(normalized); // expects HH:mm:ss
        } catch (Exception e) {
            logger.warn("Invalid time format: {}", timeStr);
            return null;
        }
    }

    @DeleteMapping("/routine/{jobId}")
    public Map<String, Object> deleteRoutine(
            @PathVariable("jobId") String jobId,
            @RequestParam String employeeID
    ) {
        logger.info("Received DELETE /api/routine/{} from employeeID [{}]", jobId, employeeID);
        // Check permission
        String roleSql = "SELECT Permission FROM EmployeeInfo WHERE Employee_ID = ?";
        String permission;
        try {
            permission = jdbcTemplate.queryForObject(roleSql, new Object[]{employeeID}, String.class);
        } catch (Exception ex) {
            logger.error("Error checking permission for [{}]: {}", employeeID, ex.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Database error: " + ex.getMessage());
            return result;
        }

        Map<String, Object> result = new HashMap<>();
        if (!"Admin".equalsIgnoreCase(permission)) {
            logger.warn("Delete routine denied for [{}] (not admin)", employeeID);
            result.put("success", false);
            result.put("message", "Only admin can delete routines.");
            return result;
        }

        String sql = "DELETE FROM Routine WHERE JobID = ?";
        int deleted;
        try {
            deleted = jdbcTemplate.update(sql, jobId);
        } catch (Exception ex) {
            logger.error("Error deleting routine with JobID [{}]: {}", jobId, ex.getMessage());
            result.put("success", false);
            result.put("message", "Routine deletion failed: " + ex.getMessage());
            return result;
        }

        if (deleted > 0) {
            logger.info("Routine with JobID [{}] deleted by admin [{}]", jobId, employeeID);
            result.put("success", true);
            result.put("message", "Routine deleted successfully.");
        } else {
            logger.warn("Routine with JobID [{}] not found or not deleted.", jobId);
            result.put("success", false);
            result.put("message", "Routine not found or could not be deleted.");
        }
        return result;
    }
}
