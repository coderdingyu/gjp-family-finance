# -*- coding: utf-8 -*-
"""
演示数据生成脚本（负责人：戊）。

作用：生成 data.sql —— 一个自包含的演示数据脚本，内含
  · 1 个家庭、4 名成员
  · 5 个登录账号（1 户主 + 3 普通成员 + 1 系统管理员）
  · 三级收支分类（如 文化娱乐 → 影音娱乐 → 游戏充值 / KTV）
  · 约 1000 笔跨 20 个月的收支流水，其中刻意埋入重复流水供查重功能演示
  · 资产与贷款数据

用法：
    python3 gen_demo_data.py          # 重新生成 data.sql
    mysql -u root < schema.sql        # 先建库建表
    mysql -u root < data.sql          # 再灌演示数据

为什么要脚本生成而不是手写 SQL：
    验收要求演示数据"合理可解释"，手写上千条流水既慢又容易写出统计上不自洽的数据
    （比如某月支出为 0、某成员从来不消费）。脚本按真实家庭的消费规律造数，
    并刻意埋入异常月份和重复账单，用来验证智能分析和查重功能。

注意：random 固定了随机种子，因此每次生成的 data.sql 完全一致，
      便于测试用例把预期结果写死。
"""
import random
from datetime import date, timedelta

random.seed(20260901)

# ---------------- 家庭与成员 ----------------
FAMILY_ID = 1
FAMILY_NAME = '张家'
MD5_123456 = 'e10adc3949ba59abbe56e057f20f883e'   # 123456 的 MD5

MEMBERS = [
    # (id, 姓名, 关系, 月度预算)
    (1, '张伟',   '本人', 6000),
    (2, '李娟',   '配偶', 5000),
    (3, '张小雨', '子女', 1500),
    (4, '张建国', '父母', 2000),
]

# 登录账号：(id, 账号, 姓名, family_id, member_id, role)
# role: 0=普通成员 1=户主 2=系统管理员
USERS = [
    (1, 'zhangwei', '张伟',   FAMILY_ID, 1, 1),
    (2, 'lijuan',   '李娟',   FAMILY_ID, 2, 0),
    (3, 'xiaoyu',   '张小雨', FAMILY_ID, 3, 0),
    (4, 'jianguo',  '张建国', FAMILY_ID, 4, 0),
    (5, 'admin',    '系统管理员', 0,     None, 2),
]

# ---------------- 预置分类（必须与 CategoryService 中的定义一致）----------------
# 写法：'二级>三级1|三级2' 表示该二级分类下还有三级分类
INCOME = [
    ('工资收入', ['基本工资', '奖金', '加班费']),
    ('投资收益', ['利息', '股票基金', '房租收入']),
    ('其他收入', ['红包礼金', '报销', '兼职']),
]
EXPENSE = [
    ('餐饮支出', ['家庭买菜', '外出就餐>正餐|火锅烧烤|快餐', '外卖', '饮品零食>咖啡奶茶|零食水果']),
    ('购物支出', ['服饰鞋帽', '日用品', '数码家电>手机数码|家用电器', '美妆护理']),
    ('居住支出', ['房租房贷', '水电燃气>电费|水费|燃气费', '物业费', '维修装修']),
    ('交通支出', ['公共交通', '打车', '加油', '停车过路费']),
    ('教育支出', ['学费', '书籍资料', '培训班']),
    ('医疗健康', ['门诊药品', '住院', '体检保险']),
    ('文化娱乐', ['旅游度假', '健身运动', '影音娱乐>电影|游戏充值|KTV|流媒体会员']),
    ('人情往来', ['礼金红包', '送礼', '请客吃饭']),
    ('其他支出', ['手续费', '捐赠', '杂项']),
]

cat_rows = []   # (id, parent_id, root_id, level, name, type, sort_no)
cat_id = {}     # 分类名 -> id
_next = 1


