# -*- coding: utf-8 -*-
"""
演示数据生成脚本（负责人：戊）。

作用：生成 data.sql —— 一个自包含的演示数据脚本，内含 1 个家庭、4 名成员、
52 项预置收支分类、约 700 笔跨 20 个月的收支流水，以及资产与贷款数据。

用法：
    python3 gen_demo_data.py          # 重新生成 data.sql
    mysql -u root < schema.sql        # 先建库建表
    mysql -u root < data.sql          # 再灌演示数据

为什么要脚本生成而不是手写 SQL：
    验收要求演示数据"合理可解释"，手写几百条流水既慢又容易写出统计上不自洽的数据
    （比如某月支出为 0、某成员从来不消费）。脚本按真实家庭的消费规律造数，
    并刻意埋入一个异常月份，用来验证智能分析的异常归因能否正确定位。

注意：random 固定了随机种子，因此每次生成的 data.sql 完全一致，
      便于测试用例把预期结果写死。
"""
import random
from datetime import date, timedelta

random.seed(20260901)

# ---------------- 家庭与成员 ----------------
FAMILY_ID = 1
FAMILY_NAME = '张家'
# 登录账号 zhangwei / 密码 123456（MD5 摘要）
USER = ('zhangwei', 'e10adc3949ba59abbe56e057f20f883e', '张伟')

MEMBERS = [
    # (id, 姓名, 关系, 月度预算)
    (1, '张伟',   '本人', 6000),
    (2, '李娟',   '配偶', 5000),
    (3, '张小雨', '子女', 1500),
    (4, '张建国', '父母', 2000),
]

# ---------------- 预置分类 ----------------
# 顺序必须与 AuthService 中的 DEFAULT_INCOME / DEFAULT_EXPENSE 一致，
# 否则演示数据的分类ID会对不上。
INCOME = [
    ('工资收入', ['基本工资', '奖金', '加班费']),
    ('投资收益', ['利息', '股票基金', '房租收入']),
    ('其他收入', ['红包礼金', '报销', '兼职']),
]
EXPENSE = [
    ('餐饮支出', ['家庭买菜', '外出就餐', '外卖', '饮品零食']),
    ('购物支出', ['服饰鞋帽', '日用品', '数码家电', '美妆护理']),
    ('居住支出', ['房租房贷', '水电燃气', '物业费', '维修装修']),
    ('交通支出', ['公共交通', '打车', '加油', '停车过路费']),
    ('教育支出', ['学费', '书籍资料', '培训班']),
    ('医疗健康', ['门诊药品', '住院', '体检保险']),
    ('文化娱乐', ['旅游度假', '健身运动', '影音娱乐']),
    ('人情往来', ['礼金红包', '送礼', '请客吃饭']),
    ('其他支出', ['手续费', '捐赠', '杂项']),
]

cat_rows = []   # (id, parent_id, name, type, sort_no)
cat_id = {}     # 分类名 -> id
_next = 1


def build_categories(groups, type_):
    global _next
    sort = 0
    for parent_name, children in groups:
        pid = _next
        _next += 1
        cat_rows.append((pid, 0, parent_name, type_, sort))
        cat_id[parent_name] = pid
        sort += 1
        for i, child in enumerate(children):
            cid = _next
            _next += 1
            cat_rows.append((cid, pid, child, type_, i))
            cat_id[child] = cid


build_categories(INCOME, 1)
build_categories(EXPENSE, 2)

