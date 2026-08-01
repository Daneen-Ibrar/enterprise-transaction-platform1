package com.enterprise.security;

import com.enterprise.audit.UserActivityLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

@Service
public class BackupCodeService {

    private static final Logger log = LoggerFactory.getLogger(BackupCodeService.class);

    private static final int BACKUP_CODE_COUNT = 10;
    private static final int CODE_LENGTH = 8;
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"; // no confusing chars (0,1,I,O)

    private final BackupCodeRepository backupCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserActivityLogService activityLogService;
    private final SecureRandom secureRandom = new SecureRandom();

    public BackupCodeService(BackupCodeRepository backupCodeRepository,
                             UserActivityLogService activityLogService) {
        this.backupCodeRepository = backupCodeRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.activityLogService = activityLogService;
    }

    @Transactional
    public List<String> generateBackupCodes(Long userId) {
        // Delete existing codes
        backupCodeRepository.deleteByUserId(userId);

        List<String> codes = new ArrayList<>();
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            String plainCode = generateRandomCode();
            String hashedCode = passwordEncoder.encode(plainCode);

            BackupCode backupCode = new BackupCode();
            backupCode.setUserId(userId);
            backupCode.setCodeHash(hashedCode);
            backupCode.setUsed(false);
            backupCodeRepository.save(backupCode);

            codes.add(plainCode);
        }

        activityLogService.logActivity(
                userId,
                "BACKUP_CODES_GENERATED",
                "Generated " + BACKUP_CODE_COUNT + " backup codes",
                null
        );

        log.info("Generated {} backup codes for user {}", BACKUP_CODE_COUNT, userId);
        return codes;
    }

    public boolean verifyBackupCode(Long userId, String code) {
        // Hash the input code and compare with stored hashes
        List<BackupCode> codes = backupCodeRepository.findByUserIdAndUsedFalse(userId);

        for (BackupCode backupCode : codes) {
            if (passwordEncoder.matches(code, backupCode.getCodeHash())) {
                // Mark as used
                backupCodeRepository.markAsUsed(backupCode.getId());
                activityLogService.logActivity(
                        userId,
                        "BACKUP_CODE_USED",
                        "Backup code used for login",
                        null
                );
                log.info("Backup code used for user {}", userId);
                return true;
            }
        }
        return false;
    }

    public long getRemainingCodeCount(Long userId) {
        return backupCodeRepository.countByUserIdAndUsedFalse(userId);
    }

    public List<BackupCode> getUnusedCodes(Long userId) {
        return backupCodeRepository.findByUserIdAndUsedFalse(userId);
    }

    @Transactional
    public void regenerateCodes(Long userId) {
        generateBackupCodes(userId);
    }

    private String generateRandomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARACTERS.charAt(secureRandom.nextInt(CHARACTERS.length())));
        }
        // Add hyphens for readability: XXXX-XXXX
        String code = sb.toString();
        return code.substring(0, 4) + "-" + code.substring(4);
    }
}