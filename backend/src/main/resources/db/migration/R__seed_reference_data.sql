-- ============================================================================
-- R__seed_reference_data.sql (Repeatable Migration)
-- Brilliant Seas Shipping Corporation
--
-- Flyway repeatable migration — re-runs when checksum changes
-- Seeds: roles, permissions, role_permissions, ports, routes, fare_classes
-- Uses ON CONFLICT for idempotency
-- ============================================================================

-- ============================================================================
-- ROLES
-- ============================================================================
INSERT INTO roles (role_name, description) VALUES
    ('PASSENGER',    'Registered passenger — can book tickets and view own bookings'),
    ('CARGO_CLIENT', 'Cargo shipper — can create and track cargo bookings'),
    ('STAFF',        'Operations staff — manages voyages, manifests, and bookings'),
    ('ADMIN',        'System administrator — full access, requires MFA'),
    ('SEAFARER',     'Vessel crew member — views assignments and certifications'),
    ('SUPERADMIN',   'Super administrator — role management, system configuration')
ON CONFLICT (role_name) DO UPDATE SET description = EXCLUDED.description;

-- ============================================================================
-- PERMISSIONS
-- ============================================================================
INSERT INTO permissions (resource, action, description) VALUES
    -- Booking permissions
    ('BOOKING', 'CREATE',  'Create new passenger bookings'),
    ('BOOKING', 'READ',    'View booking details'),
    ('BOOKING', 'UPDATE',  'Update booking status'),
    ('BOOKING', 'DELETE',  'Cancel bookings'),
    ('BOOKING', 'EXPORT',  'Export booking reports'),
    -- Voyage permissions
    ('VOYAGE',  'CREATE',  'Create new voyages'),
    ('VOYAGE',  'READ',    'View voyage schedules'),
    ('VOYAGE',  'UPDATE',  'Update voyage status and details'),
    ('VOYAGE',  'DELETE',  'Cancel voyages'),
    -- Cargo permissions
    ('CARGO',   'CREATE',  'Create cargo bookings'),
    ('CARGO',   'READ',    'View cargo booking details'),
    ('CARGO',   'UPDATE',  'Update cargo status'),
    ('CARGO',   'EXPORT',  'Export cargo reports'),
    -- User management
    ('USER',    'CREATE',  'Create user accounts'),
    ('USER',    'READ',    'View user profiles'),
    ('USER',    'UPDATE',  'Update user accounts'),
    ('USER',    'DELETE',  'Deactivate user accounts'),
    -- Reports
    ('REPORT',  'READ',    'View operational reports'),
    ('REPORT',  'EXPORT',  'Export reports to file'),
    -- Content
    ('CONTENT', 'CREATE',  'Create articles and advisories'),
    ('CONTENT', 'READ',    'View content'),
    ('CONTENT', 'UPDATE',  'Update articles and advisories'),
    ('CONTENT', 'DELETE',  'Delete content'),
    -- Audit
    ('AUDIT',   'READ',    'View audit logs'),
    -- Privacy (RA 10173)
    ('PRIVACY', 'READ',    'View own personal data'),
    ('PRIVACY', 'EXPORT',  'Export own personal data'),
    ('PRIVACY', 'DELETE',  'Request data erasure')
ON CONFLICT (resource, action) DO UPDATE SET description = EXCLUDED.description;

-- ============================================================================
-- ROLE → PERMISSION MAPPING
-- ============================================================================

-- Helper: map role to permissions
-- PASSENGER: book, read own, privacy rights
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r, permissions p
WHERE r.role_name = 'PASSENGER'
  AND (
    (p.resource = 'BOOKING' AND p.action IN ('CREATE','READ'))
    OR (p.resource = 'VOYAGE' AND p.action = 'READ')
    OR (p.resource = 'CONTENT' AND p.action = 'READ')
    OR (p.resource = 'PRIVACY' AND p.action IN ('READ','EXPORT','DELETE'))
  )
ON CONFLICT DO NOTHING;

-- CARGO_CLIENT: cargo operations + privacy
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r, permissions p
WHERE r.role_name = 'CARGO_CLIENT'
  AND (
    (p.resource = 'CARGO' AND p.action IN ('CREATE','READ'))
    OR (p.resource = 'VOYAGE' AND p.action = 'READ')
    OR (p.resource = 'CONTENT' AND p.action = 'READ')
    OR (p.resource = 'PRIVACY' AND p.action IN ('READ','EXPORT','DELETE'))
  )
