-- ===================================================
-- MediCareX Realistic Seed Data
-- ===================================================

USE medicarex;

-- Passwords below are hashed using SHA-256 with salt 'medicarex_static_salt_2026'

-- 1. Insert Users
INSERT INTO users (id, email, password_hash, salt, full_name, phone, role, avatar_url, is_active) VALUES
(1, 'priya@example.com', 'a3bb9f018e6c7104b79c314cfb5c65f0a07e997f7bb1e54911d31d4d39f729b4', 'medicarex_static_salt_2026', 'Priya Sharma', '+91 98765 43210', 'PATIENT', 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150', TRUE),
(2, 'arjun@example.com', '24116ea98cd6b73a38096c4cc9bd4509a259bb7f10b77e8fb1d76efc3cbe0f40', 'medicarex_static_salt_2026', 'Dr. Arjun Kumar', '+91 98450 11223', 'DOCTOR', 'https://images.unsplash.com/photo-1622253692010-333f2da6031d?w=150', TRUE),
(3, 'healthplus@example.com', '21665aef33276aa73dc164a39ec867568ee4d59a7a0bdfa373b9d0dc6dbef20c', 'medicarex_static_salt_2026', 'Rajesh Patel (HealthPlus)', '+91 80234 56789', 'PHARMACY', 'https://images.unsplash.com/photo-1586015555751-63bb77f4322a?w=150', TRUE),
(4, 'admin@example.com', '0192023a7bbd73250516f069df18b500e854973909772ee57a8df8d48625ec1d', 'medicarex_static_salt_2026', 'System Administrator', '+91 90000 00001', 'ADMIN', 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150', TRUE),
(5, 'medlife@example.com', '21665aef33276aa73dc164a39ec867568ee4d59a7a0bdfa373b9d0dc6dbef20c', 'medicarex_static_salt_2026', 'Suresh Reddy (MedLife)', '+91 80987 65432', 'PHARMACY', 'https://images.unsplash.com/photo-1576091160550-2173dba999ef?w=150', TRUE),
(6, 'apollocare@example.com', '21665aef33276aa73dc164a39ec867568ee4d59a7a0bdfa373b9d0dc6dbef20c', 'medicarex_static_salt_2026', 'Ananya Iyer (Apollo Care)', '+91 80112 23344', 'PHARMACY', 'https://images.unsplash.com/photo-1559839734-2b71ea197ec2?w=150', TRUE);

-- 2. Insert Patient Profiles
INSERT INTO patients (id, user_id, dob, gender, blood_group, address, emergency_contact, insurance_provider) VALUES
(1, 1, '1996-05-14', 'Female', 'O+', '42, 4th Main, Indiranagar, Bengaluru, KA 560038', '+91 98765 00000 (Spouse)', 'Star Health Comprehensive');

-- 3. Insert Doctor Profiles
INSERT INTO doctors (id, user_id, specialization, license_number, hospital_affiliation, experience_years, consultation_fee, rating, available_hours) VALUES
(1, 2, 'Cardiologist & General Physician', 'KMC-88492-MD', 'Manipal Hospital, Bangalore', 12, 750.00, 4.9, '09:00 AM - 05:00 PM');

-- 4. Insert Pharmacies
INSERT INTO pharmacies (id, user_id, pharmacy_name, license_number, owner_name, address, city, state, postal_code, latitude, longitude, phone, email, opening_hours, is_24_hours, is_verified, rating, status) VALUES
(1, 3, 'HealthPlus Pharmacy', 'DL-BLR-2023-8891', 'Rajesh Patel', '102, 100 Feet Rd, Indiranagar', 'Bengaluru', 'Karnataka', '560038', 12.9784, 77.6408, '+91 80234 56789', 'support@healthplus.in', '8:00 AM - 11:00 PM', FALSE, TRUE, 4.8, 'Active'),
(2, 5, 'MedLife Store', 'DL-BLR-2022-4412', 'Suresh Reddy', '45, CMH Road, Indiranagar', 'Bengaluru', 'Karnataka', '560038', 12.9798, 77.6360, '+91 80987 65432', 'contact@medlifestore.in', '24 Hours Open', TRUE, TRUE, 4.7, 'Active'),
(3, 6, 'Apollo Care Pharmacy', 'DL-BLR-2024-1109', 'Ananya Iyer', '18, 80 Feet Road, Koramangala 4th Block', 'Bengaluru', 'Karnataka', '560034', 12.9345, 77.6245, '+91 80112 23344', 'care@apollocare.in', '7:00 AM - 11:30 PM', FALSE, TRUE, 4.9, 'Active'),
(4, NULL, 'Guardian Lifecare Pharmacy', 'DL-BLR-2021-9988', 'Vikram Malhotra', '77, MG Road, CBD Area', 'Bengaluru', 'Karnataka', '560001', 12.9756, 77.6067, '+91 80445 56677', 'info@guardianlife.in', '24 Hours Open', TRUE, TRUE, 4.6, 'Active'),
(5, NULL, 'GreenCross Meds & Wellness', 'DL-BLR-2023-6677', 'Deepak Verma', '12, 12th Main, HAL 2nd Stage', 'Bengaluru', 'Karnataka', '560008', 12.9660, 77.6499, '+91 80332 21100', 'greencross@wellness.in', '8:30 AM - 10:30 PM', FALSE, TRUE, 4.5, 'Active'),
(6, NULL, 'Care & Cure Pharmacy', 'DL-BLR-2023-5521', 'Kavitha Nair', '14, 27th Main Rd, HSR Layout Sector 1', 'Bengaluru', 'Karnataka', '560102', 12.9121, 77.6446, '+91 80456 78901', 'carecure@hsr.in', '8:00 AM - 11:00 PM', FALSE, TRUE, 4.8, 'Active'),
(7, NULL, 'Apollo Pharmacy Whitefield', 'DL-BLR-2024-9902', 'Manoj Kumar', '88, ITPL Main Road, Whitefield', 'Bengaluru', 'Karnataka', '560066', 12.9864, 77.7345, '+91 80678 90123', 'apollowf@care.in', '24 Hours Open', TRUE, TRUE, 4.9, 'Active'),
(8, NULL, 'MedPlus Malleshwaram', 'DL-BLR-2022-7711', 'Raghavendra Rao', '52, 8th Cross, Margosa Road', 'Bengaluru', 'Karnataka', '560003', 13.0035, 77.5701, '+91 80234 11223', 'medplus@malleshwaram.in', '8:00 AM - 10:30 PM', FALSE, TRUE, 4.7, 'Active'),
(9, NULL, 'Apollo Pharmacy T Nagar', 'DL-CHN-2023-3312', 'R. Swaminathan', '42, Venkatanarayana Road, T. Nagar', 'Chennai', 'Tamil Nadu', '600017', 13.0418, 80.2341, '+91 44 2434 5678', 'tnagar@apollocare.in', '24 Hours Open', TRUE, TRUE, 4.8, 'Active'),
(10, NULL, 'MedPlus Anna Nagar', 'DL-CHN-2024-8821', 'P. Karthik', '15, 2nd Avenue, Anna Nagar', 'Chennai', 'Tamil Nadu', '600040', 13.0850, 80.2100, '+91 44 2621 9900', 'annanagar@medplus.in', '8:00 AM - 11:00 PM', FALSE, TRUE, 4.7, 'Active');

-- 5. Insert Medicines
INSERT INTO medicines (id, name, generic_name, brand, category, dosage, form, prescription_required, description, uses, side_effects, important_info, storage_info, image_url, base_price, is_active) VALUES
(1, 'Amoxicillin 500mg', 'Amoxicillin Trihydrate', 'Moxclav / Amoxil', 'Antibiotics', '500mg', 'Capsule', TRUE, 
'Amoxicillin is a broad-spectrum penicillin-class antibiotic used to treat a wide variety of bacterial infections including respiratory tract infections, ear infections, and skin infections.',
'Treatment of bacterial infections such as pneumonia, bronchitis, ear infections, urinary tract infections, and skin infections.',
'Nausea, vomiting, diarrhea, mild rash, headache. Seek immediate help if experiencing severe allergic reactions.',
'Complete the full prescribed course even if symptoms improve early. Take with meals to reduce stomach upset.',
'Store below 25°C in a dry place away from direct light.', 'https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?w=300', 85.50, TRUE),

(2, 'Paracetamol 650mg', 'Acetaminophen', 'Dolo 650 / Crocin', 'Pain Relief', '650mg', 'Tablet', FALSE,
'Paracetamol 650mg is a widely trusted analgesic and antipyretic medicine that relieves moderate pain, headaches, muscle aches, toothaches, and helps lower high fever.',
'Fever reduction, relief from mild to moderate headaches, toothache, muscle aches, backache, and common cold symptoms.',
'Generally well tolerated. Rare side effects include mild nausea, skin rash, or allergic reactions. Avoid alcohol overdose.',
'Do not exceed 4000mg in 24 hours. High doses can cause serious liver damage. Keep out of reach of children.',
'Store at room temperature (15°C - 30°C) protected from moisture.', 'https://images.unsplash.com/photo-1587854692152-cbe660dbde88?w=300', 32.00, TRUE),

(3, 'Metformin 500mg', 'Metformin Hydrochloride', 'Glycomet / Glucophage', 'Diabetes', '500mg', 'Tablet', TRUE,
'Metformin is the first-line medication for the treatment of type 2 diabetes, particularly in people who are overweight. It helps lower blood sugar levels and improves insulin sensitivity.',
'Management of type 2 diabetes mellitus, improving glycemic control alongside diet and exercise.',
'Stomach upset, diarrhea, flatulence, metallic taste in mouth, decreased vitamin B12 levels over prolonged use.',
'Take with meals to minimize gastrointestinal discomfort. Regular blood sugar and renal function monitoring is recommended.',
'Store at 20°C to 25°C away from heat and moisture.', 'https://images.unsplash.com/photo-1471864190281-a93a3070b6de?w=300', 48.00, TRUE),

(4, 'Atorvastatin 20mg', 'Atorvastatin Calcium', 'Lipitor / Atorva', 'Cardiology', '20mg', 'Tablet', TRUE,
'Atorvastatin is an HMG-CoA reductase inhibitor (statin) used to lower bad cholesterol (LDL) and triglycerides in the blood, reducing the risk of heart disease and stroke.',
'Hypercholesterolemia, prevention of cardiovascular disease, reducing myocardial infarction risk.',
'Muscle pain, joint pain, diarrhea, mild headache, elevated liver enzymes.',
'Avoid consuming large amounts of grapefruit juice while taking this medication. Take consistently in the evening.',
'Store below 30°C in a dry location.', 'https://images.unsplash.com/photo-1550572017-ed20015ade08?w=300', 145.00, TRUE),

(5, 'Azithromycin 500mg', 'Azithromycin', 'Azee 500 / Zithromax', 'Antibiotics', '500mg', 'Tablet', TRUE,
'Azithromycin is a macrolide antibiotic that halts the growth of bacteria causing respiratory infections, skin infections, sinus infections, and throat infections.',
'Throat infections, tonsillitis, sinusitis, community-acquired pneumonia, acute otitis media.',
'Nausea, diarrhea, abdominal cramps, mild headache, temporary changes in taste.',
'Take 1 hour before or 2 hours after meals. Do not take antacids containing aluminum or magnesium simultaneously.',
'Store in airtight containers below 25°C.', 'https://images.unsplash.com/photo-1584017911766-d451b3d0e843?w=300', 115.00, TRUE),

(6, 'Cetirizine 10mg', 'Cetirizine Hydrochloride', 'Zyrtec / Cetzine', 'Allergy', '10mg', 'Tablet', FALSE,
'Cetirizine is a non-drowsy second-generation antihistamine used to relieve allergy symptoms such as watery eyes, runny nose, itching eyes/nose, and sneezing.',
'Seasonal allergic rhinitis, perennial allergic rhinitis, chronic idiopathic urticaria (hives).',
'Mild drowsiness in some individuals, dry mouth, tiredness, dizziness.',
'Use caution when driving or operating heavy machinery if drowsiness occurs. Avoid alcohol consumption.',
'Store at controlled room temperature 20°C to 25°C.', 'https://images.unsplash.com/photo-1512069772995-ec65ed45afd6?w=300', 25.00, TRUE),

(7, 'Pantoprazole 40mg', 'Pantoprazole Sodium', 'Pan 40 / Pantocid', 'Gastroenterology', '40mg', 'Tablet', FALSE,
'Pantoprazole is a proton pump inhibitor that decreases the amount of acid produced in the stomach, healing acid damage to the stomach and esophagus.',
'Gastroesophageal reflux disease (GERD), erosive esophagitis, peptic ulcer disease, Zollinger-Ellison syndrome.',
'Headache, mild diarrhea, stomach pain, flatulence, nausea.',
'Take once daily in the morning, 30 minutes before breakfast with a glass of water.',
'Store below 25°C protected from moisture.', 'https://images.unsplash.com/photo-1550572017-4fcdbb590e5b?w=300', 92.00, TRUE),

(8, 'Ibuprofen 400mg', 'Ibuprofen', 'Brufen 400 / Advil', 'Pain Relief', '400mg', 'Tablet', FALSE,
'Ibuprofen is a nonsteroidal anti-inflammatory drug (NSAID) used for relieving pain, helping reduce inflammation, and lowering fever.',
'Osteoarthritis, rheumatoid arthritis, dental pain, headache, menstrual cramps, muscular strains.',
'Stomach pain, heartburn, dizziness, nausea, mild indigestion.',
'Always take after meals or with milk to protect stomach lining. Avoid prolonged use without medical advice.',
'Store in a dry location at 15°C to 25°C.', 'https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?w=300', 40.00, TRUE),

(9, 'Montelukast 10mg', 'Montelukast Sodium', 'Montair 10 / Singulair', 'Respiratory', '10mg', 'Tablet', TRUE,
'Montelukast is a leukotriene receptor antagonist that prevents airway edema, smooth muscle contraction, and inflammation in asthma and seasonal allergies.',
'Prophylaxis and chronic treatment of asthma, relief of symptoms of allergic rhinitis.',
'Headache, cough, abdominal pain, fever, mild dizziness.',
'Take regularly every day as prescribed, usually in the evening. Not intended for immediate relief of acute asthma attacks.',
'Store in original packaging at 20°C to 25°C.', 'https://images.unsplash.com/photo-1471864190281-a93a3070b6de?w=300', 160.00, TRUE),

(10, 'Vitamin D3 60,000 IU', 'Cholecalciferol', 'Calcirol / D-Rise', 'Vitamins & Supplements', '60,000 IU', 'Capsule', FALSE,
'High-potency Vitamin D3 supplement that aids in calcium absorption, supports healthy bone mineralization, and boosts immune health.',
'Vitamin D deficiency, osteoporosis management, hypocalcemia, osteomalacia.',
'Extremely well tolerated at recommended doses. Excessive intake may cause hypercalcemia, weakness, nausea.',
'Take once weekly or as directed by physician, preferably with a fat-containing meal for optimal absorption.',
'Store protected from light and moisture at temperature not exceeding 25°C.', 'https://images.unsplash.com/photo-1587854692152-cbe660dbde88?w=300', 120.00, TRUE);

-- 6. Insert Inventory (Stock for Pharmacies)
INSERT INTO inventory (pharmacy_id, medicine_id, batch_number, stock_quantity, unit_price, expiry_date, status) VALUES
-- HealthPlus Pharmacy (ID 1)
(1, 1, 'HP-AMX-901', 45, 85.50, '2027-08-31', 'In Stock'),
(1, 2, 'HP-PCM-882', 120, 32.00, '2028-01-15', 'In Stock'),
(1, 3, 'HP-MET-401', 18, 48.00, '2026-11-30', 'Low Stock'),
(1, 4, 'HP-ATV-112', 60, 145.00, '2027-06-20', 'In Stock'),
(1, 5, 'HP-AZI-773', 0, 115.00, '2026-10-15', 'Out of Stock'),
(1, 6, 'HP-CET-209', 80, 25.00, '2027-12-31', 'In Stock'),
(1, 7, 'HP-PAN-504', 35, 92.00, '2027-04-15', 'In Stock'),
(1, 8, 'HP-IBU-319', 55, 40.00, '2028-03-20', 'In Stock'),
(1, 9, 'HP-MON-662', 12, 160.00, '2026-12-10', 'Low Stock'),
(1, 10, 'HP-VD3-008', 95, 120.00, '2028-05-30', 'In Stock'),

-- MedLife Store (ID 2)
(2, 1, 'ML-AMX-101', 30, 84.00, '2027-09-15', 'In Stock'),
(2, 2, 'ML-PCM-332', 200, 30.00, '2028-02-28', 'In Stock'),
(2, 3, 'ML-MET-991', 50, 46.50, '2027-05-20', 'In Stock'),
(2, 4, 'ML-ATV-442', 25, 142.00, '2027-07-10', 'In Stock'),
(2, 5, 'ML-AZI-883', 20, 112.00, '2027-03-15', 'In Stock'),
(2, 6, 'ML-CET-419', 5, 24.00, '2026-10-01', 'Low Stock'),
(2, 7, 'ML-PAN-884', 40, 90.00, '2027-11-20', 'In Stock'),

-- Apollo Care Pharmacy (ID 3)
(3, 1, 'AP-AMX-204', 50, 88.00, '2027-10-10', 'In Stock'),
(3, 2, 'AP-PCM-119', 150, 33.00, '2028-04-15', 'In Stock'),
(3, 5, 'AP-AZI-449', 35, 118.00, '2027-08-20', 'In Stock'),
(3, 9, 'AP-MON-991', 40, 165.00, '2027-09-30', 'In Stock'),

-- Guardian Lifecare Pharmacy (ID 4)
(4, 1, 'GL-AMX-801', 25, 86.00, '2027-07-20', 'In Stock'),
(4, 2, 'GL-PCM-802', 80, 31.50, '2028-03-10', 'In Stock'),

-- GreenCross Meds (ID 5)
(5, 1, 'GC-AMX-701', 40, 85.00, '2027-08-15', 'In Stock'),
(5, 2, 'GC-PCM-702', 90, 32.50, '2028-01-20', 'In Stock'),

-- Care & Cure Pharmacy (ID 6)
(6, 1, 'CC-AMX-601', 35, 87.00, '2027-09-01', 'In Stock'),
(6, 2, 'CC-PCM-602', 110, 32.00, '2028-02-15', 'In Stock'),

-- Apollo Whitefield (ID 7)
(7, 1, 'AW-AMX-501', 60, 89.00, '2027-11-20', 'In Stock'),
(7, 2, 'AW-PCM-502', 140, 33.00, '2028-06-10', 'In Stock'),

-- MedPlus Malleshwaram (ID 8)
(8, 1, 'MM-AMX-401', 45, 86.50, '2027-10-05', 'In Stock'),
(8, 2, 'MM-PCM-402', 95, 31.00, '2028-04-01', 'In Stock'),

-- Apollo Pharmacy T Nagar (ID 9)
(9, 1, 'AT-AMX-301', 50, 87.50, '2027-11-15', 'In Stock'),
(9, 2, 'AT-PCM-302', 130, 32.00, '2028-05-10', 'In Stock'),

-- MedPlus Anna Nagar (ID 10)
(10, 1, 'MA-AMX-201', 40, 86.00, '2027-12-01', 'In Stock'),
(10, 2, 'MA-PCM-202', 100, 31.50, '2028-03-25', 'In Stock');

-- 7. Insert Health Records (General consults and lab tests)
INSERT INTO health_records (id, patient_id, record_type, title, provider_name, description, document_url, file_size, record_date) VALUES
(1, 1, 'Consultations', 'General Physician Health Checkup', 'Dr. Arjun Kumar (Manipal Hospital)', 'Routine annual wellness exam. Vitals normal, chest clear, heart sounds normal S1/S2.', 'health_record_01.pdf', '1.2 MB', '2026-09-01'),
(3, 1, 'Lab Reports', 'Complete Blood Count (CBC) & Lipid Profile', 'Apollo Diagnostics Lab, Indiranagar', 'Hemoglobin: 13.8 g/dL, Total WBC: 7,400 /mcL, Total Cholesterol: 185 mg/dL, Triglycerides: 140 mg/dL.', 'cbc_lipid_report.pdf', '2.4 MB', '2026-08-20'),
(4, 1, 'Documents', 'Health Insurance Digital Card', 'Star Health Allied Insurance', 'Policy # SH-IND-9948201 valid through December 2026. Primary insured: Priya Sharma.', 'insurance_card.pdf', '620 KB', '2026-01-10');

-- 8. Insert Notifications
INSERT INTO notifications (id, user_id, type, title, message, reference_id, reference_type, is_read, time_group) VALUES
(1, 1, 'Medicine', 'Amoxicillin 500mg In Stock Near You', 'HealthPlus Pharmacy (0.8 km away) has 45 units of Amoxicillin 500mg available at ₹85.50.', 1, 'MEDICINE', FALSE, 'Today'),
(3, 1, 'Pharmacy', 'MedLife Store Order Confirmed', 'Your recurring refill at MedLife Store is ready for in-store pickup or delivery.', 2, 'PHARMACY', TRUE, 'Yesterday'),
(4, 1, 'System', 'Security Notification: Password Updated', 'Your MediCareX account credentials were successfully verified.', NULL, 'SECURITY', TRUE, 'This Week');

-- 11. Insert Appointments
INSERT INTO appointments (id, doctor_id, patient_id, appointment_date, appointment_time, type, status, notes) VALUES
(1, 1, 1, '2026-09-08', '10:30 AM', 'Follow-up Consultation', 'Confirmed', 'Review blood pressure recovery and antibiotic completion response.'),
(2, 1, 1, '2026-09-15', '03:00 PM', 'Cardio Wellness Review', 'Pending', 'Routine checkup.');

-- 12. Insert Saved Medicines for Patient
INSERT INTO saved_medicines (user_id, medicine_id) VALUES
(1, 1),
(1, 2),
(1, 10);
