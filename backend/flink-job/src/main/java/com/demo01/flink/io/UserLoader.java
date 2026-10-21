package com.demo01.flink.io;

import com.demo01.flink.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 读 {@code users.json}，构造离线指标用的用户名册。
 *
 * <p>手工逐字段取值而不是让 Jackson 直接反序列化成 POJO：一是只有五个字段，
 * 二是 {@code register_date} 是字符串，交给 Jackson 还得额外注册 JavaTimeModule，
 * 而 Flink 的依赖树里并不需要它。
 */
public final class UserLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static List<User> load(String inputDir) throws Exception {
        File file = new File(inputDir, "users.json");
        if (!file.isFile()) {
            throw new IllegalArgumentException("找不到用户名册：" + file.getAbsolutePath());
        }
        JsonNode root = MAPPER.readTree(file);
        if (root == null || !root.isArray() || root.size() == 0) {
            throw new IllegalArgumentException("users.json 为空或格式不对");
        }

        List<User> users = new ArrayList<User>(root.size());
        for (JsonNode n : root) {
            User u = new User();
            u.setUserId(n.get("user_id").asLong());
            u.setLoginName(n.get("login_name").asText());
            u.setRegion(n.get("region").asText());
            u.setPackageId(n.get("package_id").asLong());
            u.setRegisterDate(LocalDate.parse(n.get("register_date").asText()));
            users.add(u);
        }
        return users;
    }

    private UserLoader() {
    }
}
