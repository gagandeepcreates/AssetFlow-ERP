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

/**
 * Servlet handling user authentication.
 * Mapped to the "/login" endpoint.
 * Compatibile with Java EE 8 / javax.servlet namespace (Tomcat 9).
 */
@WebServlet("/login")
public class AuthServlet extends HttpServlet {
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
     * Handles user login POST requests.
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
        String email = "";
        String password = "";
        try {
            JsonObject requestJson = gson.fromJson(jsonBody, JsonObject.class);
            if (requestJson.has("email") && requestJson.has("password")) {
                email = requestJson.get("email").getAsString().trim();
                password = requestJson.get("password").getAsString();
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Malformed JSON request.");
            out.print(gson.toJson(responseJson));
            return;
        }

        if (email.isEmpty() || password.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Email and Password are required.");
            out.print(gson.toJson(responseJson));
            return;
        }

        // 3. Query Database connection to validate credentials
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnection.getInstance().getConnection();
            if (conn == null) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                responseJson.addProperty("success", false);
                responseJson.addProperty("message", "Database connection unavailable.");
                out.print(gson.toJson(responseJson));
                return;
            }

            String sql = "SELECT name, role, password_hash FROM Employees WHERE email = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, email);
            rs = stmt.executeQuery();

            if (rs.next()) {
                String name = rs.getString("name");
                String role = rs.getString("role");
                String dbPasswordHash = rs.getString("password_hash");

                // Verify input password against password hash
                if (verifyPassword(password, dbPasswordHash)) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    responseJson.addProperty("success", true);
                    responseJson.addProperty("message", "Login successful.");
                    responseJson.addProperty("name", name);
                    responseJson.addProperty("role", role);
                } else {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    responseJson.addProperty("success", false);
                    responseJson.addProperty("message", "Invalid email or password.");
                }
            } else {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                responseJson.addProperty("success", false);
                responseJson.addProperty("message", "Invalid email or password.");
            }

        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Database query error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Clean resources
            try { if (rs != null) rs.close(); } catch (SQLException e) { e.printStackTrace(); }
            try { if (stmt != null) stmt.close(); } catch (SQLException e) { e.printStackTrace(); }
        }

        out.print(gson.toJson(responseJson));
        out.flush();
    }

    /**
     * Verify the plain password against the hashed string in the database.
     * Note: Change the logic below to bcrypt (e.g. BCrypt.checkpw) for production.
     */
    private boolean verifyPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null) {
            return false;
        }
        
        // Check for SHA-256 hex hash matches
        String hashedInput = sha256Hex(plainPassword);
        return hashedInput.equalsIgnoreCase(hashedPassword) || plainPassword.equals(hashedPassword);
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