ON CONFLICT DO NOTHING;

-- STAFF: operational access
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r, permissions p
WHERE r.role_name = 'STAFF'
  AND (
    (p.resource = 'BOOKING' AND p.action IN ('READ','UPDATE'))
    OR (p.resource = 'VOYAGE' AND p.action IN ('READ','UPDATE'))
    OR (p.resource = 'CARGO' AND p.action IN ('READ','UPDATE'))
    OR (p.resource = 'CONTENT' AND p.action = 'READ')
    OR (p.resource = 'REPORT' AND p.action = 'READ')
    OR (p.resource = 'PRIVACY' AND p.action IN ('READ','EXPORT','DELETE'))
  )
ON CONFLICT DO NOTHING;

-- ADMIN: full access except SUPERADMIN-only
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r, permissions p
WHERE r.role_name = 'ADMIN'
  AND (
    (p.resource IN ('BOOKING','VOYAGE','CARGO','CONTENT','REPORT','AUDIT'))
    OR (p.resource = 'USER' AND p.action IN ('READ','UPDATE'))
    OR (p.resource = 'PRIVACY' AND p.action IN ('READ','EXPORT','DELETE'))
  )
ON CONFLICT DO NOTHING;

-- SUPERADMIN: everything
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r, permissions p
WHERE r.role_name = 'SUPERADMIN'
ON CONFLICT DO NOTHING;

-- SEAFARER: read-only on own data + voyages
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r, permissions p
WHERE r.role_name = 'SEAFARER'
  AND (
    (p.resource = 'VOYAGE' AND p.action = 'READ')
    OR (p.resource = 'CONTENT' AND p.action = 'READ')
    OR (p.resource = 'PRIVACY' AND p.action IN ('READ','EXPORT','DELETE'))
  )
ON CONFLICT DO NOTHING;

-- ============================================================================
-- PHILIPPINE PORTS (Major Domestic Shipping Ports)
-- ============================================================================
INSERT INTO ports (port_code, port_name, city, province, region, latitude, longitude, ppa_terminal) VALUES
    ('MNL', 'Port of Manila',             'Manila',       'Metro Manila',    'NCR',         14.5833,  120.9667, 'Manila North Harbor'),
    ('CEB', 'Port of Cebu',               'Cebu City',    'Cebu',            'Region VII',  10.2934,  123.9021, 'Cebu International Port'),
    ('ILO', 'Port of Iloilo',             'Iloilo City',  'Iloilo',          'Region VI',   10.6970,  122.5644, 'Iloilo Ferry Terminal'),
    ('DVO', 'Port of Davao',              'Davao City',   'Davao del Sur',   'Region XI',    7.0650,  125.6039, 'Sasa Wharf'),
    ('CDO', 'Port of Cagayan de Oro',     'CDO',          'Misamis Oriental','Region X',     8.4822,  124.6472, 'Macabalan Wharf'),
    ('ZAM', 'Port of Zamboanga',          'Zamboanga City','Zamboanga del Sur','Region IX',   6.9100,  122.0740, 'Zamboanga Port'),
    ('BAT', 'Port of Batangas',           'Batangas City','Batangas',        'Region IV-A', 13.7500,  121.0500, 'Batangas Port Terminal'),
    ('TAC', 'Port of Tacloban',           'Tacloban City','Leyte',           'Region VIII',  11.2500, 125.0000, 'Tacloban Port'),
    ('OZA', 'Port of Ozamiz',             'Ozamiz City',  'Misamis Occidental','Region X',   8.1500,  123.8500, 'Ozamiz Port'),
    ('NAG', 'Port of Nasipit',            'Nasipit',      'Agusan del Norte','Region XIII',   8.9667, 125.3333, 'Nasipit Port'),
    ('DUM', 'Port of Dumaguete',          'Dumaguete City','Negros Oriental','Region VII',    9.3068, 123.3054, 'Dumaguete Port'),
    ('GEN', 'Port of General Santos',     'GenSan',       'South Cotabato','Region XII',     6.1100, 125.1700, 'Makar Wharf'),
    ('SUB', 'Port of Subic',              'Subic',        'Zambales',        'Region III',  14.8000, 120.2833, 'Subic Bay Freeport'),
    ('TAG', 'Port of Tagbilaran',         'Tagbilaran',   'Bohol',           'Region VII',   9.6500, 123.8500, 'Tagbilaran Port')
