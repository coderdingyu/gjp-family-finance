<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">
        <span class="logo-badge">
          <el-icon :size="18"><Wallet /></el-icon>
        </span>
        <span>管家婆</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        class="menu"
        router
      >
        <!-- 菜单按角色显示：管理员只有维护相关，普通成员看不到资产负债 -->
        <template v-if="isFamilyUser">
          <el-menu-item index="/home">
            <el-icon><DataBoard /></el-icon><span>家庭看板</span>
          </el-menu-item>
          <el-menu-item index="/me">
            <el-icon><Avatar /></el-icon><span>个人看板</span>
          </el-menu-item>
          <el-menu-item index="/record">
            <el-icon><Tickets /></el-icon><span>收支流水</span>
          </el-menu-item>
          <el-menu-item index="/import">
            <el-icon><Upload /></el-icon><span>文件导入</span>
          </el-menu-item>
          <el-menu-item index="/stat">
            <el-icon><PieChart /></el-icon><span>统计报表</span>
          </el-menu-item>
          <el-menu-item index="/analysis">
            <el-icon><MagicStick /></el-icon><span>智能分析</span>
          </el-menu-item>
          <el-menu-item index="/dedup">
            <el-icon><CopyDocument /></el-icon><span>账单查重</span>
          </el-menu-item>
          <el-menu-item v-if="isOwner" index="/asset">
            <el-icon><House /></el-icon><span>资产负债</span>
          </el-menu-item>
          <el-menu-item index="/member">
            <el-icon><User /></el-icon><span>成员与账号</span>
          </el-menu-item>
          <el-menu-item index="/category">
            <el-icon><Menu /></el-icon><span>分类管理</span>
          </el-menu-item>
        </template>

        <el-menu-item v-if="isAdmin" index="/admin">
          <el-icon><Monitor /></el-icon><span>系统维护</span>
        </el-menu-item>
        <el-menu-item index="/log">
          <el-icon><Document /></el-icon><span>操作日志</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container class="workspace" direction="vertical">
      <el-header class="header">
        <div class="crumb">
          <span class="family">{{ user.familyName }}</span>
          <el-divider direction="vertical" />
          <span class="page-name">{{ route.meta.title }}</span>
        </div>
        <div class="user-area">
          <!-- 数据范围提示：普通成员需要明确知道自己只看得到自己的账 -->
          <el-tag v-if="scopeLocked" type="info" size="small" effect="plain" class="scope-tag">
            <el-icon><Lock /></el-icon>
            仅显示我（{{ user.memberName || user.realName }}）的数据
          </el-tag>
          <el-tag v-else-if="isOwner" size="small" effect="plain" class="scope-tag">
            <el-icon><View /></el-icon>
            户主视角 · 可查看全家数据
          </el-tag>

          <el-dropdown @command="onCommand">
            <span class="user-chip">
              <el-icon><UserFilled /></el-icon>
              {{ user.realName || user.username }}
              <span class="role-pill" :class="rolePillClass">{{ user.roleName }}</span>
              <el-icon class="chevron"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="newLogin">新标签页登录其他账号</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>

    <!-- 全局快速记账悬浮球（需求第 3 条） -->
    <QuickAddBall />
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { logout as doLogout } from '../api/auth'
import QuickAddBall from '../components/QuickAddBall.vue'
import { currentUser, isAdmin, isFamilyUser, isOwner, scopeLocked, ROLE } from '../utils/auth'
import { resetTabIdentity } from '../utils/authSession'

const route = useRoute()

const user = currentUser
const activeMenu = computed(() => (route.path === '/personal' ? '/me' : route.path))

const rolePillClass = computed(() => {
  if (user.value.role === ROLE.ADMIN) return 'is-admin'
  if (user.value.role === ROLE.OWNER) return 'is-owner'
  return 'is-member'
})

function onCommand(cmd) {
  if (cmd === 'newLogin') return openAnotherLogin()
  if (cmd === 'logout') return logoutThisTab()
}

