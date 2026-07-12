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
 * Servlet managing Asset Category CRUD operations.
 * Mapped to the "/categories" endpoint.
 * Compatible with Java EE 8 / javax.servlet namespace (Tomcat 9).
 */
@WebServlet("/categories")
public class CategoryServlet extends HttpServlet {
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
     * GET: Retrieve all asset categories.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        setCorsHeaders(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();
        JsonArray catArray = new JsonArray();

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnection.getInstance().getConnection();
            String sql = "SELECT id, name, warranty_period FROM Asset_Categories ORDER BY name ASC";
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();

            while (rs.next()) {
                JsonObject catObj = new JsonObject();
                catObj.addProperty("id", rs.getInt("id"));
                catObj.addProperty("name", rs.getString("name"));
                int warranty = rs.getInt("warranty_period");
                if (rs.wasNull()) {
                    catObj.add("warranty_period", null);
                } else {
                    catObj.addProperty("warranty_period", warranty);
                }
                catArray.add(catObj);
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

        out.print(gson.toJson(catArray));
        out.flush();
    }

    /**
     * POST: Create or Update an asset category.
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
        Integer warrantyPeriod = null;
        Integer id = null;

        try {
            JsonObject requestJson = gson.fromJson(jsonBody, JsonObject.class);
            if (requestJson.has("name")) {
                name = requestJson.get("name").getAsString().trim();
            }
            if (requestJson.has("warranty_period") && !requestJson.get("warranty_period").isJsonNull()) {
                warrantyPeriod = requestJson.get("warranty_period").getAsInt();
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
            responseJson.addProperty("message", "Category name is required.");
            out.print(gson.toJson(responseJson));
            return;
        }

        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = DatabaseConnection.getInstance().getConnection();
            if (id != null) {
                // Update category
                String sql = "UPDATE Asset_Categories SET name = ?, warranty_period = ? WHERE id = ?";
                stmt = conn.prepareStatement(sql);
                stmt.setString(1, name);
                if (warrantyPeriod != null) {
                    stmt.setInt(2, warrantyPeriod);
                } else {
                    stmt.setNull(2, Types.INTEGER);
                }
                stmt.setInt(3, id);
                
                int rowsUpdated = stmt.executeUpdate();
                if (rowsUpdated > 0) {
                    responseJson.addProperty("success", true);
                    responseJson.addProperty("message", "Asset category updated successfully.");
                } else {
                    responseJson.addProperty("success", false);
                    responseJson.addProperty("message", "Asset category not found.");
                }
            } else {
                // Create category
                String sql = "INSERT INTO Asset_Categories (name, warranty_period) VALUES (?, ?)";
                stmt = conn.prepareStatement(sql);
                stmt.setString(1, name);
                if (warrantyPeriod != null) {
                    stmt.setInt(2, warrantyPeriod);
                } else {
                    stmt.setNull(2, Types.INTEGER);
                }
                
                int rowsInserted = stmt.executeUpdate();
                if (rowsInserted > 0) {
                    responseJson.addProperty("success", true);
                    responseJson.addProperty("message", "Asset category created successfully.");
                } else {
                    responseJson.addProperty("success", false);
                    responseJson.addProperty("message", "Failed to create category.");
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