def build_categories(groups, type_):
    """按 CategoryService.insertGroup 的同一顺序生成分类，保证 ID 与线上注册流程一致"""
    global _next
    sort = 0
    for parent_name, children in groups:
        first_id = _next
        _next += 1
        cat_rows.append((first_id, 0, first_id, 1, parent_name, type_, sort))
        cat_id[parent_name] = first_id
        sort += 1

        sub_sort = 0
        for spec in children:
            if '>' in spec:
                second_name, third_part = spec.split('>', 1)
                thirds = third_part.split('|')
            else:
                second_name, thirds = spec, []

            second_id = _next
            _next += 1
            cat_rows.append((second_id, first_id, first_id, 2, second_name, type_, sub_sort))
            cat_id[second_name] = second_id
            sub_sort += 1

            for i, third_name in enumerate(thirds):
                third_id = _next
                _next += 1
                cat_rows.append((third_id, second_id, first_id, 3, third_name.strip(), type_, i))
                cat_id[third_name.strip()] = third_id


build_categories(INCOME, 1)
build_categories(EXPENSE, 2)

# ---------------- 候选商家 / 片区 ----------------
# 商家按分类归口，这样"商家排行"和"分类占比"两张图讲的是同一个故事。
MERCHANTS = {
    '家庭买菜': ['永辉超市', '钱大妈', '盒马鲜生', '菜市场'],
    '正餐':     ['西贝莜面村', '老碗会', '湘满楼'],
    '火锅烧烤': ['海底捞', '小龙坎'],
    '快餐':     ['必胜客', '肯德基'],
    '外卖':     ['美团外卖', '饿了么'],
    '咖啡奶茶': ['星巴克', '蜜雪冰城', '瑞幸咖啡'],
    '零食水果': ['良品铺子', '百果园'],
    '服饰鞋帽': ['优衣库', '海澜之家', 'ZARA', '安踏'],
    '日用品':   ['名创优品', '屈臣氏', '永辉超市'],
    '手机数码': ['京东商城', '苹果直营店', '小米之家'],
    '家用电器': ['京东商城', '苏宁易购'],
    '美妆护理': ['丝芙兰', '屈臣氏'],
    '电费':     ['国家电网'],
    '水费':     ['市自来水公司'],
    '燃气费':   ['市燃气公司'],
    '物业费':   ['锦绣园物业'],
    '维修装修': ['红星美凯龙', '本地装修队'],
    '公共交通': ['城市公交', '地铁'],
    '打车':     ['滴滴出行'],
    '加油':     ['中石化', '中石油'],
    '停车过路费': ['万达停车场', '高速收费站'],
    '学费':     ['市第三小学', '新东方'],
    '书籍资料': ['当当网', '西西弗书店'],
    '培训班':   ['学而思', '少年宫'],
    '门诊药品': ['市第一医院', '老百姓大药房'],
    '住院':     ['市第一医院'],
    '体检保险': ['爱康国宾', '中国平安'],
    '旅游度假': ['携程旅行', '飞猪旅行'],
    '健身运动': ['乐刻健身', '游泳馆'],
    '电影':     ['万达影城'],
    '游戏充值': ['Steam', '腾讯游戏', '网易游戏'],
    'KTV':      ['钱柜KTV', '纯K'],
    '流媒体会员': ['腾讯视频', '爱奇艺', '网易云音乐'],
    '礼金红包': ['亲友往来'],
    '送礼':     ['百果园', '茅台专卖店'],
    '请客吃饭': ['海底捞', '湘满楼', '老碗会'],
    '手续费':   ['招商银行'],
    '捐赠':     ['慈善总会'],
    '杂项':     ['其他'],
}
AREAS = ['城东', '城西', '城南', '城北', '市中心']
# 城东是"居住地"，因此占比最高，符合真实生活半径
AREA_WEIGHTS = [42, 14, 13, 11, 20]
PAY = ['微信', '支付宝', '银行卡', '现金']
PAY_WEIGHTS = [40, 30, 22, 8]

records = []  # (member_id, category_id, type, amount, date, merchant, area, pay, is_gift, remark)


