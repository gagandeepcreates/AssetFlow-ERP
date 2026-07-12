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
 * Servlet managing the Employee Directory and role assignments.
 * Mapped to the "/employees" endpoint.
 * Compatible with Java EE 8 / javax.servlet namespace (Tomcat 9).
 */
@WebServlet("/employees")
public class EmployeeServlet extends HttpServlet {
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
     * GET: List employees along with their resolved department names.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        setCorsHeaders(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();
        JsonArray empArray = new JsonArray();

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnection.getInstance().getConnection();
            String sql = "SELECT e.id, e.name, e.email, e.role, e.department_id, d.name AS department_name " +
                         "FROM Employees e LEFT JOIN Departments d ON e.department_id = d.id " +
                         "ORDER BY e.name ASC";
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();

            while (rs.next()) {
                JsonObject empObj = new JsonObject();
                empObj.addProperty("id", rs.getInt("id"));
                empObj.addProperty("name", rs.getString("name"));
                empObj.addProperty("email", rs.getString("email"));
                empObj.addProperty("role", rs.getString("role"));
                
                int deptId = rs.getInt("department_id");
                if (rs.wasNull()) {
                    empObj.add("department_id", null);
                    empObj.addProperty("department_name", "Unassigned");
                } else {
                    empObj.addProperty("department_id", deptId);
                    empObj.addProperty("department_name", rs.getString("department_name"));
                }
                empArray.add(empObj);
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

        out.print(gson.toJson(empArray));
        out.flush();
    }

    /**
     * POST: Promote/change roles for existing employees.
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

        Integer employeeId = null;
        String newRole = "";

        try {
            JsonObject requestJson = gson.fromJson(jsonBody, JsonObject.class);
            if (requestJson.has("id")) {
                employeeId = requestJson.get("id").getAsInt();
            }
            if (requestJson.has("role")) {
                newRole = requestJson.get("role").getAsString().trim();
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Malformed JSON request.");
            out.print(gson.toJson(responseJson));
            return;
        }

        if (employeeId == null || newRole.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Employee ID and new Role parameters are required.");
            out.print(gson.toJson(responseJson));
            return;
        }

        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = DatabaseConnection.getInstance().getConnection();
            String sql = "UPDATE Employees SET role = ? WHERE id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, newRole);
            stmt.setInt(2, employeeId);

            int rowsUpdated = stmt.executeUpdate();
            if (rowsUpdated > 0) {
                responseJson.addProperty("success", true);
                responseJson.addProperty("message", "Employee role promoted successfully.");
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                responseJson.addProperty("success", false);
                responseJson.addProperty("message", "Employee not found.");
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