ON CONFLICT (port_code) DO UPDATE SET
    port_name = EXCLUDED.port_name,
    city = EXCLUDED.city,
    province = EXCLUDED.province;

-- ============================================================================
-- ROUTES (Major Domestic Routes)
-- ============================================================================
INSERT INTO routes (route_code, origin_port_id, dest_port_id, distance_nm, est_duration_hr)
SELECT 'MNL-CEB', o.port_id, d.port_id, 390, 21
FROM ports o, ports d WHERE o.port_code = 'MNL' AND d.port_code = 'CEB'
ON CONFLICT (route_code) DO NOTHING;

INSERT INTO routes (route_code, origin_port_id, dest_port_id, distance_nm, est_duration_hr)
SELECT 'CEB-MNL', o.port_id, d.port_id, 390, 21
FROM ports o, ports d WHERE o.port_code = 'CEB' AND d.port_code = 'MNL'
ON CONFLICT (route_code) DO NOTHING;

INSERT INTO routes (route_code, origin_port_id, dest_port_id, distance_nm, est_duration_hr)
SELECT 'MNL-ILO', o.port_id, d.port_id, 340, 18
FROM ports o, ports d WHERE o.port_code = 'MNL' AND d.port_code = 'ILO'
ON CONFLICT (route_code) DO NOTHING;

INSERT INTO routes (route_code, origin_port_id, dest_port_id, distance_nm, est_duration_hr)
SELECT 'ILO-MNL', o.port_id, d.port_id, 340, 18
FROM ports o, ports d WHERE o.port_code = 'ILO' AND d.port_code = 'MNL'
ON CONFLICT (route_code) DO NOTHING;

INSERT INTO routes (route_code, origin_port_id, dest_port_id, distance_nm, est_duration_hr)
SELECT 'MNL-CDO', o.port_id, d.port_id, 480, 24
FROM ports o, ports d WHERE o.port_code = 'MNL' AND d.port_code = 'CDO'
ON CONFLICT (route_code) DO NOTHING;

INSERT INTO routes (route_code, origin_port_id, dest_port_id, distance_nm, est_duration_hr)
SELECT 'CDO-MNL', o.port_id, d.port_id, 480, 24
FROM ports o, ports d WHERE o.port_code = 'CDO' AND d.port_code = 'MNL'
ON CONFLICT (route_code) DO NOTHING;

INSERT INTO routes (route_code, origin_port_id, dest_port_id, distance_nm, est_duration_hr)
SELECT 'MNL-DVO', o.port_id, d.port_id, 580, 30
FROM ports o, ports d WHERE o.port_code = 'MNL' AND d.port_code = 'DVO'
ON CONFLICT (route_code) DO NOTHING;

INSERT INTO routes (route_code, origin_port_id, dest_port_id, distance_nm, est_duration_hr)
SELECT 'DVO-MNL', o.port_id, d.port_id, 580, 30
FROM ports o, ports d WHERE o.port_code = 'DVO' AND d.port_code = 'MNL'
ON CONFLICT (route_code) DO NOTHING;

INSERT INTO routes (route_code, origin_port_id, dest_port_id, distance_nm, est_duration_hr)
SELECT 'CEB-DVO', o.port_id, d.port_id, 310, 16
FROM ports o, ports d WHERE o.port_code = 'CEB' AND d.port_code = 'DVO'
ON CONFLICT (route_code) DO NOTHING;

INSERT INTO routes (route_code, origin_port_id, dest_port_id, distance_nm, est_duration_hr)
SELECT 'MNL-BAT', o.port_id, d.port_id, 60, 3
FROM ports o, ports d WHERE o.port_code = 'MNL' AND d.port_code = 'BAT'
ON CONFLICT (route_code) DO NOTHING;

INSERT INTO routes (route_code, origin_port_id, dest_port_id, distance_nm, est_duration_hr)
SELECT 'BAT-MNL', o.port_id, d.port_id, 60, 3
FROM ports o, ports d WHERE o.port_code = 'BAT' AND d.port_code = 'MNL'
ON CONFLICT (route_code) DO NOTHING;

INSERT INTO routes (route_code, origin_port_id, dest_port_id, distance_nm, est_duration_hr)
SELECT 'CEB-TAG', o.port_id, d.port_id, 45, 2
FROM ports o, ports d WHERE o.port_code = 'CEB' AND d.port_code = 'TAG'
ON CONFLICT (route_code) DO NOTHING;

