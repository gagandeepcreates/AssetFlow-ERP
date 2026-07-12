package com.assetflow.servlet;

import com.assetflow.util.DatabaseConnection;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Servlet providing home dashboard metric counters.
 * Mapped to the "/dashboard" endpoint.
 * Compatible with Java EE 8 / javax.servlet namespace (Tomcat 9).
 */
@WebServlet("/dashboard")
public class DashboardServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    /**
     * Set standard CORS headers for cross-origin resource sharing.
     */
    private void setCorsHeaders(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:5173");
        response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
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
     * Handles Dashboard statistics request.
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

            // Single query executing subqueries to fetch counts for ERP components
            String sql = "SELECT " +
                    "(SELECT COUNT(*) FROM Assets WHERE status = 'Available') AS available, " +
                    "(SELECT COUNT(*) FROM Assets WHERE status = 'Allocated') AS allocated, " +
                    "(SELECT COUNT(*) FROM Maintenance_Tickets WHERE status IN ('Pending', 'Approved', 'In Progress')) AS maintenance, " +
                    "(SELECT COUNT(*) FROM Transfer_Requests WHERE status = 'Pending') AS transfers, " +
                    "(SELECT COUNT(*) FROM Allocations WHERE status = 'Approved' AND expected_return_date < CURRENT_DATE() AND actual_return_date IS NULL) AS overdue";

            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();

            if (rs.next()) {
                responseJson.addProperty("success", true);
                responseJson.addProperty("available", rs.getInt("available"));
                responseJson.addProperty("allocated", rs.getInt("allocated"));
                responseJson.addProperty("maintenance", rs.getInt("maintenance"));
                responseJson.addProperty("transfers", rs.getInt("transfers"));
                responseJson.addProperty("overdue", rs.getInt("overdue"));
            } else {
                responseJson.addProperty("success", false);
                responseJson.addProperty("message", "Failed to retrieve metrics.");
            }

        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            responseJson.addProperty("success", false);
            responseJson.addProperty("message", "Database error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException e) { e.printStackTrace(); }
            try { if (stmt != null) stmt.close(); } catch (SQLException e) { e.printStackTrace(); }
        }

        out.print(gson.toJson(responseJson));
        out.flush();
    }
}
