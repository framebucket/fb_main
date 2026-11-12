package com.framebucket.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framebucket.admin.AdminDtos.AuditEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Append-only record of every admin action. */
@Service
public class AdminAuditService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AdminAuditService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void log(String adminEmail, String action, String targetType, Object targetId, Map<String, ?> details) {
        String json;
        try {
            json = details == null ? null : objectMapper.writeValueAsString(details);
        } catch (Exception exception) {
            json = null;
        }
        jdbc.update("""
                        INSERT INTO fb_admin_audit (id, admin_email, action, target_type, target_id, details)
                        VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb))
                        """, UUID.randomUUID(), adminEmail, action, targetType,
                targetId == null ? null : targetId.toString(), json);
    }

    public List<AuditEntry> recent(int limit) {
        return jdbc.query("""
                        SELECT a.*, a.details::text AS details_json,
                               CASE WHEN a.target_type = 'PHOTOGRAPHER' THEN
                                   (SELECT COALESCE(NULLIF(u.studio_name, ''), u.full_name) FROM fb_user u WHERE u.id::text = a.target_id)
                               END AS target_name
                        FROM fb_admin_audit a ORDER BY a.created_at DESC LIMIT ?
                        """,
                (rs, n) -> new AuditEntry(rs.getObject("id", UUID.class), rs.getString("admin_email"), rs.getString("action"),
                        rs.getString("target_type"), rs.getString("target_id"), rs.getString("target_name"),
                        rs.getString("details_json"), rs.getTimestamp("created_at").toInstant()),
                Math.min(Math.max(limit, 1), 500));
    }
}