# ---------------- 候选商家 / 片区 ----------------
# 商家按分类归口，这样"商家排行"和"分类占比"两张图讲的是同一个故事。
MERCHANTS = {
    '家庭买菜': ['永辉超市', '钱大妈', '盒马鲜生', '菜市场'],
    '外出就餐': ['海底捞', '西贝莜面村', '老碗会', '小龙坎', '必胜客'],
    '外卖':     ['美团外卖', '饿了么'],
    '饮品零食': ['星巴克', '蜜雪冰城', '瑞幸咖啡', '良品铺子'],
    '服饰鞋帽': ['优衣库', '海澜之家', 'ZARA', '安踏'],
    '日用品':   ['名创优品', '屈臣氏', '永辉超市'],
    '数码家电': ['京东商城', '苹果直营店', '小米之家'],
    '美妆护理': ['丝芙兰', '屈臣氏'],
    '水电燃气': ['市自来水公司', '市燃气公司', '国家电网'],
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
    '影音娱乐': ['万达影城', '腾讯视频'],
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

    # ---- 固定支出：房贷、物业、水电 ----
    add(1, '房租房贷', 2, 6850, date(y, m, 20), '招商银行', '城东', '银行卡', remark='商业房贷月供')
    add(1, '物业费', 2, 420, date(y, m, 8), '锦绣园物业', '城东', '微信')
    add(1, '水电燃气', 2, random.uniform(260, 620), rday(y, m), None, '城东')

    # ---- 餐饮：家庭买菜高频、外出就餐中频、外卖与饮品零散 ----
    for _ in range(random.randint(6, 9)):
        add(pick([1, 2, 2, 4]), '家庭买菜', 2, random.uniform(80, 320), rday(y, m))
    for _ in range(random.randint(3, 6)):
        add(pick([1, 1, 2]), '外出就餐', 2, random.uniform(160, 680), rday(y, m))
    for _ in range(random.randint(2, 5)):
        add(pick([1, 2, 3]), '外卖', 2, random.uniform(25, 90), rday(y, m))
    for _ in range(random.randint(3, 7)):
        add(pick([1, 2, 3, 3]), '饮品零食', 2, random.uniform(12, 78), rday(y, m))

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
        add(1, '数码家电', 2, random.uniform(600, 6800), rday(y, m))
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

    # ---- 文化娱乐 ----
    if random.random() < 0.7:
        add(pick([1, 2, 3]), '影音娱乐', 2, random.uniform(40, 220), rday(y, m))
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
w('-- 演示账号：zhangwei / 123456')
w('-- ============================================================')
w('')
w('-- 客户端字符集：不加这一行，中文数据在导入时会被按 latin1 解析而乱码')
w('SET NAMES utf8mb4;')
w('')
w('USE gjp;')
w('')
w('-- 先清空，保证脚本可以反复执行')
w('SET FOREIGN_KEY_CHECKS = 0;')
for t in ['t_record', 't_category', 't_member', 't_asset', 't_loan', 't_user', 't_family']:
    w(f'TRUNCATE TABLE {t};')
w('SET FOREIGN_KEY_CHECKS = 1;')
w('')
w('-- 1. 家庭')
w(f"INSERT INTO t_family (id, family_name) VALUES ({FAMILY_ID}, {sql_str(FAMILY_NAME)});")
w('')
w('-- 2. 登录账号（密码 123456 的 MD5）')
w('INSERT INTO t_user (id, username, password, real_name, family_id) VALUES')
w(f"  (1, {sql_str(USER[0])}, {sql_str(USER[1])}, {sql_str(USER[2])}, {FAMILY_ID});")
w('')
w('-- 3. 家庭成员')
w('INSERT INTO t_member (id, family_id, member_name, relation, monthly_budget) VALUES')
w(',\n'.join(
    f"  ({mid}, {FAMILY_ID}, {sql_str(name)}, {sql_str(rel)}, {budget:.2f})"
    for mid, name, rel, budget in MEMBERS) + ';')
w('')
w(f'-- 4. 收支分类（{len(cat_rows)} 项，与 AuthService 的预置分类保持一致）')
w('INSERT INTO t_category (id, family_id, parent_id, category_name, type, is_default, sort_no) VALUES')
w(',\n'.join(
    f"  ({cid}, {FAMILY_ID}, {pid}, {sql_str(name)}, {t}, 1, {sort})"
    for cid, pid, name, t, sort in cat_rows) + ';')
w('')
w(f'-- 5. 收支流水（{len(records)} 笔，覆盖 {months[0][0]}-{months[0][1]:02d} 至 '
  f'{months[-1][0]}-{months[-1][1]:02d} 共 {len(months)} 个月）')
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

with open('data.sql', 'w', encoding='utf-8') as f:
    f.write('\n'.join(lines))

income = sum(r[3] for r in records if r[2] == 1)
expense = sum(r[3] for r in records if r[2] == 2)
print(f'已生成 data.sql')
print(f'  成员 {len(MEMBERS)} 名，分类 {len(cat_rows)} 项，流水 {len(records)} 笔，'
      f'跨 {len(months)} 个月')
print(f'  收入合计 {income:,.2f} 元，支出合计 {expense:,.2f} 元，结余 {income - expense:,.2f} 元')
