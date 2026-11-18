-- ============================================================
-- 宿舍维修系统 · 演示数据（可重复执行：先清表再灌）
-- 时间用 NOW() 相对量，什么时候导入都像"今天发生的"
-- ============================================================

USE dorm_repair;

DELETE FROM t_work_order_event;
DELETE FROM t_work_order_photo;
DELETE FROM t_work_order;
DELETE FROM t_part_request;
DELETE FROM t_exception_record;
DELETE FROM t_user;
DELETE FROM t_building;
DELETE FROM t_part;
DELETE FROM t_config;
DELETE FROM t_seq;

-- 楼栋
INSERT INTO t_building (id, name, floors) VALUES
  ('B7', '东区7号楼', '2,3,4,5'),
  ('B8', '东区8号楼', '1,2,3');

-- 人员：3 个学生、2 个师傅、1 个管理员
INSERT INTO t_user (id, role, name, team, building_id, room, phone) VALUES
  ('S001', 'STUDENT', '张同学', NULL, 'B7', '402', '13800000001'),
  ('S002', 'STUDENT', '李同学', NULL, 'B7', '508', '13800000002'),
  ('S003', 'STUDENT', '王同学', NULL, 'B8', '205', '13800000003'),
  ('W0428', 'WORKER', '李建国', '水电班', 'B7', NULL, '13900000001'),
  ('W0512', 'WORKER', '王海涛', '综合班', 'B8', NULL, '13900000002'),
  ('A001', 'ADMIN', '王主任', NULL, NULL, NULL, '13700000001');

-- 配置：三个阈值，代码里不许写死
INSERT INTO t_config (k, v, note) VALUES
  ('budgetLine', '200', '配件预算线（元），超过进例外清单'),
  ('verifyHours', '168', '待验收超 168 小时系统自动验收（T1）'),
  ('archiveDays', '7', '已完成满 7 天系统自动归档（T2）');

-- 配件台账
INSERT INTO t_part (name, spec, unit, price, stock) VALUES
  ('不锈钢软管', '60cm', '根', 18.00, 24),
  ('角阀', '4分', '个', 26.00, 12),
  ('面板模块', '五孔 10A', '个', 43.00, 6),
  ('阀芯', '陶瓷 35mm', '个', 38.00, 9),
  ('玻璃胶', '中性 300ml', '支', 12.00, 30),
  ('PVC 弯头', '50mm', '个', 6.00, 40);

-- 工单：覆盖 6 个状态
INSERT INTO t_work_order
  (no, status, version, building_id, floor, room, student_id, student_name,
   description, visit_window, category, level, level_source, ai_basis,
   worker_id, worker_name, rework_count, created_at, assigned_at, arrive_at,
   finish_at, sign_at, status_at)
