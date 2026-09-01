<template>
  <el-container class="layout">
    <el-aside width="210px" class="aside">
      <div class="logo">
        <el-icon :size="22"><Wallet /></el-icon>
        <span>管家婆</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        class="menu"
        background-color="#233a30"
        text-color="#c8d6cf"
        active-text-color="#ffffff"
        router
      >
        <el-menu-item index="/home">
          <el-icon><DataBoard /></el-icon><span>家庭看板</span>
        </el-menu-item>
        <el-menu-item index="/record">
          <el-icon><Tickets /></el-icon><span>收支流水</span>
        </el-menu-item>
        <el-menu-item index="/stat">
          <el-icon><PieChart /></el-icon><span>统计报表</span>
        </el-menu-item>
        <el-menu-item index="/analysis">
          <el-icon><MagicStick /></el-icon><span>智能分析</span>
        </el-menu-item>
        <el-menu-item index="/asset">
          <el-icon><House /></el-icon><span>资产负债</span>
        </el-menu-item>
        <el-menu-item index="/member">
          <el-icon><User /></el-icon><span>成员管理</span>
        </el-menu-item>
        <el-menu-item index="/category">
          <el-icon><Menu /></el-icon><span>分类管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="crumb">
          <span class="family">{{ user.familyName }}</span>
          <el-divider direction="vertical" />
          <span class="page-name">{{ route.meta.title }}</span>
        </div>
        <div class="user-area">
          <el-dropdown @command="onCommand">
            <span class="user-name">
              <el-icon><UserFilled /></el-icon>
              {{ user.realName || user.username }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { currentUser, logout } from '../api/auth'

const route = useRoute()
const router = useRouter()
const user = ref(JSON.parse(sessionStorage.getItem('gjp_user') || '{}'))

const activeMenu = computed(() => route.path)

onMounted(async () => {
  // 页面刷新后向后端确认 session 是否还在，避免本地有缓存但服务端已过期
  try {
    const data = await currentUser()
    user.value = data
    sessionStorage.setItem('gjp_user', JSON.stringify(data))
  } catch (e) {
    // 401 已由 request 拦截器统一跳转，这里不再重复处理
  }
})

async function onCommand(cmd) {
  if (cmd !== 'logout') return
  await ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' })
  await logout()
  sessionStorage.removeItem('gjp_user')
  ElMessage.success('已退出登录')
  router.replace('/login')
}
</script>

<style scoped>
.layout {
  height: 100%;
}

.aside {
  background: #233a30;
  overflow-x: hidden;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding-left: 20px;
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 2px;
  background: #1c2f27;
}

.menu {
  border-right: none;
}

.header {
  height: 60px;
  background: #fff;
  border-bottom: 1px solid var(--gjp-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.crumb .family {
  font-weight: 600;
  color: var(--gjp-primary);
}

.crumb .page-name {
  color: var(--gjp-text-light);
}

.user-name {
  display: flex;
  align-items: center;
  gap: 5px;
  cursor: pointer;
  outline: none;
  color: var(--gjp-text);
}

.main {
  padding: 16px;
  overflow-y: auto;
}
</style>
