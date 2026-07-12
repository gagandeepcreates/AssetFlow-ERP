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
import java.sql.Timestamp;

/**
 * Servlet handling resource reservations and scheduler overlap validations.
 * Mapped to the "/bookings" endpoint.
 * Compatible with Java EE 8 / javax.servlet namespace (Tomcat 9).
 */
@WebServlet("/bookings")
public class BookingServlet extends HttpServlet {
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
     * GET: Fetch bookings for a specific resource.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        setCorsHeaders(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();
        JsonArray bookingArray = new JsonArray();

        String resourceIdStr = request.getParameter("resource_id");
        if (resourceIdStr == null || resourceIdStr.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("success", false);
            error.addProperty("message", "Parameter 'resource_id' is required.");
            out.print(gson.toJson(error));
            return;
        }

        int resourceId = Integer.parseInt(resourceIdStr);
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnection.getInstance().getConnection();
            String sql = "SELECT b.id, b.resource_id, b.user_id, emp.name AS user_name, b.start_time, b.end_time, b.status " +
                         "FROM Bookings b " +
                         "JOIN Employees emp ON b.user_id = emp.id " +
                         "WHERE b.resource_id = ? AND b.status = 'Approved' " +
                         "ORDER BY b.start_time ASC";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, resourceId);
            rs = stmt.executeQuery();

            while (rs.next()) {
                JsonObject booking = new JsonObject();
                booking.addProperty("id", rs.getInt("id"));
                booking.addProperty("resource_id", rs.getInt("resource_id"));
                booking.addProperty("user_id", rs.getInt("user_id"));
                booking.addProperty("user_name", rs.getString("user_name"));
                booking.addProperty("start_time", rs.getTimestamp("start_time").toString());
                booking.addProperty("end_time", rs.getTimestamp("end_time").toString());
                booking.addProperty("status", rs.getString("status"));
                bookingArray.add(booking);
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

        out.print(gson.toJson(bookingArray));
        out.flush();
    }

    /**
     * POST: Make a new resource booking with strict overlap conflict checks.
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

        Integer resourceId = null;
        Integer userId = null;
        String startTimeStr = "";
        String endTimeStr = "";

        try {
            JsonObject requestJson = gson.fromJson(jsonBody, JsonObject.class);
            if (requestJson.has("resource_id")) resourceId = requestJson.get("resource_id").getAsInt();
            if (requestJson.has("user_id")) userId = requestJson.get("user_id").getAsInt();
            if (requestJson.has("start_time")) startTimeStr = requestJson.get("start_time").getAsString();
            if (requestJson.has("end_time")) endTimeStr = requestJson.get("end_time").getAsString();
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Malformed JSON request.");
            out.print(gson.toJson(responseJson));
            return;
        }

        if (resourceId == null || userId == null || startTimeStr.isEmpty() || endTimeStr.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Resource ID, User ID, start, and end times are required.");
            out.print(gson.toJson(responseJson));
            return;
        }

        Timestamp reqStart = Timestamp.valueOf(startTimeStr.replace('T', ' '));
        Timestamp reqEnd = Timestamp.valueOf(endTimeStr.replace('T', ' '));

        if (reqEnd.before(reqStart) || reqEnd.equals(reqStart)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Booking end time must be after start time.");
            out.print(gson.toJson(responseJson));
            return;
        }

        Connection conn = null;
        PreparedStatement checkStmt = null;
        PreparedStatement insertStmt = null;
        ResultSet checkRs = null;

        try {
            conn = DatabaseConnection.getInstance().getConnection();
            conn.setAutoCommit(false);

            // OVERLAP VALIDATION: Checks if resource is already booked during the requested slot
            String checkSql = "SELECT COUNT(*) FROM Bookings WHERE resource_id = ? AND status = 'Approved' " +
                              "AND (? < end_time AND ? > start_time)";
            checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setInt(1, resourceId);
            checkStmt.setTimestamp(2, reqStart);
            checkStmt.setTimestamp(3, reqEnd);
            checkRs = checkStmt.executeQuery();

            if (checkRs.next() && checkRs.getInt(1) > 0) {
                // Conflict rule violated!
                response.setStatus(HttpServletResponse.SC_CONFLICT); // 409 Conflict
                responseJson.addProperty("success", false);
                responseJson.addProperty("overlap", true);
                responseJson.addProperty("message", "Overlap Conflict: The selected slot overlaps with an existing booking.");
                conn.rollback();
                out.print(gson.toJson(responseJson));
                return;
            }

            // Confirm booking
            String insertSql = "INSERT INTO Bookings (resource_id, user_id, start_time, end_time, status) VALUES (?, ?, ?, ?, 'Approved')";
            insertStmt = conn.prepareStatement(insertSql);
            insertStmt.setInt(1, resourceId);
            insertStmt.setInt(2, userId);
            insertStmt.setTimestamp(3, reqStart);
            insertStmt.setTimestamp(4, reqEnd);
            insertStmt.executeUpdate();

            conn.commit();
            responseJson.addProperty("success", true);
            responseJson.addProperty("message", "Booking confirmed successfully.");

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Database error during booking: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try { if (checkRs != null) checkRs.close(); } catch (SQLException e) { e.printStackTrace(); }
            try { if (checkStmt != null) checkStmt.close(); } catch (SQLException e) { e.printStackTrace(); }
            try { if (insertStmt != null) insertStmt.close(); } catch (SQLException e) { e.printStackTrace(); }
            if (conn != null) {
                try { conn.setAutoCommit(true); } catch (SQLException ex) { ex.printStackTrace(); }
            }
        }

        out.print(gson.toJson(responseJson));
        out.flush();
    }
}
