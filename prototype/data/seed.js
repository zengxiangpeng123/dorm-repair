/* ============================================================
 * 唯一一份假数据。所有页面都读这里，不再各自硬编码。
 * 覆盖 9 个状态，每单都有完整事件流，够演示一条闭环。
 * ============================================================ */

window.SEED = {
  meta: {
    brand: '宿舍维修',
    school: '示范大学',
    version: 'V5.0',
    today: '2026-11-17',
    budget: 200,          // 单次采购预算线（元）
    verifyHours: 168,     // T1 待验收超时（小时）= 7 天
    archiveDays: 7        // T2 已完成归档（天）
  },

  buildings: [
    { id: 'B7', name: '东区7号楼', floors: [2, 3, 4, 5] },
    { id: 'B8', name: '东区8号楼', floors: [1, 2, 3] }
  ],

  workers: [
    { id: 'W0428', name: '李建国', team: '水电班', buildings: ['B7'], phone: '138****0123' },
    { id: 'W0512', name: '王海涛', team: '综合班', buildings: ['B8'], phone: '139****0456' }
  ],

  categories: ['给排水', '电气', '电器', '门窗家具', '网络'],

  levels: [
    { id: '紧急', color: 'danger', rule: '安全类（漏电、冒烟、爆管）或漏水已影响使用' },
    { id: '较急', color: 'warn', rule: '影响基本生活，但可短时忍受' },
    { id: '普通', color: 'muted', rule: '不影响使用，可排期处理' }
  ],

  /* 学生的备选时段（选填，默认全天） */
  windows: ['全天', '上午 08:00-12:00', '下午 14:00-18:00', '晚上 18:00-22:00'],

  orders: [
    {
      no: 'BX20261117012', status: 'PENDING', building: 'B8', room: '205',
      category: '网络', level: '较急',
      desc: '宿舍网口插上没反应，隔壁房间是好的，怀疑是本屋的交换机端口坏了。',
      photosBefore: 1, window: '全天',
      createdAt: '2026-11-17 07:40',
      aiConfidence: 0.9, aiBasis: '命中「网口/没反应」，归网络；影响上课用网，定较急',
      aiCategory: '网络', aiLevel: '较急',
      manual: 1,
      events: [
        { at: '2026-11-17 07:40', event: 'SUBMIT', actor: 'student:周同学', manual: 1, note: '照片 1 张、描述 27 字，时段 全天' }
      ]
    },
    {
      no: 'BX20261117001', status: 'PENDING', building: 'B7', room: '402',
      category: '给排水', level: '紧急',
      desc: '洗手盆下面的软管一直滴水，水桶快接满了，麻烦师傅尽快来看看。',
      photosBefore: 1, window: '晚上 18:00-22:00',
      createdAt: '2026-11-17 09:12', aiConfidence: 0.98,
      aiBasis: '命中「滴水/软管」，归给排水；描述提到「快接满了」，按浸水风险算安全类，直接定紧急，不再往下判',
      aiCategory: '给排水', aiLevel: '紧急',
      workerId: null, manual: 1,
      events: [
        { at: '2026-11-17 09:12', event: 'SUBMIT', actor: 'student:张同学', manual: 1, note: '照片 1 张、描述 24 字，时段 晚上' }
      ]
    },
    {
      no: 'BX20261117002', status: 'ASSIGNED', building: 'B7', room: '415',
      category: '电气', level: '较急',
      desc: '书桌旁的插座没有电，台灯和充电器都不通。',
      photosBefore: 1, window: '全天',
      createdAt: '2026-11-17 09:40', assignedAt: '2026-11-17 09:52',
      aiConfidence: 0.91, aiBasis: '命中「插座/没电」，归电气；非安全类，定较急',
      aiCategory: '电气', aiLevel: '较急',
      workerId: 'W0428', manual: 1,
      events: [
        { at: '2026-11-17 09:40', event: 'SUBMIT', actor: 'student:张同学', manual: 1, note: '照片 1 张、描述 20 字，时段 全天' },
        { at: '2026-11-17 09:52', event: 'ASSIGN', actor: 'system', manual: 0, note: '本栋当班师傅：李建国' }
      ]
    },
    {
      no: 'BX20261117003', status: 'REPAIRING', building: 'B7', room: '305',
      category: '给排水', level: '较急',
      desc: '浴室地漏返水，洗澡水漫到门口了。',
      photosBefore: 1, window: '下午 14:00-18:00',
      createdAt: '2026-11-17 08:30', assignedAt: '2026-11-17 09:05', arriveAt: '2026-11-17 10:20',
      aiConfidence: 0.94, aiBasis: '命中「返水/地漏」，归给排水；影响使用定较急',
      aiCategory: '给排水', aiLevel: '较急',
      workerId: 'W0428', manual: 1,
      events: [
        { at: '2026-11-17 08:30', event: 'SUBMIT', actor: 'student:王同学', manual: 1, note: '照片 1 张、描述 18 字，时段 下午' },
        { at: '2026-11-17 09:05', event: 'ASSIGN', actor: 'system', manual: 0, note: '本栋当班师傅：李建国' },
        { at: '2026-11-17 10:20', event: 'ARRIVE', actor: 'worker:李建国', manual: 1, note: '进楼打卡（本批 5 单同时到场）' }
      ]
    },
    {
      no: 'BX20261116004', status: 'PARTS', building: 'B7', room: '402',
      category: '门窗家具', level: '普通',
      desc: '衣柜合页断了，门掉下来一半。',
      photosBefore: 1, window: '全天',
      createdAt: '2026-11-16 15:10', assignedAt: '2026-11-16 16:00', arriveAt: '2026-11-16 16:40',
      aiConfidence: 0.88, aiBasis: '命中「合页/衣柜」，归门窗家具；不影响使用定普通',
      aiCategory: '门窗家具', aiLevel: '普通',
      workerId: 'W0428', manual: 1,
      parts: { name: '衣柜合页 A2', amount: 38, etaDate: '2026-11-19', overBudget: false },
      events: [
        { at: '2026-11-16 15:10', event: 'SUBMIT', actor: 'student:张同学', manual: 1, note: '照片 1 张、描述 14 字，时段 全天' },
        { at: '2026-11-16 16:00', event: 'ASSIGN', actor: 'system', manual: 0, note: '本栋当班师傅：李建国' },
        { at: '2026-11-16 16:40', event: 'ARRIVE', actor: 'worker:李建国', manual: 1, note: '进楼打卡' },
        { at: '2026-11-16 16:58', event: 'NEED_PARTS', actor: 'worker:李建国', manual: 1, note: '合页 A2 38 元，预计 11-19 到货（预算内自动下单）' }
      ]
    },
    {
      no: 'BX20261116006', status: 'PARTS', building: 'B7', room: '217',
      category: '电器', level: '普通',
      desc: '洗衣机脱水时剧烈晃动，怀疑是减震器坏了。',
      photosBefore: 1, window: '全天',
      createdAt: '2026-11-16 11:20', assignedAt: '2026-11-16 13:00', arriveAt: '2026-11-16 14:10',
      aiConfidence: 0.86, aiBasis: '命中「洗衣机/晃动」，归电器；不影响基本生活定普通',
      aiCategory: '电器', aiLevel: '普通',
      workerId: 'W0512', manual: 1,
      parts: { name: '滚筒减震器 ×4', amount: 480, etaDate: '2026-11-20', overBudget: true },
      events: [
        { at: '2026-11-16 11:20', event: 'SUBMIT', actor: 'student:刘同学', manual: 1, note: '照片 1 张、描述 20 字，时段 全天' },
        { at: '2026-11-16 13:00', event: 'ASSIGN', actor: 'system', manual: 0, note: '本栋当班师傅：李建国' },
        { at: '2026-11-16 14:10', event: 'ARRIVE', actor: 'worker:李建国', manual: 1, note: '进楼打卡' },
        { at: '2026-11-16 14:35', event: 'NEED_PARTS', actor: 'worker:李建国', manual: 1, note: '减震器 480 元，超预算线 200 元 → 转管理端待批，还没批下来' }
      ]
    },
    {
      no: 'BX20261115005', status: 'VERIFYING', building: 'B7', room: '301',
      category: '电器', level: '普通',
      desc: '空调不制冷，出风口只有自然风。',
      photosBefore: 1, window: '下午 14:00-18:00',
      createdAt: '2026-11-15 10:00', assignedAt: '2026-11-15 11:00', arriveAt: '2026-11-15 15:10',
      finishAt: '2026-11-15 16:40', photosAfter: 0,
      aiConfidence: 0.93, aiBasis: '命中「空调/不制冷」，归电器；不影响基本生活，定普通',
      aiCategory: '电器', aiLevel: '普通',
      workerId: 'W0428', manual: 1,
      events: [
        { at: '2026-11-15 10:00', event: 'SUBMIT', actor: 'student:陈同学', manual: 1, note: '照片 1 张、描述 13 字，时段 下午' },
        { at: '2026-11-15 11:00', event: 'ASSIGN', actor: 'system', manual: 0, note: '本栋当班师傅：李建国' },
        { at: '2026-11-15 15:10', event: 'ARRIVE', actor: 'worker:李建国', manual: 1, note: '进楼打卡' },
        { at: '2026-11-15 16:40', event: 'FINISH', actor: 'worker:李建国', manual: 1, note: '清洗滤网并加雪种，完工照 0 张 ← 数据对不上的样本' }
      ]
    },
    {
      no: 'BX20261114001', status: 'REWORKING', building: 'B7', room: '218',
      category: '给排水', level: '较急',
      desc: '水龙头关了还在渗水。',
      photosBefore: 1, window: '全天',
      createdAt: '2026-11-14 09:00', assignedAt: '2026-11-14 09:30', arriveAt: '2026-11-14 11:00',
      finishAt: '2026-11-14 11:50', photosAfter: 1,
      reworkCount: 2, aiConfidence: 0.95, aiBasis: '命中「渗水/水龙头」，归给排水；定较急',
      aiCategory: '给排水', aiLevel: '较急',
      workerId: 'W0428', manual: 1,
      events: [
        { at: '2026-11-14 09:00', event: 'SUBMIT', actor: 'student:赵同学', manual: 1, note: '照片 1 张、描述 10 字，时段 全天' },
        { at: '2026-11-14 09:30', event: 'ASSIGN', actor: 'system', manual: 0, note: '本栋当班师傅：李建国' },
        { at: '2026-11-14 11:00', event: 'ARRIVE', actor: 'worker:李建国', manual: 1, note: '进楼打卡' },
        { at: '2026-11-14 11:50', event: 'FINISH', actor: 'worker:李建国', manual: 1, note: '更换阀芯，完工照 1 张' },
        { at: '2026-11-15 19:00', event: 'REJECT', actor: 'student:赵同学', manual: 1, note: '还在渗水 → 生成 -R1' },
        { at: '2026-11-16 20:10', event: 'REJECT', actor: 'student:赵同学', manual: 1, note: '第 2 次不通过 → 转管理端例外（反复返修）' }
      ]
    },
    {
      no: 'BX20261114001-R1', status: 'PENDING', building: 'B7', room: '218',
      category: '给排水', level: '紧急',
      desc: '水龙头关了还在渗水。（返修单，继承原单照片与位置）',
      photosBefore: 1, window: '全天',
      createdAt: '2026-11-15 19:00', reworkOf: 'BX20261114001', reworkIndex: 1,
      aiConfidence: 0.95, aiBasis: '继承原单 AI 依据，紧急度升一档：较急 → 紧急',
      aiCategory: '给排水', aiLevel: '紧急',
      workerId: null, manual: 1,
      events: [
        { at: '2026-11-15 19:00', event: 'SUBMIT', actor: 'system', manual: 1, note: '由 REJECT 生成，rework_of=BX20261114001，入队' }
      ]
    },
    {
      no: 'BX20261114002', status: 'DONE', building: 'B8', room: '206',
      category: '网络', level: '普通',
      desc: '宿舍网口插上没反应。',
      photosBefore: 1, window: '上午 08:00-12:00',
      createdAt: '2026-11-14 08:10', assignedAt: '2026-11-14 08:30', arriveAt: '2026-11-14 09:40',
      finishAt: '2026-11-14 10:05', signAt: '2026-11-16 09:00', photosAfter: 1,
      allManualZero: false,
      aiConfidence: 0.9, aiBasis: '命中「网口/没反应」，归网络；定普通',
      aiCategory: '网络', aiLevel: '普通',
      workerId: 'W0512', manual: 1,
      events: [
        { at: '2026-11-14 08:10', event: 'SUBMIT', actor: 'student:孙同学', manual: 1, note: '照片 1 张、描述 10 字，时段 上午' },
        { at: '2026-11-14 08:30', event: 'ASSIGN', actor: 'system', manual: 0, note: '本栋当班师傅：王海涛' },
        { at: '2026-11-14 09:40', event: 'ARRIVE', actor: 'worker:王海涛', manual: 1, note: '进楼打卡' },
        { at: '2026-11-14 10:05', event: 'FINISH', actor: 'worker:王海涛', manual: 1, note: '重做网线水晶头，完工照 1 张' },
        { at: '2026-11-16 09:00', event: 'ACCEPT', actor: 'student:孙同学', manual: 1, note: '电子签字' }
      ]
    },
    {
      no: 'BX20261108003', status: 'CLOSED', building: 'B7', room: '512',
      category: '给排水', level: '普通',
      desc: '阳台水龙头滴水。',
      photosBefore: 1, window: '全天',
      createdAt: '2026-11-08 09:20', assignedAt: '2026-11-08 09:40', arriveAt: '2026-11-08 11:00',
      finishAt: '2026-11-08 11:30', signAt: '2026-11-08 12:00', closedAt: '2026-11-15 00:05', photosAfter: 1,
      aiConfidence: 0.92, aiBasis: '命中「滴水/水龙头」，归给排水；定普通',
      aiCategory: '给排水', aiLevel: '普通',
      workerId: 'W0428', manual: 0,   // 全链路无人工：示例用自动验收
      events: [
        { at: '2026-11-08 09:20', event: 'SUBMIT', actor: 'student:周同学', manual: 0, note: '（示例：演示数据中记为系统代录）' },
        { at: '2026-11-08 09:40', event: 'ASSIGN', actor: 'system', manual: 0, note: '本栋当班师傅：李建国' },
        { at: '2026-11-08 11:00', event: 'ARRIVE', actor: 'worker:李建国', manual: 0, note: '（示例）' },
        { at: '2026-11-08 11:30', event: 'FINISH', actor: 'worker:李建国', manual: 0, note: '更换水龙头垫圈' },
        { at: '2026-11-15 00:05', event: 'AUTO_ACCEPT', actor: 'system', manual: 0, note: '待验收满 168 小时，系统自动确认' },
        { at: '2026-11-15 00:05', event: 'ARCHIVE', actor: 'system', manual: 0, note: '（示例：归档在 7 天后，此处压缩时间轴）' }
      ]
    },
    {
      no: 'BX20261116009', status: 'CANCELLED', building: 'B8', room: '310',
      category: '门窗家具', level: '普通',
      desc: '窗户关不严，漏风。',
      photosBefore: 1, window: '全天',
      createdAt: '2026-11-16 21:10', cancelledAt: '2026-11-17 07:30',
      aiConfidence: 0.87, aiBasis: '命中「窗户/漏风」，归门窗家具；定普通',
      aiCategory: '门窗家具', aiLevel: '普通',
      workerId: null, manual: 1,
      events: [
        { at: '2026-11-16 21:10', event: 'SUBMIT', actor: 'student:吴同学', manual: 1, note: '照片 1 张、描述 9 字，时段 全天' },
        { at: '2026-11-17 07:30', event: 'CANCEL', actor: 'student:吴同学', manual: 1, note: '师傅还没接单，学生直接撤销' }
      ]
    }
  ]
};


