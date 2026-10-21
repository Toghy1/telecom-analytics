package com.demo01.business.common;

/**
 * 脱敏（选型⑥：脱敏写在后端 Entity → VO 的转换里，**前端不含任何脱敏代码**）。
 *
 * <p>⚠️ 这不是「前端显示时加个星号」那种假脱敏——那种做法 F12 看一眼接口响应
 * 就能拿到完整号码，Postman 直调更是完全绕过。这里脱的是**接口返回的 JSON
 * 本身**，完整值根本不出服务端。
 *
 * <p>格式（决策「身份证脱敏格式定稿」）：
 * <ul>
 *   <li>手机号 {@code 13800001234 → 138****1234}（前 3 后 4）</li>
 *   <li>身份证 {@code 440301199001011234 → 440301********1234}（前 6 后 4）</li>
 * </ul>
 * 身份证的前 6 位是行政区划、后 4 位含校验码，中间 8 位恰好整段盖住出生日期。
 *
 * <p>⚠️ **不是 11 / 18 位就原样返回**：分析员与管理员以用户名登录
 * （{@code analyst} / {@code admin}），套手机号规则会变成 {@code ana***lyst}，
 * 比不脱敏还糟。
 */
public final class MaskUtil {

    private MaskUtil() {
    }

    public static String loginName(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        return s.matches("\\d{11}") ? s.substring(0, 3) + "****" + s.substring(7) : s;
    }

    public static String idCard(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        return s.matches("\\d{18}") ? s.substring(0, 6) + "********" + s.substring(14) : s;
    }
}
