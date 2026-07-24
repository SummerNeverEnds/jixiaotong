package com.jixiaotong.performance.util;

public final class OperationLogDescriptions {

    private OperationLogDescriptions() {
    }

    public static String[] resolve(String method, String uri) {
        String path = uri == null ? "" : uri;
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        String m = method == null ? "" : method.toUpperCase();

        if (path.startsWith("/api/auth/login")) {
            return new String[]{"认证", "用户登录"};
        }
        if (path.startsWith("/api/auth/logout")) {
            return new String[]{"认证", "退出登录"};
        }
        if (path.startsWith("/api/auth/change-password")) {
            return new String[]{"认证", "修改密码"};
        }
        if (path.startsWith("/api/user") && "POST".equals(m) && path.endsWith("/add")) {
            return new String[]{"用户管理", "新增用户"};
        }
        if (path.startsWith("/api/user") && "PUT".equals(m)) {
            return new String[]{"用户管理", "编辑用户"};
        }
        if (path.matches("/api/user/\\d+/reset-password")) {
            return new String[]{"用户管理", "重置用户密码"};
        }
        if (path.matches("/api/user/\\d+/disable")) {
            return new String[]{"用户管理", "禁用/启用用户"};
        }
        if (path.matches("/api/user/\\d+") && "DELETE".equals(m)) {
            return new String[]{"用户管理", "删除用户"};
        }
        if (path.startsWith("/api/material") && path.contains("complete")) {
            return new String[]{"学习中心", "完成资料学习"};
        }
        if (path.startsWith("/api/material") && "POST".equals(m)) {
            return new String[]{"资料管理", "发布学习资料"};
        }
        if (path.startsWith("/api/material") && "PUT".equals(m)) {
            return new String[]{"资料管理", "编辑学习资料"};
        }
        if (path.startsWith("/api/material") && "DELETE".equals(m)) {
            return new String[]{"资料管理", "删除学习资料"};
        }
        if (path.startsWith("/api/indicator") && "POST".equals(m)) {
            return new String[]{"题库管理", "新增/导入题目"};
        }
        if (path.startsWith("/api/indicator") && "PUT".equals(m)) {
            return new String[]{"题库管理", "编辑题目"};
        }
        if (path.startsWith("/api/indicator") && "DELETE".equals(m)) {
            return new String[]{"题库管理", "删除题目"};
        }
        if (path.startsWith("/api/template") && path.contains("publish")) {
            return new String[]{"考核模板", "发布考核试卷"};
        }
        if (path.startsWith("/api/template") && "POST".equals(m)) {
            return new String[]{"考核模板", "保存考核模板"};
        }
        if (path.startsWith("/api/template") && "DELETE".equals(m)) {
            return new String[]{"考核模板", "删除考核模板"};
        }
        if (path.startsWith("/api/review") && path.contains("/start")) {
            return new String[]{"绩效考核", "开始考试"};
        }
        if (path.startsWith("/api/review") && path.contains("/submit")) {
            return new String[]{"绩效考核", "提交考试答卷"};
        }
        if (path.startsWith("/api/review") && path.contains("/draft")) {
            return new String[]{"绩效考核", "暂存答卷草稿"};
        }
        if (path.startsWith("/api/review") && path.contains("/cheat")) {
            return new String[]{"绩效考核", "切屏行为上报"};
        }
        if (path.startsWith("/api/review") && path.contains("/appeal") && !path.contains("appeals")) {
            return new String[]{"绩效考核", "提交成绩申诉"};
        }
        if (path.startsWith("/api/review/appeals/review")) {
            return new String[]{"绩效考核", "复核申诉"};
        }
        if (path.startsWith("/api/es") && "POST".equals(m)) {
            return new String[]{"档案检索", "手动同步 ES"};
        }

        return new String[]{"系统", m + " " + path};
    }
}