VALUES
  ('BX20261118001', 'PENDING', 1, 'B7', 4, '402', 'S001', '张同学',
   '洗手盆软管一直滴水，水桶快接满了', '上午 08:00-12:00', '给排水', '紧急', 'AI',
   '命中「滴水/软管」，归给排水；描述提到「快接满了」，按浸水风险算安全类，定紧急',
   NULL, NULL, 0,
   NOW() - INTERVAL 22 MINUTE, NULL, NULL, NULL, NULL, NOW() - INTERVAL 22 MINUTE),

  ('BX20261118002', 'ASSIGNED', 2, 'B7', 5, '508', 'S002', '李同学',
   '插座打火，有焦味，不敢用了', '下午 14:00-18:00', '电气', '紧急', 'AI',
   '命中「打火/焦味」，归电气；有起火风险，按安全类定紧急',
   'W0428', '李建国', 0,
   NOW() - INTERVAL 2 HOUR, NOW() - INTERVAL 95 MINUTE, NULL, NULL, NULL, NOW() - INTERVAL 95 MINUTE),

  ('BX20261118003', 'REPAIRING', 3, 'B7', 3, '317', 'S001', '张同学',
   '水龙头关不严，一直滴', '全天（08:00-22:00）', '给排水', '较急', 'AI',
   '命中「水龙头/关不严」，归给排水；不影响使用但浪费水，定较急',
   'W0428', '李建国', 0,
   NOW() - INTERVAL 4 HOUR, NOW() - INTERVAL 3 HOUR, NOW() - INTERVAL 40 MINUTE,
   NULL, NULL, NOW() - INTERVAL 40 MINUTE),

  ('BX20261118004', 'PARTS', 4, 'B7', 2, '217', 'S002', '李同学',
   '卫生间下水慢，怀疑堵了', '晚上 18:00-22:00', '给排水', '普通', 'AI',
   '命中「下水慢/堵」，归给排水；不影响基本生活，定普通',
   'W0428', '李建国', 0,
   NOW() - INTERVAL 6 HOUR, NOW() - INTERVAL 5 HOUR, NOW() - INTERVAL 2 HOUR,
   NULL, NULL, NOW() - INTERVAL 90 MINUTE),

  ('BX20261117005', 'VERIFYING', 4, 'B7', 4, '402', 'S001', '张同学',
   '空调不制冷，出热风', '上午 08:00-12:00', '电器', '普通', 'AI',
   '命中「空调/不制冷」，归电器；不影响基本生活，定普通',
   'W0428', '李建国', 0,
   NOW() - INTERVAL 26 HOUR, NOW() - INTERVAL 25 HOUR, NOW() - INTERVAL 24 HOUR,
   NOW() - INTERVAL 1 HOUR, NULL, NOW() - INTERVAL 1 HOUR),

  ('BX20261116006', 'DONE', 5, 'B8', 2, '205', 'S003', '王同学',
   '窗户把手松了，关不严', '下午 14:00-18:00', '门窗家具', '普通', 'AI',
   '命中「窗户/把手」，归门窗家具；不影响基本生活，定普通',
   'W0512', '王海涛', 0,
   NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 47 HOUR, NOW() - INTERVAL 46 HOUR,
   NOW() - INTERVAL 45 HOUR, NOW() - INTERVAL 44 HOUR, NOW() - INTERVAL 44 HOUR);

-- 照片：现场照每单 1 张；完工照只有已完工的那单有
INSERT INTO t_work_order_photo (order_no, kind, url, uploaded_by, created_at) VALUES
  ('BX20261118001', 'BEFORE', 'demo://before/001.jpg', 'S001', NOW() - INTERVAL 22 MINUTE),
  ('BX20261118002', 'BEFORE', 'demo://before/002.jpg', 'S002', NOW() - INTERVAL 2 HOUR),
  ('BX20261118003', 'BEFORE', 'demo://before/003.jpg', 'S001', NOW() - INTERVAL 4 HOUR),
  ('BX20261118004', 'BEFORE', 'demo://before/004.jpg', 'S002', NOW() - INTERVAL 6 HOUR),
  ('BX20261117005', 'BEFORE', 'demo://before/005.jpg', 'S001', NOW() - INTERVAL 26 HOUR),
  ('BX20261117005', 'AFTER',  'demo://after/005.jpg',  'W0428', NOW() - INTERVAL 1 HOUR),
  ('BX20261116006', 'BEFORE', 'demo://before/006.jpg', 'S003', NOW() - INTERVAL 2 DAY),
  ('BX20261116006', 'AFTER',  'demo://after/006.jpg',  'W0512', NOW() - INTERVAL 45 HOUR);

-- 配件申请：004 这单在等配件（预算内 38 元）
INSERT INTO t_part_request (order_no, part_id, part_name, amount, eta_date, over_budget, status, created_by, created_at)
VALUES ('BX20261118004', 4, '阀芯', 38.00, CURRENT_DATE + INTERVAL 1 DAY, 0, 'REQUESTED', 'W0428', NOW() - INTERVAL 90 MINUTE);