def pick(seq, weights=None):
    return random.choices(seq, weights=weights, k=1)[0]


def add(member, cat, type_, amount, day, merchant=None, area=None, pay=None, gift=0, remark=None):
    if merchant is None:
        merchant = pick(MERCHANTS[cat]) if cat in MERCHANTS else None
    if area is None:
        area = pick(AREAS, AREA_WEIGHTS)
    if pay is None:
        pay = pick(PAY, PAY_WEIGHTS)
    records.append((member, cat_id[cat], type_, round(amount, 2), day, merchant, area, pay, gift, remark))


def rday(y, m):
    """当月随机一天"""
    last = (date(y + (m == 12), (m % 12) + 1, 1) - timedelta(days=1)).day
    return date(y, m, random.randint(1, last))


# ---------------- 逐月造数：2025-01 ~ 2026-08 ----------------
months = [(2025, m) for m in range(1, 13)] + [(2026, m) for m in range(1, 9)]

# 5 月是刻意埋入的异常月份：装修 + 旅游两笔大额支出叠加，
# 用来验证智能分析 A1 能否定位到"居住支出"这个超支主因。
ABNORMAL = (2026, 5)

for (y, m) in months:
    # ---- 收入：工资固定发放，年终奖只在 1 月，投资收益偶发 ----
    add(1, '基本工资', 1, random.uniform(14500, 15500), date(y, m, 10), '公司财务', '市中心', '银行卡')
    add(2, '基本工资', 1, random.uniform(9500, 10500), date(y, m, 10), '公司财务', '市中心', '银行卡')
    add(4, '基本工资', 1, random.uniform(3800, 4200), date(y, m, 15), '社保局', '市中心', '银行卡',
        remark='退休金')
    if m == 1:
        add(1, '奖金', 1, random.uniform(28000, 42000), date(y, 1, 20), '公司财务', '市中心', '银行卡',
            remark='上一年度年终奖')
    if m in (3, 6, 9, 12):
        add(1, '股票基金', 1, random.uniform(800, 5200), rday(y, m), '证券账户', '市中心', '银行卡')
    if random.random() < 0.4:
        add(2, '房租收入', 1, 2200, date(y, m, 5), '租客', '城西', '微信', remark='老房出租')
    if random.random() < 0.3:
        add(1, '报销', 1, random.uniform(300, 1800), rday(y, m), '公司财务', '市中心', '银行卡',
            remark='出差报销')

    # ---- 固定支出：房贷、物业、水电（水电已细分到三级）----
    add(1, '房租房贷', 2, 6850, date(y, m, 20), '招商银行', '城东', '银行卡', remark='商业房贷月供')
    add(1, '物业费', 2, 420, date(y, m, 8), '锦绣园物业', '城东', '微信')
    add(1, '电费', 2, random.uniform(120, 380), rday(y, m), None, '城东')
    add(1, '水费', 2, random.uniform(40, 90), rday(y, m), None, '城东')
    add(1, '燃气费', 2, random.uniform(30, 160), rday(y, m), None, '城东')

    # ---- 餐饮：三级分类，家庭买菜高频、外出就餐分正餐/火锅/快餐 ----
    for _ in range(random.randint(6, 9)):
        add(pick([1, 2, 2, 4]), '家庭买菜', 2, random.uniform(80, 320), rday(y, m))
    for _ in range(random.randint(3, 6)):
        add(pick([1, 1, 2]), pick(['正餐', '火锅烧烤', '快餐'], [50, 30, 20]), 2,
            random.uniform(160, 680), rday(y, m))
    for _ in range(random.randint(2, 5)):
        add(pick([1, 2, 3]), '外卖', 2, random.uniform(25, 90), rday(y, m))
    for _ in range(random.randint(3, 7)):
        add(pick([1, 2, 3, 3]), pick(['咖啡奶茶', '零食水果']), 2, random.uniform(12, 78), rday(y, m))

    # ---- 交通 ----
    add(1, '加油', 2, random.uniform(380, 520), rday(y, m))
    for _ in range(random.randint(2, 5)):
        add(pick([1, 2, 3]), '公共交通', 2, random.uniform(2, 15), rday(y, m))
    for _ in range(random.randint(1, 4)):
        add(pick([1, 2, 4]), '打车', 2, random.uniform(15, 68), rday(y, m))
    if random.random() < 0.7:
        add(1, '停车过路费', 2, random.uniform(10, 180), rday(y, m))

    # ---- 购物 ----
    for _ in range(random.randint(1, 3)):
        add(pick([1, 2, 2]), '服饰鞋帽', 2, random.uniform(160, 1200), rday(y, m))
    for _ in range(random.randint(2, 4)):
        add(pick([1, 2]), '日用品', 2, random.uniform(45, 260), rday(y, m))
    if random.random() < 0.35:
        add(1, pick(['手机数码', '家用电器']), 2, random.uniform(600, 6800), rday(y, m))
    if random.random() < 0.6:
        add(2, '美妆护理', 2, random.uniform(120, 880), rday(y, m))

    # ---- 教育：学费按学期，培训班每月 ----
    if m in (2, 9):
        add(3, '学费', 2, random.uniform(1800, 2600), rday(y, m), '市第三小学', '城东', '银行卡',
            remark='学期费用')
    add(3, '培训班', 2, random.uniform(800, 1600), rday(y, m))
    if random.random() < 0.5:
        add(3, '书籍资料', 2, random.uniform(45, 260), rday(y, m))

    # ---- 医疗 ----
    if random.random() < 0.6:
        add(pick([3, 4, 4]), '门诊药品', 2, random.uniform(60, 480), rday(y, m))
    if m == 11:
        add(4, '体检保险', 2, random.uniform(1200, 2200), rday(y, m), '爱康国宾', '市中心', '银行卡',
            remark='年度体检')

    # ---- 文化娱乐：影音娱乐细分到 电影/游戏充值/KTV/流媒体会员 ----
    add(pick([1, 2, 3]), '流媒体会员', 2, pick([15, 25, 30]), rday(y, m), remark='包月会员')
    if random.random() < 0.6:
        add(pick([1, 2, 3]), '电影', 2, random.uniform(40, 160), rday(y, m))
    if random.random() < 0.55:
        # 孩子是游戏充值的主力，父母偶尔也充
        add(pick([3, 3, 1]), '游戏充值', 2, pick([30, 68, 128, 328, 648]), rday(y, m))
    if random.random() < 0.35:
        add(pick([1, 2]), 'KTV', 2, random.uniform(180, 560), rday(y, m), remark='朋友聚会')
    if random.random() < 0.5:
        add(pick([1, 2]), '健身运动', 2, random.uniform(99, 360), rday(y, m))
    # 暑假与国庆出游
    if m in (7, 8, 10):
        add(1, '旅游度假', 2, random.uniform(3200, 9800), rday(y, m), '携程旅行', '市中心', '银行卡',
            remark='家庭出游')

    # ---- 人情往来：is_gift = 1，春节与婚庆季偏多 ----
    gift_times = 4 if m in (1, 2, 5, 10) else random.randint(0, 2)
    for _ in range(gift_times):
        cat = pick(['礼金红包', '送礼', '请客吃饭'])
        amount = random.uniform(600, 2000) if cat == '礼金红包' else random.uniform(150, 900)
        add(pick([1, 1, 2]), cat, 2, amount, rday(y, m), gift=1, remark='亲友往来')

    # ---- 杂项 ----
    if random.random() < 0.4:
        add(1, '手续费', 2, random.uniform(2, 30), rday(y, m))

    # ---- 异常月份：装修 + 长线旅游，制造一个可被分析定位的超支月 ----
    if (y, m) == ABNORMAL:
        add(1, '维修装修', 2, 38600, date(y, m, 12), '红星美凯龙', '城东', '银行卡',
            remark='卫生间与厨房翻新')
        add(1, '维修装修', 2, 12400, date(y, m, 19), '本地装修队', '城东', '银行卡', remark='人工费')
        add(2, '旅游度假', 2, 16800, date(y, m, 2), '携程旅行', '市中心', '银行卡',
            remark='五一云南全家游')

