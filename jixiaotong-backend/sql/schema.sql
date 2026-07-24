-- 设置字符集
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 0. 部门基础表 (sys_dept)
-- ----------------------------
DROP TABLE IF EXISTS `sys_dept`;
CREATE TABLE `sys_dept` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(64) NOT NULL COMMENT '部门名称',
  `manager_id` bigint(20) DEFAULT NULL COMMENT '部门经理ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='部门基础表';

-- ----------------------------
-- 1. 岗位培训与改进资料表 (perf_material)
-- ----------------------------
DROP TABLE IF EXISTS `perf_material`;
CREATE TABLE `perf_material` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `title` varchar(128) NOT NULL COMMENT '资料标题',
  `type` varchar(32) NOT NULL COMMENT '资料类型 (VIDEO-视频, DOC-文档, LINK-链接)',
  `url` varchar(512) NOT NULL COMMENT '资料链接地址',
  `description` text COMMENT '资料描述/学习要求',
  `deadline` datetime DEFAULT NULL COMMENT '学习截止时间',
  `status` varchar(16) NOT NULL DEFAULT 'ON_SHELF' COMMENT '上架状态 (ON_SHELF-上架, OFF_SHELF-已下架)',
  `creator_id` bigint(20) NOT NULL COMMENT '创建人ID(Manager或Admin)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除 (0-未删除, 1-已删除)',
  PRIMARY KEY (`id`),
  KEY `idx_type` (`type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='岗位培训与改进资料表';

-- ----------------------------
-- 2. 员工学习完成记录表 (perf_material_study)
-- ----------------------------
DROP TABLE IF EXISTS `perf_material_study`;
CREATE TABLE `perf_material_study` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `employee_id` bigint(20) NOT NULL COMMENT '员工ID',
  `material_id` bigint(20) NOT NULL COMMENT '资料ID',
  `watch_progress` decimal(5,2) DEFAULT NULL COMMENT '视频观看进度百分比(0-100)',
  `stay_seconds` int NOT NULL DEFAULT '0' COMMENT '页面停留秒数',
  `complete_time` datetime NOT NULL COMMENT '完成时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_emp_material` (`employee_id`,`material_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='员工学习完成记录表';

-- ----------------------------
-- 3. 考核指标与题库表 (perf_indicator)
-- ----------------------------
DROP TABLE IF EXISTS `perf_indicator`;
CREATE TABLE `perf_indicator` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(255) NOT NULL COMMENT '指标名称或题目题干',
  `type` varchar(32) NOT NULL COMMENT '指标类型 (OBJECTIVE-客观题单选/判断, SUBJECTIVE-主观定性指标)',
  `job_level` varchar(16) NOT NULL DEFAULT 'P1' COMMENT '题库所属职级 (P1/P2/P3/P4)',
  `options_content` json DEFAULT NULL COMMENT '客观题选项内容(JSON格式,如[{"A":"选项1"},{"B":"选项2"}])',
  `standard_answer` varchar(64) DEFAULT NULL COMMENT '客观题标准答案',
  `creator_id` bigint(20) NOT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除 (0-未删除, 1-已删除)',
  PRIMARY KEY (`id`),
  KEY `idx_level_type` (`job_level`,`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='考核指标与题库表';

-- ----------------------------
-- 4. 考核模板表 (perf_template)
-- ----------------------------
DROP TABLE IF EXISTS `perf_template`;
CREATE TABLE `perf_template` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(128) NOT NULL COMMENT '考核模板名称 (如: 2026Q3研发部绩效考核)',
  `cycle_name` varchar(32) NOT NULL COMMENT '考核周期 (如: 2026-Q3，用于防重校验)',
  `manager_id` bigint(20) NOT NULL COMMENT '创建者(经理)ID',
  `status` varchar(32) NOT NULL DEFAULT 'UNPUBLISHED' COMMENT '状态 (UNPUBLISHED-未发布, PUBLISHED-已发布)',
  `deadline` datetime DEFAULT NULL COMMENT '考核截止时间(硬拦截)',
  `duration_minutes` int(11) DEFAULT NULL COMMENT '限制答题时长(分钟)',
  `objective_count` int(11) NOT NULL DEFAULT '0' COMMENT '每名员工按职级抽取的客观题数量',
  `subjective_count` int(11) NOT NULL DEFAULT '0' COMMENT '每名员工按职级抽取的主观题数量',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除 (0-未删除, 1-已删除)',
  PRIMARY KEY (`id`),
  KEY `idx_manager_id` (`manager_id`),
  KEY `idx_cycle_name` (`cycle_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='考核模板表';

-- ----------------------------
-- 5. 模板指标关联表 (perf_template_indicator)
-- ----------------------------
DROP TABLE IF EXISTS `perf_template_indicator`;
CREATE TABLE `perf_template_indicator` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `template_id` bigint(20) NOT NULL COMMENT '模板ID',
  `indicator_id` bigint(20) NOT NULL COMMENT '指标ID',
  `weight_ratio` decimal(5,2) NOT NULL DEFAULT '0.00' COMMENT '权重比例 (如 20.00 表示 20%)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tpl_ind` (`template_id`,`indicator_id`) COMMENT '同一模板下指标不能重复'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='模板指标关联表';

-- ----------------------------
-- 6. 绩效考核主表 (perf_review)
-- ----------------------------
DROP TABLE IF EXISTS `perf_review`;
CREATE TABLE `perf_review` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `employee_id` bigint(20) NOT NULL COMMENT '员工ID',
  `template_id` bigint(20) NOT NULL COMMENT '模板ID',
  `status` varchar(32) NOT NULL DEFAULT 'UNSTARTED' COMMENT '状态 (UNSTARTED-未开始, IN_PROGRESS-考试中, SUBMITTED-已出分申诉期, APPEALING-申诉复核中, GRADED-已归档, CHEATED-作弊零分)',
  `objective_score` decimal(8,2) DEFAULT '0.00' COMMENT '定量(客观)得分',
  `subjective_score` decimal(8,2) DEFAULT '0.00' COMMENT '定性(主观)得分',
  `total_score` decimal(8,2) DEFAULT '0.00' COMMENT '最终总分',
  `start_time` datetime DEFAULT NULL COMMENT '员工开始考试时间',
  `submit_time` datetime DEFAULT NULL COMMENT '员工提交时间',
  `manager_id` bigint(20) NOT NULL COMMENT '复核经理ID',
  `manager_comment` varchar(512) DEFAULT NULL COMMENT '经理总体评语',
  `appeal_count` int(11) NOT NULL DEFAULT '0' COMMENT '已提交申诉次数，最多2次',
  `appeal_deadline` datetime DEFAULT NULL COMMENT '申诉截止时间，考试结束后三天',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_emp_tpl` (`employee_id`,`template_id`) COMMENT '单人单周期(模板)防重提交唯一约束',
  KEY `idx_manager_status` (`manager_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='绩效考核主表';

-- ----------------------------
-- 7. 成绩申诉主表 (perf_review_appeal)
-- ----------------------------
DROP TABLE IF EXISTS `perf_review_appeal_detail`;
DROP TABLE IF EXISTS `perf_review_appeal`;
CREATE TABLE `perf_review_appeal` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `review_id` bigint(20) NOT NULL COMMENT '考核单ID',
  `employee_id` bigint(20) NOT NULL COMMENT '员工ID',
  `appeal_no` int(11) NOT NULL COMMENT '第几次申诉',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态 (PENDING-待复核, RESOLVED-已返回)',
  `reviewer_manager_id` bigint(20) DEFAULT NULL COMMENT '复核经理ID',
  `review_time` datetime DEFAULT NULL COMMENT '复核时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_review_status` (`review_id`,`status`),
  KEY `idx_status_create` (`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='成绩申诉主表';

-- ----------------------------
-- 7.1 成绩申诉明细表 (perf_review_appeal_detail)
-- ----------------------------
CREATE TABLE `perf_review_appeal_detail` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `appeal_id` bigint(20) NOT NULL COMMENT '申诉主表ID',
  `reason` varchar(1000) NOT NULL COMMENT '申诉理由',
  `review_opinion` varchar(1000) DEFAULT NULL COMMENT '复核意见',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_appeal_id` (`appeal_id`),
  KEY `idx_appeal_id` (`appeal_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='成绩申诉明细表';

-- ----------------------------
-- 8. 考核明细表 (perf_review_detail)
-- ----------------------------
DROP TABLE IF EXISTS `perf_review_detail`;
CREATE TABLE `perf_review_detail` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `review_id` bigint(20) NOT NULL COMMENT '考核主表ID',
  `indicator_id` bigint(20) NOT NULL COMMENT '指标ID',
  `employee_answer` text COMMENT '员工填写的自评内容或客观题答案',
  `objective_score` decimal(8,2) DEFAULT '0.00' COMMENT '系统客观打分',
  `ai_comment` varchar(512) DEFAULT NULL COMMENT 'AI预评语分析',
  `manager_score` decimal(8,2) DEFAULT '0.00' COMMENT '经理复审主观打分',
  `final_score` decimal(8,2) DEFAULT '0.00' COMMENT '该项最终得分(结合权重后)',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '单项明细状态 (PENDING-待批阅, REVIEWED-已批阅)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_ind` (`review_id`,`indicator_id`) COMMENT '防重约束'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='考核明细表';

-- ----------------------------
-- 9. 员工周期绩效统计表 (perf_employee_score)
-- ----------------------------
DROP TABLE IF EXISTS `perf_employee_score`;
CREATE TABLE `perf_employee_score` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `employee_id` bigint(20) NOT NULL COMMENT '员工ID',
  `cycle_name` varchar(32) NOT NULL COMMENT '考核周期 (如 2026-Q3)',
  `learning_score` decimal(8,2) NOT NULL DEFAULT '0.00' COMMENT '学习得分 (0-100)',
  `exam_score` decimal(8,2) NOT NULL DEFAULT '0.00' COMMENT '考试得分 (0-100)',
  `work_score` decimal(8,2) NOT NULL DEFAULT '0.00' COMMENT '工作实绩得分 (0-100)',
  `performance_score` decimal(8,2) NOT NULL DEFAULT '0.00' COMMENT '综合绩效分 = 学习*0.3 + 考试*0.4 + 实绩*0.3',
  `material_total` int(11) NOT NULL DEFAULT '0' COMMENT '周期内资料总数',
  `material_completed` int(11) NOT NULL DEFAULT '0' COMMENT '周期内已完成学习数',
  `exam_count` int(11) NOT NULL DEFAULT '0' COMMENT '周期内已出分考核单数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_emp_cycle` (`employee_id`,`cycle_name`),
  KEY `idx_cycle` (`cycle_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='员工周期绩效统计表';

-- ----------------------------
-- 10. 站内消息通知表 (sys_notification)
-- ----------------------------
DROP TABLE IF EXISTS `sys_notification`;
CREATE TABLE `sys_notification` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '接收用户ID',
  `title` varchar(128) NOT NULL COMMENT '通知标题',
  `content` varchar(512) NOT NULL COMMENT '通知内容',
  `type` varchar(32) NOT NULL COMMENT '通知类型',
  `link` varchar(256) DEFAULT NULL COMMENT '跳转路径',
  `is_read` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否已读 (0-未读, 1-已读)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_read` (`user_id`, `is_read`),
  KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='站内消息通知表';

-- ----------------------------
-- 11. 用户历史操作日志表 (sys_operation_log)
-- ----------------------------
DROP TABLE IF EXISTS `sys_operation_log`;
CREATE TABLE `sys_operation_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) DEFAULT NULL COMMENT '操作用户ID',
  `username` varchar(64) DEFAULT NULL COMMENT '工号',
  `real_name` varchar(64) DEFAULT NULL COMMENT '真实姓名',
  `role` varchar(32) DEFAULT NULL COMMENT '角色',
  `module` varchar(64) DEFAULT NULL COMMENT '业务模块',
  `action` varchar(128) NOT NULL COMMENT '操作描述',
  `status` varchar(16) NOT NULL DEFAULT 'SUCCESS' COMMENT '结果 (SUCCESS/FAIL)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`create_time`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户历史操作日志表';

-- ----------------------------
-- 12. 系统用户表 (sys_user)
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(64) NOT NULL COMMENT '工号',
  `password` varchar(128) NOT NULL COMMENT '登录密码',
  `real_name` varchar(64) NOT NULL COMMENT '真实姓名',
  `role` varchar(32) NOT NULL COMMENT '角色 (ADMIN-管理员, MANAGER-经理, EMPLOYEE-员工)',
  `dept_id` bigint(20) DEFAULT NULL COMMENT '所属部门ID',
  `job_level` varchar(16) DEFAULT NULL COMMENT '员工职级 (P1/P2/P3/P4)',
  `phone` varchar(32) DEFAULT NULL COMMENT '手机号',
  `login_fail_count` int(11) NOT NULL DEFAULT '0' COMMENT '连续登录失败次数',
  `lock_until` datetime DEFAULT NULL COMMENT '登录锁定截止时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统用户表';

-- 插入初始化用户数据（明文口令均为 123456，库内存储 BCrypt 哈希）
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `role`, `dept_id`, `job_level`, `phone`) VALUES
(1, 'admin', '$2b$10$TJoKANSxC1FQcAQbOK5cEOr3mFVIyIj4ml.pCMSO5hBGa3adSMyQG', '系统管理员', 'ADMIN', 0, NULL, '13800000000'),
(2, 'manager', '$2b$10$TJoKANSxC1FQcAQbOK5cEOr3mFVIyIj4ml.pCMSO5hBGa3adSMyQG', '研发部王经理', 'MANAGER', 10, NULL, '13800000001'),
(3, 'employee', '$2b$10$TJoKANSxC1FQcAQbOK5cEOr3mFVIyIj4ml.pCMSO5hBGa3adSMyQG', '开发张三', 'EMPLOYEE', 10, 'P1', '13800000002'),
(4, 'employee2', '$2b$10$TJoKANSxC1FQcAQbOK5cEOr3mFVIyIj4ml.pCMSO5hBGa3adSMyQG', '测试李四', 'EMPLOYEE', 10, 'P2', '13800000003'),
(5, 'employee3', '$2b$10$TJoKANSxC1FQcAQbOK5cEOr3mFVIyIj4ml.pCMSO5hBGa3adSMyQG', '产品赵六', 'EMPLOYEE', 20, 'P3', '13800000004'),
(6, 'employee4', '$2b$10$TJoKANSxC1FQcAQbOK5cEOr3mFVIyIj4ml.pCMSO5hBGa3adSMyQG', '架构孙七', 'EMPLOYEE', 10, 'P4', '13800000005'),
(7, 'manager2', '$2b$10$TJoKANSxC1FQcAQbOK5cEOr3mFVIyIj4ml.pCMSO5hBGa3adSMyQG', '产品部刘经理', 'MANAGER', 20, NULL, '13800000006'),
(8, 'employee5', '$2b$10$TJoKANSxC1FQcAQbOK5cEOr3mFVIyIj4ml.pCMSO5hBGa3adSMyQG', '开发周八', 'EMPLOYEE', 10, 'P1', '13800000007'),
(9, 'employee6', '$2b$10$TJoKANSxC1FQcAQbOK5cEOr3mFVIyIj4ml.pCMSO5hBGa3adSMyQG', '测试吴九', 'EMPLOYEE', 10, 'P2', '13800000008'),
(10, 'employee7', '$2b$10$TJoKANSxC1FQcAQbOK5cEOr3mFVIyIj4ml.pCMSO5hBGa3adSMyQG', '产品郑十', 'EMPLOYEE', 20, 'P3', '13800000009');

-- 插入操作日志演示数据（按时间倒序查看）
INSERT INTO `sys_operation_log` (`user_id`, `username`, `real_name`, `role`, `module`, `action`, `status`, `create_time`) VALUES
(1, 'admin', '系统管理员', 'ADMIN', '用户管理', '新增用户', 'SUCCESS', '2026-07-18 09:10:00'),
(2, 'manager', '研发部王经理', 'MANAGER', '资料管理', '发布学习资料', 'SUCCESS', '2026-07-18 10:20:00'),
(3, 'employee', '开发张三', 'EMPLOYEE', '认证', '用户登录', 'SUCCESS', '2026-07-18 11:05:00'),
(3, 'employee', '开发张三', 'EMPLOYEE', '学习中心', '完成资料学习', 'SUCCESS', '2026-07-18 11:30:00'),
(4, 'employee2', '测试李四', 'EMPLOYEE', '绩效考核', '提交考试答卷', 'SUCCESS', '2026-07-18 14:00:00'),
(4, 'employee2', '测试李四', 'EMPLOYEE', '绩效考核', '提交成绩申诉', 'SUCCESS', '2026-07-18 15:10:00'),
(2, 'manager', '研发部王经理', 'MANAGER', '绩效考核', '复核申诉', 'SUCCESS', '2026-07-19 09:40:00'),
(1, 'admin', '系统管理员', 'ADMIN', '用户管理', '重置用户密码', 'SUCCESS', '2026-07-19 16:00:00');

-- 插入部门数据
INSERT INTO `sys_dept` (`id`, `name`, `manager_id`) VALUES
(10, '研发部', 2),
(20, '产品部', 7),
(30, '市场部', NULL);

-- 插入学习资料演示数据
INSERT INTO `perf_material` (`id`, `title`, `type`, `url`, `description`, `deadline`, `creator_id`) VALUES
(1, '研发代码提交规范', 'DOC', 'https://www.w3.org/TR/WCAG21/', '研发代码提交规范要求，开发人员在提交代码之前，必须完成三项关键准备动作，以确保交付质量与后续追踪的可行性。首先，需在本地环境中对本次变更进行充分的自测。其次，代码必须经过同行或技术负责人的审查。最后，每次提交都需附带清晰的变更说明。', '2026-08-31 23:59:59', 2),
(2, '绩效自评填写指南', 'LINK', 'https://example.com/performance-guide', '说明如何围绕目标、结果、复盘和改进措施填写季度自评。', '2026-08-31 23:59:59', 2),
(3, '岗位 SOP 培训视频', 'VIDEO', 'https://www.w3schools.com/html/movie.mp4', '熟悉岗位标准作业流程，参与考核前建议完成学习。', '2026-09-15 23:59:59', 2),
(4, '敏捷开发流程指南', 'VIDEO', 'https://media.w3.org/2010/05/video/movie_300.mp4', '介绍Scrum和Kanban在日常开发中的应用。', '2026-09-30 23:59:59', 2),
(5, '产品需求文档(PRD)编写规范', 'VIDEO', 'https://mdn.github.io/learning-area/html/multimedia-and-embedding/video-and-audio-content/rabbit320.webm', '产品经理必看，规范化PRD输出。', '2026-08-31 23:59:59', 7);

INSERT INTO `perf_material_study` (`employee_id`, `material_id`, `watch_progress`, `stay_seconds`, `complete_time`) VALUES
(3, 1, NULL, 620, '2026-07-16 09:00:00'),
(3, 2, NULL, 650, '2026-07-16 10:30:00'),
(4, 1, NULL, 600, '2026-07-15 14:00:00'),
(5, 5, NULL, 720, '2026-07-14 11:00:00'),
(8, 1, NULL, 610, '2026-07-16 16:00:00');

-- 插入题库与指标演示数据
INSERT INTO `perf_indicator` (`id`, `name`, `type`, `job_level`, `options_content`, `standard_answer`, `creator_id`) VALUES
(101, 'P1-代码提交前是否必须完成自测并关联任务单？', 'OBJECTIVE', 'P1', JSON_OBJECT('A', '必须完成', 'B', '可选操作', 'C', '只需口头说明', 'D', '无需记录'), 'A', 1),
(102, 'P1-季度目标达成情况自评', 'SUBJECTIVE', 'P1', NULL, NULL, 1),
(103, 'P1-高优先级线上问题应在多久内响应？', 'OBJECTIVE', 'P1', JSON_OBJECT('A', '1个工作日内', 'B', '2小时内', 'C', '一周内', 'D', '下个迭代'), 'B', 1),
(104, 'P1-团队协作与改进措施总结', 'SUBJECTIVE', 'P1', NULL, NULL, 1),
(105, 'P1-需求变更进入开发前，最应优先确认哪项内容？', 'OBJECTIVE', 'P1', JSON_OBJECT('A', '变更背景与影响范围', 'B', '个人工作量感受', 'C', '上线后的宣传文案', 'D', '会议纪要格式'), 'A', 1),
(106, 'P2-代码合并到主分支前，以下哪项最符合团队质量要求？', 'OBJECTIVE', 'P2', JSON_OBJECT('A', '直接合并提高效率', 'B', '通过自测、评审和流水线检查', 'C', '仅通知测试人员即可', 'D', '等线上问题再回滚'), 'B', 1),
(107, 'P2-线上故障复盘报告通常不应缺少哪项内容？', 'OBJECTIVE', 'P2', JSON_OBJECT('A', '责任人批评', 'B', '根因、影响、修复与预防措施', 'C', '无关截图', 'D', '个人情绪说明'), 'B', 1),
(108, 'P2-处理客户紧急反馈时，第一步应当是什么？', 'OBJECTIVE', 'P2', JSON_OBJECT('A', '先关闭工单', 'B', '确认问题现象、影响范围和优先级', 'C', '直接修改数据库', 'D', '等待下次例会'), 'B', 1),
(109, 'P3-接口变更可能影响外部调用方时，应优先采用哪种做法？', 'OBJECTIVE', 'P3', JSON_OBJECT('A', '直接删除旧字段', 'B', '提供兼容期并同步变更说明', 'C', '只在代码注释中说明', 'D', '不需要通知'), 'B', 1),
(110, 'P3-以下哪项属于有效的绩效目标描述？', 'OBJECTIVE', 'P3', JSON_OBJECT('A', '尽量做好工作', 'B', '提升系统稳定性', 'C', 'Q3 将核心接口 P95 响应时间降低 30%', 'D', '多参加会议'), 'C', 1),
(111, 'P3-在团队协作中发现计划风险时，最合适的行为是？', 'OBJECTIVE', 'P3', JSON_OBJECT('A', '等截止日前再说明', 'B', '提前暴露风险并给出备选方案', 'C', '只私下抱怨', 'D', '忽略风险继续推进'), 'B', 1),
(112, 'P4-员工处理敏感业务数据时，以下哪项是正确做法？', 'OBJECTIVE', 'P4', JSON_OBJECT('A', '导出到个人网盘备份', 'B', '按权限访问并避免传播明文数据', 'C', '发送到群聊方便排查', 'D', '长期保存在本地桌面'), 'B', 1),
(113, 'P2-本季度关键成果与量化指标说明', 'SUBJECTIVE', 'P2', NULL, NULL, 1),
(114, 'P2-一次典型问题的分析、处理和复盘过程', 'SUBJECTIVE', 'P2', NULL, NULL, 1),
(115, 'P3-个人能力成长与学习成果总结', 'SUBJECTIVE', 'P3', NULL, NULL, 1),
(116, 'P3-跨部门协作中的贡献、困难与改进建议', 'SUBJECTIVE', 'P3', NULL, NULL, 1),
(117, 'P4-下季度工作目标与风险预案', 'SUBJECTIVE', 'P4', NULL, NULL, 1),
(118, 'P4-对团队流程、工具或规范建设的改进建议', 'SUBJECTIVE', 'P4', NULL, NULL, 1),
(119, 'P4-客户价值或用户体验提升案例说明', 'SUBJECTIVE', 'P4', NULL, NULL, 1),
(120, 'P4-个人对绩效结果的自我评价与证据材料说明', 'SUBJECTIVE', 'P4', NULL, NULL, 1),
(121, 'P4-设计跨系统方案时，首要关注哪项内容？', 'OBJECTIVE', 'P4', JSON_OBJECT('A', '单点实现速度', 'B', '边界、容量、故障隔离与演进成本', 'C', '页面颜色', 'D', '个人偏好'), 'B', 1),
(122, 'P4-推进技术方案落地时，最有效的协作方式是？', 'OBJECTIVE', 'P4', JSON_OBJECT('A', '只发布最终结论', 'B', '拆解里程碑并同步风险和决策依据', 'C', '避免评审', 'D', '全部交给新人'), 'B', 1);

-- 插入考核模板与题目关联
INSERT INTO `perf_template` (`id`, `name`, `cycle_name`, `manager_id`, `status`, `deadline`, `duration_minutes`, `objective_count`, `subjective_count`) VALUES
(1001, '2026-Q3 研发部绩效考核', '2026-Q3', 2, 'PUBLISHED', '2026-12-31 23:59:59', 120, 2, 2),
(1002, '2026-Q2 研发部绩效考核', '2026-Q2', 2, 'PUBLISHED', '2026-06-30 23:59:59', 120, 2, 2),
(1003, '2026-Q3 产品部绩效考核', '2026-Q3', 7, 'PUBLISHED', '2026-12-31 23:59:59', 120, 2, 2),
(1004, '2025-Q4 研发部年度绩效考核', '2025-Q4', 2, 'PUBLISHED', '2025-12-31 23:59:59', 120, 2, 2),
(1005, '2025-Q3 产品部绩效考核', '2025-Q3', 7, 'PUBLISHED', '2025-09-30 23:59:59', 120, 2, 2),
(1006, '2024-Q4 架构专项绩效考核', '2024-Q4', 2, 'PUBLISHED', '2024-12-31 23:59:59', 120, 2, 2);

INSERT INTO `perf_template_indicator` (`id`, `template_id`, `indicator_id`, `weight_ratio`) VALUES
(1, 1001, 101, 25.00),
(2, 1001, 102, 25.00),
(3, 1001, 103, 25.00),
(4, 1001, 104, 25.00),
(5, 1001, 106, 25.00),
(6, 1001, 107, 25.00),
(7, 1001, 113, 25.00),
(8, 1001, 114, 25.00),
(9, 1001, 109, 25.00),
(10, 1001, 110, 25.00),
(11, 1001, 115, 25.00),
(12, 1001, 116, 25.00),
(13, 1002, 101, 25.00),
(14, 1002, 102, 25.00),
(15, 1002, 103, 25.00),
(16, 1002, 104, 25.00),
(17, 1003, 109, 25.00),
(18, 1003, 110, 25.00),
(19, 1003, 115, 25.00),
(20, 1003, 116, 25.00),
(21, 1004, 106, 25.00),
(22, 1004, 107, 25.00),
(23, 1004, 113, 25.00),
(24, 1004, 114, 25.00),
(25, 1005, 109, 25.00),
(26, 1005, 110, 25.00),
(27, 1005, 115, 25.00),
(28, 1005, 116, 25.00),
(29, 1006, 112, 25.00),
(30, 1006, 121, 25.00),
(31, 1006, 117, 25.00),
(32, 1006, 118, 25.00);

-- 插入考核单演示数据：张三未开始、李四提交申诉中、赵六已归档
INSERT INTO `perf_review` (`id`, `employee_id`, `template_id`, `status`, `objective_score`, `subjective_score`, `total_score`, `start_time`, `submit_time`, `manager_id`, `manager_comment`, `appeal_count`, `appeal_deadline`) VALUES
(2001, 3, 1001, 'UNSTARTED', 0.00, 0.00, 0.00, NULL, NULL, 2, NULL, 0, NULL),
(2002, 4, 1001, 'APPEALING', 50.00, 50.00, 100.00, '2026-07-16 09:00:00', '2026-07-16 10:00:00', 2, NULL, 1, '2026-12-31 23:59:59'),
(2003, 5, 1001, 'GRADED', 50.00, 43.00, 93.00, '2026-07-15 08:30:00', '2026-07-15 09:30:00', 2, '目标完成质量高，复盘充分，建议继续保持跨团队推进能力。', 0, '2026-07-18 09:30:00'),
(2004, 3, 1002, 'GRADED', 50.00, 45.00, 95.00, '2026-06-25 10:00:00', '2026-06-25 11:30:00', 2, 'Q2表现优异，代码质量稳定。', 0, '2026-06-28 11:30:00'),
(2005, 8, 1001, 'SUBMITTED', 25.00, 40.00, 65.00, '2026-07-16 14:00:00', '2026-07-16 15:30:00', 2, NULL, 0, '2026-12-31 23:59:59'),
(2006, 10, 1003, 'IN_PROGRESS', 0.00, 0.00, 0.00, '2026-07-17 10:00:00', NULL, 7, NULL, 0, NULL),
(2007, 4, 1004, 'GRADED', 50.00, 44.00, 94.00, '2025-12-20 09:00:00', '2025-12-20 10:30:00', 2, '全年交付稳定，故障复盘和自动化测试建设有明显成效。', 0, '2025-12-23 10:30:00'),
(2008, 5, 1005, 'GRADED', 50.00, 42.00, 92.00, '2025-09-25 14:00:00', '2025-09-25 15:20:00', 7, '产品规划清晰，跨部门协作推进顺畅，用户体验优化结果明显。', 0, '2025-09-28 15:20:00'),
(2009, 6, 1006, 'GRADED', 50.00, 46.00, 96.00, '2024-12-18 09:30:00', '2024-12-18 11:00:00', 2, '架构方案兼顾容量、稳定性和演进成本，风险预案充分。', 0, '2024-12-21 11:00:00'),
(2010, 10, 1005, 'GRADED', 25.00, 39.00, 64.00, '2025-09-26 09:00:00', '2025-09-26 10:00:00', 7, '目标拆解还不够量化，建议加强数据分析和里程碑管理。', 1, '2025-09-29 10:00:00');

INSERT INTO `perf_review_appeal` (`id`, `review_id`, `employee_id`, `appeal_no`, `status`) VALUES
(4001, 2002, 4, 1, 'PENDING');

INSERT INTO `perf_review_appeal_detail` (`id`, `appeal_id`, `reason`, `review_opinion`) VALUES
(5001, 4001, '我认为主观题回答已覆盖关键成果和复盘过程，希望经理重新核对主观题得分。', NULL);

INSERT INTO `perf_review_detail` (`id`, `review_id`, `indicator_id`, `employee_answer`, `objective_score`, `ai_comment`, `manager_score`, `final_score`, `status`) VALUES
(3001, 2001, 101, NULL, 0.00, NULL, 0.00, 0.00, 'PENDING'),
(3002, 2001, 102, NULL, 0.00, NULL, 0.00, 0.00, 'PENDING'),
(3003, 2001, 103, NULL, 0.00, NULL, 0.00, 0.00, 'PENDING'),
(3004, 2001, 104, NULL, 0.00, NULL, 0.00, 0.00, 'PENDING'),
(3005, 2002, 106, 'B', 25.00, NULL, 0.00, 25.00, 'REVIEWED'),
(3006, 2002, 113, '本季度完成用户中心重构与接口性能优化，按期交付核心需求，并补齐接口文档。', 0.00, '自评内容聚焦目标完成与工程质量，建议经理结合交付结果给出较高评分。', 25.00, 25.00, 'REVIEWED'),
(3007, 2002, 107, 'B', 25.00, NULL, 0.00, 25.00, 'REVIEWED'),
(3008, 2002, 114, '主动参与测试联调，沉淀了故障排查清单，后续计划加强需求前置沟通。', 0.00, '体现出协作意识和持续改进思路，建议重点关注改进措施落地情况。', 25.00, 25.00, 'REVIEWED'),
(3009, 2003, 109, 'B', 25.00, NULL, 0.00, 25.00, 'REVIEWED'),
(3010, 2003, 115, '完成核心产品方案设计与上线复盘，推动关键需求按期验收。', 0.00, '目标达成充分，结果描述清晰。', 22.00, 22.00, 'REVIEWED'),
(3011, 2003, 110, 'C', 25.00, NULL, 0.00, 25.00, 'REVIEWED'),
(3012, 2003, 116, '组织跨部门评审并优化反馈机制，提升了协作效率。', 0.00, '协作表现突出，改进措施可执行。', 21.00, 21.00, 'REVIEWED'),
(3013, 2004, 101, 'A', 25.00, NULL, 0.00, 25.00, 'REVIEWED'),
(3014, 2004, 102, 'Q2完成了基础模块搭建，零线上故障。', 0.00, '目标明确，结果良好。', 23.00, 23.00, 'REVIEWED'),
(3015, 2004, 103, 'B', 25.00, NULL, 0.00, 25.00, 'REVIEWED'),
(3016, 2004, 104, '与测试团队配合良好，提升了自动化覆盖率。', 0.00, '协作积极，有具体产出。', 22.00, 22.00, 'REVIEWED'),
(3017, 2005, 101, 'B', 0.00, NULL, 0.00, 0.00, 'REVIEWED'),
(3018, 2005, 102, '按时完成开发任务，但有几次提测打回。', 0.00, '如实反映了问题，建议加强自测。', 20.00, 20.00, 'REVIEWED'),
(3019, 2005, 103, 'B', 25.00, NULL, 0.00, 25.00, 'REVIEWED'),
(3020, 2005, 104, '后续会增加单元测试时间。', 0.00, '改进措施明确。', 20.00, 20.00, 'REVIEWED'),
(3021, 2006, 109, NULL, 0.00, NULL, 0.00, 0.00, 'PENDING'),
(3022, 2006, 110, NULL, 0.00, NULL, 0.00, 0.00, 'PENDING'),
(3023, 2006, 115, NULL, 0.00, NULL, 0.00, 0.00, 'PENDING'),
(3024, 2006, 116, NULL, 0.00, NULL, 0.00, 0.00, 'PENDING'),
(3025, 2007, 106, 'B', 25.00, NULL, 0.00, 25.00, 'REVIEWED'),
(3026, 2007, 107, 'B', 25.00, NULL, 0.00, 25.00, 'REVIEWED'),
(3027, 2007, 113, '全年完成支付链路重构、接口自动化测试和质量门禁建设，核心服务稳定性持续提升。', 0.00, '内容覆盖关键成果、质量建设和稳定性指标，结果可信。', 22.00, 22.00, 'REVIEWED'),
(3028, 2007, 114, '针对一次线上超时问题完成根因分析，补充监控告警和容量压测流程。', 0.00, '复盘完整，预防措施具体，具备复用价值。', 22.00, 22.00, 'REVIEWED'),
(3029, 2008, 109, 'B', 25.00, NULL, 0.00, 25.00, 'REVIEWED'),
(3030, 2008, 110, 'C', 25.00, NULL, 0.00, 25.00, 'REVIEWED'),
(3031, 2008, 115, '完成会员增长方案设计，通过A/B实验提升转化率，并沉淀数据看板。', 0.00, '目标清晰，指标可量化，体现数据驱动。', 21.00, 21.00, 'REVIEWED'),
(3032, 2008, 116, '组织研发、运营和客服完成需求评审，减少返工并缩短上线周期。', 0.00, '跨部门推进效果较好，沟通机制有改善。', 21.00, 21.00, 'REVIEWED'),
(3033, 2009, 112, 'B', 25.00, NULL, 0.00, 25.00, 'REVIEWED'),
(3034, 2009, 121, 'B', 25.00, NULL, 0.00, 25.00, 'REVIEWED'),
(3035, 2009, 117, '主导多系统治理方案，完成容量评估、故障隔离和灰度发布预案。', 0.00, '架构视角完整，风险识别充分。', 23.00, 23.00, 'REVIEWED'),
(3036, 2009, 118, '推动统一日志规范和链路追踪建设，显著提升问题定位效率。', 0.00, '流程建设对团队有长期收益。', 23.00, 23.00, 'REVIEWED'),
(3037, 2010, 109, 'A', 0.00, NULL, 0.00, 0.00, 'REVIEWED'),
(3038, 2010, 110, 'C', 25.00, NULL, 0.00, 25.00, 'REVIEWED'),
(3039, 2010, 115, '参与需求调研和竞品分析，但目标量化不足。', 0.00, '能说明参与过程，但结果指标不足。', 19.00, 19.00, 'REVIEWED'),
(3040, 2010, 116, '协作中能及时反馈问题，后续需要提高方案完整度。', 0.00, '协作态度积极，但改进措施仍需细化。', 20.00, 20.00, 'REVIEWED');

SET FOREIGN_KEY_CHECKS = 1;