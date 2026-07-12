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
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * Servlet handling asset allocations, transfer lifecycles, returns, and conflict checks.
 * Mapped to the "/allocations" endpoint.
 * Compatible with Java EE 8 / javax.servlet namespace (Tomcat 9).
 */
@WebServlet("/allocations")
public class AllocationServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

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

    /**
     * GET: Retrieve allocations history, pending transfers, and overdue records.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        setCorsHeaders(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();
        JsonObject responseJson = new JsonObject();

        Connection conn = null;
        PreparedStatement stmtAlloc = null;
        PreparedStatement stmtTrans = null;
        PreparedStatement stmtOverdue = null;
        ResultSet rsAlloc = null;
        ResultSet rsTrans = null;
        ResultSet rsOverdue = null;

        try {
            conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                responseJson.addProperty("success", false);
                responseJson.addProperty("message", "Database connection unavailable.");
                out.print(gson.toJson(responseJson));
                return;
            }

            // 1. Fetch Allocations History
            JsonArray allocationsArray = new JsonArray();
            String sqlAlloc = "SELECT al.id, al.asset_id, ast.tag AS asset_tag, ast.name AS asset_name, " +
                              "al.employee_id, emp.name AS employee_name, al.allocated_at, " +
                              "al.expected_return_date, al.actual_return_date, al.condition_notes, al.status " +
                              "FROM Allocations al " +
                              "JOIN Assets ast ON al.asset_id = ast.id " +
                              "JOIN Employees emp ON al.employee_id = emp.id " +
                              "ORDER BY al.id DESC";
            stmtAlloc = conn.prepareStatement(sqlAlloc);
            rsAlloc = stmtAlloc.executeQuery();
            while (rsAlloc.next()) {
                JsonObject alloc = new JsonObject();
                alloc.addProperty("id", rsAlloc.getInt("id"));
                alloc.addProperty("asset_id", rsAlloc.getInt("asset_id"));
                alloc.addProperty("asset_tag", rsAlloc.getString("asset_tag"));
                alloc.addProperty("asset_name", rsAlloc.getString("asset_name"));
                alloc.addProperty("employee_id", rsAlloc.getInt("employee_id"));
                alloc.addProperty("employee_name", rsAlloc.getString("employee_name"));
                alloc.addProperty("allocated_at", rsAlloc.getTimestamp("allocated_at").toString());
                
                Date expDate = rsAlloc.getDate("expected_return_date");
                alloc.addProperty("expected_return_date", expDate != null ? expDate.toString() : null);
                
                Timestamp actDate = rsAlloc.getTimestamp("actual_return_date");
                alloc.addProperty("actual_return_date", actDate != null ? actDate.toString() : null);
                
                alloc.addProperty("condition_notes", rsAlloc.getString("condition_notes"));
                alloc.addProperty("status", rsAlloc.getString("status"));
                allocationsArray.add(alloc);
            }
            responseJson.add("allocations", allocationsArray);

            // 2. Fetch Transfer Requests
            JsonArray transfersArray = new JsonArray();
            String sqlTrans = "SELECT tr.id, tr.asset_id, ast.tag AS asset_tag, ast.name AS asset_name, " +
                              "tr.from_employee_id, empFrom.name AS from_employee_name, " +
                              "tr.to_employee_id, empTo.name AS to_employee_name, " +
                              "tr.reason, tr.status, tr.request_date " +
                              "FROM Transfer_Requests tr " +
                              "JOIN Assets ast ON tr.asset_id = ast.id " +
                              "LEFT JOIN Employees empFrom ON tr.from_employee_id = empFrom.id " +
                              "LEFT JOIN Employees empTo ON tr.to_employee_id = empTo.id " +
                              "ORDER BY tr.id DESC";
            stmtTrans = conn.prepareStatement(sqlTrans);
            rsTrans = stmtTrans.executeQuery();
            while (rsTrans.next()) {
                JsonObject trans = new JsonObject();
                trans.addProperty("id", rsTrans.getInt("id"));
                trans.addProperty("asset_id", rsTrans.getInt("asset_id"));
                trans.addProperty("asset_tag", rsTrans.getString("asset_tag"));
                trans.addProperty("asset_name", rsTrans.getString("asset_name"));
                trans.addProperty("from_employee_id", rsTrans.getInt("from_employee_id"));
                trans.addProperty("from_employee_name", rsTrans.getString("from_employee_name"));
                trans.addProperty("to_employee_id", rsTrans.getInt("to_employee_id"));
                trans.addProperty("to_employee_name", rsTrans.getString("to_employee_name"));
                trans.addProperty("reason", rsTrans.getString("reason"));
                trans.addProperty("status", rsTrans.getString("status"));
                trans.addProperty("request_date", rsTrans.getTimestamp("request_date").toString());
                transfersArray.add(trans);
            }
            responseJson.add("transfers", transfersArray);

            // 3. Fetch Overdue Returns Count & Detail
            JsonArray overdueArray = new JsonArray();
            String sqlOverdue = "SELECT al.id, ast.tag AS asset_tag, ast.name AS asset_name, emp.name AS employee_name, al.expected_return_date " +
                                "FROM Allocations al " +
                                "JOIN Assets ast ON al.asset_id = ast.id " +
                                "JOIN Employees emp ON al.employee_id = emp.id " +
                                "WHERE al.status = 'Approved' AND al.expected_return_date < CURRENT_DATE() AND al.actual_return_date IS NULL";
            stmtOverdue = conn.prepareStatement(sqlOverdue);
            rsOverdue = stmtOverdue.executeQuery();
            while (rsOverdue.next()) {
                JsonObject overdue = new JsonObject();
                overdue.addProperty("id", rsOverdue.getInt("id"));
                overdue.addProperty("asset_tag", rsOverdue.getString("asset_tag"));
                overdue.addProperty("asset_name", rsOverdue.getString("asset_name"));
                overdue.addProperty("employee_name", rsOverdue.getString("employee_name"));
                overdue.addProperty("expected_return_date", rsOverdue.getDate("expected_return_date").toString());
                overdueArray.add(overdue);
            }
            responseJson.add("overdue", overdueArray);
            responseJson.addProperty("success", true);

        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Database error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try { if (rsAlloc != null) rsAlloc.close(); } catch (SQLException e) { e.printStackTrace(); }
            try { if (rsTrans != null) rsTrans.close(); } catch (SQLException e) { e.printStackTrace(); }
            try { if (rsOverdue != null) rsOverdue.close(); } catch (SQLException e) { e.printStackTrace(); }
            try { if (stmtAlloc != null) stmtAlloc.close(); } catch (SQLException e) { e.printStackTrace(); }
            try { if (stmtTrans != null) stmtTrans.close(); } catch (SQLException e) { e.printStackTrace(); }
            try { if (stmtOverdue != null) stmtOverdue.close(); } catch (SQLException e) { e.printStackTrace(); }
        }

        out.print(gson.toJson(responseJson));
        out.flush();
    }

    /**
     * POST: Handle allocate, return, transfer, and approve_transfer lifecycle actions.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        setCorsHeaders(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();
        JsonObject responseJson = new JsonObject();

        StringBuilder sb = new StringBuilder();
        String line;
        try (BufferedReader reader = request.getReader()) {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }

        String jsonBody = sb.toString();
        if (jsonBody.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Empty request body.");
            out.print(gson.toJson(responseJson));
            return;
        }

        String action = "";
        JsonObject requestJson = null;
        try {
            requestJson = gson.fromJson(jsonBody, JsonObject.class);
            if (requestJson.has("action")) {
                action = requestJson.get("action").getAsString().trim().toLowerCase();
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Malformed JSON request.");
            out.print(gson.toJson(responseJson));
            return;
        }

        Connection conn = null;

        try {
            conn = DatabaseConnection.getInstance().getConnection();
            conn.setAutoCommit(false); // Enable transactional integrity

            if ("allocate".equals(action)) {
                int assetId = requestJson.get("asset_id").getAsInt();
                int employeeId = requestJson.get("employee_id").getAsInt();
                String returnDateStr = requestJson.get("expected_return_date").getAsString();

                // 1. Conflict Check: check if the asset is already allocated
                String checkSql = "SELECT status, name, allocated_to FROM Assets WHERE id = ?";
                PreparedStatement checkStmt = conn.prepareStatement(checkSql);
                checkStmt.setInt(1, assetId);
                ResultSet rs = checkStmt.executeQuery();

                if (rs.next()) {
                    String status = rs.getString("status");
                    Integer currentAssignee = rs.getInt("allocated_to");
                    if (rs.wasNull()) currentAssignee = null;

                    if ("allocated".equalsIgnoreCase(status) || currentAssignee != null) {
                        // CONFLICT RULE: Block direct allocation and require transfer request
                        response.setStatus(HttpServletResponse.SC_CONFLICT); // 409 Conflict
                        responseJson.addProperty("success", false);
                        responseJson.addProperty("conflict", true);
                        responseJson.addProperty("current_assignee_id", currentAssignee);
                        responseJson.addProperty("message", "Conflict Rule Violated: This asset is already allocated to another employee. Please initiate a Transfer Request instead.");
                        conn.rollback();
                        rs.close();
                        checkStmt.close();
                        out.print(gson.toJson(responseJson));
                        return;
                    }
                }
                rs.close();
                checkStmt.close();

                // 2. Insert into Allocations
                String insertSql = "INSERT INTO Allocations (asset_id, employee_id, expected_return_date, status) VALUES (?, ?, ?, 'Approved')";
                PreparedStatement insertStmt = conn.prepareStatement(insertSql);
                insertStmt.setInt(1, assetId);
                insertStmt.setInt(2, employeeId);
                insertStmt.setDate(3, Date.valueOf(returnDateStr));
                insertStmt.executeUpdate();
                insertStmt.close();

                // 3. Update Assets Table
                String updateSql = "UPDATE Assets SET status = 'Allocated', allocated_to = ? WHERE id = ?";
                PreparedStatement updateStmt = conn.prepareStatement(updateSql);
                updateStmt.setInt(1, employeeId);
                updateStmt.setInt(2, assetId);
                updateStmt.executeUpdate();
                updateStmt.close();

                conn.commit();
                responseJson.addProperty("success", true);
                responseJson.addProperty("message", "Asset allocated successfully.");

            } else if ("transfer".equals(action)) {
                int assetId = requestJson.get("asset_id").getAsInt();
                int fromEmployeeId = requestJson.get("from_employee_id").getAsInt();
                int toEmployeeId = requestJson.get("to_employee_id").getAsInt();
                String reason = requestJson.get("reason").getAsString().trim();

                String sql = "INSERT INTO Transfer_Requests (asset_id, from_employee_id, to_employee_id, reason, status) VALUES (?, ?, ?, ?, 'Pending')";
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setInt(1, assetId);
                stmt.setInt(2, fromEmployeeId);
                stmt.setInt(3, toEmployeeId);
                stmt.setString(4, reason);
                stmt.executeUpdate();
                stmt.close();

                conn.commit();
                responseJson.addProperty("success", true);
                responseJson.addProperty("message", "Transfer request submitted successfully. Awaiting approval.");

            } else if ("approve_transfer".equals(action)) {
                int transferId = requestJson.get("transfer_id").getAsInt();

                // 1. Fetch Transfer Details
                String selectSql = "SELECT asset_id, from_employee_id, to_employee_id FROM Transfer_Requests WHERE id = ? AND status = 'Pending'";
                PreparedStatement selectStmt = conn.prepareStatement(selectSql);
                selectStmt.setInt(1, transferId);
                ResultSet rs = selectStmt.executeQuery();

                if (!rs.next()) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    responseJson.addProperty("success", false);
                    responseJson.addProperty("message", "Pending transfer request not found.");
                    rs.close();
                    selectStmt.close();
                    conn.rollback();
                    out.print(gson.toJson(responseJson));
                    return;
                }

                int assetId = rs.getInt("asset_id");
                int fromEmp = rs.getInt("from_employee_id");
                int toEmp = rs.getInt("to_employee_id");
                rs.close();
                selectStmt.close();

                // 2. Approve Transfer Request
                String updateTransSql = "UPDATE Transfer_Requests SET status = 'Approved' WHERE id = ?";
                PreparedStatement updateTransStmt = conn.prepareStatement(updateTransSql);
                updateTransStmt.setInt(1, transferId);
                updateTransStmt.executeUpdate();
                updateTransStmt.close();

                // 3. Mark old allocation as Returned (Condition notes: Transferred)
                String closeAllocSql = "UPDATE Allocations SET actual_return_date = CURRENT_TIMESTAMP(), status = 'Returned', condition_notes = 'Transferred' " +
                                       "WHERE asset_id = ? AND employee_id = ? AND status = 'Approved'";
                PreparedStatement closeAllocStmt = conn.prepareStatement(closeAllocSql);
                closeAllocStmt.setInt(1, assetId);
                closeAllocStmt.setInt(2, fromEmp);
                closeAllocStmt.executeUpdate();
                closeAllocStmt.close();

                // 4. Create new Allocation (Default: 30 days return date)
                long oneMonthMs = 30L * 24 * 60 * 60 * 1000;
                Date nextReturn = new Date(System.currentTimeMillis() + oneMonthMs);
                String insertAllocSql = "INSERT INTO Allocations (asset_id, employee_id, expected_return_date, status) VALUES (?, ?, ?, 'Approved')";
                PreparedStatement insertAllocStmt = conn.prepareStatement(insertAllocSql);
                insertAllocStmt.setInt(1, assetId);
                insertAllocStmt.setInt(2, toEmp);
                insertAllocStmt.setDate(3, nextReturn);
                insertAllocStmt.executeUpdate();
                insertAllocStmt.close();

                // 5. Update Asset Owner
                String updateAssetSql = "UPDATE Assets SET allocated_to = ? WHERE id = ?";
                PreparedStatement updateAssetStmt = conn.prepareStatement(updateAssetSql);
                updateAssetStmt.setInt(1, toEmp);
                updateAssetStmt.setInt(2, assetId);
                updateAssetStmt.executeUpdate();
                updateAssetStmt.close();

                conn.commit();
                responseJson.addProperty("success", true);
                responseJson.addProperty("message", "Transfer approved successfully. Asset ownership re-allocated.");

            } else if ("return".equals(action)) {
                int allocationId = requestJson.get("allocation_id").getAsInt();
                String conditionNotes = requestJson.get("condition_notes").getAsString().trim();

                // 1. Fetch Asset ID of Allocation
                String selectSql = "SELECT asset_id FROM Allocations WHERE id = ? AND status = 'Approved'";
                PreparedStatement selectStmt = conn.prepareStatement(selectSql);
                selectStmt.setInt(1, allocationId);
                ResultSet rs = selectStmt.executeQuery();

                if (!rs.next()) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    responseJson.addProperty("success", false);
                    responseJson.addProperty("message", "Active allocation not found.");
                    rs.close();
                    selectStmt.close();
                    conn.rollback();
                    out.print(gson.toJson(responseJson));
                    return;
                }

                int assetId = rs.getInt("asset_id");
                rs.close();
                selectStmt.close();

                // 2. Update Allocation status to Returned
                String updateAllocSql = "UPDATE Allocations SET actual_return_date = CURRENT_TIMESTAMP(), status = 'Returned', condition_notes = ? WHERE id = ?";
                PreparedStatement updateAllocStmt = conn.prepareStatement(updateAllocSql);
                updateAllocStmt.setString(1, conditionNotes);
                updateAllocStmt.setInt(2, allocationId);
                updateAllocStmt.executeUpdate();
                updateAllocStmt.close();

                // 3. Mark Asset as Available (Unassigned)
                String updateAssetSql = "UPDATE Assets SET status = 'Available', allocated_to = NULL WHERE id = ?";
                PreparedStatement updateAssetStmt = conn.prepareStatement(updateAssetSql);
                updateAssetStmt.setInt(1, assetId);
                updateAssetStmt.executeUpdate();
                updateAssetStmt.close();

                conn.commit();
                responseJson.addProperty("success", true);
                responseJson.addProperty("message", "Asset returned successfully.");
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                responseJson.addProperty("success", false);
                responseJson.addProperty("message", "Invalid action parameter.");
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
}
