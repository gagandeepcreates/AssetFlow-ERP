package com.assetflow.servlet;

import com.assetflow.util.DatabaseConnection;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Servlet handling user registration.
 * Mapped to the "/register" endpoint.
 * Compatible with Java EE 8 / javax.servlet namespace (Tomcat 9).
 */
@WebServlet("/register")
public class RegisterServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    /**
     * Set standard CORS headers for cross-origin resource sharing.
     */
    private void setCorsHeaders(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:5173");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, PUT, DELETE");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Accept, Authorization");
        response.setHeader("Access-Control-Allow-Credentials", "true");
    }

    /**
     * Handles CORS preflight requests (OPTIONS).
     */
    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * Handles user registration POST requests.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        setCorsHeaders(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();
        JsonObject responseJson = new JsonObject();

        // 1. Read raw JSON payload from request reader
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

        // 2. Parse request JSON parameters using Gson
        String name = "";
        String email = "";
        String password = "";
        String departmentName = "";
        try {
            JsonObject requestJson = gson.fromJson(jsonBody, JsonObject.class);
            if (requestJson.has("name") && requestJson.has("email") && requestJson.has("password") && requestJson.has("department")) {
                name = requestJson.get("name").getAsString().trim();
                email = requestJson.get("email").getAsString().trim();
                password = requestJson.get("password").getAsString();
                departmentName = requestJson.get("department").getAsString().trim();
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Malformed JSON request.");
            out.print(gson.toJson(responseJson));
            return;
        }

        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || departmentName.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "All registration fields are required.");
            out.print(gson.toJson(responseJson));
            return;
        }

        // 3. Insert user into the Database (Employees table)
        Connection conn = null;
        PreparedStatement checkStmt = null;
        PreparedStatement deptStmt = null;
        PreparedStatement insertStmt = null;
        ResultSet rs = null;
        ResultSet deptRs = null;

        try {
            conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                responseJson.addProperty("success", false);
                responseJson.addProperty("message", "Database connection unavailable.");
                out.print(gson.toJson(responseJson));
                return;
            }

            // Server-side validation: Check if email already exists in Employees table
            String checkSql = "SELECT COUNT(*) FROM Employees WHERE email = ?";
            checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setString(1, email);
            rs = checkStmt.executeQuery();

            if (rs.next() && rs.getInt(1) > 0) {
                response.setStatus(HttpServletResponse.SC_CONFLICT); // 409 Conflict
                responseJson.addProperty("success", false);
                responseJson.addProperty("message", "Email address is already registered.");
                out.print(gson.toJson(responseJson));
                return;
            }

            // Resolve Department ID from Department Name
            Integer departmentId = null;
            String deptSql = "SELECT id FROM Departments WHERE name = ?";
            deptStmt = conn.prepareStatement(deptSql);
            deptStmt.setString(1, departmentName);
            deptRs = deptStmt.executeQuery();
            if (deptRs.next()) {
                departmentId = deptRs.getInt("id");
            }

            // Hash the password
            // BCRYPT LOGIC PLACEMENT:
            // String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
            String hashedPassword = sha256Hex(password); // Fallback: SHA-256 hashed

            // Insert new employee record
            String insertSql = "INSERT INTO Employees (name, email, password_hash, role, department_id) VALUES (?, ?, ?, ?, ?)";
            insertStmt = conn.prepareStatement(insertSql);
            insertStmt.setString(1, name);
            insertStmt.setString(2, email);
            insertStmt.setString(3, hashedPassword);
            insertStmt.setString(4, "User"); // Set default role to 'User'
            if (departmentId != null) {
                insertStmt.setInt(5, departmentId);
            } else {
                insertStmt.setNull(5, Types.INTEGER);
            }

            int rowsInserted = insertStmt.executeUpdate();
            if (rowsInserted > 0) {
                response.setStatus(HttpServletResponse.SC_CREATED); // 201 Created
                responseJson.addProperty("success", true);
                responseJson.addProperty("message", "User registered successfully.");
            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                responseJson.addProperty("success", false);
                responseJson.addProperty("message", "Failed to create user account.");
            }

        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Database error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Clean resources
            try { if (rs != null) rs.close(); } catch (SQLException e) { e.printStackTrace(); }
            try { if (deptRs != null) deptRs.close(); } catch (SQLException e) { e.printStackTrace(); }
            try { if (checkStmt != null) checkStmt.close(); } catch (SQLException e) { e.printStackTrace(); }
            try { if (deptStmt != null) deptStmt.close(); } catch (SQLException e) { e.printStackTrace(); }
            try { if (insertStmt != null) insertStmt.close(); } catch (SQLException e) { e.printStackTrace(); }
        }

        out.print(gson.toJson(responseJson));
        out.flush();
    }

    /**
     * Computes the SHA-256 hash of a string in hexadecimal format.
     */
    private String sha256Hex(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException("SHA-256 algorithm not supported", ex);
        }
    }
}
