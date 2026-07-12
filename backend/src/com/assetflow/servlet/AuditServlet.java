package com.assetflow.servlet;

import com.assetflow.util.DatabaseConnection;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * AuditServlet — manages Audit Cycles and per-item verification for AssetFlow ERP.
 *
 * Audit Cycle lifecycle:
 *   1. Auditor creates a cycle (Open)         → POST { action: "create_cycle" }
 *   2. Auditor marks items Verified/Missing/Damaged
 *                                             → POST { action: "update_item" }
 *   3. Auditor closes the cycle               → POST { action: "close_cycle" }
 *        → Auto-generates a summary report (stored as JSON in Audit_Cycles.report)
 *        → All 'Missing' items have their Assets.status set to 'Lost'
 *        → All 'Damaged' items have their Assets.status set to 'Maintenance'
 *        → Cycle status becomes 'Closed'
 *
 * GET params:
 *   ?action=cycles                  → list all audit cycles
 *   ?action=items&cycle_id=<id>     → list all items + their audit status for one cycle
 *
 * Endpoint: /audit
 * Compatible with Java EE 8 / javax.servlet namespace (Tomcat 9).
 */
@WebServlet("/audit")
public class AuditServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    // ──────────────────────────────────────────────────────────────
    // CORS helpers
    // ──────────────────────────────────────────────────────────────

    private void setCorsHeaders(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:5173");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Accept, Authorization");
        response.setHeader("Access-Control-Allow-Credentials", "true");
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    // ──────────────────────────────────────────────────────────────
    // GET
    // ──────────────────────────────────────────────────────────────

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        setCorsHeaders(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out   = response.getWriter();
        String      param = request.getParameter("action");
        if (param == null) param = "cycles";

        switch (param.toLowerCase()) {
            case "items":
                handleGetItems(request, response, out);
                break;
            case "cycles":
            default:
                handleGetCycles(response, out);
        }
    }

    /** List all audit cycles with auditor name and summary stats. */
    private void handleGetCycles(HttpServletResponse response, PrintWriter out) {
        String sql =
            "SELECT ac.id, ac.title, ac.scope_description, ac.auditor_id, " +
            "       emp.name AS auditor_name, ac.status, ac.report, " +
            "       ac.created_at, ac.closed_at, " +
            "       COUNT(ai.id)                                           AS total_items, " +
            "       SUM(ai.audit_status = 'Verified')                     AS verified_count, " +
            "       SUM(ai.audit_status = 'Missing')                      AS missing_count, " +
            "       SUM(ai.audit_status = 'Damaged')                      AS damaged_count, " +
            "       SUM(ai.audit_status = 'Pending')                      AS pending_count " +
            "FROM Audit_Cycles ac " +
            "LEFT JOIN Employees emp ON ac.auditor_id = emp.id " +
            "LEFT JOIN Audit_Items ai ON ai.cycle_id = ac.id " +
            "GROUP BY ac.id " +
            "ORDER BY ac.id DESC";

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs   = null;
        JsonObject responseJson = new JsonObject();

        try {
            conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) { sendError(response, out, 500, "DB unavailable."); return; }

            stmt = conn.prepareStatement(sql);
            rs   = stmt.executeQuery();

            JsonArray cycles = new JsonArray();
            while (rs.next()) {
                JsonObject c = new JsonObject();
                c.addProperty("id",               rs.getInt("id"));
                c.addProperty("title",            rs.getString("title"));
                c.addProperty("scope_description",rs.getString("scope_description"));
                c.addProperty("auditor_id",       rs.getInt("auditor_id"));
                c.addProperty("auditor_name",     rs.getString("auditor_name"));
                c.addProperty("status",           rs.getString("status"));
                c.addProperty("report",           rs.getString("report"));
                c.addProperty("created_at",       rs.getTimestamp("created_at") != null
                                                   ? rs.getTimestamp("created_at").toString() : null);
                c.addProperty("closed_at",        rs.getTimestamp("closed_at") != null
                                                   ? rs.getTimestamp("closed_at").toString() : null);
                c.addProperty("total_items",      rs.getInt("total_items"));
                c.addProperty("verified_count",   rs.getInt("verified_count"));
                c.addProperty("missing_count",    rs.getInt("missing_count"));
                c.addProperty("damaged_count",    rs.getInt("damaged_count"));
                c.addProperty("pending_count",    rs.getInt("pending_count"));
                cycles.add(c);
            }
            responseJson.add("cycles", cycles);
            responseJson.addProperty("success", true);

        } catch (SQLException e) {
            sendError(response, out, 500, "DB error: " + e.getMessage());
            e.printStackTrace();
            return;
        } finally {
            closeQuietly(rs, stmt);
        }

        out.print(gson.toJson(responseJson));
        out.flush();
    }

    /** List all audit items for a given cycle_id. */
    private void handleGetItems(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        String cycleIdParam = request.getParameter("cycle_id");
        JsonObject responseJson = new JsonObject();

        if (cycleIdParam == null || cycleIdParam.isBlank()) {
            sendError(response, out, 400, "Missing query param: cycle_id");
            return;
        }

        int cycleId;
        try { cycleId = Integer.parseInt(cycleIdParam.trim()); }
        catch (NumberFormatException e) {
            sendError(response, out, 400, "cycle_id must be an integer.");
            return;
        }

        String sql =
            "SELECT ai.id, ai.cycle_id, ai.asset_id, ast.tag AS asset_tag, " +
            "       ast.name AS asset_name, ast.expected_location, " +
            "       ai.audit_status, ai.notes, ai.audited_at " +
            "FROM Audit_Items ai " +
            "JOIN Assets ast ON ai.asset_id = ast.id " +
            "WHERE ai.cycle_id = ? " +
            "ORDER BY ai.id ASC";

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs   = null;

        try {
            conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) { sendError(response, out, 500, "DB unavailable."); return; }

            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, cycleId);
            rs   = stmt.executeQuery();

            JsonArray items = new JsonArray();
            while (rs.next()) {
                JsonObject item = new JsonObject();
                item.addProperty("id",               rs.getInt("id"));
                item.addProperty("cycle_id",         rs.getInt("cycle_id"));
                item.addProperty("asset_id",         rs.getInt("asset_id"));
                item.addProperty("asset_tag",        rs.getString("asset_tag"));
                item.addProperty("asset_name",       rs.getString("asset_name"));
                item.addProperty("expected_location",rs.getString("expected_location"));
                item.addProperty("audit_status",     rs.getString("audit_status"));
                item.addProperty("notes",            rs.getString("notes"));
                item.addProperty("audited_at",       rs.getTimestamp("audited_at") != null
                                                      ? rs.getTimestamp("audited_at").toString() : null);
                items.add(item);
            }
            responseJson.add("items", items);
            responseJson.addProperty("success", true);

        } catch (SQLException e) {
            sendError(response, out, 500, "DB error: " + e.getMessage());
            e.printStackTrace();
            return;
        } finally {
            closeQuietly(rs, stmt);
        }

        out.print(gson.toJson(responseJson));
        out.flush();
    }

    // ──────────────────────────────────────────────────────────────
    // POST — dispatch
    // ──────────────────────────────────────────────────────────────

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        setCorsHeaders(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();

        // Read body
        StringBuilder sb = new StringBuilder();
        String line;
        try (BufferedReader reader = request.getReader()) {
            while ((line = reader.readLine()) != null) sb.append(line);
        }

        String jsonBody = sb.toString().trim();
        if (jsonBody.isEmpty()) { sendError(response, out, 400, "Empty request body."); return; }

        JsonObject requestJson;
        String action;
        try {
            requestJson = gson.fromJson(jsonBody, JsonObject.class);
            action      = requestJson.has("action")
                          ? requestJson.get("action").getAsString().trim().toLowerCase()
                          : "";
        } catch (Exception e) {
            sendError(response, out, 400, "Malformed JSON.");
            return;
        }

        JsonObject responseJson = new JsonObject();
        Connection conn = null;

        try {
            conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) { sendError(response, out, 500, "DB unavailable."); return; }
            conn.setAutoCommit(false);

            switch (action) {
                case "create_cycle":
                    handleCreateCycle(requestJson, conn, response, out, responseJson);
                    break;
                case "update_item":
                    handleUpdateItem(requestJson, conn, response, out, responseJson);
                    break;
                case "close_cycle":
                    handleCloseCycle(requestJson, conn, response, out, responseJson);
                    break;
                default:
                    response.setStatus(400);
                    responseJson.addProperty("success", false);
                    responseJson.addProperty("message",
                        "Unknown action '" + action + "'. Use: create_cycle | update_item | close_cycle");
                    conn.rollback();
            }

        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            response.setStatus(500);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Transaction failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); } catch (SQLException ex) { ex.printStackTrace(); }
        }

        out.print(gson.toJson(responseJson));
        out.flush();
    }

    // ══════════════════════════════════════════════════════════════
    // Action: create_cycle
    //   Required: title, scope_description, auditor_id, asset_ids[] (int array)
    //   Creates an Open audit cycle and inserts one Audit_Item (Pending) per asset.
    // ══════════════════════════════════════════════════════════════

    private void handleCreateCycle(JsonObject req, Connection conn,
                                   HttpServletResponse response, PrintWriter out,
                                   JsonObject res) throws SQLException {

        if (!req.has("title") || !req.has("auditor_id") || !req.has("asset_ids")) {
            response.setStatus(400);
            res.addProperty("success", false);
            res.addProperty("message", "Required: title, auditor_id, asset_ids (array of ints).");
            conn.rollback();
            return;
        }

        String    title       = req.get("title").getAsString().trim();
        String    scopeDesc   = req.has("scope_description")
                                ? req.get("scope_description").getAsString().trim() : "";
        int       auditorId   = req.get("auditor_id").getAsInt();
        JsonArray assetIdsArr = req.get("asset_ids").getAsJsonArray();

        if (title.isEmpty() || assetIdsArr.size() == 0) {
            response.setStatus(400);
            res.addProperty("success", false);
            res.addProperty("message", "Title and at least one asset_id are required.");
            conn.rollback();
            return;
        }

        // 1. Insert Audit_Cycle
        String insertCycleSql =
            "INSERT INTO Audit_Cycles (title, scope_description, auditor_id, status) VALUES (?, ?, ?, 'Open')";
        PreparedStatement cycleStmt = conn.prepareStatement(insertCycleSql,
                java.sql.Statement.RETURN_GENERATED_KEYS);
        cycleStmt.setString(1, title);
        cycleStmt.setString(2, scopeDesc);
        cycleStmt.setInt(3, auditorId);
        cycleStmt.executeUpdate();

        ResultSet generatedKeys = cycleStmt.getGeneratedKeys();
        if (!generatedKeys.next()) {
            response.setStatus(500);
            res.addProperty("success", false);
            res.addProperty("message", "Could not retrieve generated cycle ID.");
            generatedKeys.close(); cycleStmt.close();
            conn.rollback();
            return;
        }
        int newCycleId = generatedKeys.getInt(1);
        generatedKeys.close();
        cycleStmt.close();

        // 2. Insert one Audit_Item per asset (status = 'Pending')
        String insertItemSql =
            "INSERT INTO Audit_Items (cycle_id, asset_id, audit_status) VALUES (?, ?, 'Pending')";
        PreparedStatement itemStmt = conn.prepareStatement(insertItemSql);
        for (int i = 0; i < assetIdsArr.size(); i++) {
            int assetId = assetIdsArr.get(i).getAsInt();
            itemStmt.setInt(1, newCycleId);
            itemStmt.setInt(2, assetId);
            itemStmt.addBatch();
        }
        itemStmt.executeBatch();
        itemStmt.close();

        conn.commit();
        res.addProperty("success", true);
        res.addProperty("cycle_id", newCycleId);
        res.addProperty("message", "Audit cycle '" + title + "' created with " +
                assetIdsArr.size() + " asset(s). Status: Open.");
    }

    // ══════════════════════════════════════════════════════════════
    // Action: update_item
    //   Required: item_id, audit_status ('Verified'|'Missing'|'Damaged'|'Pending')
    //   Optional: notes
    //   Auditor assigns verification status to a single item.
    // ══════════════════════════════════════════════════════════════

    private void handleUpdateItem(JsonObject req, Connection conn,
                                  HttpServletResponse response, PrintWriter out,
                                  JsonObject res) throws SQLException {

        if (!req.has("item_id") || !req.has("audit_status")) {
            response.setStatus(400);
            res.addProperty("success", false);
            res.addProperty("message", "Required: item_id, audit_status (Verified|Missing|Damaged|Pending).");
            conn.rollback();
            return;
        }

        int    itemId      = req.get("item_id").getAsInt();
        String auditStatus = req.get("audit_status").getAsString().trim();
        String notes       = req.has("notes") ? req.get("notes").getAsString().trim() : null;

        // Validate status values
        if (!auditStatus.equals("Verified") && !auditStatus.equals("Missing") &&
            !auditStatus.equals("Damaged")  && !auditStatus.equals("Pending")) {
            response.setStatus(400);
            res.addProperty("success", false);
            res.addProperty("message", "Invalid audit_status. Use: Verified | Missing | Damaged | Pending");
            conn.rollback();
            return;
        }

        // Verify item exists and cycle is still Open
        String checkSql =
            "SELECT ai.id, ac.status AS cycle_status " +
            "FROM Audit_Items ai " +
            "JOIN Audit_Cycles ac ON ai.cycle_id = ac.id " +
            "WHERE ai.id = ?";
        PreparedStatement checkStmt = conn.prepareStatement(checkSql);
        checkStmt.setInt(1, itemId);
        ResultSet rs = checkStmt.executeQuery();
        if (!rs.next()) {
            response.setStatus(404);
            res.addProperty("success", false);
            res.addProperty("message", "Audit item #" + itemId + " not found.");
            rs.close(); checkStmt.close();
            conn.rollback();
            return;
        }
        String cycleStatus = rs.getString("cycle_status");
        rs.close(); checkStmt.close();

        if (!"Open".equalsIgnoreCase(cycleStatus)) {
            response.setStatus(409);
            res.addProperty("success", false);
            res.addProperty("message", "Cannot update items on a '" + cycleStatus + "' cycle. Only Open cycles allow edits.");
            conn.rollback();
            return;
        }

        // Update the item
        String updateSql =
            "UPDATE Audit_Items SET audit_status = ?, notes = ?, audited_at = CURRENT_TIMESTAMP() WHERE id = ?";
        PreparedStatement updateStmt = conn.prepareStatement(updateSql);
        updateStmt.setString(1, auditStatus);
        updateStmt.setString(2, notes);
        updateStmt.setInt(3, itemId);
        updateStmt.executeUpdate();
        updateStmt.close();

        conn.commit();
        res.addProperty("success", true);
        res.addProperty("message", "Item #" + itemId + " marked as '" + auditStatus + "'.");
    }

    // ══════════════════════════════════════════════════════════════
    // Action: close_cycle
    //   Required: cycle_id
    //   1. Validates all items have been reviewed (no 'Pending' remaining).
    //   2. Marks 'Missing' assets → status 'Lost'.
    //   3. Marks 'Damaged' assets → status 'Maintenance'.
    //   4. Auto-generates a JSON summary report and stores it in Audit_Cycles.report.
    //   5. Marks Audit_Cycles.status = 'Closed'.
    // ══════════════════════════════════════════════════════════════

    private void handleCloseCycle(JsonObject req, Connection conn,
                                  HttpServletResponse response, PrintWriter out,
                                  JsonObject res) throws SQLException {

        if (!req.has("cycle_id")) {
            response.setStatus(400);
            res.addProperty("success", false);
            res.addProperty("message", "Required: cycle_id.");
            conn.rollback();
            return;
        }

        int cycleId = req.get("cycle_id").getAsInt();

        // 1. Verify cycle exists and is Open
        String checkCycleSql = "SELECT id, title, status FROM Audit_Cycles WHERE id = ?";
        PreparedStatement checkStmt = conn.prepareStatement(checkCycleSql);
        checkStmt.setInt(1, cycleId);
        ResultSet rsCheck = checkStmt.executeQuery();
        if (!rsCheck.next()) {
            response.setStatus(404);
            res.addProperty("success", false);
            res.addProperty("message", "Audit cycle #" + cycleId + " not found.");
            rsCheck.close(); checkStmt.close();
            conn.rollback();
            return;
        }
        String cycleStatus = rsCheck.getString("status");
        String cycleTitle  = rsCheck.getString("title");
        rsCheck.close(); checkStmt.close();

        if (!"Open".equalsIgnoreCase(cycleStatus)) {
            response.setStatus(409);
            res.addProperty("success", false);
            res.addProperty("message", "Cycle #" + cycleId + " is already '" + cycleStatus + "'. Cannot close again.");
            conn.rollback();
            return;
        }

        // 2. Check for unreviewed (Pending) items
        String pendingCheckSql =
            "SELECT COUNT(*) FROM Audit_Items WHERE cycle_id = ? AND audit_status = 'Pending'";
        PreparedStatement pendingStmt = conn.prepareStatement(pendingCheckSql);
        pendingStmt.setInt(1, cycleId);
        ResultSet rsPending = pendingStmt.executeQuery();
        rsPending.next();
        int pendingCount = rsPending.getInt(1);
        rsPending.close(); pendingStmt.close();

        if (pendingCount > 0) {
            response.setStatus(409);
            res.addProperty("success", false);
            res.addProperty("message",
                "Cannot close cycle: " + pendingCount + " item(s) still have 'Pending' status. " +
                "Please review all assets before closing.");
            conn.rollback();
            return;
        }

        // 3. Collect stats for the report
        String statsSql =
            "SELECT " +
            "  SUM(audit_status = 'Verified') AS verified, " +
            "  SUM(audit_status = 'Missing')  AS missing, " +
            "  SUM(audit_status = 'Damaged')  AS damaged, " +
            "  COUNT(*)                        AS total " +
            "FROM Audit_Items WHERE cycle_id = ?";
        PreparedStatement statsStmt = conn.prepareStatement(statsSql);
        statsStmt.setInt(1, cycleId);
        ResultSet rsStats = statsStmt.executeQuery();
        rsStats.next();
        int totalItems  = rsStats.getInt("total");
        int verifiedCnt = rsStats.getInt("verified");
        int missingCnt  = rsStats.getInt("missing");
        int damagedCnt  = rsStats.getInt("damaged");
        rsStats.close(); statsStmt.close();

        // 4. Mark Missing assets → Lost
        if (missingCnt > 0) {
            String markLostSql =
                "UPDATE Assets a " +
                "JOIN Audit_Items ai ON a.id = ai.asset_id " +
                "SET a.status = 'Lost' " +
                "WHERE ai.cycle_id = ? AND ai.audit_status = 'Missing'";
            PreparedStatement lostStmt = conn.prepareStatement(markLostSql);
            lostStmt.setInt(1, cycleId);
            lostStmt.executeUpdate();
            lostStmt.close();
        }

        // 5. Mark Damaged assets → Maintenance
        if (damagedCnt > 0) {
            String markDamagedSql =
                "UPDATE Assets a " +
                "JOIN Audit_Items ai ON a.id = ai.asset_id " +
                "SET a.status = 'Maintenance' " +
                "WHERE ai.cycle_id = ? AND ai.audit_status = 'Damaged'";
            PreparedStatement damagedStmt = conn.prepareStatement(markDamagedSql);
            damagedStmt.setInt(1, cycleId);
            damagedStmt.executeUpdate();
            damagedStmt.close();
        }

        // 6. Build the auto-generated JSON report
        //    Fetch detailed item list for the report body
        String detailSql =
            "SELECT ast.tag, ast.name, ai.audit_status, ai.notes " +
            "FROM Audit_Items ai " +
            "JOIN Assets ast ON ai.asset_id = ast.id " +
            "WHERE ai.cycle_id = ? " +
            "ORDER BY ai.audit_status, ast.tag";
        PreparedStatement detailStmt = conn.prepareStatement(detailSql);
        detailStmt.setInt(1, cycleId);
        ResultSet rsDetail = detailStmt.executeQuery();

        JsonObject report = new JsonObject();
        report.addProperty("cycle_id",      cycleId);
        report.addProperty("cycle_title",   cycleTitle);
        report.addProperty("closed_at",     new java.util.Date().toString());
        report.addProperty("total_assets",  totalItems);
        report.addProperty("verified",      verifiedCnt);
        report.addProperty("missing",       missingCnt);
        report.addProperty("damaged",       damagedCnt);
        report.addProperty("accuracy_pct",
            totalItems > 0 ? Math.round((verifiedCnt * 100.0) / totalItems) : 0);

        JsonArray reportItems = new JsonArray();
        while (rsDetail.next()) {
            JsonObject ri = new JsonObject();
            ri.addProperty("tag",          rsDetail.getString("tag"));
            ri.addProperty("name",         rsDetail.getString("name"));
            ri.addProperty("audit_status", rsDetail.getString("audit_status"));
            ri.addProperty("notes",        rsDetail.getString("notes"));
            reportItems.add(ri);
        }
        report.add("items", reportItems);
        rsDetail.close(); detailStmt.close();

        String reportJson = gson.toJson(report);

        // 7. Close the cycle, store report
        String closeSql =
            "UPDATE Audit_Cycles SET status = 'Closed', report = ?, closed_at = CURRENT_TIMESTAMP() WHERE id = ?";
        PreparedStatement closeStmt = conn.prepareStatement(closeSql);
        closeStmt.setString(1, reportJson);
        closeStmt.setInt(2, cycleId);
        closeStmt.executeUpdate();
        closeStmt.close();

        conn.commit();

        res.addProperty("success", true);
        res.addProperty("message",
            "Audit cycle '" + cycleTitle + "' closed. " +
            verifiedCnt + " verified, " + missingCnt + " marked Lost, " +
            damagedCnt + " flagged for Maintenance. Report generated.");
        res.addProperty("report", reportJson);
    }

    // ──────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────

    private void sendError(HttpServletResponse response, PrintWriter out,
                           int code, String message) {
        response.setStatus(code);
        JsonObject err = new JsonObject();
        err.addProperty("success", false);
        err.addProperty("message", message);
        out.print(gson.toJson(err));
        out.flush();
    }

    private void closeQuietly(ResultSet rs, PreparedStatement stmt) {
        try { if (rs   != null) rs.close();   } catch (SQLException e) { e.printStackTrace(); }
        try { if (stmt != null) stmt.close(); } catch (SQLException e) { e.printStackTrace(); }
    }
}