# ---------------- 刻意埋入的重复账单（供查重功能演示）----------------
# 三种典型场景，覆盖"完全一致"和"高度相似"两类：
#   1. 同一笔消费被手动记了两次（同成员、同金额、同日期）—— 完全一致
#   2. 先手动记了一笔，后来又从账单文件导入了同一笔（相差 1 天）—— 高度相似
#   3. 一笔消费被记了三次 —— 验证并查集能把三条合并成一组而不是拆成三对
DUPLICATES = [
    # (成员, 分类, 金额, 基准日期, 偏移天数列表, 商家, 备注)
    (1, '火锅烧烤', 468.00, date(2026, 7, 18), [0, 0],    '海底捞',   '朋友聚餐'),
    (2, '手机数码', 5999.00, date(2026, 6, 6),  [0, 1],    '苹果直营店', '换手机'),
    (1, '正餐',     286.50, date(2026, 8, 12), [0, 0, 2], '西贝莜面村', '家庭晚餐'),
    (3, '游戏充值', 328.00, date(2026, 8, 20), [0, 0],    'Steam',    '游戏充值'),
    (2, '礼金红包', 1200.00, date(2026, 5, 9),  [0, 3],    '亲友往来', '同事婚礼'),
]
for (member, cat, amount, base_day, offsets, merchant, remark) in DUPLICATES:
    for off in offsets:
        add(member, cat, 2, amount, base_day + timedelta(days=off), merchant,
            '城东', '微信', gift=1 if cat == '礼金红包' else 0, remark=remark)

