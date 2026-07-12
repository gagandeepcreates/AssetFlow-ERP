package com.assetflow.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Singleton class to manage database connections for the AssetFlow ERP application.
 * Establishes connection to the MySQL database hosted locally.
 */
public class DatabaseConnection {
    
    // Static instance of the singleton
    private static DatabaseConnection instance = null;
    
    // Connection object
    private Connection connection = null;

    // Database connection credentials
    private static final String URL = "jdbc:mysql://localhost:3306/AssetFlowDB?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String USER = "root";
    private static final String PASSWORD = "";

    /**
     * Private constructor to prevent instantiation.
     * Loads the JDBC driver and attempts to connect.
     */
    private DatabaseConnection() {
        try {
            // Load MySQL JDBC driver class
            Class.forName("com.mysql.cj.jdbc.Driver");
            
            // Establish the connection
            this.connection = DriverManager.getConnection(URL, USER, PASSWORD);
            System.out.println("Database Connection established successfully to AssetFlowDB.");
        } catch (ClassNotFoundException e) {
            System.err.println("JDBC Driver class not found: com.mysql.cj.jdbc.Driver");
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("Database connection failed. Verify URL, credentials, or MySQL status.");
            System.err.println("Target URL: " + URL);
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the single thread-safe instance of DatabaseConnection.
     * Re-initializes connection if the existing connection is null or closed.
     *
     * @return The DatabaseConnection singleton instance
     */
    public static synchronized DatabaseConnection getInstance() {
        try {
            if (instance == null || instance.getConnection() == null || instance.getConnection().isClosed()) {
                instance = new DatabaseConnection();
            }
        } catch (SQLException e) {
            System.err.println("Error verifying database connection state. Re-creating connection.");
            e.printStackTrace();
            instance = new DatabaseConnection();
        }
        return instance;
    }

    /**
     * Gets the active Connection object.
     *
     * @return Connection object
     */
    public Connection getConnection() {
        return this.connection;
    }
}
