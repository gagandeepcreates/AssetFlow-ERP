-- SQL Schema for AssetFlowDB
-- Local MySQL (XAMPP Port 3306)

CREATE DATABASE IF NOT EXISTS AssetFlowDB;
USE AssetFlowDB;

-- 1. Departments Table
CREATE TABLE IF NOT EXISTS Departments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    parent_id INT,
    status VARCHAR(50) DEFAULT 'Active', -- 'Active', 'Inactive'
    FOREIGN KEY (parent_id) REFERENCES Departments(id) ON DELETE SET NULL
);

-- 2. Employees (Authentication & Directory) Table
CREATE TABLE IF NOT EXISTS Employees (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    role VARCHAR(50) DEFAULT 'User', -- 'User', 'Admin', 'Department Head', 'Asset Manager'
    department_id INT,
    FOREIGN KEY (department_id) REFERENCES Departments(id) ON DELETE SET NULL
);

-- 3. Asset Categories Table
CREATE TABLE IF NOT EXISTS Asset_Categories (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    warranty_period INT -- In months
);

-- 4. Assets Table
CREATE TABLE IF NOT EXISTS Assets (
    id INT AUTO_INCREMENT PRIMARY KEY,
    tag VARCHAR(50) UNIQUE NOT NULL, -- e.g. 'AF-0114'
    name VARCHAR(100) NOT NULL,
    category_id INT,
    status VARCHAR(50) DEFAULT 'Available', -- 'Available', 'Allocated', 'Maintenance', 'Retired'
    serial_number VARCHAR(100),
    expected_location VARCHAR(100),
    allocated_to INT,
    FOREIGN KEY (category_id) REFERENCES Asset_Categories(id) ON DELETE SET NULL,
    FOREIGN KEY (allocated_to) REFERENCES Employees(id) ON DELETE SET NULL
);

-- 5. Maintenance Tickets Table
CREATE TABLE IF NOT EXISTS Maintenance_Tickets (
    id INT AUTO_INCREMENT PRIMARY KEY,
    asset_id INT,
    description TEXT,
    priority VARCHAR(50) DEFAULT 'Medium', -- 'Low', 'Medium', 'High', 'Critical'
    status VARCHAR(50) DEFAULT 'Pending', -- 'Pending', 'Approved', 'In Progress', 'Resolved'
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (asset_id) REFERENCES Assets(id) ON DELETE CASCADE
);

-- 6. Transfer Requests Table
CREATE TABLE IF NOT EXISTS Transfer_Requests (
    id INT AUTO_INCREMENT PRIMARY KEY,
    asset_id INT,
    from_employee_id INT,
    to_employee_id INT,
    reason TEXT,
    status VARCHAR(50) DEFAULT 'Pending', -- 'Pending', 'Approved', 'Rejected'
    request_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (asset_id) REFERENCES Assets(id) ON DELETE CASCADE,
    FOREIGN KEY (from_employee_id) REFERENCES Employees(id) ON DELETE SET NULL,
    FOREIGN KEY (to_employee_id) REFERENCES Employees(id) ON DELETE SET NULL
);

-- 7. Allocations (History & Lifecycle) Table
CREATE TABLE IF NOT EXISTS Allocations (
    id INT AUTO_INCREMENT PRIMARY KEY,
    asset_id INT NOT NULL,
    employee_id INT NOT NULL,
    allocated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expected_return_date DATE,
    actual_return_date TIMESTAMP NULL,
    condition_notes TEXT,
    status VARCHAR(50) DEFAULT 'Approved', -- 'Approved', 'Returned'
    FOREIGN KEY (asset_id) REFERENCES Assets(id) ON DELETE CASCADE,
    FOREIGN KEY (employee_id) REFERENCES Employees(id) ON DELETE CASCADE
);

-- ==========================================
-- SEEDING SAMPLE DATA FOR TESTING
-- ==========================================