# ---------------- 资产与贷款 ----------------
ASSETS = [
    ('城东锦绣园住房', '房产', 1850000, 1280000, '2019-06-15', '自住，128 平'),
    ('城西老房',       '房产', 720000, 480000, '2012-03-20', '已出租，月租 2200'),
    ('家用轿车',       '车辆', 92000, 168000, '2021-09-08', '燃油车，日常代步'),
    ('招行活期存款',   '存款', 168000, None, None, '家庭备用金'),
    ('沪深300指数基金', '基金', 96500, 82000, '2023-04-11', '定投中'),
    ('某银行股',       '股票', 41200, 52000, '2022-08-02', '长期持有'),
]
LOANS = [
    ('城东住房商业贷款', '房贷', 900000, 6850, 240, 74, '2019-07-20'),
    ('家用轿车车贷',     '车贷', 120000, 2180, 60, 47, '2021-10-08'),
]


# ---------------- 演示操作日志 ----------------
# 为什么要造日志：日志页和管理员面板（近 14 天操作量趋势）没有历史数据就是一张空表，
# 演示时看不出功能。这里按"每天有人记几笔账、偶尔改删一次、时不时登录"的规律铺开，
# 并掺入少量失败记录，让管理员面板的失败率指标不是 0。
from datetime import datetime, timedelta as _td

LOG_USERS = [
    (1, 'zhangwei', '张伟', 1),
    (2, 'lijuan', '李娟', 1),
    (3, 'xiaoyu', '张小雨', 1),
    (4, 'jianguo', '张建国', 1),
    (5, 'admin', '系统管理员', 0),
]

logs = []   # (family_id, user_id, username, real_name, module, action, summary, ip, success, error_msg, create_time)
IPS = ['192.168.1.12', '192.168.1.23', '192.168.1.31', '127.0.0.1']

