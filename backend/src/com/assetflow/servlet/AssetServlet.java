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
import java.sql.Types;

/**
 * Servlet managing Asset Registry operations.
 * Mapped to the "/assets" endpoint.
 * Compatible with Java EE 8 / javax.servlet namespace (Tomcat 9).
 */
@WebServlet("/assets")
public class AssetServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    private void setCorsHeaders(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:5173");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE");
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
     * GET: Retrieve all assets, joined with Category and Employee names.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        setCorsHeaders(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();
        JsonArray assetArray = new JsonArray();

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnection.getInstance().getConnection();
            String sql = "SELECT a.id, a.tag, a.name, a.serial_number, a.status, a.expected_location, " +
                         "a.category_id, c.name AS category_name, a.allocated_to, e.name AS allocated_employee_name " +
                         "FROM Assets a " +
                         "LEFT JOIN Asset_Categories c ON a.category_id = c.id " +
                         "LEFT JOIN Employees e ON a.allocated_to = e.id " +
                         "ORDER BY a.id DESC";
            
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();

            while (rs.next()) {
                JsonObject assetObj = new JsonObject();
                assetObj.addProperty("id", rs.getInt("id"));
                assetObj.addProperty("tag", rs.getString("tag"));
                assetObj.addProperty("name", rs.getString("name"));
                assetObj.addProperty("serial_number", rs.getString("serial_number"));
                assetObj.addProperty("status", rs.getString("status"));
                assetObj.addProperty("expected_location", rs.getString("expected_location"));
                
                int catId = rs.getInt("category_id");
                if (rs.wasNull()) {
                    assetObj.add("category_id", null);
                    assetObj.addProperty("category_name", "Uncategorized");
                } else {
                    assetObj.addProperty("category_id", catId);
                    assetObj.addProperty("category_name", rs.getString("category_name"));
                }
                
                int empId = rs.getInt("allocated_to");
                if (rs.wasNull()) {
                    assetObj.add("allocated_to", null);
                    assetObj.addProperty("allocated_employee_name", "Unassigned");
                } else {
                    assetObj.addProperty("allocated_to", empId);
                    assetObj.addProperty("allocated_employee_name", rs.getString("allocated_employee_name"));
                }
                
                assetArray.add(assetObj);
            }
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("success", false);
            error.addProperty("message", "Database error: " + e.getMessage());
            out.print(gson.toJson(error));
            return;
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException e) { e.printStackTrace(); }
            try { if (stmt != null) stmt.close(); } catch (SQLException e) { e.printStackTrace(); }
        }

        out.print(gson.toJson(assetArray));
        out.flush();
    }

    /**
     * POST: Register a new asset (or update if ID is provided).
     * Auto-generates tag if a new registration.
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

        Integer id = null;
        String name = "";
        Integer categoryId = null;
        String serialNumber = "";
        String status = "Available";
        String expectedLocation = "";

        try {
            JsonObject requestJson = gson.fromJson(jsonBody, JsonObject.class);
            if (requestJson.has("id")) {
                id = requestJson.get("id").getAsInt();
            }
            if (requestJson.has("name")) {
                name = requestJson.get("name").getAsString().trim();
            }
            if (requestJson.has("category_id") && !requestJson.get("category_id").isJsonNull()) {
                categoryId = requestJson.get("category_id").getAsInt();
            }
            if (requestJson.has("serial_number")) {
                serialNumber = requestJson.get("serial_number").getAsString().trim();
            }
            if (requestJson.has("status")) {
                status = requestJson.get("status").getAsString().trim();
            }
            if (requestJson.has("expected_location")) {
                expectedLocation = requestJson.get("expected_location").getAsString().trim();
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Malformed JSON request.");
            out.print(gson.toJson(responseJson));
            return;
        }

        if (name.isEmpty() || status.isEmpty() || expectedLocation.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Asset name, status, and location are required.");
            out.print(gson.toJson(responseJson));
            return;
        }

        Connection conn = null;
        PreparedStatement maxIdStmt = null;
        PreparedStatement insertStmt = null;
        PreparedStatement updateStmt = null;
        ResultSet maxIdRs = null;

        try {
            conn = DatabaseConnection.getInstance().getConnection();
            if (id != null) {
                // Update asset details
                String sql = "UPDATE Assets SET name = ?, category_id = ?, serial_number = ?, status = ?, expected_location = ? WHERE id = ?";
                updateStmt = conn.prepareStatement(sql);
                updateStmt.setString(1, name);
                if (categoryId != null) {
                    updateStmt.setInt(2, categoryId);
                } else {
                    updateStmt.setNull(2, Types.INTEGER);
                }
                updateStmt.setString(3, serialNumber);
                updateStmt.setString(4, status);
                updateStmt.setString(5, expectedLocation);
                updateStmt.setInt(6, id);

                int rows = updateStmt.executeUpdate();
                if (rows > 0) {
                    responseJson.addProperty("success", true);
                    responseJson.addProperty("message", "Asset updated successfully.");
                } else {
                    responseJson.addProperty("success", false);
                    responseJson.addProperty("message", "Asset not found.");
                }
            } else {
                // Get next available ID for Tag auto-generation
                String maxIdSql = "SELECT COALESCE(MAX(id), 0) + 1 AS next_id FROM Assets";
                maxIdStmt = conn.prepareStatement(maxIdSql);
                maxIdRs = maxIdStmt.executeQuery();
                int nextId = 1;
                if (maxIdRs.next()) {
                    nextId = maxIdRs.getInt("next_id");
                }
                
                // Format Asset Tag: e.g., AF-0001, AF-0002
                String autoTag = String.format("AF-%04d", nextId);

                // Insert asset
                String sql = "INSERT INTO Assets (tag, name, category_id, serial_number, status, expected_location) VALUES (?, ?, ?, ?, ?, ?)";
                insertStmt = conn.prepareStatement(sql);
                insertStmt.setString(1, autoTag);
                insertStmt.setString(2, name);
                if (categoryId != null) {
                    insertStmt.setInt(3, categoryId);
                } else {
                    insertStmt.setNull(3, Types.INTEGER);
                }
                insertStmt.setString(4, serialNumber);
                insertStmt.setString(5, status);
                insertStmt.setString(6, expectedLocation);

                int rows = insertStmt.executeUpdate();
                if (rows > 0) {
                    responseJson.addProperty("success", true);
                    responseJson.addProperty("message", "Asset registered successfully.");
                    responseJson.addProperty("tag", autoTag);
                } else {
                    responseJson.addProperty("success", false);
                    responseJson.addProperty("message", "Failed to register asset.");
                }
            }
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Database error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try { if (maxIdRs != null) maxIdRs.close(); } catch (SQLException e) { e.printStackTrace(); }
            try { if (maxIdStmt != null) maxIdStmt.close(); } catch (SQLException e) { e.printStackTrace(); }
            try { if (insertStmt != null) insertStmt.close(); } catch (SQLException e) { e.printStackTrace(); }
            try { if (updateStmt != null) updateStmt.close(); } catch (SQLException e) { e.printStackTrace(); }
        }

        out.print(gson.toJson(responseJson));
        out.flush();
    }
}