-- ============================================================================
-- FARE CLASSES (sample fares per route)
-- ============================================================================
INSERT INTO fare_classes (route_id, class_name, base_fare, available_slots)
SELECT r.route_id, fc.class_name, fc.base_fare, fc.slots
FROM routes r
CROSS JOIN (VALUES
    ('ECONOMY',  800.00,  500),
    ('TOURIST',  1200.00, 200),
    ('BUSINESS', 2500.00, 50),
    ('CABIN',    4500.00, 20)
) AS fc(class_name, base_fare, slots)
WHERE r.route_code = 'MNL-CEB'
ON CONFLICT DO NOTHING;

INSERT INTO fare_classes (route_id, class_name, base_fare, available_slots)
SELECT r.route_id, fc.class_name, fc.base_fare, fc.slots
FROM routes r
CROSS JOIN (VALUES
    ('ECONOMY',  800.00,  500),
    ('TOURIST',  1200.00, 200),
    ('BUSINESS', 2500.00, 50),
    ('CABIN',    4500.00, 20)
) AS fc(class_name, base_fare, slots)
WHERE r.route_code = 'CEB-MNL'
ON CONFLICT DO NOTHING;

INSERT INTO fare_classes (route_id, class_name, base_fare, available_slots)
SELECT r.route_id, fc.class_name, fc.base_fare, fc.slots
FROM routes r
CROSS JOIN (VALUES
    ('ECONOMY',  700.00,  400),
    ('TOURIST',  1100.00, 150),
    ('BUSINESS', 2200.00, 40),
    ('CABIN',    4000.00, 15)
) AS fc(class_name, base_fare, slots)
WHERE r.route_code IN ('MNL-ILO', 'ILO-MNL')
ON CONFLICT DO NOTHING;

INSERT INTO fare_classes (route_id, class_name, base_fare, available_slots)
SELECT r.route_id, fc.class_name, fc.base_fare, fc.slots
FROM routes r
CROSS JOIN (VALUES
    ('ECONOMY',  1000.00, 450),
    ('TOURIST',  1500.00, 180),
    ('BUSINESS', 3000.00, 45),
    ('CABIN',    5500.00, 18)
) AS fc(class_name, base_fare, slots)
WHERE r.route_code IN ('MNL-CDO', 'CDO-MNL', 'MNL-DVO', 'DVO-MNL')
ON CONFLICT DO NOTHING;

INSERT INTO fare_classes (route_id, class_name, base_fare, available_slots)
SELECT r.route_id, fc.class_name, fc.base_fare, fc.slots
FROM routes r
CROSS JOIN (VALUES
    ('ECONOMY',  300.00,  600),
    ('TOURIST',  500.00,  250)
) AS fc(class_name, base_fare, slots)
WHERE r.route_code IN ('MNL-BAT', 'BAT-MNL', 'CEB-TAG')
ON CONFLICT DO NOTHING;

-- ============================================================================
-- SAMPLE VESSELS
-- ============================================================================
INSERT INTO vessels (vessel_code, vessel_name, vessel_type, gross_tonnage, passenger_cap, cargo_cap_tons, year_built, status, marina_cert_no) VALUES
    ('BSS-001', 'MV Brilliant Star',     'RORO',      15000.00, 1500, 2000.00, 2018, 'ACTIVE', 'MARINA-2024-001'),
    ('BSS-002', 'MV Brilliant Voyager',   'FERRY',     12000.00, 1200, 800.00,  2020, 'ACTIVE', 'MARINA-2024-002'),
    ('BSS-003', 'MV Brilliant Pearl',     'RORO',      18000.00, 2000, 2500.00, 2022, 'ACTIVE', 'MARINA-2024-003'),
    ('BSS-004', 'MV Brilliant Horizon',   'CARGO',     8000.00,  200,  5000.00, 2019, 'ACTIVE', 'MARINA-2024-004'),
    ('BSS-005', 'MV Brilliant Express',   'FASTCRAFT', 3000.00,  600,  100.00,  2023, 'ACTIVE', 'MARINA-2024-005')
ON CONFLICT (vessel_code) DO UPDATE SET
    vessel_name = EXCLUDED.vessel_name,
    vessel_type = EXCLUDED.vessel_type,
    status = EXCLUDED.status;