SAMPLE_RECORD_SUMMARY = [
    ('新增', '新增支出 {a} 元｜{d}｜{who}｜餐饮支出/家庭买菜｜永辉超市'),
    ('新增', '新增支出 {a} 元｜{d}｜{who}｜餐饮支出/外出就餐/正餐｜西贝莜面村'),
    ('新增', '新增支出 {a} 元｜{d}｜{who}｜文化娱乐/影音娱乐/游戏充值｜Steam'),
    ('新增', '新增支出 {a} 元｜{d}｜{who}｜交通支出/打车｜滴滴出行'),
    ('新增', '新增收入 {a} 元｜{d}｜{who}｜工资收入/基本工资｜公司财务'),
    ('修改', '修改支出 {a} 元｜{d}｜{who}｜购物支出/日用品｜名创优品'),
    ('删除', '删除支出 {a} 元｜{d}｜{who}｜餐饮支出/外卖｜美团外卖'),
]

# 今天固定成生成脚本运行日的前一天，避免与真实使用产生的日志时间重叠
today0 = datetime(2026, 9, 2, 0, 0, 0)

for day_back in range(13, -1, -1):
    day = today0 - _td(days=day_back)
    # 最后一天就是"今天"，时间不能落在未来，否则日志里会出现还没到的时刻
    hour_max = 11 if day_back == 0 else 23
    # 周末记账更频繁一点
    is_weekend = day.weekday() >= 5
    n_login = random.randint(1, 3)
    n_record = random.randint(2, 7) + (2 if is_weekend else 0)

    for _ in range(n_login):
        uid, uname, rname, fid = pick(LOG_USERS[:4])
        t = day.replace(hour=random.randint(7, min(22, hour_max)), minute=random.randint(0, 59), second=random.randint(0, 59))
        role_name = '户主' if uid == 1 else '普通成员'
        logs.append((fid, uid, uname, rname, '登录', '登录',
                     f'{role_name}【{uname}】登录成功', pick(IPS), 1, None, t))

    for _ in range(n_record):
        uid, uname, rname, fid = pick(LOG_USERS[:4], [45, 25, 20, 10])
        action, tpl = pick(SAMPLE_RECORD_SUMMARY)
        t = day.replace(hour=random.randint(8, min(23, hour_max)), minute=random.randint(0, 59), second=random.randint(0, 59))
        amount = f'{random.uniform(15, 680):.2f}'
        logs.append((fid, uid, uname, rname, '流水', action,
                     tpl.format(a=amount, d=(day - _td(days=random.randint(0, 3))).strftime('%Y-%m-%d'), who=rname),
                     pick(IPS), 1, None, t))

    # 偶发的失败操作：用户输错导致的校验不通过，是最真实的失败来源。
    # 概率刻意压低：正常使用下失败率应该是个位数百分比，
    # 造得太高会让管理员面板一上来就显示"需关注"，反而掩盖了这个指标的意义。
    if random.random() < 0.12:
        uid, uname, rname, fid = pick(LOG_USERS[:4])
        t = day.replace(hour=random.randint(9, min(22, hour_max)), minute=random.randint(0, 59))
        err = pick(['金额必须大于 0', '发生日期不能晚于今天',
                    '分类【基本工资】属于收入类，与当前流水类型不一致'])
        logs.append((fid, uid, uname, rname, '流水', '新增',
                     '录入流水失败', pick(IPS), 0, err, t))
    if random.random() < 0.08:
        t = day.replace(hour=random.randint(9, min(20, hour_max)), minute=random.randint(0, 59))
        logs.append((1, None, pick(['zhangwei', 'lijuan', 'unknown']), None, '登录', '登录',
                     '登录失败', pick(IPS), 0, '账号或密码错误', t))

    # 分类与成员维护是低频操作
    if random.random() < 0.25:
        t = day.replace(hour=random.randint(9, min(21, hour_max)), minute=random.randint(0, 59))
        logs.append((1, 1, 'zhangwei', '张伟', '分类', pick(['新增', '修改']),
                     pick(['新增3级分类【外卖优惠】', '分类改名：【零食水果】→【零食生鲜】',
                           '新增2级分类【宠物用品】']), pick(IPS), 1, None, t))
    if random.random() < 0.15:
        t = day.replace(hour=random.randint(9, min(21, hour_max)), minute=random.randint(0, 59))
        logs.append((1, 1, 'zhangwei', '张伟', '资产', pick(['新增', '修改']),
                     pick(['修改资产【招行活期存款】当前价值 168000.00 元',
                           '修改资产【沪深300指数基金】当前价值 96500.00 元']), pick(IPS), 1, None, t))
    # 管理员偶尔登录做巡检
    if random.random() < 0.3:
        t = day.replace(hour=random.randint(9, min(18, hour_max)), minute=random.randint(0, 59))
        logs.append((0, 5, 'admin', '系统管理员', '登录', '登录',
                     '系统管理员【admin】登录成功', '127.0.0.1', 1, None, t))

