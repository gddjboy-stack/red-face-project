package com.redface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.redface.service.LiveWatermarkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * C20-4A 校准文案约束测试（Claude 裁定 V3.1 批准的文案）。
 *
 * <p><b>为什么文案需要写测试</b>：这几段文字不是界面装饰，而是防误操作措施。
 * 运营若把「校准」理解为「分数清零」，会在发现选手分数未变时判定系统故障，
 * 进而手动调分「修正」——手动调分是合法操作，系统无法识别这种数据污染。
 * 即：文案失效的后果是<b>比赛结果被静默污染</b>，风险等级与代码缺陷相同。
 *
 * <p>因此把文案约束固化为可执行断言：日后任何人为了按钮排版好看而改回
 * 「清零」二字，或删掉「不会改变人气值」的说明，测试立即失败。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CalibrationCopyC20Test extends C9MockMvcSupport {

    @BeforeEach
    void setUp() {
        clearTables();
        jdbcTemplate.update("DELETE FROM live_metric_watermark");
    }

    @Test
    @DisplayName("按钮文案不得包含「清零」，且须点明校准对象是中控台读数")
    void actionLabelMustNotContainZeroingWord() {
        String label = LiveWatermarkService.CALIBRATION_ACTION_LABEL;
        // 「清零」会被理解为分数清零，而这正是要规避的误解本身
        assertThat(label).doesNotContain("清零");
        // 必须让运营看懂被重置的是中控台读数，而非某个业务分值
        assertThat(label).contains("中控台");
    }

    @Test
    @DisplayName("二次确认文案必须明写不改变任何选手人气值")
    void confirmMessageMustDenyPopularityChange() {
        String confirm = LiveWatermarkService.CALIBRATION_CONFIRM_MESSAGE;
        assertThat(confirm).contains("不会改变");
        assertThat(confirm).contains("人气值");
        assertThat(confirm).doesNotContain("清零");
    }

    @Test
    @DisplayName("校准与撤销的成功提示都须重申未改变人气值")
    void successMessagesMustRestateNoPopularityChange() {
        assertThat(LiveWatermarkService.CALIBRATION_SUCCESS_MESSAGE)
                .contains("未改变").contains("人气值");
        assertThat(LiveWatermarkService.REVOKE_SUCCESS_MESSAGE)
                .contains("未改变").contains("人气值");
        // 成功提示还须告知下一步动作，否则运营不知道校准完该做什么
        assertThat(LiveWatermarkService.CALIBRATION_SUCCESS_MESSAGE).contains("录入");
    }

    @Test
    @DisplayName("文案端点向前端下发四段官方文案")
    void copyEndpointExposesAllCopies() throws Exception {
        mockMvc.perform(get("/api/admin/live/watermarks/calibrate-copy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actionLabel")
                        .value(LiveWatermarkService.CALIBRATION_ACTION_LABEL))
                .andExpect(jsonPath("$.data.confirmMessage")
                        .value(LiveWatermarkService.CALIBRATION_CONFIRM_MESSAGE))
                .andExpect(jsonPath("$.data.successMessage")
                        .value(LiveWatermarkService.CALIBRATION_SUCCESS_MESSAGE))
                .andExpect(jsonPath("$.data.revokeSuccessMessage")
                        .value(LiveWatermarkService.REVOKE_SUCCESS_MESSAGE));
    }

    @Test
    @DisplayName("校准接口返回体自带成功提示，前端无需自行拼装")
    void calibrateResponseCarriesMessage() throws Exception {
        mockMvc.perform(post("/api/admin/live/watermarks/calibrate")
                        .contentType("application/json")
                        .content("{\"operatorId\":\"director\",\"reason\":\"新一场直播开播\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message")
                        .value(LiveWatermarkService.CALIBRATION_SUCCESS_MESSAGE));
    }

    @Test
    @DisplayName("撤销接口返回体自带成功提示")
    void revokeResponseCarriesMessage() throws Exception {
        mockMvc.perform(post("/api/admin/live/watermarks/calibrate")
                        .contentType("application/json")
                        .content("{\"operatorId\":\"director\",\"reason\":\"新一场直播开播\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/live/watermarks/revoke-calibration")
                        .contentType("application/json")
                        .content("{\"operatorId\":\"director\",\"reason\":\"误点校准\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message")
                        .value(LiveWatermarkService.REVOKE_SUCCESS_MESSAGE));
    }
}
