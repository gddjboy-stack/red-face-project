package com.redface;

import static org.assertj.core.api.Assertions.assertThat;

import com.redface.dto.UserMembershipSummary;
import com.redface.entity.UserMembershipEntity;
import com.redface.mapper.UserMembershipMapper;
import com.redface.service.UserMembershipService;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * C16 会员有效期服务测试。
 */
@SpringBootTest
@ActiveProfiles("test")
class UserMembershipServiceC16Test {
    @Autowired
    private UserMembershipService userMembershipService;

    @Autowired
    private UserMembershipMapper userMembershipMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM user_membership");
    }

    @Test
    void grantSevenDaysShouldCreateMembershipWhenNoRecord() {
        LocalDateTime before = LocalDateTime.now();

        UserMembershipSummary summary = userMembershipService.grantSevenDays("u_new", "RFZJ-2345-6789-ABCD");

        assertThat(summary.isMemberActive()).isTrue();
        assertThat(summary.getMembershipAddedDays()).isEqualTo(7);
        assertThat(summary.getMembershipUntil()).isAfter(before.plusDays(6).plusHours(23));
        assertThat(summary.getMembershipRemainingDays()).isBetween(6, 7);
    }

    @Test
    void grantSevenDaysShouldStartFromNowWhenExpired() {
        LocalDateTime before = LocalDateTime.now();
        userMembershipMapper.ensureRow("u_expired", before.minusDays(2));

        UserMembershipSummary summary = userMembershipService.grantSevenDays("u_expired", "RFZJ-2345-6789-ABCD");

        assertThat(summary.getMembershipUntil()).isAfter(before.plusDays(6).plusHours(23));
        assertThat(summary.getMembershipUntil()).isAfter(before.plusDays(6));
    }

    @Test
    void grantSevenDaysShouldExtendFromExistingActiveUntil() {
        LocalDateTime activeUntil = LocalDateTime.now().plusDays(3);
        userMembershipMapper.ensureRow("u_active", activeUntil);

        UserMembershipSummary summary = userMembershipService.grantSevenDays("u_active", "RFZJ-2345-6789-ABCD");

        assertThat(summary.getMembershipUntil()).isAfter(activeUntil.plusDays(6).plusHours(23));
        assertThat(summary.getMembershipUntil()).isBefore(activeUntil.plusDays(7).plusMinutes(1));
    }

    @Test
    void concurrentTwoDifferentTokensShouldAccumulateFourteenDays() throws Exception {
        String userId = "u_concurrent";
        LocalDateTime before = LocalDateTime.now();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            executor.submit(() -> grantAfterStart(userId, "RFZJ-2345-6789-ABCD", ready, start));
            executor.submit(() -> grantAfterStart(userId, "RFZJ-3456-789A-BCDE", ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        UserMembershipEntity entity = userMembershipMapper.findByUserId(userId);
        assertThat(entity).isNotNull();
        assertThat(entity.getMembershipUntil()).isAfter(before.plusDays(13).plusHours(23));
        assertThat(entity.getMembershipUntil()).isBefore(before.plusDays(14).plusMinutes(1));
    }

    private void grantAfterStart(String userId, String tokenId, CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            userMembershipService.grantSevenDays(userId, tokenId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