-- Seed Departments
INSERT INTO Departments (id, name, parent_id, status) VALUES
(1, 'Engineering', NULL, 'Active'),
(2, 'Facilities', NULL, 'Active'),
(3, 'Field Ops', NULL, 'Active'),
(4, 'Field Ops (East)', 3, 'Inactive')
ON DUPLICATE KEY UPDATE name=name;

-- Seed Employees (Password for all is 'password123', SHA-256 hashed)
-- Hash: 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f'
INSERT INTO Employees (id, name, email, password_hash, role, department_id) VALUES
(1, 'Aditi Rao', 'aditi.rao@organization.com', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f', 'Admin', 1),
(2, 'Rohan Mehta', 'rohan.mehta@organization.com', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f', 'Department Head', 2),
(3, 'Sana Iqbal', 'sana.iqbal@organization.com', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f', 'User', 3),
(4, 'Priya Shah', 'priya.shah@organization.com', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f', 'User', 1)
ON DUPLICATE KEY UPDATE name=name;

-- Seed Asset Categories
INSERT INTO Asset_Categories (id, name, warranty_period) VALUES
(1, 'Laptops', 36),
(2, 'Office Chairs', 12),
(3, 'Monitors', 24)
ON DUPLICATE KEY UPDATE name=name;

-- Seed Assets
INSERT INTO Assets (id, tag, name, category_id, status, serial_number, expected_location, allocated_to) VALUES
(1, 'AF-0114', 'Dell Laptop Lat 5420', 1, 'Allocated', 'SN-DELL-99218', 'Desk E12', 4),
(2, 'AF-0033', 'MacBook Pro 14', 1, 'Available', 'SN-APPLE-88310', 'IT Storage', NULL),
(3, 'AF-9921', 'Ergonomic Mesh Chair', 2, 'Allocated', 'SN-STEELCASE-11', 'Desk E14', 3),
(4, 'AF-9838', 'Dell 27-inch Monitor', 3, 'Maintenance', 'SN-DELL-33290', 'IT Lab', NULL)
ON DUPLICATE KEY UPDATE tag=tag;

-- Seed Maintenance Tickets
INSERT INTO Maintenance_Tickets (id, asset_id, description, priority, status) VALUES
(1, 4, 'Monitor power supply failure. Screen does not turn on.', 'Medium', 'Pending')
ON DUPLICATE KEY UPDATE id=id;

-- Seed Transfer Requests
INSERT INTO Transfer_Requests (id, asset_id, from_employee_id, to_employee_id, reason, status) VALUES
(1, 1, 4, 1, 'Priya moving to East Coast ops, laptop needs re-assignment.', 'Pending')
ON DUPLICATE KEY UPDATE id=id;

-- Seed Allocations (Asset 1 active allocation, Asset 3 overdue allocation)
INSERT INTO Allocations (id, asset_id, employee_id, allocated_at, expected_return_date, actual_return_date, condition_notes, status) VALUES
(1, 1, 4, '2026-07-01 10:00:00', '2026-08-01', NULL, NULL, 'Approved'),
(2, 3, 3, '2026-06-01 09:00:00', '2026-07-01', NULL, NULL, 'Approved') -- Overdue return (since expected date is 2026-07-01, and current time is July 12 2026)
ON DUPLICATE KEY UPDATE id=id;

-- 8. Bookings (Scheduler & Space Booking) Table
CREATE TABLE IF NOT EXISTS Bookings (
    id INT AUTO_INCREMENT PRIMARY KEY,
    resource_id INT NOT NULL,
    user_id INT NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    status VARCHAR(50) DEFAULT 'Approved', -- 'Approved', 'Cancelled'
    FOREIGN KEY (resource_id) REFERENCES Assets(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES Employees(id) ON DELETE CASCADE
);

-- Seed Bookings
INSERT INTO Bookings (id, resource_id, user_id, start_time, end_time, status) VALUES
(1, 2, 3, '2026-07-12 09:00:00', '2026-07-12 10:00:00', 'Approved')
ON DUPLICATE KEY UPDATE id=id;