logs.sort(key=lambda x: x[10])


def sql_str(v):
    if v is None:
        return 'NULL'
    return "'" + str(v).replace("'", "''") + "'"


lines = []
w = lines.append
w('-- ============================================================')
w('-- 《管家婆 — 家庭收支管理系统》演示数据')
w('-- 本文件由 gen_demo_data.py 自动生成，请勿手工编辑；需要调整请改脚本后重新生成。')
w('-- 执行顺序：先 schema.sql 建库建表，再执行本文件。')
w('--')
w('-- 演示账号（密码统一 123456）：')
w('--   zhangwei  户主       可看全家数据、管理成员与分类、查看资产负债')
w('--   lijuan    普通成员   只能看到自己名下的流水与统计')
w('--   xiaoyu    普通成员   同上')
w('--   jianguo   普通成员   同上')
w('--   admin     系统管理员 网站维护与日志排查，看不到具体账单金额')
w('-- ============================================================')
w('')
w('-- 客户端字符集：不加这一行，中文数据在导入时会被按 latin1 解析而乱码')
w('SET NAMES utf8mb4;')
w('')
w('USE gjp;')
w('')
w('-- 先清空，保证脚本可以反复执行')
w('SET FOREIGN_KEY_CHECKS = 0;')
for t in ['t_record', 't_category', 't_member', 't_asset', 't_loan',
          't_user', 't_family', 't_operation_log']:
    w(f'TRUNCATE TABLE {t};')
w('SET FOREIGN_KEY_CHECKS = 1;')
w('')
w('-- 1. 家庭')
w(f"INSERT INTO t_family (id, family_name) VALUES ({FAMILY_ID}, {sql_str(FAMILY_NAME)});")
w('')
w('-- 2. 家庭成员')
w('INSERT INTO t_member (id, family_id, member_name, relation, monthly_budget) VALUES')
w(',\n'.join(
    f"  ({mid}, {FAMILY_ID}, {sql_str(name)}, {sql_str(rel)}, {budget:.2f})"
    for mid, name, rel, budget in MEMBERS) + ';')
w('')
w('-- 3. 登录账号（密码统一为 123456 的 MD5）')
w('--    role: 0=普通成员 1=户主 2=系统管理员；member_id 决定普通成员能看到哪些数据')
w('INSERT INTO t_user (id, username, password, real_name, family_id, member_id, role, status) VALUES')
w(',\n'.join(
    f"  ({uid}, {sql_str(uname)}, '{MD5_123456}', {sql_str(rname)}, {fid}, "
    f"{'NULL' if mid is None else mid}, {role}, 1)"
    for uid, uname, rname, fid, mid, role in USERS) + ';')
w('')
w(f'-- 4. 收支分类（{len(cat_rows)} 项，三级结构，与 CategoryService 的预置分类保持一致）')
w('--    例：文化娱乐(1级) → 影音娱乐(2级) → 电影 / 游戏充值 / KTV / 流媒体会员(3级)')
w('INSERT INTO t_category (id, family_id, parent_id, root_id, level, category_name, type, is_default, sort_no) VALUES')
w(',\n'.join(
    f"  ({cid}, {FAMILY_ID}, {pid}, {rid}, {lv}, {sql_str(name)}, {t}, 1, {sort})"
    for cid, pid, rid, lv, name, t, sort in cat_rows) + ';')