/**
 * 在新标签页登录另一个账号，本标签页原样保持登录。
 *
 * 两个细节都不能改：
 * · 不能加确认框。await 之后浏览器认为已经脱离用户手势，window.open 会被当弹窗拦掉。
 * · 必须带 noopener。否则新标签页会复制本页的 sessionStorage，把身份 token 一起带过去，
 *   打开后仍是同一个账号。fresh=1 是第二道保险，见路由守卫。
 */
function openAnotherLogin() {
  window.open('/login?fresh=1', '_blank', 'noopener')
  ElMessage.success('已在新标签页打开登录页，当前账号保持登录')
}

async function logoutThisTab() {
  await ElMessageBox.confirm(
    '确认退出登录？只退出当前标签页，其他标签页登录的账号不受影响。',
    '提示',
    { type: 'warning' }
  )
  // 后端按本标签页的 token 只退掉这一个身份槽位
  await doLogout()
  resetTabIdentity()
  ElMessage.success('已退出登录')
  window.location.replace('/login')
}
</script>

<style scoped>
.layout {
  height: 100%;
  background: var(--gjp-bg);
  padding: 12px;
  gap: 12px;
}

.aside {
  background: var(--gjp-card);
  border-radius: var(--gjp-radius);
  box-shadow: var(--gjp-shadow);
  overflow-x: hidden;
  overflow-y: auto;
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 18px;
  color: var(--gjp-text);
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 1px;
}

.logo-badge {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--gjp-primary);
  background: color-mix(in srgb, var(--gjp-primary) 12%, #fff);
}

.menu {
  border-right: none;
  padding: 4px 12px 16px;
  --el-menu-bg-color: transparent;
  --el-menu-hover-bg-color: var(--gjp-primary-soft);
  --el-menu-text-color: #4b5563;
  --el-menu-active-color: #fff;
  --el-menu-hover-text-color: var(--gjp-primary);
}

.menu :deep(.el-menu-item) {
  height: 42px;
  line-height: 42px;
  margin: 4px 0;
  border-radius: 16px;
  color: #4b5563;
}

.menu :deep(.el-menu-item:hover) {
  background: var(--gjp-primary-soft);
  color: var(--gjp-primary);
}

.menu :deep(.el-menu-item.is-active) {
  background: var(--gjp-primary) !important;
  color: #fff !important;
  font-weight: 500;
  box-shadow: var(--gjp-shadow-active);
}

.menu :deep(.el-menu-item.is-active:hover) {
  background: var(--gjp-primary) !important;
  color: #fff !important;
}

.menu :deep(.el-menu-item:focus-visible) {
  outline: 2px solid var(--gjp-primary);
  outline-offset: 2px;
}

.workspace {
  min-width: 0;
  background: transparent;
}

.header {
  height: 56px;
  background: var(--gjp-card);
  border-radius: var(--gjp-radius);
  box-shadow: var(--gjp-shadow);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  margin-bottom: 12px;
}

.crumb .family {
  font-weight: 600;
  color: var(--gjp-primary);
}

.crumb .page-name {
  color: #64748b;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

.scope-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: var(--gjp-primary-soft);
  color: var(--gjp-primary);
  border: none;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  outline: none;
  color: var(--gjp-text);
  padding: 4px 4px 4px 10px;
  border-radius: var(--gjp-radius-pill);
  transition: background 0.15s ease;
}

.user-chip:hover {
  background: var(--gjp-primary-soft);
  color: var(--gjp-primary);
}

.role-pill {
  margin-left: 2px;
  padding: 2px 8px;
  border-radius: var(--gjp-radius-pill);
  font-size: 11px;
  line-height: 16px;
  color: #fff;
}

.role-pill.is-owner {
  background: var(--gjp-primary);
  box-shadow: var(--gjp-shadow-active);
}

.role-pill.is-admin {
  background: var(--gjp-expense);
}

.role-pill.is-member {
  background: #94a3b8;
}

.chevron {
  color: var(--gjp-text-light);
}

.main {
  padding: 0 2px 8px;
  overflow-y: auto;
  background: transparent;
}
</style>
