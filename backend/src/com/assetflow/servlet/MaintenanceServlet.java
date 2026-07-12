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
 * MaintenanceServlet — handles the full Maintenance Request lifecycle for AssetFlow ERP.
 *
 * Status Flow:
 *   Employee raises request  →  Pending
 *   Asset Manager approves  →  Approved   (Asset status → 'Maintenance')
 *   Technician assigned     →  In Progress
 *   Work resolved           →  Resolved   (Asset status → 'Available')
 *
 * Endpoint: /maintenance
 * Compatible with Java EE 8 / javax.servlet namespace (Tomcat 9).
 */
@WebServlet("/maintenance")
public class MaintenanceServlet extends HttpServlet {

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
    // GET — fetch all maintenance requests (with asset & employee names)
    // ──────────────────────────────────────────────────────────────

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        setCorsHeaders(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();
        JsonObject responseJson = new JsonObject();

        String sql =
            "SELECT mr.id, mr.asset_id, ast.tag AS asset_tag, ast.name AS asset_name, " +
            "       mr.reporter_id, emp.name AS reporter_name, " +
            "       mr.issue_title, mr.description, mr.priority, mr.status, " +
            "       mr.technician_name, mr.resolution_notes, mr.created_at, mr.updated_at " +
            "FROM Maintenance_Requests mr " +
            "JOIN Assets ast  ON mr.asset_id   = ast.id " +
            "LEFT JOIN Employees emp ON mr.reporter_id = emp.id " +
            "ORDER BY mr.id DESC";

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs   = null;

        try {
            conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                sendError(response, out, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                          "Database connection unavailable.");
                return;
            }

            stmt = conn.prepareStatement(sql);
            rs   = stmt.executeQuery();

            JsonArray ticketsArray = new JsonArray();
            while (rs.next()) {
                JsonObject ticket = new JsonObject();
                ticket.addProperty("id",               rs.getInt("id"));
                ticket.addProperty("asset_id",         rs.getInt("asset_id"));
                ticket.addProperty("asset_tag",        rs.getString("asset_tag"));
                ticket.addProperty("asset_name",       rs.getString("asset_name"));
                ticket.addProperty("reporter_id",      rs.getInt("reporter_id"));
                ticket.addProperty("reporter_name",    rs.getString("reporter_name"));
                ticket.addProperty("issue_title",      rs.getString("issue_title"));
                ticket.addProperty("description",      rs.getString("description"));
                ticket.addProperty("priority",         rs.getString("priority"));
                ticket.addProperty("status",           rs.getString("status"));
                ticket.addProperty("technician_name",  rs.getString("technician_name"));
                ticket.addProperty("resolution_notes", rs.getString("resolution_notes"));
                ticket.addProperty("created_at",       rs.getTimestamp("created_at") != null
                                                        ? rs.getTimestamp("created_at").toString() : null);
                ticket.addProperty("updated_at",       rs.getTimestamp("updated_at") != null
                                                        ? rs.getTimestamp("updated_at").toString() : null);
                ticketsArray.add(ticket);
            }

            responseJson.add("tickets", ticketsArray);
            responseJson.addProperty("success", true);

        } catch (SQLException e) {
            sendError(response, out, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                      "Database error: " + e.getMessage());
            e.printStackTrace();
            return;
        } finally {
            try { if (rs   != null) rs.close();   } catch (SQLException e) { e.printStackTrace(); }
            try { if (stmt != null) stmt.close(); } catch (SQLException e) { e.printStackTrace(); }
        }