w('')
w(f'-- 5. 收支流水（{len(records)} 笔，覆盖 {months[0][0]}-{months[0][1]:02d} 至 '
  f'{months[-1][0]}-{months[-1][1]:02d} 共 {len(months)} 个月）')
w(f'--    其中包含 {sum(len(d[4]) for d in DUPLICATES)} 笔刻意埋入的重复账单，供查重功能演示')
w('INSERT INTO t_record (family_id, member_id, category_id, type, amount, record_date, '
  'merchant, area, pay_method, is_gift, remark) VALUES')
rec_sql = []
for (member, cid, type_, amount, day, merchant, area, pay, gift, remark) in records:
    rec_sql.append(
        f"  ({FAMILY_ID}, {member}, {cid}, {type_}, {amount:.2f}, '{day}', "
        f"{sql_str(merchant)}, {sql_str(area)}, {sql_str(pay)}, {gift}, {sql_str(remark)})")
w(',\n'.join(rec_sql) + ';')
w('')
w('-- 6. 家庭资产')
w('INSERT INTO t_asset (family_id, asset_name, asset_type, amount, cost, buy_date, remark) VALUES')
w(',\n'.join(
    f"  ({FAMILY_ID}, {sql_str(n)}, {sql_str(t)}, {a:.2f}, "
    f"{'NULL' if c is None else f'{c:.2f}'}, {sql_str(d)}, {sql_str(r)})"
    for n, t, a, c, d, r in ASSETS) + ';')
w('')
w('-- 7. 家庭贷款')
w('INSERT INTO t_loan (family_id, loan_name, loan_type, total_amount, monthly_payment, '
  'total_months, paid_months, start_date) VALUES')
w(',\n'.join(
    f"  ({FAMILY_ID}, {sql_str(n)}, {sql_str(t)}, {total:.2f}, {pay:.2f}, {tm}, {pm}, {sql_str(sd)})"
    for n, t, total, pay, tm, pm, sd in LOANS) + ';')
w('')
w(f'-- 8. 操作日志（{len(logs)} 条，覆盖最近 14 天，含少量失败记录）')
w('--    用于演示日志页的筛选与管理员面板的操作量趋势、失败率指标')
w('INSERT INTO t_operation_log (family_id, user_id, username, real_name, module, action, '
  'summary, ip, success, error_msg, create_time) VALUES')
w(',\n'.join(
    f"  ({fid}, {'NULL' if uid is None else uid}, {sql_str(uname)}, {sql_str(rname)}, "
    f"{sql_str(mod)}, {sql_str(act)}, {sql_str(summ)}, {sql_str(ip)}, {ok}, {sql_str(err)}, "
    f"'{t.strftime('%Y-%m-%d %H:%M:%S')}')"
    for fid, uid, uname, rname, mod, act, summ, ip, ok, err, t in logs) + ';')
w('')

with open('data.sql', 'w', encoding='utf-8') as f:
    f.write('\n'.join(lines))

income = sum(r[3] for r in records if r[2] == 1)
expense = sum(r[3] for r in records if r[2] == 2)
by_level = {}
for _, _, _, lv, _, _, _ in cat_rows:
    by_level[lv] = by_level.get(lv, 0) + 1
print('已生成 data.sql')
print(f'  成员 {len(MEMBERS)} 名，账号 {len(USERS)} 个')
print(f'  分类 {len(cat_rows)} 项（一级 {by_level.get(1,0)} / 二级 {by_level.get(2,0)} / 三级 {by_level.get(3,0)}）')
print(f'  流水 {len(records)} 笔，跨 {len(months)} 个月，'
      f'含 {sum(len(d[4]) for d in DUPLICATES)} 笔重复账单（{len(DUPLICATES)} 组）')
print(f'  收入合计 {income:,.2f} 元，支出合计 {expense:,.2f} 元，结余 {income - expense:,.2f} 元')
print(f'  操作日志 {len(logs)} 条（失败 {sum(1 for l in logs if l[8] == 0)} 条），覆盖最近 14 天')