-- 事件时间线（manual=0 表示系统自动，学生提交与师傅打卡都算 1）
INSERT INTO t_work_order_event (order_no, at, event, actor_type, actor_id, actor_name, manual, note) VALUES
  ('BX20261118001', NOW() - INTERVAL 22 MINUTE, 'SUBMIT', 'STUDENT', 'S001', '张同学', 1, '提交报修：洗手盆软管一直滴水，水桶快接满了'),
  ('BX20261118002', NOW() - INTERVAL 2 HOUR,   'SUBMIT', 'STUDENT', 'S002', '李同学', 1, '提交报修：插座打火，有焦味，不敢用了'),
  ('BX20261118002', NOW() - INTERVAL 95 MINUTE,'ASSIGN', 'SYSTEM',  'system', '系统自动派工', 0, '派给 李建国（水电班）'),
  ('BX20261118003', NOW() - INTERVAL 4 HOUR,   'SUBMIT', 'STUDENT', 'S001', '张同学', 1, '提交报修：水龙头关不严，一直滴'),
  ('BX20261118003', NOW() - INTERVAL 3 HOUR,   'ASSIGN', 'SYSTEM',  'system', '系统自动派工', 0, '派给 李建国（水电班）'),
  ('BX20261118003', NOW() - INTERVAL 40 MINUTE,'ARRIVE', 'WORKER',  'W0428', '李建国', 1, '到门打卡'),
  ('BX20261118004', NOW() - INTERVAL 6 HOUR,   'SUBMIT', 'STUDENT', 'S002', '李同学', 1, '提交报修：卫生间下水慢，怀疑堵了'),
  ('BX20261118004', NOW() - INTERVAL 5 HOUR,   'ASSIGN', 'SYSTEM',  'system', '系统自动派工', 0, '派给 李建国（水电班）'),
  ('BX20261118004', NOW() - INTERVAL 2 HOUR,   'ARRIVE', 'WORKER',  'W0428', '李建国', 1, '到门打卡'),
  ('BX20261118004', NOW() - INTERVAL 90 MINUTE,'NEED_PARTS', 'WORKER', 'W0428', '李建国', 1, '缺件：阀芯 ¥38'),
  ('BX20261117005', NOW() - INTERVAL 26 HOUR,  'SUBMIT', 'STUDENT', 'S001', '张同学', 1, '提交报修：空调不制冷，出热风'),
  ('BX20261117005', NOW() - INTERVAL 25 HOUR,  'ASSIGN', 'SYSTEM',  'system', '系统自动派工', 0, '派给 李建国（水电班）'),
  ('BX20261117005', NOW() - INTERVAL 24 HOUR,  'ARRIVE', 'WORKER',  'W0428', '李建国', 1, '到门打卡'),
  ('BX20261117005', NOW() - INTERVAL 1 HOUR,   'FINISH', 'WORKER',  'W0428', '李建国', 1, '完工提交：清洗滤网，加氟'),
  ('BX20261116006', NOW() - INTERVAL 2 DAY,    'SUBMIT', 'STUDENT', 'S003', '王同学', 1, '提交报修：窗户把手松了，关不严'),
  ('BX20261116006', NOW() - INTERVAL 47 HOUR,  'ASSIGN', 'SYSTEM',  'system', '系统自动派工', 0, '派给 王海涛（综合班）'),
  ('BX20261116006', NOW() - INTERVAL 46 HOUR,  'ARRIVE', 'WORKER',  'W0512', '王海涛', 1, '到门打卡'),
  ('BX20261116006', NOW() - INTERVAL 45 HOUR,  'FINISH', 'WORKER',  'W0512', '王海涛', 1, '完工提交：紧固把手，上润滑油'),
  ('BX20261116006', NOW() - INTERVAL 44 HOUR,  'ACCEPT', 'STUDENT','S003', '王同学', 1, '验收通过，签字人 王同学');

-- 单号序号：今天已经用到 004
INSERT INTO t_seq (day, name, next_val) VALUES (DATE_FORMAT(CURRENT_DATE, '%Y%m%d'), 'BX', 5);