        out.print(gson.toJson(responseJson));
        out.flush();
    }

    // ──────────────────────────────────────────────────────────────
    // POST — dispatch to action handlers
    // ──────────────────────────────────────────────────────────────

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        setCorsHeaders(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();
        JsonObject responseJson = new JsonObject();

        // Read raw JSON body
        StringBuilder sb = new StringBuilder();
        String line;
        try (BufferedReader reader = request.getReader()) {
            while ((line = reader.readLine()) != null) sb.append(line);
        }

        String jsonBody = sb.toString().trim();
        if (jsonBody.isEmpty()) {
            sendError(response, out, HttpServletResponse.SC_BAD_REQUEST, "Empty request body.");
            return;
        }

        JsonObject requestJson;
        String action;
        try {
            requestJson = gson.fromJson(jsonBody, JsonObject.class);
            action      = requestJson.has("action")
                          ? requestJson.get("action").getAsString().trim().toLowerCase()
                          : "";
        } catch (Exception e) {
            sendError(response, out, HttpServletResponse.SC_BAD_REQUEST, "Malformed JSON request.");
            return;
        }

        Connection conn = null;
        try {
            conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                sendError(response, out, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                          "Database connection unavailable.");
                return;
            }
            conn.setAutoCommit(false);

            switch (action) {

                // ── 1. Employee raises a new maintenance request ───────────────
                case "raise":
                    handleRaise(requestJson, conn, response, out, responseJson);
                    break;

                // ── 2. Asset Manager approves → asset becomes 'Maintenance' ───
                case "approve":
                    handleApprove(requestJson, conn, response, out, responseJson);
                    break;

                // ── 3. Technician assigned to the ticket ───────────────────────
                case "assign_technician":
                    handleAssignTechnician(requestJson, conn, response, out, responseJson);
                    break;

                // ── 4. Work resolved → asset returns to 'Available' ────────────
                case "resolve":
                    handleResolve(requestJson, conn, response, out, responseJson);
                    break;

                default:
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    responseJson.addProperty("success", false);
                    responseJson.addProperty("message", "Invalid action: '" + action + "'. " +
                            "Expected: raise | approve | assign_technician | resolve");
                    conn.rollback();
            }

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Database transaction failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); } catch (SQLException ex) { ex.printStackTrace(); }
            }
        }

        out.print(gson.toJson(responseJson));
        out.flush();
    }

    // ══════════════════════════════════════════════════════════════
    // Action Handlers
    // ══════════════════════════════════════════════════════════════

    /**
     * Action: raise
     * Creates a new Maintenance_Request with status 'Pending'.
     * Required fields: asset_id, reporter_id, issue_title, description, priority
     */
    private void handleRaise(JsonObject req, Connection conn,
                              HttpServletResponse response, PrintWriter out,
                              JsonObject responseJson) throws SQLException {

        if (!req.has("asset_id") || !req.has("reporter_id") ||
            !req.has("issue_title") || !req.has("description") || !req.has("priority")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message",
                "Missing required fields: asset_id, reporter_id, issue_title, description, priority.");
            try { conn.rollback(); } catch (SQLException e) { e.printStackTrace(); }
            return;
        }

        int    assetId     = req.get("asset_id").getAsInt();
        int    reporterId  = req.get("reporter_id").getAsInt();
        String issueTitle  = req.get("issue_title").getAsString().trim();
        String description = req.get("description").getAsString().trim();
        String priority    = req.get("priority").getAsString().trim().toUpperCase();

        // Validate priority
        if (!priority.equals("NORMAL") && !priority.equals("HIGH") && !priority.equals("CRITICAL")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Invalid priority. Use: NORMAL | HIGH | CRITICAL");
            conn.rollback();
            return;
        }

        // Validate asset exists
        String checkAsset = "SELECT id FROM Assets WHERE id = ?";
        PreparedStatement checkStmt = conn.prepareStatement(checkAsset);
        checkStmt.setInt(1, assetId);
        ResultSet rsCheck = checkStmt.executeQuery();
        if (!rsCheck.next()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Asset ID " + assetId + " does not exist.");
            rsCheck.close(); checkStmt.close();
            conn.rollback();
            return;
        }
        rsCheck.close(); checkStmt.close();

        // Insert request — status defaults to 'Pending' (see DDL)
        String insertSql =
            "INSERT INTO Maintenance_Requests " +
            "(asset_id, reporter_id, issue_title, description, priority, status) " +
            "VALUES (?, ?, ?, ?, ?, 'Pending')";
        PreparedStatement insertStmt = conn.prepareStatement(insertSql);
        insertStmt.setInt(1, assetId);
        insertStmt.setInt(2, reporterId);
        insertStmt.setString(3, issueTitle);
        insertStmt.setString(4, description);
        insertStmt.setString(5, priority);
        insertStmt.executeUpdate();
        insertStmt.close();

        conn.commit();
        responseJson.addProperty("success", true);
        responseJson.addProperty("message",
            "Maintenance request raised successfully. Status: Pending — awaiting Asset Manager review.");
    }

    /**
     * Action: approve
     * Asset Manager approves the request.
     *   - Maintenance_Requests.status → 'Approved'
     *   - Assets.status               → 'Maintenance'
     * Required field: ticket_id
     */
    private void handleApprove(JsonObject req, Connection conn,
                                HttpServletResponse response, PrintWriter out,
                                JsonObject responseJson) throws SQLException {

        if (!req.has("ticket_id")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Missing required field: ticket_id.");
            conn.rollback();
            return;
        }

        int ticketId = req.get("ticket_id").getAsInt();

        // Fetch ticket and verify it is still Pending
        String selectSql =
            "SELECT asset_id, status FROM Maintenance_Requests WHERE id = ?";
        PreparedStatement selectStmt = conn.prepareStatement(selectSql);
        selectStmt.setInt(1, ticketId);
        ResultSet rs = selectStmt.executeQuery();

        if (!rs.next()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Maintenance ticket #" + ticketId + " not found.");
            rs.close(); selectStmt.close();
            conn.rollback();
            return;
        }

        String currentStatus = rs.getString("status");
        int    assetId       = rs.getInt("asset_id");
        rs.close(); selectStmt.close();

        if (!"Pending".equalsIgnoreCase(currentStatus)) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message",
                "Ticket #" + ticketId + " is already '" + currentStatus +
                "'. Only 'Pending' tickets can be approved.");
            conn.rollback();
            return;
        }

        // 1. Update ticket status → Approved
        String updateTicket =
            "UPDATE Maintenance_Requests SET status = 'Approved', updated_at = CURRENT_TIMESTAMP() WHERE id = ?";
        PreparedStatement updateTicketStmt = conn.prepareStatement(updateTicket);
        updateTicketStmt.setInt(1, ticketId);
        updateTicketStmt.executeUpdate();
        updateTicketStmt.close();

        // 2. Update asset status → Maintenance (taken out of service)
        String updateAsset = "UPDATE Assets SET status = 'Maintenance' WHERE id = ?";
        PreparedStatement updateAssetStmt = conn.prepareStatement(updateAsset);
        updateAssetStmt.setInt(1, assetId);
        updateAssetStmt.executeUpdate();
        updateAssetStmt.close();

        conn.commit();
        responseJson.addProperty("success", true);
        responseJson.addProperty("message",
            "Ticket #" + ticketId + " approved. Asset #" + assetId +
            " has been placed 'Under Maintenance'.");
    }

    /**
     * Action: assign_technician
     * Records the technician name assigned to the approved ticket.
     *   - Maintenance_Requests.status           → 'In Progress'
     *   - Maintenance_Requests.technician_name  → provided name
     * Required fields: ticket_id, technician_name
     */
    private void handleAssignTechnician(JsonObject req, Connection conn,
                                         HttpServletResponse response, PrintWriter out,
                                         JsonObject responseJson) throws SQLException {

        if (!req.has("ticket_id") || !req.has("technician_name")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Missing required fields: ticket_id, technician_name.");
            conn.rollback();
            return;
        }

        int    ticketId       = req.get("ticket_id").getAsInt();
        String technicianName = req.get("technician_name").getAsString().trim();

        // Verify ticket is in 'Approved' state before assigning
        String selectSql = "SELECT status FROM Maintenance_Requests WHERE id = ?";
        PreparedStatement selectStmt = conn.prepareStatement(selectSql);
        selectStmt.setInt(1, ticketId);
        ResultSet rs = selectStmt.executeQuery();

        if (!rs.next()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Maintenance ticket #" + ticketId + " not found.");
            rs.close(); selectStmt.close();
            conn.rollback();
            return;
        }

        String currentStatus = rs.getString("status");
        rs.close(); selectStmt.close();

        if (!"Approved".equalsIgnoreCase(currentStatus)) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message",
                "Ticket #" + ticketId + " is '" + currentStatus +
                "'. A technician can only be assigned to 'Approved' tickets.");
            conn.rollback();
            return;
        }

        String updateSql =
            "UPDATE Maintenance_Requests " +
            "SET status = 'In Progress', technician_name = ?, updated_at = CURRENT_TIMESTAMP() " +
            "WHERE id = ?";
        PreparedStatement updateStmt = conn.prepareStatement(updateSql);
        updateStmt.setString(1, technicianName);
        updateStmt.setInt(2, ticketId);
        updateStmt.executeUpdate();
        updateStmt.close();

        conn.commit();
        responseJson.addProperty("success", true);
        responseJson.addProperty("message",
            "Technician '" + technicianName + "' assigned to ticket #" + ticketId +
            ". Status: In Progress.");
    }

    /**
     * Action: resolve
     * Marks the ticket as resolved and restores asset availability.
     *   - Maintenance_Requests.status           → 'Resolved'
     *   - Maintenance_Requests.resolution_notes → provided notes
     *   - Assets.status                         → 'Available'
     * Required fields: ticket_id, resolution_notes
     */
    private void handleResolve(JsonObject req, Connection conn,
                                HttpServletResponse response, PrintWriter out,
                                JsonObject responseJson) throws SQLException {

        if (!req.has("ticket_id") || !req.has("resolution_notes")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Missing required fields: ticket_id, resolution_notes.");
            conn.rollback();
            return;
        }

        int    ticketId        = req.get("ticket_id").getAsInt();
        String resolutionNotes = req.get("resolution_notes").getAsString().trim();

        // Fetch ticket — must be 'In Progress'
        String selectSql = "SELECT asset_id, status FROM Maintenance_Requests WHERE id = ?";
        PreparedStatement selectStmt = conn.prepareStatement(selectSql);
        selectStmt.setInt(1, ticketId);
        ResultSet rs = selectStmt.executeQuery();

        if (!rs.next()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Maintenance ticket #" + ticketId + " not found.");
            rs.close(); selectStmt.close();
            conn.rollback();
            return;
        }

        String currentStatus = rs.getString("status");
        int    assetId       = rs.getInt("asset_id");
        rs.close(); selectStmt.close();

        if (!"In Progress".equalsIgnoreCase(currentStatus)) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message",
                "Ticket #" + ticketId + " is '" + currentStatus +
                "'. Only 'In Progress' tickets can be resolved.");
            conn.rollback();
            return;
        }

        // 1. Resolve the ticket
        String updateTicket =
            "UPDATE Maintenance_Requests " +
            "SET status = 'Resolved', resolution_notes = ?, updated_at = CURRENT_TIMESTAMP() " +
            "WHERE id = ?";
        PreparedStatement updateTicketStmt = conn.prepareStatement(updateTicket);
        updateTicketStmt.setString(1, resolutionNotes);
        updateTicketStmt.setInt(2, ticketId);
        updateTicketStmt.executeUpdate();
        updateTicketStmt.close();

        // 2. Restore asset to Available (un-allocated_to stays as-is; only status is cleared)
        String updateAsset = "UPDATE Assets SET status = 'Available' WHERE id = ? AND status = 'Maintenance'";
        PreparedStatement updateAssetStmt = conn.prepareStatement(updateAsset);
        updateAssetStmt.setInt(1, assetId);
        updateAssetStmt.executeUpdate();
        updateAssetStmt.close();

        conn.commit();
        responseJson.addProperty("success", true);
        responseJson.addProperty("message",
            "Ticket #" + ticketId + " resolved. Asset #" + assetId +
            " is now 'Available'.");
    }

    // ──────────────────────────────────────────────────────────────
    // Helper: write error JSON and set HTTP status in one call
    // ──────────────────────────────────────────────────────────────

    private void sendError(HttpServletResponse response, PrintWriter out,
                           int statusCode, String message) throws IOException {
        response.setStatus(statusCode);
        JsonObject err = new JsonObject();
        err.addProperty("success", false);
        err.addProperty("message", message);
        out.print(gson.toJson(err));
        out.flush();
    }
}
