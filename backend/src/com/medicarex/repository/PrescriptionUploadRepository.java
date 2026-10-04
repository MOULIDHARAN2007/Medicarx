package com.medicarex.repository;

import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.Medicine;
import com.medicarex.model.PrescriptionExtractedMedicine;
import com.medicarex.model.PrescriptionUpload;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PrescriptionUploadRepository {
    private final MedicineRepository medicineRepo = new MedicineRepository();

    public PrescriptionUpload create(PrescriptionUpload upload) {
        String sql = "INSERT INTO prescription_uploads (patient_id, original_filename, stored_filename, file_type, file_size, extraction_status, raw_text) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, upload.getPatientId());
            ps.setString(2, upload.getOriginalFilename());
            ps.setString(3, upload.getStoredFilename());
            ps.setString(4, upload.getFileType());
            ps.setInt(5, upload.getFileSize());
            ps.setString(6, upload.getExtractionStatus());
            ps.setString(7, upload.getRawText());

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    upload.setId(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return upload;
    }

    public void saveExtractedMedicines(int uploadId, List<PrescriptionExtractedMedicine> medicines) {
        if (medicines == null || medicines.isEmpty()) return;

        String sql = "INSERT INTO prescription_extracted_medicines (upload_id, medicine_id, extracted_name, extracted_dosage, confidence, is_confirmed) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (PrescriptionExtractedMedicine m : medicines) {
                ps.setInt(1, uploadId);
                if (m.getMedicineId() != null && m.getMedicineId() > 0) {
                    ps.setInt(2, m.getMedicineId());
                } else {
                    ps.setNull(2, Types.INTEGER);
                }
                ps.setString(3, m.getExtractedName());
                ps.setString(4, m.getExtractedDosage());
                ps.setDouble(5, m.getConfidence());
                ps.setInt(6, m.isConfirmed() ? 1 : 0);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public PrescriptionUpload findById(int id) {
        String sql = "SELECT * FROM prescription_uploads WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    PrescriptionUpload u = mapUploadRow(rs);
                    u.setExtractedMedicines(findMedicinesByUploadId(id));
                    return u;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<PrescriptionUpload> findByPatientId(int patientId) {
        List<PrescriptionUpload> list = new ArrayList<>();
        String sql = "SELECT * FROM prescription_uploads WHERE patient_id = ? ORDER BY id DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PrescriptionUpload u = mapUploadRow(rs);
                    u.setExtractedMedicines(findMedicinesByUploadId(u.getId()));
                    list.add(u);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<PrescriptionExtractedMedicine> findMedicinesByUploadId(int uploadId) {
        List<PrescriptionExtractedMedicine> list = new ArrayList<>();
        String sql = "SELECT * FROM prescription_extracted_medicines WHERE upload_id = ? ORDER BY id ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, uploadId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PrescriptionExtractedMedicine m = mapMedicineRow(rs);
                    if (m.getMedicineId() != null && m.getMedicineId() > 0) {
                        Medicine catMed = medicineRepo.findById(m.getMedicineId());
                        if (catMed != null) {
                            m.setCatalogName(catMed.getName());
                            m.setGenericName(catMed.getGenericName());
                            m.setBrand(catMed.getBrand());
                            m.setCategory(catMed.getCategory());
                            m.setBasePrice(catMed.getBasePrice());
                            m.setTotalStock(catMed.getTotalStock());
                            m.setPharmacyCount(catMed.getPharmacyCount());
                        }
                    }
                    list.add(m);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void updateConfirmedMedicines(int uploadId, List<PrescriptionExtractedMedicine> items) {
        String delSql = "DELETE FROM prescription_extracted_medicines WHERE upload_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(delSql)) {
            ps.setInt(1, uploadId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        saveExtractedMedicines(uploadId, items);
    }

    private PrescriptionUpload mapUploadRow(ResultSet rs) throws SQLException {
        PrescriptionUpload u = new PrescriptionUpload();
        u.setId(rs.getInt("id"));
        u.setPatientId(rs.getInt("patient_id"));
        u.setOriginalFilename(rs.getString("original_filename"));
        u.setStoredFilename(rs.getString("stored_filename"));
        u.setFileType(rs.getString("file_type"));
        u.setFileSize(rs.getInt("file_size"));
        u.setExtractionStatus(rs.getString("extraction_status"));
        u.setRawText(rs.getString("raw_text"));
        u.setCreatedAt(rs.getString("created_at"));
        return u;
    }

    private PrescriptionExtractedMedicine mapMedicineRow(ResultSet rs) throws SQLException {
        PrescriptionExtractedMedicine m = new PrescriptionExtractedMedicine();
        m.setId(rs.getInt("id"));
        m.setUploadId(rs.getInt("upload_id"));
        int medId = rs.getInt("medicine_id");
        if (!rs.wasNull()) {
            m.setMedicineId(medId);
        }
        m.setExtractedName(rs.getString("extracted_name"));
        m.setExtractedDosage(rs.getString("extracted_dosage"));
        m.setConfidence(rs.getDouble("confidence"));
        m.setConfirmed(rs.getInt("is_confirmed") == 1);
        m.setCreatedAt(rs.getString("created_at"));
        return m;
    }
}
