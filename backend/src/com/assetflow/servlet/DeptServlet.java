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
 * Servlet managing Department CRUD operations.
 * Mapped to the "/departments" endpoint.
 * Compatible with Java EE 8 / javax.servlet namespace (Tomcat 9).
 */
@WebServlet("/departments")
public class DeptServlet extends HttpServlet {
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
     * GET: Retrieve all departments.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        setCorsHeaders(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();
        JsonArray deptArray = new JsonArray();

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnection.getInstance().getConnection();
            String sql = "SELECT id, name, parent_id, status FROM Departments ORDER BY name ASC";
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();

            while (rs.next()) {
                JsonObject deptObj = new JsonObject();
                deptObj.addProperty("id", rs.getInt("id"));
                deptObj.addProperty("name", rs.getString("name"));
                int parentId = rs.getInt("parent_id");
                if (rs.wasNull()) {
                    deptObj.add("parent_id", null);
                } else {
                    deptObj.addProperty("parent_id", parentId);
                }
                deptObj.addProperty("status", rs.getString("status"));
                deptArray.add(deptObj);
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

        out.print(gson.toJson(deptArray));
        out.flush();
    }

    /**
     * POST: Create or Update a department.
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

        String name = "";
        Integer parentId = null;
        String status = "Active";
        Integer id = null;

        try {
            JsonObject requestJson = gson.fromJson(jsonBody, JsonObject.class);
            if (requestJson.has("name")) {
                name = requestJson.get("name").getAsString().trim();
            }
            if (requestJson.has("parent_id") && !requestJson.get("parent_id").isJsonNull()) {
                parentId = requestJson.get("parent_id").getAsInt();
            }
            if (requestJson.has("status")) {
                status = requestJson.get("status").getAsString().trim();
            }
            if (requestJson.has("id")) {
                id = requestJson.get("id").getAsInt();
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Malformed JSON request.");
            out.print(gson.toJson(responseJson));
            return;
        }

        if (name.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Department name is required.");
            out.print(gson.toJson(responseJson));
            return;
        }

        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = DatabaseConnection.getInstance().getConnection();
            if (id != null) {
                // Update existing department
                String sql = "UPDATE Departments SET name = ?, parent_id = ?, status = ? WHERE id = ?";
                stmt = conn.prepareStatement(sql);
                stmt.setString(1, name);
                if (parentId != null) {
                    stmt.setInt(2, parentId);
                } else {
                    stmt.setNull(2, Types.INTEGER);
                }
                stmt.setString(3, status);
                stmt.setInt(4, id);
                
                int rowsUpdated = stmt.executeUpdate();
                if (rowsUpdated > 0) {
                    responseJson.addProperty("success", true);
                    responseJson.addProperty("message", "Department updated successfully.");
                } else {
                    responseJson.addProperty("success", false);
                    responseJson.addProperty("message", "Department not found.");
                }
            } else {
                // Create new department
                String sql = "INSERT INTO Departments (name, parent_id, status) VALUES (?, ?, ?)";
                stmt = conn.prepareStatement(sql);
                stmt.setString(1, name);
                if (parentId != null) {
                    stmt.setInt(2, parentId);
                } else {
                    stmt.setNull(2, Types.INTEGER);
                }
                stmt.setString(3, status);
                
                int rowsInserted = stmt.executeUpdate();
                if (rowsInserted > 0) {
                    responseJson.addProperty("success", true);
                    responseJson.addProperty("message", "Department created successfully.");
                } else {
                    responseJson.addProperty("success", false);
                    responseJson.addProperty("message", "Failed to create department.");
                }
            }
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Database error: " + e.getMessage());
        } finally {
            try { if (stmt != null) stmt.close(); } catch (SQLException e) { e.printStackTrace(); }
        }

        out.print(gson.toJson(responseJson));
        out.flush();
    }
}
