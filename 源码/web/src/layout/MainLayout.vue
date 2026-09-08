<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="aside-inner">
        <div class="logo">
          <span class="logo-badge">管</span>
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
      </div>
    </el-aside>

    <el-container class="right">
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
          <el-tag v-else-if="isOwner" size="small" effect="plain" class="scope-tag scope-owner">
            <el-icon><View /></el-icon>
            户主视角 · 可查看全家数据
          </el-tag>

          <button
            type="button"
            class="theme-toggle"
            :title="isDark ? '切换日间模式' : '切换夜间模式'"
            :aria-label="isDark ? '切换日间模式' : '切换夜间模式'"
            @click="onToggleTheme"
          >
            <el-icon :size="18"><Moon v-if="!isDark" /><Sunny v-else /></el-icon>
          </button>

          <el-dropdown @command="onCommand">
            <span class="user-name">
              <el-icon><UserFilled /></el-icon>
              {{ user.realName || user.username }}
              <el-tag size="small" :type="roleTagType" effect="dark" class="role-tag">
                {{ user.roleName }}
              </el-tag>
              <el-icon><ArrowDown /></el-icon>
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
import { computed, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { logout as doLogout } from '../api/auth'
import QuickAddBall from '../components/QuickAddBall.vue'
import { currentUser, isAdmin, isFamilyUser, isOwner, scopeLocked, ROLE } from '../utils/auth'
import { resetTabIdentity } from '../utils/authSession'
import { getStoredTheme, toggleTheme } from '../utils/theme'

const route = useRoute()

const user = currentUser
const isDark = ref(getStoredTheme() === 'dark')
const activeMenu = computed(() => (route.path === '/personal' ? '/me' : route.path))

function onToggleTheme() {
  isDark.value = toggleTheme() === 'dark'
}

onMounted(() => {
  isDark.value = document.documentElement.classList.contains('dark')
})

const roleTagType = computed(() => {
  if (user.value.role === ROLE.ADMIN) return 'danger'
  if (user.value.role === ROLE.OWNER) return 'primary'
  return 'info'
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
}

.aside {
  background: transparent;
  overflow: visible;
  /* 右侧留出与主内容的呼吸间距（总间隙约 20px） */
  padding: 12px 0 12px 16px;
}

.aside-inner {
  height: 100%;
  background: var(--gjp-surface);
  border-radius: var(--gjp-radius);
  box-shadow: var(--gjp-soft-shadow);
  overflow-x: hidden;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  color: var(--gjp-text);
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 1px;
  flex-shrink: 0;
}

.logo-badge {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: rgba(79, 70, 229, 0.12);
  color: var(--gjp-primary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
}

.menu {
  border-right: none;
  background: transparent;
  padding: 0 10px 16px;
  flex: 1;
}

.menu :deep(.el-menu-item) {
  height: 42px;
  line-height: 42px;
  margin: 2px 0;
  border-radius: var(--gjp-radius-pill);
  color: var(--gjp-text-muted);
}

.menu :deep(.el-menu-item:hover) {
  background: var(--gjp-menu-hover-bg) !important;
  color: var(--gjp-primary) !important;
}

.menu :deep(.el-menu-item.is-active) {
  background: #4f46e5 !important;
  color: #fff !important;
  font-weight: 500;
  box-shadow: 0 10px 24px -6px rgba(79, 70, 229, 0.45);
}

.menu :deep(.el-menu-item.is-active .el-icon) {
  color: #fff;
}

.right {
  min-width: 0;
  /* 左侧 20px：侧栏圆角卡片与主区卡片之间的清晰间隙 */
  padding: 12px 16px 12px 20px;
}

.header {
  height: 56px;
  background: var(--gjp-surface);
  border-bottom: none;
  border-radius: var(--gjp-radius);
  box-shadow: var(--gjp-soft-shadow);
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
  color: var(--gjp-text-muted);
}

.user-area {
  display: flex;
  align-items: center;
  gap: 14px;
}

.theme-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 999px;
  background: var(--gjp-menu-hover-bg);
  color: var(--gjp-primary);
  cursor: pointer;
  transition: background 0.15s ease, transform 0.15s ease;
}

.theme-toggle:hover {
  transform: translateY(-1px);
  background: rgba(79, 70, 229, 0.18);
}

.scope-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border-radius: 999px;
}

.scope-owner {
  background: var(--gjp-scope-owner-bg) !important;
  border-color: transparent !important;
  color: var(--gjp-primary) !important;
}

.user-name {
  display: flex;
  align-items: center;
  gap: 5px;
  cursor: pointer;
  outline: none;
  color: var(--gjp-text);
}

.role-tag {
  margin-left: 2px;
  border-radius: 999px;
}

.main {
  padding: 0;
  overflow-y: auto;
  background: transparent;
}
</style>
